//
//  EPSLabelPrinterStatus.h
//  Epson Label Printer SDK for Mobile
//
//  Copyright © 2021 Seiko Epson Corporation. All rights reserved.
//

#import <Foundation/Foundation.h>

typedef enum : NSUInteger {
    EPSPrinterStateNoError                   = 0,
    EPSPrinterStateConnectionError           = 1,
    EPSPrinterStateGeneralError              = 2,
    EPSPrinterStatePrinterOperating          = 3,
    EPSPrinterStatePaperJamError             = 4,
    EPSPrinterStateInkEndError               = 5,
    EPSPrinterStateCartridgeError            = 6,
    EPSPrinterStatePaperOutError             = 7,
    EPSPrinterStateMaintenanceError          = 8,
    EPSPrinterStatePaperTypeError            = 9,
    EPSPrinterStatePrintingIsPaused          = 10,
    EPSPrinterStateInvalidPrintSettingsError = 11,
} EPSPrinterState;

typedef enum : NSUInteger {
    EPSSupplyTypeBlackInk,
    EPSSupplyTypeCyanInk,
    EPSSupplyTypeMagentaInk,
    EPSSupplyTypeYellowInk,
    EPSSupplyTypeMaintenanceBox,
} EPSSupplyType;

extern NSString * const EPSSupplyTypeKey;
extern NSString * const EPSSupplyLevelKey;

@interface EPSLabelPrinterStatus : NSObject

@property(nonatomic, readonly) EPSPrinterState state;
@property(nonatomic, readonly) NSArray *supplyLevels;

@end
