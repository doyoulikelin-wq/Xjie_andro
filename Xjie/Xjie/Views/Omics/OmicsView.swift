import SwiftUI
import UniformTypeIdentifiers

/// 多组学数据页面 — 对应小程序 pages/omics/omics
struct OmicsView: View {
    @State private var activeTab = 0
    @StateObject private var vm = OmicsViewModel()

    private let tabs = ["蛋白组学", "代谢组学", "基因组学"]

    // 占位数据 (对应 omics.js 的 data)
    private let proteomics = OmicsPanel(
        title: "蛋白组学", icon: "allergens",
        desc: "通过质谱分析技术检测血液中的蛋白质表达谱，识别疾病相关的生物标志物。",
        items: [
            OmicsItem(name: "CRP (C-反应蛋白)", value: "--", unit: "mg/L"),
            OmicsItem(name: "TNF-α (肿瘤坏死因子)", value: "--", unit: "pg/mL"),
            OmicsItem(name: "IL-6 (白介素-6)", value: "--", unit: "pg/mL"),
            OmicsItem(name: "Adiponectin (脂联素)", value: "--", unit: "μg/mL"),
        ]
    )
    private let metabolomics = OmicsPanel(
        title: "代谢组学", icon: "flask",
        desc: "利用代谢物谱分析技术检测体内小分子代谢产物，反映机体代谢状态。",
        items: [
            OmicsItem(name: "BCAA (支链氨基酸)", value: "--", unit: "μmol/L"),
            OmicsItem(name: "TMAO (氧化三甲胺)", value: "--", unit: "μmol/L"),
            OmicsItem(name: "Bile Acids (胆汁酸)", value: "--", unit: "μmol/L"),
            OmicsItem(name: "Ceramides (神经酰胺)", value: "--", unit: "nmol/L"),
        ]
    )
    private let genomics = OmicsPanel(
        title: "基因组学", icon: "microscope",
        desc: "基于全基因组关联分析 (GWAS)，评估个体遗传风险和药物基因组学特征。",
        items: [
            OmicsItem(name: "TCF7L2 (2 型糖尿病风险)", value: "--", unit: ""),
            OmicsItem(name: "FTO (肥胖易感基因)", value: "--", unit: ""),
            OmicsItem(name: "APOE (脂代谢基因)", value: "--", unit: ""),
            OmicsItem(name: "MTHFR (叶酸代谢基因)", value: "--", unit: ""),
        ]
    )

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    tabBar

                    switch activeTab {
                    case 0:
                        lockedPanel(proteomics)
                    case 1:
                        metabolomicsPanel
                    default:
                        lockedPanel(genomics)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
            .background(Color.appBackground)
            .navigationTitle("多组学")
            .navigationBarTitleDisplayMode(.inline)
        }
        .sheet(isPresented: $vm.showFilePicker) {
            MetabolomicsFilePicker(vm: vm)
        }
        .alert("错误", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { i, label in
                Button { activeTab = i } label: {
                    HStack(spacing: 4) {
                        Text(label)
                            .font(.subheadline.bold())
                        if i == 0 || i == 2 {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 8))
                        }
                    }
                    .foregroundColor(activeTab == i ? .white : .appPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(activeTab == i ? (i == 1 ? Color.appPrimary : Color.gray) : Color.clear)
                    .cornerRadius(8)
                }
            }
        }
        .padding(4)
        .background(Color.appPrimary.opacity(0.1))
        .cornerRadius(10)
    }

    // MARK: - 锁定面板（蛋白组学 & 基因组学）

    private func lockedPanel(_ panel: OmicsPanel) -> some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: panel.icon)
                    .font(.title2)
                    .foregroundColor(.appMuted)
                Text(panel.title).font(.headline).foregroundColor(.appMuted)
            }
            Text(panel.desc)
                .font(.subheadline)
                .foregroundColor(.appMuted)

            VStack(spacing: 12) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 36))
                    .foregroundColor(.appMuted.opacity(0.5))
                Text("开发中")
                    .font(.title3.bold())
                    .foregroundColor(.appMuted)
                Text("该模块正在开发中，敬请期待")
                    .font(.subheadline)
                    .foregroundColor(.appMuted.opacity(0.7))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 32)

            ForEach(panel.items) { item in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.subheadline).foregroundColor(.appMuted.opacity(0.5))
                        if !item.unit.isEmpty {
                            Text(item.unit).font(.caption).foregroundColor(.appMuted.opacity(0.3))
                        }
                    }
                    Spacer()
                    Image(systemName: "lock.fill")
                        .font(.caption)
                        .foregroundColor(.appMuted.opacity(0.3))
                }
                .padding(12)
                .background(Color.gray.opacity(0.03))
                .cornerRadius(8)
            }
        }
        .cardStyle()
        .opacity(0.7)
    }

    // MARK: - 代谢组学面板（带上传）

    private var metabolomicsPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: metabolomics.icon)
                    .font(.title2)
                    .foregroundColor(.appPrimary)
                Text(metabolomics.title).font(.headline)
            }
            Text(metabolomics.desc)
                .font(.subheadline)
                .foregroundColor(.appMuted)

            // 上传入口
            uploadSection

            // LLM 分析中
            if vm.analyzing {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("AI 正在分析代谢组数据...")
                        .font(.subheadline)
                        .foregroundColor(.appMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            }

            // LLM 分析结果
            if let result = vm.analysisResult {
                analysisResultView(result)
            }

            // 模型分析结果（占位）
            modelAnalysisPlaceholder

            // 指标列表
            ForEach(metabolomics.items) { item in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.subheadline)
                        if !item.unit.isEmpty {
                            Text(item.unit).font(.caption).foregroundColor(.appMuted)
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text(item.value)
                            .font(.subheadline).bold()
                            .foregroundColor(.appMuted)
                        Text("待检测")
                            .font(.caption2)
                            .foregroundColor(.appMuted)
                    }
                }
                .padding(12)
                .background(Color.gray.opacity(0.05))
                .cornerRadius(8)
            }
        }
        .cardStyle()
    }

    private var uploadSection: some View {
        VStack(spacing: 10) {
            if let fileName = vm.uploadedFileName {
                HStack {
                    Image(systemName: "doc.fill")
                        .foregroundColor(.appPrimary)
                    Text(fileName)
                        .font(.subheadline)
                        .lineLimit(1)
                    Spacer()
                    Button { vm.clearUpload() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.appMuted)
                    }
                }
                .padding(12)
                .background(Color.appPrimary.opacity(0.05))
                .cornerRadius(8)
            }

            Button { vm.showFilePicker = true } label: {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.up.doc.fill")
                        .font(.title3)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("上传代谢组数据")
                            .font(.subheadline.bold())
                        Text("支持 CSV、Excel、PDF 格式")
                            .font(.caption)
                            .foregroundColor(.appMuted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.appMuted)
                }
                .padding(14)
                .background(Color.appPrimary.opacity(0.08))
                .foregroundColor(.appPrimary)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(Color.appPrimary.opacity(0.2), style: StrokeStyle(lineWidth: 1, dash: [6, 3]))
                )
            }
        }
    }

    @State private var analysisExpanded = false

    private func analysisResultView(_ result: MetabolomicsAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image("Logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                Text("AI 分析结果")
                    .font(.subheadline.bold())
                Spacer()
                Text(result.riskLevel)
                    .font(.caption.bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(riskColor(result.riskLevel).opacity(0.15))
                    .foregroundColor(riskColor(result.riskLevel))
                    .cornerRadius(6)
            }

            Text(result.summary)
                .font(.subheadline)

            Button {
                withAnimation { analysisExpanded.toggle() }
            } label: {
                HStack(spacing: 4) {
                    Text(analysisExpanded ? "收起详情" : "查看详细分析")
                    Image(systemName: analysisExpanded ? "chevron.up" : "chevron.down")
                }
                .font(.caption)
                .foregroundColor(.appPrimary)
            }
            .buttonStyle(.plain)

            if analysisExpanded {
                Divider()
                Text(result.analysis)
                    .font(.caption)
                    .foregroundColor(.appMuted)
                    .transition(.opacity)
            }
        }
        .padding(12)
        .background(Color.appPrimary.opacity(0.04))
        .cornerRadius(10)
    }

    // MARK: - 模型分析占位

    private var modelAnalysisPlaceholder: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "chart.bar.xaxis.ascending")
                    .foregroundColor(.orange)
                Text("模型深度分析")
                    .font(.subheadline.bold())
                Spacer()
                Text("即将上线")
                    .font(.caption2.bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.orange.opacity(0.12))
                    .foregroundColor(.orange)
                    .cornerRadius(6)
            }
            Text("专业代谢组学分析模型，提供更精准的代谢通路分析、生物标志物筛选和风险预测")
                .font(.caption)
                .foregroundColor(.appMuted)
        }
        .padding(12)
        .background(Color.orange.opacity(0.04))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(Color.orange.opacity(0.15), lineWidth: 0.5)
        )
    }

    private func riskColor(_ level: String) -> Color {
        switch level {
        case "低风险": return .green
        case "中风险": return .orange
        case "高风险": return .red
        default: return .appMuted
        }
    }
}

// MARK: - 文件选择器

struct MetabolomicsFilePicker: UIViewControllerRepresentable {
    @ObservedObject var vm: OmicsViewModel

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType] = [.commaSeparatedText, .pdf, .spreadsheet, .data]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(vm: vm) }

    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let vm: OmicsViewModel
        init(vm: OmicsViewModel) { self.vm = vm }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            vm.handlePickedFile(url)
        }
    }
}

// MARK: - 数据模型

struct OmicsPanel {
    let title: String
    let icon: String
    let desc: String
    let items: [OmicsItem]
}

struct OmicsItem: Identifiable {
    let id = UUID()
    let name: String
    let value: String
    let unit: String
}
