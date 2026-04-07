import SwiftUI

/// 血糖曲线页面 — 对应小程序 pages/glucose/glucose
struct GlucoseView: View {
    @StateObject private var vm = GlucoseViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // 时间窗口切换
                windowTabs

                // 统计卡片
                if let summary = vm.summary {
                    summaryCard(summary)
                }

                // Canvas 图表
                chartCard
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
        }
        .background(Color.appBackground)
        .navigationTitle("血糖曲线")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.fetchRange() }
        .refreshable { await vm.fetchPoints() }
        .alert("错误", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }

    // MARK: - 时间窗口

    private var windowTabs: some View {
        HStack(spacing: 0) {
            ForEach(["24h", "7d", "all"], id: \.self) { w in
                Button {
                    Task {
                        vm.window = w
                        await vm.fetchPoints()
                    }
                } label: {
                    Text(w == "all" ? "全部" : w == "7d" ? "7 天" : "24h")
                        .font(.subheadline.bold())
                        .foregroundColor(vm.window == w ? .white : .appPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(vm.window == w ? Color.appPrimary : Color.clear)
                        .cornerRadius(8)
                }
            }
        }
        .padding(4)
        .background(Color.appPrimary.opacity(0.1))
        .cornerRadius(10)
    }

    // MARK: - 统计卡片

    private func summaryCard(_ s: GlucoseSummary) -> some View {
        HStack {
            MetricItemView(value: Utils.toFixed(s.avg), label: "平均")
            Spacer()
            MetricItemView(value: s.tir_70_180_pct != nil ? Utils.toFixed(s.tir_70_180_pct) + "%" : "--", label: "TIR", color: .appSuccess)
            Spacer()
            MetricItemView(value: s.variability ?? "--", label: "变异性")
            Spacer()
            MetricItemView(value: "\(vm.points.count)", label: "数据点")
        }
        .cardStyle()
    }

    // MARK: - Canvas 图表

    private var chartCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("血糖曲线").font(.headline)

            if vm.loading {
                ProgressView("加载中...")
                    .frame(maxWidth: .infinity, minHeight: 220)
            } else if vm.points.isEmpty {
                Text("暂无血糖数据")
                    .foregroundColor(.appMuted)
                    .frame(maxWidth: .infinity, minHeight: 220)
            } else {
                GlucoseChartCanvas(chartData: vm.chartData)
                    .frame(height: 220)

                // 图例
                HStack(spacing: 16) {
                    legendItem(color: Color.green.opacity(0.15), label: "目标范围 70-180")
                    legendItem(color: .appPrimary, label: "血糖值")
                }
                .font(.caption2)
            }
        }
        .cardStyle()
    }

    private func legendItem(color: Color, label: String) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).foregroundColor(.appMuted)
        }
    }
}

// MARK: - 血糖图表 Canvas (对应 glucose.js 的 drawChart)

struct GlucoseChartCanvas: View {
    /// PERF-02: 接收预计算好的 (Date, Double) 数据，Canvas 内不再做日期解析
    let chartData: [(date: Date, value: Double)]

    var body: some View {
        GeometryReader { geo in
            Canvas { ctx, size in
                let w = size.width
                let h = size.height
                // CODE-02: 使用常量替代 magic number
                let padLeft = ChartConstants.padLeft
                let padRight = ChartConstants.padRight
                let padTop = ChartConstants.padTop
                let padBottom = ChartConstants.padBottom
                let chartW = w - padLeft - padRight
                let chartH = h - padTop - padBottom

                let values = chartData.map { $0.value }
                let minVal = min(values.min() ?? 50, 50)
                let maxVal = max(values.max() ?? 200, 200)
                let range = maxVal - minVal == 0 ? 1 : maxVal - minVal

                // 目标范围背景
                let y180 = padTop + chartH * (1 - (ChartConstants.targetHigh - minVal) / range)
                let y70 = padTop + chartH * (1 - (ChartConstants.targetLow - minVal) / range)
                let targetRect = CGRect(
                    x: padLeft,
                    y: max(y180, padTop),
                    width: chartW,
                    height: min(y70 - y180, chartH)
                )
                ctx.fill(Path(targetRect), with: .color(.green.opacity(0.08)))

                // 参考线 — CODE-02: 使用 ChartConstants.refLines
                for refVal in ChartConstants.refLines {
                    let y = padTop + chartH * (1 - (refVal - minVal) / range)
                    var linePath = Path()
                    linePath.move(to: CGPoint(x: padLeft, y: y))
                    linePath.addLine(to: CGPoint(x: w - padRight, y: y))
                    ctx.stroke(linePath, with: .color(.gray.opacity(0.3)), style: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    ctx.draw(Text("\(Int(refVal))").font(.system(size: ChartConstants.labelFontSize)).foregroundColor(.gray), at: CGPoint(x: 18, y: y))
                }

                // 曲线 — 直接使用预计算的 Date
                guard chartData.count > 1 else { return }
                let timestamps = chartData.map { $0.date.timeIntervalSince1970 }
                let minT = timestamps.min() ?? 0
                let maxT = timestamps.max() ?? 1
                let tRange = maxT - minT == 0 ? 1 : maxT - minT

                var curvePath = Path()
                for (i, pt) in chartData.enumerated() {
                    let x = padLeft + chartW * CGFloat((timestamps[i] - minT) / tRange)
                    let y = padTop + chartH * CGFloat(1 - (pt.value - minVal) / range)
                    if i == 0 { curvePath.move(to: CGPoint(x: x, y: y)) }
                    else { curvePath.addLine(to: CGPoint(x: x, y: y)) }
                }
                ctx.stroke(curvePath, with: .color(.appPrimary), lineWidth: ChartConstants.lineWidth)
            }
        }
    }
}

