import SwiftUI

/// 助手「小捷」的轻量化矢量头像：
/// - 圆形主色背景 + 白色对话气泡 + 心电波，呼应「健康 / 对话」双重定位
/// - 完全用 SwiftUI 绘制，避免位图资源；与 Color.appPrimary 主题色强一致
struct AssistantAvatar: View {
    var size: CGFloat = 36
    var bordered: Bool = false

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color.appPrimary, Color.appPrimary.opacity(0.78)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            // 对话气泡 + 心电
            BubbleHeartbeat()
                .stroke(Color.white, style: StrokeStyle(lineWidth: max(1.4, size / 22), lineCap: .round, lineJoin: .round))
                .padding(size * 0.22)
        }
        .frame(width: size, height: size)
        .overlay(
            Circle()
                .strokeBorder(Color.white.opacity(bordered ? 0.9 : 0), lineWidth: bordered ? 1.5 : 0)
        )
        .accessibilityHidden(true)
    }
}

/// 圆角对话气泡 + 内嵌一段心电波。坐标系按 1×1 缩放。
private struct BubbleHeartbeat: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width
        let h = rect.height

        // 气泡：圆角矩形 + 左下小尾巴
        let bubbleRect = CGRect(x: 0, y: 0, width: w, height: h * 0.82)
        let radius = min(bubbleRect.width, bubbleRect.height) * 0.28
        p.addRoundedRect(in: bubbleRect, cornerSize: CGSize(width: radius, height: radius))

        // 气泡尾巴
        let tailTopY = bubbleRect.maxY - radius * 0.4
        p.move(to: CGPoint(x: w * 0.30, y: tailTopY))
        p.addLine(to: CGPoint(x: w * 0.22, y: h))
        p.addLine(to: CGPoint(x: w * 0.45, y: tailTopY))

        // 心电波（在气泡内部）
        let baseY = bubbleRect.midY
        let amp = bubbleRect.height * 0.22
        let leftX = w * 0.18
        let rightX = w * 0.82
        let segs: [CGFloat] = [0.00, 0.18, 0.30, 0.42, 0.55, 0.68, 0.82, 1.0]
        let amps: [CGFloat] = [0.0, 0.0, -1.0, 1.5, -1.2, 0.6, 0.0, 0.0]
        for i in 0..<segs.count {
            let x = leftX + (rightX - leftX) * segs[i]
            let y = baseY + amp * amps[i]
            if i == 0 {
                p.move(to: CGPoint(x: x, y: y))
            } else {
                p.addLine(to: CGPoint(x: x, y: y))
            }
        }
        return p
    }
}

#Preview {
    HStack(spacing: 16) {
        AssistantAvatar(size: 32)
        AssistantAvatar(size: 48)
        AssistantAvatar(size: 80, bordered: true)
    }
    .padding()
    .background(Color.appBackground)
}
