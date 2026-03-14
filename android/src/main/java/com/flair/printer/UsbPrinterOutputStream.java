package com.flair.printer;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.image.EscPosImage;
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import com.github.anastaciocintra.escpos.image.BitonalThreshold;

import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;

// Your OutputStream wrapper for the USB connection
public class UsbPrinterOutputStream extends OutputStream {
    private UsbDeviceConnection connection;
    private UsbEndpoint endpoint;

    public UsbPrinterOutputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        this.connection = connection;
        this.endpoint = endpoint;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        byte[] toSend = buffer;
        if (offset != 0 || count != buffer.length) {
            toSend = Arrays.copyOfRange(buffer, offset, offset + count);
        }
        int result = connection.bulkTransfer(endpoint, toSend, count, 2000);
        if (result < 0) {
            throw new IOException("Bulk transfer failed");
        }
    }
}
