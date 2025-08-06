//
//  EPSLog.h
//  EpsonColorWorksSDK
//
//  Created by takahashi on 2022/12/05.
//  Copyright © 2022 Seiko Epson Corporation. All rights reserved.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef enum : NSUInteger {
    EPSLogOutputTypeDisabled = 0,
    EPSLogOutputTypeStorage,
} EPSLogOutputType;

@interface EPSLog : NSObject

+ (void)setOutputType:(EPSLogOutputType)type;
+ (void)setMaxSize:(NSInteger)maxSize;
+ (EPSLogOutputType)getOutputType;
+ (NSInteger)getMaxSize;

@end

NS_ASSUME_NONNULL_END
