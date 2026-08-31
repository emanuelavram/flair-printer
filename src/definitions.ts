import type { PluginListenerHandle } from '@capacitor/core';

/**
 * How the bridge reaches the printer.
 *
 * - `WINDOWS` — a Windows print queue, addressed by name and fed raw ESC/POS through
 *   the spooler. Desktop only, and the preferred choice for a USB receipt printer on
 *   Windows: it needs no driver swap.
 * - `NETWORK` / `TCP` — raw ESC/POS over TCP, default port 9100. Desktop and Android.
 * - `USB` — direct USB. On Android this is the USB host API; on desktop it is libusb,
 *   which on Windows only works after the printer has been moved off its print driver
 *   onto WinUSB (Zadig). Prefer `WINDOWS` there.
 *
 * `HTTP` was the old name for `NETWORK` — it was never HTTP. It has been removed, and
 * the bridges no longer accept it: a printer still stored as `HTTP` is rejected with
 * "Unsupported printer type" and has to be re-saved as `NETWORK`.
 */
export type PrinterType = 'USB' | 'NETWORK' | 'TCP' | 'WINDOWS';

export interface PrinterResult {
  success: boolean;
  message?: string;
}

export interface Printer {
  id: string;
  name?: string;
  type?: PrinterType;
  /**
   * Address of the printer, interpreted per `type`:
   * - `NETWORK` / `TCP` — `host` or `host:port` (port defaults to 9100)
   * - `USB` — `vendorIdxproductId`, e.g. `5380x156`
   * - `WINDOWS` — the print queue name; `queueName` is preferred but this is accepted
   */
  connectionInfo?: string;
  /** Windows print queue name, exactly as returned by `listSystemPrinters()`. */
  queueName?: string;
  lineWidth?: number;
}

export interface USBPrinter {
  vendorId: string;
  productId: string;
}

/** An OS print queue, as reported by the host platform. */
export interface SystemPrinter {
  /** Queue name to store as `queueName` — this is what the spooler resolves. */
  name: string;
  /** Human-readable name for the picker; falls back to `name`. */
  displayName: string;
  description?: string;
  status?: number;
  isDefault?: boolean;
}

export interface SystemPrintersResult {
  /**
   * False wherever the platform has no print spooler (Android, web).
   *
   * Check this rather than testing whether the method exists — Capacitor defines every
   * declared method on every platform, so `typeof plugin.listSystemPrinters` is always
   * `'function'` and cannot be used for feature detection.
   */
  supported: boolean;
  printers: SystemPrinter[];
  error?: string;
}

export interface ProbeResult {
  reachable: boolean;
  /** The `host:port` that was actually dialled. */
  target?: string;
  latencyMs?: number;
  error?: string;
}

export interface TapAndPayOptions {
  /** Client secret of a PaymentIntent created on your backend (payment_method_types: ['card_present']) */
  paymentIntentClientSecret: string;
  /** Stripe Terminal location ID belonging to the connected account */
  locationId: string;
  /** Business name shown on the native payment overlay */
  merchantDisplayName: string;
  /** Use simulated reader — for testing only, no real card needed. Defaults to false. */
  simulated?: boolean;
}

export interface TapAndPayResult {
  /**
   * succeeded        — payment done (auto-capture PI)
   * requires_capture — collected, call POST /capture on your backend with paymentIntentId
   * canceled         — user pressed Cancel on the overlay
   * error            — something failed, check errorMessage
   */
  status: 'succeeded' | 'requires_capture' | 'canceled' | 'error';
  /** PaymentIntent ID — present for succeeded / requires_capture / canceled */
  paymentIntentId?: string;
  errorMessage?: string;
  errorCode?: string;
}

declare module '@capacitor/core' {
  interface PluginRegistry {
    FlairPrinter: FlairPrinterPlugin;
  }
}

export interface FlairPrinterPlugin {
  getPrinters(): Promise<{ printers: Printer[] }>;
  /**
   * libusb / USB-host discovery. On Windows this only finds printers that have been
   * switched to a WinUSB driver — use `listSystemPrinters()` there instead.
   */
  scanUsbPrinters(): Promise<{ printers: USBPrinter[] }>;
  /**
   * The OS print queues available for `type: 'WINDOWS'` printers.
   * Resolves `{ supported: false, printers: [] }` on platforms without a spooler.
   */
  listSystemPrinters(): Promise<SystemPrintersResult>;
  /**
   * Check that a network printer answers before its config is saved.
   * Only `NETWORK` / `TCP` printers can be probed.
   */
  probePrinter(options: { type?: PrinterType; connectionInfo?: string }): Promise<ProbeResult>;
  setPrinter({ printer }: { printer: Printer }): Promise<PrinterResult>;
  removePrinter({ printerId }: { printerId: string }): Promise<PrinterResult>;
  /** Print a short diagnostic receipt to verify a printer config. */
  testPrint({ printerId }: { printerId: string }): Promise<PrinterResult>;
  printReceipt({
    printerId,
    data,
  }: {
    printerId: string;
    data: { raw: number[]; logo?: string };
  }): Promise<PrinterResult>;
  /**
   * Prime the Stripe Terminal SDK in the background so the first payment is fast.
   * Call this on app/device page load with the connectionTokenNeeded listener already set up.
   * Fire-and-forget — resolves immediately while warmup runs in the background.
   */
  warmUp(options?: { simulated?: boolean }): Promise<void>;
  executeTapAndPay(options: TapAndPayOptions): Promise<TapAndPayResult>;
  /**
   * Supply a fresh connection token to the Stripe Terminal SDK.
   * Call this in response to the `fetchConnectionToken` event (re-auth / reconnect scenarios).
   */
  provideConnectionToken(options: { token: string }): Promise<void>;
  /**
   * Fired by the native plugin every time the Stripe Terminal SDK needs a fresh
   * connection token (on init, discovery, connect, and any SDK reconnect).
   * Fetch a new token from your backend and call provideConnectionToken().
   */
  addListener(
    eventName: 'connectionTokenNeeded',
    listenerFunc: () => void,
  ): Promise<PluginListenerHandle>;
  /** Persist a new server URL and immediately navigate the WebView to it. */
  setServerUrl(options: { url: string }): Promise<void>;
  /** Return the currently-saved server URL, or null if none is stored (falls back to build-time default). */
  getServerUrl(): Promise<{ url: string | null }>;
  /** Clear the saved server URL and open the env picker so the user can choose a new one. */
  clearServerUrl(): Promise<void>;
  /** Load the environment-switcher page (launcher.html) over the current WebView. */
  openEnvPicker(): Promise<void>;
}
