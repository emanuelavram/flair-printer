//
//  EPSLabelPrinter.h
//  Epson Label Printer SDK for Mobile
//
//  Copyright © 2021 Seiko Epson Corporation. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

typedef enum : NSUInteger {
    EPSPaperSize108x152mm,
    EPSPaperSize108x174mm,
	EPSPaperSize211x304mm,
    EPSPaperSizeCustom = 1000,
} EPSPaperSizeType;

typedef enum : NSUInteger {
    EPSMediaFormDieCutGap,
    EPSMediaFormDieCutBlackmark,
    EPSMediaFormContinuousLabelBlackmark,
    EPSMediaFormContinuousLabelNoDetection,
    EPSMediaFormContinuousPaperBlackmark,
    EPSMediaFormContinuousPaperNoDetection,
    EPSMediaFormWristbandBlackmark,
    EPSMediaFormTagHole,
} EPSMediaForm;

typedef enum : NSUInteger {
    EPSMediaSavingNoSaving,
    EPSMediaSavingSaveBottom,
    EPSMediaSavingSaveTopBottom,
} EPSMediaSaving;

typedef enum : NSUInteger {
    EPSMediaTypePlainPaper,
    EPSMediaTypeMattePaper,
    EPSMediaTypeSynthetic,
    EPSMediaTypeTexturePaper,
    EPSMediaTypeGlossyPaper,
    EPSMediaTypeGlossyFilm,
    EPSMediaTypeHighGlossyPaper,
    EPSMediaTypeWristband,
} EPSMediaType;

typedef enum : NSUInteger {
    EPSActionModeAutoCutLastPage,
    EPSActionModeAutoCutPeriodically,
    EPSActionModeAutoCutCollateEnd,
    EPSActionModeFeedToPeelPosition,
    EPSActionModeFeedToCutPosition,
    EPSActionModePauseAtPrintEnd,
    EPSActionModePeelerAutoMode,
    EPSActionModePeelerManualMode,
    EPSActionModePeelerReelMode,
} EPSActionMode;

typedef enum : NSUInteger {
    EPSPrintQualityMaxSpeed,
    EPSPrintQualitySpeed,
    EPSPrintQualityNormal,
    EPSPrintQualityQuality,
    EPSPrintQualityMaxQuality,
} EPSPrintQuality;

typedef enum : NSUInteger {
    EPSBuzzerNone,
    EPSBuzzerAfterCut,
    EPSBuzzerLastPage,
    EPSBuzzerCollateEnd,
} EPSBuzzer;

typedef enum : NSUInteger {
    EPSPauseNone,
    EPSPauseAfterCut,
    EPSPauseLastPage,
    EPSPauseCollateEnd,
} EPSPause;

typedef enum : NSUInteger {
    EPSColorAdjustmentNone,
    EPSColorAdjustmentVivid,
    EPSColorAdjustmentPhoto,
    EPSColorAdjustmentStandard,
} EPSColorAdjustment;

typedef enum : NSUInteger {
    EPSHeadMaintenancePauseForMaintenance,
    EPSHeadMaintenanceContinuousPrinting,
} EPSHeadMaintenance;

typedef enum : NSUInteger {
    EPSIncompleteLabelTreatmentNeverCut,
    EPSIncompleteLabelTreatmentCut,
} EPSIncompleteLabelTreatment;

extern NSString * const EPSPaperSizeTypeKey;
extern NSString * const EPSCustomPaperWidthKey;
extern NSString * const EPSCustomPaperHeightKey;
extern NSString * const EPSMediaFormKey;
extern NSString * const EPSMediaSavingKey;
extern NSString * const EPSMediaTypeKey;
extern NSString * const EPSCollateKey;
extern NSString * const EPSActionModeKey;
extern NSString * const EPSCutIntervalKey;
extern NSString * const EPSPrintQualityKey;
extern NSString * const EPSBuzzerKey;
extern NSString * const EPSPauseKey;
extern NSString * const EPSColorAdjustmentKey;
extern NSString * const EPSInkProfileKey;
extern NSString * const EPSBrightnessKey;
extern NSString * const EPSContrastKey;
extern NSString * const EPSSaturationKey;
extern NSString * const EPSCyanKey;
extern NSString * const EPSMagentaKey;
extern NSString * const EPSYellowKey;
extern NSString * const EPSBiDirectionKey;
extern NSString * const EPSBlackRatioKey;
extern NSString * const EPSDryingTimeKey;
extern NSString * const EPSVerticalPositionKey;
extern NSString * const EPSHorizontalPositionKey;
extern NSString * const EPSCutPositionKey;
extern NSString * const EPSGapBetweenLabelsKey;
extern NSString * const EPSLeftAndRightGapKey;
extern NSString * const EPSPeelPositionKey;
extern NSString * const EPSLeftGapKey;
extern NSString * const EPSRightGapKey;
extern NSString * const EPSLabelEdgeToHoleLengthKey;
extern NSString * const EPSPrintingSpeedKey;
extern NSString * const EPSHeadMaintenanceKey;
extern NSString * const EPSBandingReductionKey;
extern NSString * const EPSShrinkingCorrectionKey;
extern NSString * const EPSIncompleteLabelTreatmentKey;
extern NSString * const EPSHeadMediaClearanceHeightKey;
extern NSString * const EPSEdgeAdjustmentKey;
extern NSString * const EPSCopiesKey;

@class EPSLabelPrinterStatus;

typedef enum : NSInteger {
    EPSPrintResultSuccess = 0,
    EPSPrintResultConnectionError = -1,
    EPSPrintResultUserCancel = -2,
    EPSPrintResultTimeoutError = -3,
    EPSPrintResultInvalidParameterError = -4,
    EPSPrintResultOtherError = -100,
} EPSPrintResult;

@interface EPSLabelPrinter : NSObject

@property(nonatomic, readonly) NSString *modelName;
@property(nonatomic, readonly) NSString *location;
@property(nonatomic, readonly) NSString *serialNumber;
@property(nonatomic, readonly) NSString *printerID;
@property(nonatomic, readonly) NSString *printerIDWithServiceName;
@property(nonatomic, readonly) NSDictionary *defaultPrintSettings;

- (EPSLabelPrinterStatus *)requestStatus;
- (EPSPrintResult)printWithSettings:(NSDictionary *)settings
                          renderer:(void (^)(NSInteger pageIndex, CGSize pageSize, CGRect targetRect, BOOL *hasNextPage))renderer
                   progressHandler:(void (^)(NSInteger pageIndex))progressHandler;
- (EPSPrintResult)printWithSettings:(NSDictionary *)settings
                          dataCount:(NSInteger)dataCount
                   templateRenderer:(void (^)(CGSize pageSize, CGRect targetRect))templateRenderer
                       rectProvider:(CGRect (^)(NSInteger pageIndex, CGSize pageSize, NSInteger dataIndex))rectProvider
               variableDataRenderer:(void (^)(NSInteger pageIndex, NSInteger dataIndex, CGSize size, BOOL *hasNextPage))variableDataRenderer
                   progressHandler:(void (^)(NSInteger pageIndex))progressHandler;
- (void)cancelPrint;
- (NSArray *)supportedPrintSettingsForKey:(NSString *)key;
- (NSData *)sendCommand:(NSData *)data receiveTimeout:(NSInteger)timeout result:(EPSPrintResult *)result;

@end
