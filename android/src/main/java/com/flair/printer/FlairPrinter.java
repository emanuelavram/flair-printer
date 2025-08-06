package com.flair.printer;

import com.getcapacitor.Logger;

public class FlairPrinter {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
