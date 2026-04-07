import Foundation
import CoreGraphics

// MARK: - CODE-02: 消除 magic number

/// 血糖图表绘制常量
enum ChartConstants {
    static let padLeft: CGFloat = 40
    static let padRight: CGFloat = 8
    static let padTop: CGFloat = 8
    static let padBottom: CGFloat = 28
    static let targetLow: Double = 70
    static let targetHigh: Double = 180
    static let refLines: [Double] = [70, 140, 180]
    static let labelFontSize: CGFloat = 9
    static let lineWidth: CGFloat = 1.5
}

/// API / 分页常量
enum APIConstants {
    static let requestTimeout: TimeInterval = 15
    static let uploadTimeout: TimeInterval = 60
    static let llmTimeout: TimeInterval = 90
    static let maxRetries = 2
    static let pageSize = 20
}
