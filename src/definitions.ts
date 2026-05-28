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
  /** Short-lived Terminal connection token — backend: stripe.terminal.connectionTokens.create() scoped to the connected account */
  connectionToken: string;
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
  executeTapAndPay(options: TapAndPayOptions): Promise<TapAndPayResult>;
}
