import SwiftUI

/// 启动画面 — 品牌 Logo + 渐入动画
struct SplashView: View {
    @State private var opacity: Double = 0
    @State private var scale: CGFloat = 0.8
    let onFinished: () -> Void

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.appGradientStart, Color.appGradientEnd],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(.white.opacity(0.2))
                        .frame(width: 100, height: 100)
                    Text("XJ+")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                }

                Text("Xjie")
                    .font(.title).bold()
                    .foregroundColor(.white)

                Text("智能代谢健康管理")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
            }
            .scaleEffect(scale)
            .opacity(opacity)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                opacity = 1
                scale = 1
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                withAnimation(.easeIn(duration: 0.3)) {
                    opacity = 0
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    onFinished()
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Xjie 智能代谢健康管理")
    }
}
