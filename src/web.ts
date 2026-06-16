import { WebPlugin } from '@capacitor/core';
import type { FlairPrinterPlugin, Printer, USBPrinter, PrinterResult, TapAndPayOptions, TapAndPayResult } from './definitions';

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

  async executeTapAndPay(_: TapAndPayOptions): Promise<TapAndPayResult> {
    throw this.unavailable('executeTapAndPay is not available on web.');
  }

  async provideConnectionToken(_: { token: string }): Promise<void> {
    throw this.unavailable('provideConnectionToken is not available on web.');
  }

  async setServerUrl(_: { url: string }): Promise<void> {
    throw this.unavailable('setServerUrl is not available on web.');
  }

  async getServerUrl(): Promise<{ url: string | null }> {
    throw this.unavailable('getServerUrl is not available on web.');
  }

  async clearServerUrl(): Promise<void> {
    throw this.unavailable('clearServerUrl is not available on web.');
  }

  async openEnvPicker(): Promise<void> {
    throw this.unavailable('openEnvPicker is not available on web.');
  }
}
