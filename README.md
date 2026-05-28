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
* [`executeTapAndPay(...)`](#executetapandpay)
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


### executeTapAndPay(...)

```typescript
executeTapAndPay(options: TapAndPayOptions) => Promise<TapAndPayResult>
```

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#tapandpayoptions">TapAndPayOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#tapandpayresult">TapAndPayResult</a>&gt;</code>

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


#### TapAndPayResult

| Prop                  | Type                                                                    | Description                                                                                                                                                                                                                 |
| --------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`status`**          | <code>'error' \| 'succeeded' \| 'requires_capture' \| 'canceled'</code> | succeeded — payment done (auto-capture PI) requires_capture — collected, call POST /capture on your backend with paymentIntentId canceled — user pressed Cancel on the overlay error — something failed, check errorMessage |
| **`paymentIntentId`** | <code>string</code>                                                     | PaymentIntent ID — present for succeeded / requires_capture / canceled                                                                                                                                                      |
| **`errorMessage`**    | <code>string</code>                                                     |                                                                                                                                                                                                                             |
| **`errorCode`**       | <code>string</code>                                                     |                                                                                                                                                                                                                             |


#### TapAndPayOptions

| Prop                            | Type                 | Description                                                                                                                |
| ------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| **`paymentIntentClientSecret`** | <code>string</code>  | Client secret of a PaymentIntent created on your backend (payment_method_types: ['card_present'])                          |
| **`connectionToken`**           | <code>string</code>  | Short-lived Terminal connection token — backend: stripe.terminal.connectionTokens.create() scoped to the connected account |
| **`locationId`**                | <code>string</code>  | Stripe Terminal location ID belonging to the connected account                                                             |
| **`merchantDisplayName`**       | <code>string</code>  | Business name shown on the native payment overlay                                                                          |
| **`simulated`**                 | <code>boolean</code> | Use simulated reader — for testing only, no real card needed. Defaults to false.                                           |


### Type Aliases


#### PrinterType

<code>'USB' | 'HTTP'</code>

</docgen-api>
