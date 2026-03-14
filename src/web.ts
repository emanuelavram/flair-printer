import { WebPlugin } from '@capacitor/core';
import type { FlairPrinterPlugin, Printer, USBPrinter, PrinterResult } from './definitions';

export class FlairPrinterWeb extends WebPlugin implements FlairPrinterPlugin {
  async getPrinters(): Promise<{ printers: Printer[] }> {
    throw this.unavailable('getPrinters is not available on web.');
  }

  async scanUsbPrinters(): Promise<{ printers: USBPrinter[] }> {
    throw this.unavailable('scanUsbPrinters is not available on web.');
  }

  async setPrinter(_: { printer: Printer }): Promise<PrinterResult> {
    throw this.unavailable('setPrinter is not available on web.');
  }

  async removePrinter(_: { printerId: string }): Promise<PrinterResult> {
    throw this.unavailable('removePrinter is not available on web.');
  }

  async testPrint(_: { printerId: string }): Promise<PrinterResult> {
    throw this.unavailable('testPrint is not available on web.');
  }

  async printReceipt(_: any): Promise<PrinterResult> {
    throw this.unavailable('printReceipt is not available on web.');
  }
}
