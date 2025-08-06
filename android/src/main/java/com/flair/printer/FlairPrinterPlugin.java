package com.flair.printer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private byte[] escposData;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.d("FPRINT_BroadcastReceiver", "received action " + intent.getAction());
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d("FPRINT_BroadcastReceiver", "getParcelableExtra");

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null && pendingCall != null) {
                        Log.d("FPRINT_BroadcastReceiver", "call printToUsb");
                        printToUsbDevice(device, escposData, pendingCall);
                        pendingCall = null; // clear after use
                    }
                } else {
                    Log.d("FPRINT_BroadcastReceiver", "reject pendingCall");
                    if (pendingCall != null) {
                        pendingCall.reject("USB permission denied");
                        pendingCall = null;
                    }
                }
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
             getContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
             Log.d("FPRINT_load", "USB receiver registered with NOT_EXPORTED");
         } else {
             getContext().registerReceiver(usbReceiver, filter);
             Log.d("FPRINT_load", "USB receiver registered (legacy)");
         }

         Intent testIntent = new Intent(ACTION_USB_PERMISSION);
         testIntent.setPackage(getContext().getPackageName()); // ✅ Target only this app
         getContext().sendBroadcast(testIntent);

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
        String printerId = call.getString("printerId");
        if (printerId == null || printerId.isEmpty()) {
            call.reject("Printer ID is required");
            return;
        }

        JSArray dataArray = call.getArray("data");
        if (dataArray == null || dataArray.length() == 0) {
            call.reject("No ESC/POS data provided");
            return;
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

            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                    if (usbManager.hasPermission(device)) {
                        // ✅ Already have permission
                        Log.d("FPRINT_printReceipt", "already have permission: call printToUsbDevice");
                        printToUsbDevice(device, escposData, call);
                    } else {
                        // ✅ Request permission and return
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                                getActivity(), // ✅ use activity context
                                0,
                                new Intent(ACTION_USB_PERMISSION),
                                PendingIntent.FLAG_IMMUTABLE
                        );
                        Log.d("FPRINT_printReceipt", "request permission");
                        usbManager.requestPermission(device, permissionIntent);
                        pendingCall = call; // Store call so we can resolve/reject later

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (pendingCall != null && usbManager.hasPermission(device)) {
                                Log.d("FPRINT", "Fallback: Permission granted silently, printing...");
                                printToUsbDevice(device, escposData, pendingCall);
                                pendingCall = null;
                            }
                        }, 2000);
                    }
                    return;
                }
            }

            call.reject("No USB printer found matching vendorId: " + vendorId + ", productId: " + productId);

        } catch (JSONException e) {
            call.reject("Failed to load printer config", e);
        }
    }

    private void printToUsbDevice(UsbDevice device, byte[] data, PluginCall call) {
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

        int result = connection.bulkTransfer(endpoint, data, data.length, 2000);
        connection.releaseInterface(usbInterface);
        connection.close();

        Log.d("FPRINT_printToUsbDevice", "received result: " + result);

        if (result >= 0) {
            JSObject resultObj = new JSObject();
            resultObj.put("success", true);
            call.resolve(resultObj);
        } else {
            call.reject("Failed to send data to printer.");
        }
    }

}
