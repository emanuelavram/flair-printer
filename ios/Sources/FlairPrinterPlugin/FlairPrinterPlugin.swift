import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */

@objc(FlairPrinterPlugin)
public class FlairPrinterPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "FlairPrinter"
    public let jsName = "FlairPrinter"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getPrinters", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scanUsbPrinters", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setPrinter", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removePrinter", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "testPrint", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "printReceipt", returnType: CAPPluginReturnPromise)
    ]

    @objc func getPrinters(_ call: CAPPluginCall) {
        call.resolve([
            "printers": []
        ])
    }

    @objc func scanUsbPrinters(_ call: CAPPluginCall) {
        call.resolve([
            "printers": []
        ])
    }

    @objc func setPrinter(_ call: CAPPluginCall) {
        call.resolve([
            "success": true
        ])
    }

    @objc func removePrinter(_ call: CAPPluginCall) {
        call.resolve([
            "success": true
        ])
    }

    @objc func testPrint(_ call: CAPPluginCall) {
        call.resolve([
            "success": true
        ])
    }

    @objc func printReceipt(_ call: CAPPluginCall) {
        call.resolve([
            "success": true
        ])
    }
}

