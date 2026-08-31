package com.flair.printer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.image.BitImageWrapper;
import com.github.anastaciocintra.escpos.image.BitonalThreshold;
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import com.github.anastaciocintra.escpos.image.EscPosImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

@CapacitorPlugin(
    name = "FlairPrinter",
    permissions = {
        @Permission(
            alias = "location",
            strings = { Manifest.permission.ACCESS_FINE_LOCATION }
        )
    }
)
public class FlairPrinterPlugin extends Plugin {
    private static final String ACTION_USB_PERMISSION = "com.flair.printer.USB_PERMISSION";
    private UsbManager usbManager;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "FlairPrinters";
    private static final String PRINTERS_KEY = "printers";
    private PluginCall pendingCall;
    private UsbDevice pendingDevice;
    private byte[] escposData;
    private String logoBase64 = null;



    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            final String action = intent.getAction();
            if (!ACTION_USB_PERMISSION.equals(action)) return;

            // TYPE-SAFE getParcelableExtra on 33+
            UsbDevice dev = (Build.VERSION.SDK_INT >= 33)
                    ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                    : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            boolean grantedExtra = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

            // Defensive: also ask UsbManager in case the extra is missing/false but permission is granted now.
            boolean grantedNow = false;
            if (dev != null && usbManager != null) {
                grantedNow = usbManager.hasPermission(dev);
            } else if (pendingDevice != null && usbManager != null) {
                grantedNow = usbManager.hasPermission(pendingDevice);
                if (dev == null) dev = pendingDevice; // fall back to the one we asked for
            }

            boolean granted = grantedExtra || grantedNow;

            Log.d("FPRINT_BroadcastReceiver",
                    "action=" + action +
                            " dev=" + dev +
                            " grantedExtra=" + grantedExtra +
                            " grantedNow=" + grantedNow);

            if (granted && dev != null && pendingCall != null) {
                printToUsbDevice(dev, logoBase64, escposData, pendingCall);
                pendingCall = null;
                pendingDevice = null;
            } else if (pendingCall != null) {
                pendingCall.reject("USB permission denied");
                pendingCall = null;
                pendingDevice = null;
            }
        }
    };

     @SuppressLint("UnspecifiedRegisterReceiverFlag")
     @Override
    public void load() {
         super.load();
         usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
         // Register receiver when plugin loads
         IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
         // Android 13 (API 33) and above requires the flag
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             getContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
             Log.d("FPRINT_load", "USB receiver registered with NOT_EXPORTED");
         } else {
             getContext().registerReceiver(usbReceiver, filter);
             Log.d("FPRINT_load", "USB receiver registered (legacy)");
         }

//         Intent testIntent = new Intent(ACTION_USB_PERMISSION);
//         testIntent.setPackage(getContext().getPackageName()); // ✅ Target only this app
//         getContext().sendBroadcast(testIntent);

         prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        getContext().unregisterReceiver(usbReceiver); // ✅ Clean up
    }

    /**
     * Android has no print spooler, so there are no queues to offer. Resolves instead
     * of rejecting: the shared printer dialog uses `supported` to decide whether to
     * show the WINDOWS type, and Capacitor defines this method on every platform, so
     * the client cannot feature-detect any other way.
     */
    @PluginMethod
    public void listSystemPrinters(PluginCall call) {
        JSObject result = new JSObject();
        result.put("supported", false);
        result.put("printers", new JSArray());
        call.resolve(result);
    }

    @PluginMethod
    public void probePrinter(PluginCall call) {
        String transport = PrinterTransport.transportOf(call.getString("type"));
        JSObject result = new JSObject();

        if (!"network".equals(transport)) {
            result.put("reachable", false);
            result.put("error", "Only network printers can be probed");
            call.resolve(result);
            return;
        }

        final String connectionInfo = call.getString("connectionInfo");
        new Thread(() -> {
            JSObject probe = new JSObject();
            String[] target;
            try {
                target = PrinterTransport.parseTcpTarget(connectionInfo);
            } catch (IllegalArgumentException e) {
                probe.put("reachable", false);
                probe.put("error", e.getMessage());
                call.resolve(probe);
                return;
            }

            String label = target[0] + ":" + target[1];
            probe.put("target", label);
            long started = System.currentTimeMillis();
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(target[0], Integer.parseInt(target[1])), PrinterTransport.CONNECT_TIMEOUT_MS);
                probe.put("reachable", true);
                probe.put("latencyMs", System.currentTimeMillis() - started);
            } catch (Exception e) {
                probe.put("reachable", false);
                probe.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
            call.resolve(probe);
        }).start();
    }

    @PluginMethod
    public void scanUsbPrinters(PluginCall call) {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        JSObject result = new JSObject();
        JSONArray printers = new JSONArray();

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            // You can add filtering if needed (e.g., USB class 7 for printers)
            JSObject printer = new JSObject();
            printer.put("vendorId", device.getVendorId());
            printer.put("productId", device.getProductId());
            printer.put("deviceName", device.getDeviceName());
            printer.put("manufacturerName", device.getManufacturerName());
            printer.put("productName", device.getProductName());
            printers.put(printer);
        }

        result.put("printers", printers);
        Log.d("FPRINT_scanUsbPrinters","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void getPrinters(PluginCall call) {
        String json = prefs.getString(PRINTERS_KEY, "{}");
        JSObject result = new JSObject();
        try {
            JSONObject printersObj = new JSONObject(json);
            JSONArray arr = new JSONArray();
            for (Iterator<String> it = printersObj.keys(); it.hasNext();) {
                String key = it.next();
                JSONObject printer = printersObj.getJSONObject(key);
                printer.put("id", key);
                arr.put(printer);
            }
            result.put("printers", arr);
        } catch (JSONException e) {
            Log.e("FlairPrinter.getPrinters","Failed to parse printers JSON", e);
            call.reject("Failed to parse printers JSON", e);
            return;
        }
        Log.d("FPRINT_getPrinters","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void setPrinter(PluginCall call) {
        JSObject printerConfig = call.getObject("printer");
        if (printerConfig == null || !printerConfig.has("id")) {
            call.reject("Printer config must include 'id'");
            return;
        }
        Log.d("FPRINT_setPrinter","received: " + printerConfig.toString());
        String id = printerConfig.getString("id");
        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);
            printersObj.put(id, new JSONObject(printerConfig.toString()));
            // commit(), not apply(): apply() writes to disk on a background thread, so a
            // process death right after setup would lose a config we already reported
            // as saved. This runs once during setup, so the blocking write is free.
            if (!prefs.edit().putString(PRINTERS_KEY, printersObj.toString()).commit()) {
                call.reject("Failed to write printer to storage");
                return;
            }
        } catch (JSONException e) {
            call.reject("Failed to save printer", e);
            return;
        }

        JSObject result = new JSObject();
        result.put("success", true);
        Log.d("FPRINT_setPrinter","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void removePrinter(PluginCall call) {
        String printerId = call.getString("printerId");
        if (printerId == null || printerId.isEmpty()) {
            call.reject("Printer ID is required");
            return;
        }

        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);
            printersObj.remove(printerId); // Remove printer by ID
            if (!prefs.edit().putString(PRINTERS_KEY, printersObj.toString()).commit()) {
                call.reject("Failed to write printer to storage");
                return;
            }
        } catch (JSONException e) {
            call.reject("Failed to remove printer", e);
            return;
        }

        JSObject result = new JSObject();
        result.put("success", true);
        Log.d("FPRINT_removePrinter","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void printReceipt(PluginCall call) {
        Log.d("FPRINT_printReceipt", "Starting printReceipt method");
        String printerId = call.getString("printerId");
        if (printerId == null || printerId.isEmpty()) {
            call.reject("Printer ID is required");
            return;
        }

        Log.d("FPRINT_printReceipt", "printerId " + printerId);

        JSObject dataObj = call.getObject("data");
        if (dataObj == null) {
            call.reject("No data object provided");
            return;
        }

        logoBase64 = dataObj.getString("logo");

        Log.d("FPRINT_printReceipt", "logo " + logoBase64);

        JSArray dataArray = new JSArray();
        try {
            org.json.JSONArray rawJsonArray = dataObj.optJSONArray("raw");
            if (rawJsonArray == null || rawJsonArray.length() == 0) {
                call.reject("No ESC/POS raw data provided");
                return;
            }
            List<Object> rawList = jsonArrayToList(rawJsonArray);
            dataArray = new JSArray(rawList);

        } catch (JSONException e) {
            Log.d("FPRINT_printReceipt", "error " + e.getMessage());
            call.reject("Could not convert data");
        }


        // Convert JSArray to byte[]
        escposData = new byte[dataArray.length()]; // ✅ Store in class field for later
        for (int i = 0; i < dataArray.length(); i++) {
            escposData[i] = (byte) dataArray.optInt(i);
        }

        Log.d("FPRINT_printReceipt", "Received printerId: " + printerId);

        // ✅ Load printers from prefs
        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);

            Log.d("FPRINT_printReceipt", "Check stored printers: " + printersObj.toString());
            if (!printersObj.has(printerId)) {
                call.reject("Printer not found for ID: " + printerId);
                return;
            }

            JSONObject printerConfig = printersObj.getJSONObject(printerId);
            dispatchPrint(printerConfig, logoBase64, escposData, call);

        } catch (JSONException e) {
            call.reject("Failed to load printer config", e);
        }
    }

    /** Prints a short diagnostic receipt so a newly configured printer can be verified. */
    @PluginMethod
    public void testPrint(PluginCall call) {
        String printerId = call.getString("printerId");
        if (printerId == null || printerId.isEmpty()) {
            printerId = call.getString("id");
        }
        if (printerId == null || printerId.isEmpty()) {
            call.reject("Printer ID is required");
            return;
        }

        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);
            if (!printersObj.has(printerId)) {
                call.reject("Printer not found for ID: " + printerId);
                return;
            }

            JSONObject printerConfig = printersObj.getJSONObject(printerId);
            String transport = PrinterTransport.transportOf(printerConfig.optString("type", "USB"));
            String target = "network".equals(transport)
                    ? printerConfig.optString("connectionInfo", "-")
                    : "USB " + printerConfig.optString("connectionInfo", "-");

            byte[] receipt = PrinterTransport.buildTestReceipt(
                    printerConfig.optString("name", printerId),
                    transport == null ? printerConfig.optString("type", "-") : transport,
                    target
            );
            dispatchPrint(printerConfig, null, receipt, call);

        } catch (JSONException e) {
            call.reject("Failed to load printer config", e);
        }
    }

    /**
     * Routes a rendered job to the transport its config asks for. Shared by
     * printReceipt and testPrint so both behave identically.
     */
    private void dispatchPrint(JSONObject printerConfig, String logo, byte[] data, PluginCall call) {
        String rawType = printerConfig.optString("type", "USB");
        String transport = PrinterTransport.transportOf(rawType);

        if (transport == null) {
            call.reject("Unsupported printer type: " + rawType);
            return;
        }

        if ("windows".equals(transport)) {
            call.reject("Windows queue printers are only available in the desktop app");
            return;
        }

        if ("network".equals(transport)) {
            printToNetworkDevice(printerConfig.optString("connectionInfo", null), logo, data, call);
            return;
        }

        // --- USB ---
        String connectionInfo = printerConfig.optString("connectionInfo", null);
        if (connectionInfo == null || !connectionInfo.contains("x")) {
            call.reject("Invalid or missing connectionInfo for USB printer (expected vendorIdxproductId)");
            return;
        }

        String[] parts = connectionInfo.split("x");
        if (parts.length != 2) {
            call.reject("Invalid connectionInfo format for USB printer (expected vendorIdxproductId)");
            return;
        }

        int vendorId;
        int productId;
        try {
            vendorId = Integer.parseInt(parts[0].trim());
            productId = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            // Without this, a non-USB address that happens to contain an "x" throws
            // out of the JSONException catch and leaves the call unresolved forever.
            call.reject("Invalid USB ids in connectionInfo: " + connectionInfo);
            return;
        }

        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);

        UsbDevice target = findDevice(vendorId, productId);
        if (target == null) {
            call.reject("USB printer not found (" + vendorId + "x" + productId + ")");
            return;
        }

        // The permission broadcast finishes the job later, so the payload has to
        // outlive this call.
        this.logoBase64 = logo;
        this.escposData = data;

        if (usbManager.hasPermission(target)) {
            Log.d("FPRINT_printReceipt", "already have permission: call printToUsbDevice");
            // Already granted — print now
            printToUsbDevice(target, logo, data, call);
            return;
        }

        // Request permission
        pendingCall = call;
        pendingDevice = target;

        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                getContext(),
                0,
                new Intent(ACTION_USB_PERMISSION)
                        .setPackage(getContext().getPackageName()), // scope to our app
                flags
        );

        Log.d("FPRINT_printReceipt", "requestPermission for device: " + target);
        usbManager.requestPermission(target, permissionIntent);
    }

    private void printToNetworkDevice(String connectionInfo, String logo, byte[] data, PluginCall call) {
        final String[] target;
        try {
            target = PrinterTransport.parseTcpTarget(connectionInfo);
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage());
            return;
        }

        final String logoData = logo;
        // Sockets on the main thread throw NetworkOnMainThreadException.
        new Thread(() -> {
            Socket socket = new Socket();
            try {
                socket.connect(
                        new InetSocketAddress(target[0], Integer.parseInt(target[1])),
                        PrinterTransport.CONNECT_TIMEOUT_MS
                );
                OutputStream out = socket.getOutputStream();
                EscPos escpos = new EscPos(out);

                writeLogo(escpos, logoData);
                out.write(data);
                out.flush();
                escpos.close();

                Log.d("FPRINT_printToNetworkDevice", "print success " + target[0] + ":" + target[1]);
                JSObject resultObj = new JSObject();
                resultObj.put("success", true);
                call.resolve(resultObj);
            } catch (Exception e) {
                call.reject("Printing failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }).start();
    }

    /** Renders the optional receipt logo. Shared by the USB and network transports. */
    private void writeLogo(EscPos escpos, String logo) throws IOException {
        if (logo == null || logo.isEmpty()) return;

        String base64 = logo.startsWith("data:") ? logo.substring(logo.indexOf(',') + 1) : logo;
        byte[] logoBytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap logoBitmap = BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.length);
        if (logoBitmap == null) return;

        // Raster mode aligns and advances nicely
        com.github.anastaciocintra.escpos.image.RasterBitImageWrapper raster =
                new com.github.anastaciocintra.escpos.image.RasterBitImageWrapper();
        raster.setJustification(EscPosConst.Justification.Center);

        EscPosImage escImg = new EscPosImage(new CoffeeImageAndroidImpl(logoBitmap), new BitonalThreshold());
        escpos.write(raster, escImg);
        escpos.feed(1); // give it space so text does not collide with the image
    }

    private UsbDevice findDevice(int vendorId, int productId) {
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getVendorId() == vendorId && d.getProductId() == productId) return d;
        }
        return null;
    }

    private void printToUsbDevice(UsbDevice device, String logoBase64, byte[] data, PluginCall call) {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        Log.d("FPRINT_printToUsbDevice", "verify connection: " + (connection != null));
        if (connection == null) {
            call.reject("Failed to open USB connection even after permission granted.");
            return;
        }

        UsbInterface usbInterface = device.getInterface(0);
        connection.claimInterface(usbInterface, true);

        // Find OUT endpoint
        UsbEndpoint endpoint = null;
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                endpoint = ep;
                break;
            }
        }

        Log.d("FPRINT_printToUsbDevice", "found endpoint: " + endpoint);

        if (endpoint == null) {
            call.reject("No valid OUT endpoint found for printer.");
            connection.releaseInterface(usbInterface);
            connection.close();
            return;
        }

//        int result = connection.bulkTransfer(endpoint, data, data.length, 2000);
//        connection.releaseInterface(usbInterface);
//        connection.close();
//
//        Log.d("FPRINT_printToUsbDevice", "received result: " + result);
//
//        if (result >= 0) {
//            JSObject resultObj = new JSObject();
//            resultObj.put("success", true);
//            call.resolve(resultObj);
//        } else {
//            call.reject("Failed to send data to printer.");
//        }

        try {
            OutputStream printerOutputStream = new UsbPrinterOutputStream(connection, endpoint);
            EscPos escpos = new EscPos(printerOutputStream);

            writeLogo(escpos, logoBase64);

            // 2. Print the rest of your ESC/POS receipt data
            printerOutputStream.write(data);

            // 3. Clean up
            escpos.close();

            connection.releaseInterface(usbInterface);
            connection.close();

            Log.d("FPRINT_printToUsbDevice", "print success");
            JSObject resultObj = new JSObject();
            resultObj.put("success", true);
            call.resolve(resultObj);

        } catch (Exception e) {
            connection.releaseInterface(usbInterface);
            connection.close();
            call.reject("Printing failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void warmUp(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            Log.d("FPRINT_Terminal", "warmUp: location permission not granted, skipping silently");
            call.resolve();
            return;
        }
        boolean simulated = Boolean.TRUE.equals(call.getBoolean("simulated", false));
        StripeTerminalHandler.TokenFetcher fetcher = tokenCallback -> {
            Log.d("FPRINT_Terminal", "warmUp: Stripe SDK requesting connection token");
            StripeTerminalHandler.parkTokenCallback(tokenCallback);
            notifyListeners("connectionTokenNeeded", new JSObject());
        };
        StripeTerminalHandler.warmUp(getContext(), fetcher, simulated);
        call.resolve();
    }

    @PluginMethod
    public void executeTapAndPay(PluginCall call) {
        Log.d("FPRINT_Terminal", "executeTapAndPay called");
        String clientSecret = call.getString("paymentIntentClientSecret");
        String locationId = call.getString("locationId");
        String merchantDisplayName = call.getString("merchantDisplayName");
        boolean simulated = Boolean.TRUE.equals(call.getBoolean("simulated", false));

        if (clientSecret == null || clientSecret.isEmpty()) { call.reject("paymentIntentClientSecret is required"); return; }
        if (locationId == null || locationId.isEmpty()) { call.reject("locationId is required"); return; }
        if (merchantDisplayName == null || merchantDisplayName.isEmpty()) { call.reject("merchantDisplayName is required"); return; }

        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "locationPermissionCallback");
            return;
        }
        Log.d("FPRINT_Terminal", "location permission is fine");

        StripeTerminalHandler.TokenFetcher fetcher = tokenCallback -> {
            Log.d("FPRINT_Terminal", "Stripe SDK requesting connection token — emitting connectionTokenNeeded to JS");
            StripeTerminalHandler.parkTokenCallback(tokenCallback);
            notifyListeners("connectionTokenNeeded", new JSObject());
        };

        Log.d("FPRINT_Terminal", "calling StripeTerminalHandler.executeTapAndPay");
        StripeTerminalHandler.executeTapAndPay(
            getContext(),
            fetcher,
            clientSecret,
            locationId,
            merchantDisplayName,
            simulated,
            call
        );
    }

    @PluginMethod
    public void provideConnectionToken(PluginCall call) {
        String token = call.getString("token");
        String error = call.getString("error");
        if (error != null) {
            Log.e("FPRINT_Terminal", "JS failed to provide connection token: " + error);
            StripeTerminalHandler.rejectConnectionToken(error);
        } else if (token != null && !token.isEmpty()) {
            StripeTerminalHandler.provideConnectionToken(token);
        } else {
            StripeTerminalHandler.rejectConnectionToken("No token provided");
        }
        call.resolve();
    }

    @PermissionCallback
    private void locationPermissionCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            executeTapAndPay(call);
        } else {
            call.reject("Location permission denied — required by Stripe Terminal discoverReaders");
        }
    }

    // Helper function
    public static List<Object> jsonArrayToList(JSONArray arr) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.get(i));
        }
        return list;
    }

    // Helpers
    private Bitmap scaleToWidth(Bitmap src, int targetWidth) {
        if (src == null || targetWidth <= 0) return src;
        int w = src.getWidth(), h = src.getHeight();
        if (w == 0 || h == 0 || w == targetWidth) return src;
        int targetHeight = Math.round(h * (targetWidth / (float) w));
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true);
    }

    private Bitmap removeAlphaOnWhite(Bitmap src) {
        if (src == null || src.getConfig() == Bitmap.Config.RGB_565) return src;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(src, 0, 0, null);
        return out;
    }

    @PluginMethod
    public void setServerUrl(PluginCall call) {
        String url = call.getString("url");
        Log.d("FPRINT_", "setServerUrl called, url=" + url);
        if (url == null || url.isEmpty()) {
            call.reject("url required");
            return;
        }
        // commit() so the URL is on disk before we navigate — the load can restart the
        // WebView, and an apply() still in flight would be lost.
        if (!prefs.edit().putString("server_url", url).commit()) {
            call.reject("Failed to write server URL to storage");
            return;
        }
        Log.d("FPRINT_", "setServerUrl: saved to prefs, navigating");
        getBridge().getWebView().post(() -> getBridge().getWebView().loadUrl(url));
        call.resolve();
    }

    @PluginMethod
    public void getServerUrl(PluginCall call) {
        String saved = prefs.getString("server_url", null);
        JSObject result = new JSObject();
        if (saved != null) {
            result.put("url", saved);
        } else {
            result.put("url", JSObject.NULL);
        }
        call.resolve(result);
    }

    @PluginMethod
    public void clearServerUrl(PluginCall call) {
        prefs.edit().remove("server_url").commit();
        // Navigate to the env picker so the user can choose a new environment.
        getBridge().getWebView().post(() ->
                getBridge().getWebView().loadUrl("file:///android_asset/public/launcher.html"));
        call.resolve();
    }

    @PluginMethod
    public void openEnvPicker(PluginCall call) {
        String current = getBridge().getWebView().getUrl();
        String suffix = (current != null && !current.startsWith("file://"))
                ? "?current=" + Uri.encode(current)
                : "";
        getBridge().getWebView().post(() ->
                getBridge().getWebView().loadUrl(
                        "file:///android_asset/public/launcher.html" + suffix));
        call.resolve();
    }


}

