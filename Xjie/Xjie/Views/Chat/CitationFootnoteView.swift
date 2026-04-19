import SwiftUI

// MARK: - 文献引用展示组件
//
// 在 AI 回复气泡下方显示一个紧凑的小字"参考文献"块：
// - 每条引用以 [N] 角标 + 期刊/年份 形式展示
// - 点击任意一条 → 弹出底部 sheet，展示完整一句话结论 + 元数据
// - 证据等级用颜色徽章区分（L1 绿 / L2 蓝 / L3 灰 / L4 浅灰）

struct CitationFootnoteView: View {
    let citations: [Citation]
    @State private var selected: Citation?

    var body: some View {
        if citations.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text("参考文献")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.appMuted)
                ForEach(Array(citations.enumerated()), id: \.element.claim_id) { idx, c in
                    Button { selected = c } label: {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text("[\(idx + 1)]")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.appPrimary)
                            EvidenceLevelBadge(level: c.evidence_level)
                            Text(c.short_ref)
                                .font(.system(size: 10))
                                .foregroundColor(.appMuted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .sheet(item: $selected) { citation in
                CitationDetailSheet(citation: citation)
                    .presentationDetents([.medium])
            }
        }
    }
}

struct EvidenceLevelBadge: View {
    let level: String

    var body: some View {
        Text(level)
            .font(.system(size: 9, weight: .bold))
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .background(color.opacity(0.15))
            .foregroundColor(color)
            .clipShape(RoundedRectangle(cornerRadius: 3))
    }

    private var color: Color {
        switch level {
        case "L1": return .green
        case "L2": return .blue
        case "L3": return .gray
        default:   return Color(white: 0.55)
        }
    }
}

struct CitationDetailSheet: View {
    let citation: Citation
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    EvidenceLevelBadge(level: citation.evidence_level)
                    Text(citation.short_ref)
                        .font(.headline)
                    Spacer()
                }

                Text(citation.claim_text)
                    .font(.body)
                    .fixedSize(horizontal: false, vertical: true)

                Divider()

                metaRow("证据等级", value: levelLabel(citation.evidence_level))
                metaRow("期刊", value: citation.journal ?? "—")
                metaRow("年份", value: citation.year.map { "\($0)" } ?? "—")
                metaRow("样本量", value: citation.sample_size.map { "n = \($0)" } ?? "—")
                metaRow("可信度", value: confidenceLabel(citation.confidence))

                Text("说明：以上为我们撰写的一句话结论与文献元信息。仅作为健康管理参考，不构成诊断或治疗建议。")
                    .font(.caption2)
                    .foregroundColor(.appMuted)
                    .padding(.top, 8)
            }
            .padding(20)
        }
    }

    private func metaRow(_ label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.caption)
                .foregroundColor(.appMuted)
                .frame(width: 64, alignment: .leading)
            Text(value)
                .font(.caption)
                .foregroundColor(.appText)
            Spacer()
        }
    }

    private func levelLabel(_ level: String) -> String {
        switch level {
        case "L1": return "L1（Meta 分析 / 系统综述 / RCT）"
        case "L2": return "L2（队列 / 小型 RCT）"
        case "L3": return "L3（病例对照 / 机制研究）"
        case "L4": return "L4（综述 / 专家共识）"
        default:   return level
        }
    }

    private func confidenceLabel(_ c: String) -> String {
        switch c {
        case "high":   return "高"
        case "medium": return "中"
        case "low":    return "低"
        default:       return c
        }
    }
}
