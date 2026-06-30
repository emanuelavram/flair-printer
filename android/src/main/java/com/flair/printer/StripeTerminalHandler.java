package com.flair.printer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback;
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider;
import com.stripe.stripeterminal.external.callable.DiscoveryListener;
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback;
import com.stripe.stripeterminal.external.callable.ReaderCallback;
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener;
import com.stripe.stripeterminal.external.callable.TerminalListener;
import com.stripe.stripeterminal.external.models.CollectPaymentIntentConfiguration;
import com.stripe.stripeterminal.external.models.ConfirmPaymentIntentConfiguration;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration;
import com.stripe.stripeterminal.external.models.ConnectionTokenException;
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration;
import com.stripe.stripeterminal.external.models.PaymentIntent;
import com.stripe.stripeterminal.external.models.PaymentIntentStatus;
import com.stripe.stripeterminal.external.models.Reader;
import com.stripe.stripeterminal.external.models.TapUseCase;
import com.stripe.stripeterminal.external.models.TerminalErrorCode;
import com.stripe.stripeterminal.external.models.TerminalException;
import com.stripe.stripeterminal.log.LogLevel;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the Stripe Terminal SDK lifecycle for a single Tap to Pay payment.
 * Targets stripeterminal:5.5.1 split artifacts (stripeterminal-core + stripeterminal-taptopay).
 *
 * Flow per call:
 *   init (once per process) → discoverReaders → connectReader
 *   → retrievePaymentIntent → collectPaymentMethod (native NFC overlay)
 *   → confirmPaymentIntent → disconnectReader → resolve PluginCall
 *
 * All Terminal API calls run on the main thread as required by the SDK.
 */
public class StripeTerminalHandler {

    private static final String TAG = "FPRINT_Terminal";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Called by the plugin whenever Stripe needs a fresh connection token.
     * The implementation decides how to source the token (e.g. pass through
     * a JS-provided token on the first call, emit an event to JS on re-auth).
     */
    public interface TokenFetcher {
        void fetchToken(@NonNull ConnectionTokenCallback callback);
    }

    // Terminal is a process-wide singleton — can only be initialized once.
    private static volatile boolean initialized = false;
    private static volatile TokenFetcher tokenFetcher = null;

    // Prevents concurrent Tap to Pay sessions from corrupting shared SDK state.
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    // Holds a pending SDK token request while we wait for JS to supply a fresh token.
    private static final java.util.concurrent.atomic.AtomicReference<ConnectionTokenCallback>
            pendingTokenCallback = new java.util.concurrent.atomic.AtomicReference<>(null);

    private static final ConnectionTokenProvider TOKEN_PROVIDER = callback -> {
        TokenFetcher fetcher = tokenFetcher;
        if (fetcher != null) {
            fetcher.fetchToken(callback);
        } else {
            callback.onFailure(new ConnectionTokenException("No token fetcher configured"));
        }
    };

    /** Called by the plugin's TokenFetcher to park the SDK callback while waiting for JS. */
    public static void parkTokenCallback(ConnectionTokenCallback callback) {
        pendingTokenCallback.set(callback);
    }

    /**
     * Called by the plugin when JS provides a fresh connection token in response
     * to a "connectionTokenNeeded" event. Fulfills the pending SDK callback.
     */
    public static void provideConnectionToken(String token) {
        ConnectionTokenCallback cb = pendingTokenCallback.getAndSet(null);
        if (cb != null) {
            Log.d(TAG, "connectionToken provided by JS, forwarding to Stripe SDK");
            cb.onSuccess(token);
        } else {
            Log.w(TAG, "provideConnectionToken called but no pending callback — ignoring");
        }
    }

    /**
     * Called by the plugin when JS fails to provide a token (e.g. network error).
     */
    public static void rejectConnectionToken(String reason) {
        ConnectionTokenCallback cb = pendingTokenCallback.getAndSet(null);
        if (cb != null) {
            Log.e(TAG, "connectionToken rejected by JS: " + reason);
            cb.onFailure(new ConnectionTokenException(reason));
        }
    }

    // v5: TerminalListener only has default methods.
    private static final TerminalListener TERMINAL_LISTENER = new TerminalListener() {};

    // -------------------------------------------------------------------------
    // Entry point — called from FlairPrinterPlugin
    // -------------------------------------------------------------------------

    public static void executeTapAndPay(
            Context context,
            TokenFetcher fetcher,
            String clientSecret,
            String locationId,
            String merchantDisplayName,
            boolean simulated,
            PluginCall call
    ) {
        Log.d(TAG, "StripeTerminalHandler.executeTapAndPay called");
        if (!busy.compareAndSet(false, true)) {
            JSObject r = new JSObject();
            r.put("status", "error");
            r.put("errorCode", "busy");
            r.put("errorMessage", "A Tap to Pay transaction is already in progress");
            call.resolve(r);
            return;
        }

        tokenFetcher = fetcher;

        MAIN.post(() -> {
            Log.d(TAG, "MAIN.post block started");
            if (!initialized) {
                try {
                    // v5: Terminal.init() replaces Terminal.initTerminal(); OfflineListener is optional (null).
                    Terminal.init(context, LogLevel.VERBOSE, TOKEN_PROVIDER, TERMINAL_LISTENER, null);
                    initialized = true;
                    Log.d(TAG, "Terminal initialized");
                } catch (TerminalException e) {
                    Log.e(TAG, "Init failed: " + e.getMessage());
                    resolveError(call, "terminal_init_failed", e.getMessage());
                    return;
                }
            }

            Terminal terminal = Terminal.getInstance();

            // Disconnect a stale reader before starting a new payment.
            if (terminal.getConnectedReader() != null) {
                Log.d(TAG, "Stale reader — disconnecting first");
                terminal.disconnectReader(new Callback() {
                    @Override public void onSuccess() {
                        Log.d(TAG, "Stale reader disconnected");
                        startDiscovery(terminal, clientSecret, locationId, merchantDisplayName, simulated, call);
                    }
                    @Override public void onFailure(@NonNull TerminalException e) {
                        Log.d(TAG, "Stale reader disconnect failed: " + e.getMessage());
                        startDiscovery(terminal, clientSecret, locationId, merchantDisplayName, simulated, call);
                    }
                });
            } else {
                startDiscovery(terminal, clientSecret, locationId, merchantDisplayName, simulated, call);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Step 1: discover the phone as a Tap to Pay reader
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private static void startDiscovery(
            Terminal terminal,
            String clientSecret,
            String locationId,
            String merchantDisplayName,
            boolean simulated,
            PluginCall call
    ) {
        Log.d(TAG, "startDiscovery called");
        DiscoveryConfiguration.TapToPayDiscoveryConfiguration config =
                new DiscoveryConfiguration.TapToPayDiscoveryConfiguration(simulated);

        AtomicBoolean connecting = new AtomicBoolean(false);
        Cancelable[] holder = {null};

        holder[0] = terminal.discoverReaders(
                config,
                new DiscoveryListener() {
                    @Override
                    // v5: DiscoveryListener requires List<Reader>, not List<? extends Reader>
                    public void onUpdateDiscoveredReaders(@NonNull List<Reader> readers) {
                        if (readers.isEmpty()) return;
                        if (!connecting.compareAndSet(false, true)) return;

                        Reader reader = readers.get(0);
                        Log.d(TAG, "Reader found: " + reader.getDeviceType());

                        Cancelable c = holder[0];
                        if (c != null) {
                            c.cancel(new Callback() {
                                @Override public void onSuccess() {
                                    Log.d(TAG, "Discovery canceled, connecting to reader");
                                    MAIN.post(() -> connectReader(terminal, reader, clientSecret, locationId, merchantDisplayName, call));
                                }
                                @Override public void onFailure(@NonNull TerminalException e) {
                                    Log.d(TAG, "Discovery cancel failed (" + e.getMessage() + "), connecting to reader anyway");
                                    MAIN.post(() -> connectReader(terminal, reader, clientSecret, locationId, merchantDisplayName, call));
                                }
                            });
                        }
                    }
                },
                new Callback() {
                    @Override public void onSuccess() { Log.d(TAG, "Discovery ended"); }
                    @Override public void onFailure(@NonNull TerminalException e) {
                        // v5: TerminalErrorCode is a top-level class, not nested in TerminalException
                        if (e.getErrorCode() == TerminalErrorCode.CANCELED) return;
                        Log.e(TAG, "Discovery failed: " + e.getMessage());
                        resolveError(call, "discovery_failed", e.getMessage());
                    }
                }
        );
    }

    // -------------------------------------------------------------------------
    // Step 2: connect to the reader
    // -------------------------------------------------------------------------

    private static void connectReader(
            Terminal terminal,
            Reader reader,
            String clientSecret,
            String locationId,
            String merchantDisplayName,
            PluginCall call
    ) {
        // v5: LocalMobileConnectionConfiguration → TapToPayConnectionConfiguration.
        // locationId is wrapped in TapUseCase.Pay; listener is embedded in config (not a separate param).
        ConnectionConfiguration.TapToPayConnectionConfiguration connConfig =
                new ConnectionConfiguration.TapToPayConnectionConfiguration(
                        new TapUseCase.Pay(locationId),
                        false,  // autoReconnectOnUnexpectedDisconnect
                        new TapToPayReaderListener() {},  // all methods have default implementations
                        merchantDisplayName
                );

        Log.d(TAG, "connectReader called");
        // v5: connectLocalMobileReader → connectReader (unified method for all reader types)
        terminal.connectReader(
                reader,
                connConfig,
                new ReaderCallback() {
                    @Override
                    public void onSuccess(@NonNull Reader r) {
                        Log.d(TAG, "Reader connected");
                        retrievePaymentIntent(terminal, clientSecret, call);
                    }
                    @Override
                    public void onFailure(@NonNull TerminalException e) {
                        Log.e(TAG, "Connect failed: " + e.getMessage());
                        resolveError(call, "connect_failed", e.getMessage());
                    }
                }
        );
    }

    // -------------------------------------------------------------------------
    // Step 3: retrieve the PaymentIntent
    // -------------------------------------------------------------------------

    private static void retrievePaymentIntent(Terminal terminal, String clientSecret, PluginCall call) {
        Log.d(TAG, "retrievePaymentIntent called");
        terminal.retrievePaymentIntent(clientSecret, new PaymentIntentCallback() {
            @Override
            public void onSuccess(@NonNull PaymentIntent pi) {
                Log.d(TAG, "PI retrieved: " + pi.getId());
                collectPaymentMethod(terminal, pi, call);
            }
            @Override
            public void onFailure(@NonNull TerminalException e) {
                Log.e(TAG, "Retrieve failed: " + e.getMessage());
                disconnectThen(terminal, () -> resolveError(call, "retrieve_failed", e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Step 4: collect — shows the native NFC overlay; Cancel fires CANCELED error
    // -------------------------------------------------------------------------

    private static void collectPaymentMethod(Terminal terminal, PaymentIntent pi, PluginCall call) {
        Log.d(TAG, "collectPaymentMethod called");
        // v5: CollectConfiguration → CollectPaymentIntentConfiguration; config is now the LAST param.
        CollectPaymentIntentConfiguration collectConfig =
                new CollectPaymentIntentConfiguration.Builder().build();

        terminal.collectPaymentMethod(pi, new PaymentIntentCallback() {
            @Override
            public void onSuccess(@NonNull PaymentIntent collected) {
                Log.d(TAG, "Payment method collected");
                confirmPaymentIntent(terminal, collected, call);
            }
            @Override
            public void onFailure(@NonNull TerminalException e) {
                if (e.getErrorCode() == TerminalErrorCode.CANCELED) {
                    Log.d(TAG, "Payment canceled by user");
                    disconnectThen(terminal, () -> resolveResult(call, "canceled", pi.getId(), null, null));
                } else {
                    Log.e(TAG, "Collect failed: " + e.getMessage());
                    disconnectThen(terminal, () -> resolveError(call, "collect_failed", e.getMessage()));
                }
            }
        }, collectConfig);
    }

    // -------------------------------------------------------------------------
    // Step 5: confirm
    // -------------------------------------------------------------------------

    private static void confirmPaymentIntent(Terminal terminal, PaymentIntent pi, PluginCall call) {
        // v5: confirmPaymentIntent takes an optional ConfirmPaymentIntentConfiguration as 3rd param.
        terminal.confirmPaymentIntent(pi, new PaymentIntentCallback() {
            @Override
            public void onSuccess(@NonNull PaymentIntent confirmed) {
                String status = confirmed.getStatus() == PaymentIntentStatus.REQUIRES_CAPTURE
                        ? "requires_capture"
                        : "succeeded";
                Log.d(TAG, "Confirmed, status: " + status);
                disconnectThen(terminal, () -> resolveResult(call, status, confirmed.getId(), null, null));
            }
            @Override
            public void onFailure(@NonNull TerminalException e) {
                Log.e(TAG, "Confirm failed: " + e.getMessage());
                disconnectThen(terminal, () -> resolveError(call, "confirm_failed", e.getMessage()));
            }
        }, new ConfirmPaymentIntentConfiguration.Builder().build());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void disconnectThen(Terminal terminal, Runnable after) {
        terminal.disconnectReader(new Callback() {
            @Override public void onSuccess() { after.run(); }
            @Override public void onFailure(@NonNull TerminalException e) {
                Log.w(TAG, "Disconnect non-fatal: " + e.getMessage());
                after.run();
            }
        });
    }

    private static void resolveResult(
            PluginCall call, String status, String piId, String errorMsg, String errorCode) {
        tokenFetcher = null; // clear so background SDK calls between payments don't consume a future token
        busy.set(false);
        JSObject result = new JSObject();
        result.put("status", status);
        if (piId != null) result.put("paymentIntentId", piId);
        if (errorMsg != null) result.put("errorMessage", errorMsg);
        if (errorCode != null) result.put("errorCode", errorCode);
        call.resolve(result);
    }

    private static void resolveError(PluginCall call, String errorCode, String errorMessage) {
        resolveResult(call, "error", null, errorMessage, errorCode);
    }
}
