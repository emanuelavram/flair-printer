import type { PluginListenerHandle } from '@capacitor/core';

type PrinterType = 'USB' | 'HTTP';

export interface PrinterResult {
  success: boolean;
  message?: string;
}

export interface Printer {
  id: string;
  name?: string;
  type?: PrinterType;
  connectionInfo?: string;
  lineWidth?: number;
}

export interface USBPrinter {
  vendorId: string;
  productId: string;
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
  scanUsbPrinters(): Promise<{ printers: USBPrinter[] }>;
  setPrinter({ printer }: { printer: Printer }): Promise<PrinterResult>;
  removePrinter({ printerId }: { printerId: string }): Promise<PrinterResult>;
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
