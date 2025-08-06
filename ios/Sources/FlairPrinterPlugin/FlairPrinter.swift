import Foundation

@objc public class FlairPrinter: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
