package com.flair.printer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Printer-config logic shared with the desktop bridge, kept free of Android types so
 * it stays comparable with (and testable against) its JavaScript counterparts in
 * core/devices/. Any change here needs the matching change on the desktop side.
 */
final class PrinterTransport {
    /** Mirrors DEFAULT_PORT in core/devices/tcpDevice.js. */
    static final int DEFAULT_TCP_PORT = 9100;
    /** Mirrors CONNECT_TIMEOUT in core/devices/tcpDevice.js. */
    static final int CONNECT_TIMEOUT_MS = 5000;

    private PrinterTransport() {}

    /**
     * Collapses the type aliases the web-app uses onto a transport name.
     * Mirrors TYPE_ALIASES in core/devices/index.js so a printer config behaves the
     * same on desktop and mobile.
     *
     * @return "usb", "network", "windows", or null when the type is unknown
     */
    static String transportOf(String type) {
        String key = (type == null ? "" : type.trim().toLowerCase(Locale.ROOT));
        switch (key) {
            case "usb":
                return "usb";
            case "windows":
            case "spooler":
            case "queue":
            case "system":
                return "windows";
            case "network":
            case "tcp":
            case "ip":
            case "ethernet":
            case "socket":
                return "network";
            default:
                return null;
        }
    }

    /**
     * Accepts "192.168.1.50", "192.168.1.50:9100", "tcp://host:port",
     * "http://host:port" and bracketed IPv6. Mirrors parseTarget in
     * core/devices/tcpDevice.js.
     *
     * @return { host, port }
     */
    static String[] parseTcpTarget(String connectionInfo) {
        if (connectionInfo == null || connectionInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Network printer requires an address");
        }

        String value = connectionInfo.trim()
                .replaceFirst("(?i)^[a-z][a-z0-9+.-]*://", "") // strip a scheme if a URL was stored
                .split("/")[0];                                 // drop any path the URL left behind

        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("Invalid printer address: " + connectionInfo);
            String host = value.substring(1, end);
            String rest = value.substring(end + 1);
            String port = rest.startsWith(":") ? rest.substring(1) : String.valueOf(DEFAULT_TCP_PORT);
            return new String[] { host, String.valueOf(parsePort(port)) };
        }

        // A bare IPv6 address has several colons — those are not a port separator.
        int colons = value.length() - value.replace(":", "").length();
        if (colons == 1) {
            String[] parts = value.split(":");
            if (parts.length != 2 || parts[0].isEmpty()) {
                throw new IllegalArgumentException("Invalid printer address: " + connectionInfo);
            }
            return new String[] { parts[0], String.valueOf(parsePort(parts[1])) };
        }

        return new String[] { value, String.valueOf(DEFAULT_TCP_PORT) };
    }

    private static int parsePort(String port) {
        int parsed;
        try {
            parsed = Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid printer port: " + port);
        }
        if (parsed < 1 || parsed > 65535) {
            throw new IllegalArgumentException("Invalid printer port: " + port);
        }
        return parsed;
    }

    /** Same layout as testPrint in core/printEngine.js, so both platforms look alike. */
    static byte[] buildTestReceipt(String name, String transport, String target) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(0x1B); out.write(0x40);                  // init
        out.write(0x1B); out.write(0x61); out.write(1);    // centre
        out.write(0x1B); out.write(0x45); out.write(1);    // bold on
        out.write(0x1D); out.write(0x21); out.write(0x11); // double width + height
        writeAscii(out, "FLAIR");
        out.write(0x1D); out.write(0x21); out.write(0x00); // normal size
        out.write(0x1B); out.write(0x45); out.write(0);    // bold off
        writeAscii(out, "Test print");
        writeAscii(out, "");

        out.write(0x1B); out.write(0x61); out.write(0);    // left
        writeAscii(out, "Printer:   " + name);
        writeAscii(out, "Transport: " + transport);
        writeAscii(out, "Target:    " + target);
        writeAscii(out, "Time:      " + timestamp());
        writeAscii(out, "");

        out.write(0x1B); out.write(0x61); out.write(1);    // centre
        writeAscii(out, "If you can read this, printing works.");
        // Three bare line feeds rather than ESC d 3 — same result, and it is byte-for-byte
        // what escpos emits on the desktop, so the two stay comparable.
        out.write(0x0A); out.write(0x0A); out.write(0x0A);
        out.write(0x1D); out.write(0x56); out.write(0);    // full cut

        return out.toByteArray();
    }

    static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private static void writeAscii(ByteArrayOutputStream out, String line) {
        byte[] bytes = line.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
        out.write(0x0A);
    }
}
