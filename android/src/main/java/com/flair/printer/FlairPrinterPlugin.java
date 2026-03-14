package com.flair.printer;

import android.graphics.Color;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.image.BitImageWrapper;
import com.github.anastaciocintra.escpos.image.BitonalThreshold;
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import com.github.anastaciocintra.escpos.image.EscPosImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

@CapacitorPlugin(name = "FlairPrinter")
public class FlairPrinterPlugin extends Plugin {
    private static final String ACTION_USB_PERMISSION = "com.flair.printer.USB_PERMISSION";
    private UsbManager usbManager;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "FlairPrinters";
    private static final String PRINTERS_KEY = "printers";
    private PluginCall pendingCall;
    private UsbDevice pendingDevice;
    private byte[] escposData;
    private String logoBase64 = null;


    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            final String action = intent.getAction();
            if (!ACTION_USB_PERMISSION.equals(action)) return;

            // TYPE-SAFE getParcelableExtra on 33+
            UsbDevice dev = (Build.VERSION.SDK_INT >= 33)
                    ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                    : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            boolean grantedExtra = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

            // Defensive: also ask UsbManager in case the extra is missing/false but permission is granted now.
            boolean grantedNow = false;
            if (dev != null && usbManager != null) {
                grantedNow = usbManager.hasPermission(dev);
            } else if (pendingDevice != null && usbManager != null) {
                grantedNow = usbManager.hasPermission(pendingDevice);
                if (dev == null) dev = pendingDevice; // fall back to the one we asked for
            }

            boolean granted = grantedExtra || grantedNow;

            Log.d("FPRINT_BroadcastReceiver",
                    "action=" + action +
                            " dev=" + dev +
                            " grantedExtra=" + grantedExtra +
                            " grantedNow=" + grantedNow);

            if (granted && dev != null && pendingCall != null) {
                printToUsbDevice(dev, logoBase64, escposData, pendingCall);
                pendingCall = null;
                pendingDevice = null;
            } else if (pendingCall != null) {
                pendingCall.reject("USB permission denied");
                pendingCall = null;
                pendingDevice = null;
            }
        }
    };

     @Override
    public void load() {
         super.load();
         usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
         // Register receiver when plugin loads
         IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
         // Android 13 (API 33) and above requires the flag
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             getContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
             Log.d("FPRINT_load", "USB receiver registered with NOT_EXPORTED");
         } else {
             getContext().registerReceiver(usbReceiver, filter);
             Log.d("FPRINT_load", "USB receiver registered (legacy)");
         }

//         Intent testIntent = new Intent(ACTION_USB_PERMISSION);
//         testIntent.setPackage(getContext().getPackageName()); // ✅ Target only this app
//         getContext().sendBroadcast(testIntent);

         prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        getContext().unregisterReceiver(usbReceiver); // ✅ Clean up
    }

    @PluginMethod
    public void scanUsbPrinters(PluginCall call) {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        JSObject result = new JSObject();
        JSONArray printers = new JSONArray();

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            // You can add filtering if needed (e.g., USB class 7 for printers)
            JSObject printer = new JSObject();
            printer.put("vendorId", device.getVendorId());
            printer.put("productId", device.getProductId());
            printer.put("deviceName", device.getDeviceName());
            printer.put("manufacturerName", device.getManufacturerName());
            printer.put("productName", device.getProductName());
            printers.put(printer);
        }

        result.put("printers", printers);
        Log.d("FPRINT_scanUsbPrinters","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void getPrinters(PluginCall call) {
        String json = prefs.getString(PRINTERS_KEY, "{}");
        JSObject result = new JSObject();
        try {
            JSONObject printersObj = new JSONObject(json);
            JSONArray arr = new JSONArray();
            for (Iterator<String> it = printersObj.keys(); it.hasNext();) {
                String key = it.next();
                JSONObject printer = printersObj.getJSONObject(key);
                printer.put("id", key);
                arr.put(printer);
            }
            result.put("printers", arr);
        } catch (JSONException e) {
            Log.e("FlairPrinter.getPrinters","Failed to parse printers JSON", e);
            call.reject("Failed to parse printers JSON", e);
            return;
        }
        Log.d("FPRINT_getPrinters","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void setPrinter(PluginCall call) {
        JSObject printerConfig = call.getObject("printer");
        if (printerConfig == null || !printerConfig.has("id")) {
            call.reject("Printer config must include 'id'");
            return;
        }
        Log.d("FPRINT_setPrinter","received: " + printerConfig.toString());
        String id = printerConfig.getString("id");
        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);
            printersObj.put(id, new JSONObject(printerConfig.toString()));
            prefs.edit().putString(PRINTERS_KEY, printersObj.toString()).apply();
        } catch (JSONException e) {
            call.reject("Failed to save printer", e);
            return;
        }

        JSObject result = new JSObject();
        result.put("success", true);
        Log.d("FPRINT_setPrinter","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void removePrinter(PluginCall call) {
        String printerId = call.getString("printerId");
        if (printerId == null || printerId.isEmpty()) {
            call.reject("Printer ID is required");
            return;
        }

        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);
            printersObj.remove(printerId); // Remove printer by ID
            prefs.edit().putString(PRINTERS_KEY, printersObj.toString()).apply();
        } catch (JSONException e) {
            call.reject("Failed to remove printer", e);
            return;
        }

        JSObject result = new JSObject();
        result.put("success", true);
        Log.d("FPRINT_removePrinter","Result: " + result.toString());
        call.resolve(result);
    }

    @PluginMethod
    public void printReceipt(PluginCall call) {
        Log.d("FPRINT_printReceipt", "Starting printReceipt method");
        String printerId = call.getString("printerId");
        if (printerId == null || printerId.isEmpty()) {
            call.reject("Printer ID is required");
            return;
        }

        Log.d("FPRINT_printReceipt", "printerId " + printerId);

        JSObject dataObj = call.getObject("data");
        if (dataObj == null) {
            call.reject("No data object provided");
            return;
        }

        logoBase64 = dataObj.getString("logo");

        Log.d("FPRINT_printReceipt", "logo " + logoBase64);

        JSArray dataArray = new JSArray();
        try {
            org.json.JSONArray rawJsonArray = dataObj.optJSONArray("raw");
            if (rawJsonArray == null || rawJsonArray.length() == 0) {
                call.reject("No ESC/POS raw data provided");
                return;
            }
            List<Object> rawList = jsonArrayToList(rawJsonArray);
            dataArray = new JSArray(rawList);

        } catch (JSONException e) {
            Log.d("FPRINT_printReceipt", "error " + e.getMessage());
            call.reject("Could not convert data");
        }


        // Convert JSArray to byte[]
        escposData = new byte[dataArray.length()]; // ✅ Store in class field for later
        for (int i = 0; i < dataArray.length(); i++) {
            escposData[i] = (byte) dataArray.optInt(i);
        }

        Log.d("FPRINT_printReceipt", "Received printerId: " + printerId);

        // ✅ Load printers from prefs
        String json = prefs.getString(PRINTERS_KEY, "{}");
        try {
            JSONObject printersObj = new JSONObject(json);

            Log.d("FPRINT_printReceipt", "Check stored printers: " + printersObj.toString());
            if (!printersObj.has(printerId)) {
                call.reject("Printer not found for ID: " + printerId);
                return;
            }

            JSONObject printerConfig = printersObj.getJSONObject(printerId);
            String connectionInfo = printerConfig.optString("connectionInfo", null);

            if (connectionInfo == null || !connectionInfo.contains("x")) {
                call.reject("Invalid or missing connectionInfo for printer " + printerId);
                return;
            }

            String[] parts = connectionInfo.split("x");
            if (parts.length != 2) {
                call.reject("Invalid connectionInfo format for printer " + printerId);
                return;
            }

            int vendorId = Integer.parseInt(parts[0]);
            int productId = Integer.parseInt(parts[1]);

            usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);

            UsbDevice target = findDevice(vendorId, productId);
            if (target == null) {
                call.reject("USB printer not found (" + vendorId + "x" + productId + ")");
                return;
            }

            if (usbManager.hasPermission(target)) {
                Log.d("FPRINT_printReceipt", "already have permission: call printToUsbDevice");
                // Already granted — print now
                printToUsbDevice(target, logoBase64, escposData, call);
                return;
            }

            // Request permission
            pendingCall = call;
            pendingDevice = target;

            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    getContext(),
                    0,
                    new Intent(ACTION_USB_PERMISSION)
                            .setPackage(getContext().getPackageName()), // scope to our app
                    flags
            );

            Log.d("FPRINT_printReceipt", "requestPermission for device: " + target);
            usbManager.requestPermission(target, permissionIntent);

        } catch (JSONException e) {
            call.reject("Failed to load printer config", e);
        }
    }

    private UsbDevice findDevice(int vendorId, int productId) {
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getVendorId() == vendorId && d.getProductId() == productId) return d;
        }
        return null;
    }

    private void printToUsbDevice(UsbDevice device, String logoBase64, byte[] data, PluginCall call) {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        Log.d("FPRINT_printToUsbDevice", "verify connection: " + (connection != null));
        if (connection == null) {
            call.reject("Failed to open USB connection even after permission granted.");
            return;
        }

        UsbInterface usbInterface = device.getInterface(0);
        connection.claimInterface(usbInterface, true);

        // Find OUT endpoint
        UsbEndpoint endpoint = null;
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                endpoint = ep;
                break;
            }
        }

        Log.d("FPRINT_printToUsbDevice", "found endpoint: " + endpoint);

        if (endpoint == null) {
            call.reject("No valid OUT endpoint found for printer.");
            connection.releaseInterface(usbInterface);
            connection.close();
            return;
        }

//        int result = connection.bulkTransfer(endpoint, data, data.length, 2000);
//        connection.releaseInterface(usbInterface);
//        connection.close();
//
//        Log.d("FPRINT_printToUsbDevice", "received result: " + result);
//
//        if (result >= 0) {
//            JSObject resultObj = new JSObject();
//            resultObj.put("success", true);
//            call.resolve(resultObj);
//        } else {
//            call.reject("Failed to send data to printer.");
//        }

        try {
            OutputStream printerOutputStream = new UsbPrinterOutputStream(connection, endpoint);
            EscPos escpos = new EscPos(printerOutputStream);

            /*// 1. Print the logo (if provided)
            if (logoBase64 != null && !logoBase64.isEmpty()) {
                Log.d("FPRINT_printToUsbDevice", "print Logo: " + logoBase64);
                if (logoBase64.startsWith("data:")) {
                    logoBase64 = logoBase64.substring(logoBase64.indexOf(",") + 1);
                }
                byte[] logoBytes = Base64.decode(logoBase64, Base64.DEFAULT);
                Bitmap logoBitmap = BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.length);

                // Optionally, resize bitmap to your printer's width!
                // logoBitmap = resizeBitmap(logoBitmap, 384); // if printer is 384 dots wide
                BitImageWrapper imageWrapper= new BitImageWrapper();
                imageWrapper.setJustification(EscPosConst.Justification.Center);

                EscPosImage escPosImage = new EscPosImage(new CoffeeImageAndroidImpl(logoBitmap), new BitonalThreshold());
                escpos.write(imageWrapper, escPosImage);
                // Optionally feed line after logo
                escpos.feed(1);
            }*/

            if (logoBase64 != null && !logoBase64.isEmpty()) {
                if (logoBase64.startsWith("data:")) {
                    logoBase64 = logoBase64.substring(logoBase64.indexOf(',') + 1);
                }
                byte[] logoBytes = Base64.decode(logoBase64, Base64.DEFAULT);
                Bitmap logoBitmap = BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.length);

                // Choose your printer width in dots
                // 58mm printers: ~384; 80mm printers: ~576 (sometimes 512/640 depending on model)
                final int PRINTER_DOTS_WIDTH = 384;

                // Make it visible: scale up, keep aspect ratio
                Bitmap scaled = logoBitmap; // scaleToWidth(logoBitmap, Math.min(256, PRINTER_DOTS_WIDTH)); // 256-384 looks good
                //scaled = removeAlphaOnWhite(scaled);

                // Prefer raster mode — aligns and advances nicely
                com.github.anastaciocintra.escpos.image.RasterBitImageWrapper raster =
                        new com.github.anastaciocintra.escpos.image.RasterBitImageWrapper();
                raster.setJustification(EscPosConst.Justification.Center);

                // If you’re using a Bitmap adapter, keep using it; if not, swap to your own implementation.
                EscPosImage escImg = new EscPosImage(new CoffeeImageAndroidImpl(scaled),
                        new BitonalThreshold()); // or BitonalOrderedDither()

                escpos.write(raster, escImg);
                escpos.feed(1);                // <- give it space so text doesn’t collide with the image
                // Optionally reset justification for upcoming text if you plan to use escpos.write for text:
                // escpos.write(new Style().setJustification(EscPosConst.Justification.Left), "");
            }

            // 2. Print the rest of your ESC/POS receipt data
            printerOutputStream.write(data);

            // 3. Clean up
            escpos.close();

            connection.releaseInterface(usbInterface);
            connection.close();

            Log.d("FPRINT_printToUsbDevice", "print success");
            JSObject resultObj = new JSObject();
            resultObj.put("success", true);
            call.resolve(resultObj);

        } catch (Exception e) {
            connection.releaseInterface(usbInterface);
            connection.close();
            call.reject("Printing failed: " + e.getMessage());
        }
    }

    // Helper function
    public static List<Object> jsonArrayToList(JSONArray arr) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.get(i));
        }
        return list;
    }

    // Helpers
    private Bitmap scaleToWidth(Bitmap src, int targetWidth) {
        if (src == null || targetWidth <= 0) return src;
        int w = src.getWidth(), h = src.getHeight();
        if (w == 0 || h == 0 || w == targetWidth) return src;
        int targetHeight = Math.round(h * (targetWidth / (float) w));
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true);
    }

    private Bitmap removeAlphaOnWhite(Bitmap src) {
        if (src == null || src.getConfig() == Bitmap.Config.RGB_565) return src;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(src, 0, 0, null);
        return out;
    }


}

