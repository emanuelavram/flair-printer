import { registerPlugin } from '@capacitor/core';

import type { FlairPrinterPlugin } from './definitions';

const FlairPrinter = registerPlugin<FlairPrinterPlugin>('FlairPrinter', {
  web: () => import('./web').then((m) => new m.FlairPrinterWeb()),
});

export * from './definitions';
export { FlairPrinter };
