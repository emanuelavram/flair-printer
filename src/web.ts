import { WebPlugin } from '@capacitor/core';

import type { FlairPrinterPlugin } from './definitions';

export class FlairPrinterWeb extends WebPlugin implements FlairPrinterPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
