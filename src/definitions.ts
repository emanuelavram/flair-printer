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
    data: { raw: number[]; logo?: string } /* Receipt */;
  }): Promise<PrinterResult>;
}
