//
//  EPSLabelPrinterDiscovery.h
//  Epson Label Printer SDK for Mobile
//
//  Copyright © 2021 Seiko Epson Corporation. All rights reserved.
//

#import <Foundation/Foundation.h>

@class EPSLabelPrinter;

typedef enum : NSInteger {
    EPSDiscoveryResultSuccess,
    EPSDiscoveryResultConnectionError = -1,
    EPSDiscoveryResultInvalidParamterError = -2,
    EPSDiscoveryResultOtherError = -100,
} EPSDiscoveryResult;

@interface EPSLabelPrinterDiscovery : NSObject

- (EPSDiscoveryResult)startWithUpdateHandler:(void (^)(NSArray *printerList))updateHandler timeout:(NSInteger)timeout;
- (void)stop;
- (BOOL)isBusy;
- (EPSLabelPrinter *)probeByAddress:(NSString *)address errorCode:(EPSDiscoveryResult *)errorCode;
- (EPSLabelPrinter *)probeByPrinterID:(NSString *)printerID errorCode:(EPSDiscoveryResult *)errorCode;

@end
