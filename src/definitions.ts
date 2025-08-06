export interface FlairPrinterPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
