# flair-printer

This plugin allows printing receipts via USB (ESCPOS)

## Install

```bash
npm install flair-printer
npx cap sync
```

## API

<docgen-index>

* [`getPrinters()`](#getprinters)
* [`scanUsbPrinters()`](#scanusbprinters)
* [`setPrinter(...)`](#setprinter)
* [`removePrinter(...)`](#removeprinter)
* [`printReceipt(...)`](#printreceipt)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### getPrinters()

```typescript
getPrinters() => Promise<{ printers: Printer[]; }>
```

**Returns:** <code>Promise&lt;{ printers: Printer[]; }&gt;</code>

--------------------


### scanUsbPrinters()

```typescript
scanUsbPrinters() => Promise<{ printers: USBPrinter[]; }>
```

**Returns:** <code>Promise&lt;{ printers: USBPrinter[]; }&gt;</code>

--------------------


### setPrinter(...)

```typescript
setPrinter({ printer }: { printer: Printer; }) => Promise<PrinterResult>
```

| Param     | Type                                                      |
| --------- | --------------------------------------------------------- |
| **`__0`** | <code>{ printer: <a href="#printer">Printer</a>; }</code> |

**Returns:** <code>Promise&lt;<a href="#printerresult">PrinterResult</a>&gt;</code>

--------------------


### removePrinter(...)

```typescript
removePrinter({ printerId }: { printerId: string; }) => Promise<PrinterResult>
```

| Param     | Type                                |
| --------- | ----------------------------------- |
| **`__0`** | <code>{ printerId: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#printerresult">PrinterResult</a>&gt;</code>

--------------------


### printReceipt(...)

```typescript
printReceipt({ printerId, data, }: { printerId: string; data: { raw: number[]; logo?: string; }; }) => Promise<PrinterResult>
```

| Param     | Type                                                                         |
| --------- | ---------------------------------------------------------------------------- |
| **`__0`** | <code>{ printerId: string; data: { raw: number[]; logo?: string; }; }</code> |

**Returns:** <code>Promise&lt;<a href="#printerresult">PrinterResult</a>&gt;</code>

--------------------


### Interfaces


#### Printer

| Prop                 | Type                                                |
| -------------------- | --------------------------------------------------- |
| **`id`**             | <code>string</code>                                 |
| **`name`**           | <code>string</code>                                 |
| **`type`**           | <code><a href="#printertype">PrinterType</a></code> |
| **`connectionInfo`** | <code>string</code>                                 |
| **`lineWidth`**      | <code>number</code>                                 |


#### USBPrinter

| Prop            | Type                |
| --------------- | ------------------- |
| **`vendorId`**  | <code>string</code> |
| **`productId`** | <code>string</code> |


#### PrinterResult

| Prop          | Type                 |
| ------------- | -------------------- |
| **`success`** | <code>boolean</code> |
| **`message`** | <code>string</code>  |


### Type Aliases


#### PrinterType

<code>'USB' | 'HTTP'</code>

</docgen-api>
