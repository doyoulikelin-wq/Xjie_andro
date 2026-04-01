import SwiftUI

/// 体检报告列表 — 按日期分组显示
struct ExamReportListView: View {
    @StateObject private var vm = ExamReportListViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Button { vm.showDocumentPicker = true } label: {
                    HStack {
                        Image(systemName: "camera")
                        Text("上传体检报告").foregroundColor(.appText)
                    }
                    .frame(maxWidth: .infinity).padding()
                    .background(Color.appPrimary.opacity(0.1)).cornerRadius(10)
                }

                if vm.groupedItems.isEmpty && !vm.loading {
                    EmptyStateView(
                        icon: "doc.text.magnifyingglass",
                        title: "暂无体检报告",
                        subtitle: "点击上方按钮上传体检报告"
                    )
                } else {
                    ForEach(vm.groupedItems) { group in
                        examDateSection(group)
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8)
        }
        .background(Color.appBackground)
        .navigationTitle("历史体检")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.fetchList() }
        .refreshable { await vm.fetchList() }
        .overlay { if vm.loading { ProgressView() } }
        .sheet(isPresented: $vm.showDocumentPicker) {
            DocumentPickerView { data, fileName in
                Task { await vm.uploadExam(data: data, fileName: fileName) }
            }
        }
        .alert("确认删除", isPresented: $vm.showDeleteAlert) {
            Button("删除", role: .destructive) { Task { await vm.confirmDelete() } }
            Button("取消", role: .cancel) {}
        } message: { Text("删除后无法恢复，确定吗？") }
        .alert("错误", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }

    /// 按日期分组的体检卡片
    private func examDateSection(_ group: ExamDateGroup) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // 日期标题
            HStack {
                Image(systemName: "calendar")
                    .foregroundColor(.appPrimary)
                Text(group.displayDate)
                    .font(.subheadline).bold()
                    .foregroundColor(.appText)
                Spacer()
                Text("\(group.items.count) 份报告")
                    .font(.caption)
                    .foregroundColor(.appMuted)
            }

            // 该日期下的所有文档
            ForEach(group.items) { item in
                NavigationLink(destination: ExamReportDetailView(docId: item.id)) {
                    examRow(item)
                }
            }
        }
        .cardStyle()
    }

    /// CODE-01: 使用共享标签组件
    private func examRow(_ item: HealthDocument) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(item.name ?? "未命名").font(.subheadline).bold().foregroundColor(.appText)
                HStack(spacing: 6) {
                    SourceTag(sourceType: item.source_type)
                    StatusTag(status: item.extraction_status)
                    if let flags = item.abnormal_flags, !flags.isEmpty {
                        Text("\(flags.count) 项异常")
                            .font(.caption2).padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Color.appDanger.opacity(0.1)).foregroundColor(.appDanger).cornerRadius(4)
                    }
                }
            }
            Spacer()
            Button {
                vm.deleteId = item.id; vm.showDeleteAlert = true
            } label: {
                Text("删除").font(.caption).foregroundColor(.appDanger)
            }
        }
    }
}

/// 体检报告详情 — 对应小程序 pages/exam-reports/detail
struct ExamReportDetailView: View {
    let docId: String
    @StateObject private var vm = DocumentDetailViewModel()

    var body: some View {
        ScrollView {
            if let doc = vm.doc {
                VStack(alignment: .leading, spacing: 12) {
                    // 标题 — CODE-01: 使用共享标签组件
                    VStack(alignment: .leading, spacing: 4) {
                        Text(doc.name ?? "体检详情").font(.title3).bold()
                        HStack(spacing: 6) {
                            SourceDetailTag(sourceType: doc.source_type)
                            StatusDetailTag(status: doc.extraction_status)
                        }
                    }
                    .cardStyle()

                    // 异常提示
                    if let flags = doc.abnormal_flags, !flags.isEmpty {
                        HStack {
                            Image(systemName: "exclamationmark.triangle")
                                .foregroundColor(.appDanger)
                            Text("检测到 \(flags.count) 项异常指标")
                                .foregroundColor(.appDanger)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.appDanger.opacity(0.08))
                        .cornerRadius(8)

                        // 异常详情
                        VStack(alignment: .leading, spacing: 8) {
                            Label("异常项目", systemImage: "exclamationmark.octagon")
                                .font(.headline)
                                .foregroundColor(.appDanger)
                            ForEach(flags) { flag in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(flag.field ?? flag.name ?? "").font(.subheadline).bold()
                                    if let val = flag.value {
                                        Text("\(val) \(flag.unit ?? "")").font(.caption)
                                    }
                                    if let ref = flag.ref_range {
                                        Text("参考: \(ref)").font(.caption).foregroundColor(.appMuted)
                                    }
                                }
                                .padding(.vertical, 4)
                                Divider()
                            }
                        }
                        .cardStyle()
                    }

                    // CSV 表格 — CODE-01: 使用共享 CSVTableView
                    if let csv = doc.csv_data, let columns = csv.columns, let rows = csv.rows {
                        CSVTableView(title: "体检数据", icon: "tablecells", columns: columns, rows: rows, highlightAbnormal: true)
                    } else {
                        Text("暂无提取数据（LLM 处理中）")
                            .foregroundColor(.appMuted).cardStyle()
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .background(Color.appBackground)
        .navigationTitle("体检详情")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.fetchDetail(id: docId) }
        .overlay { if vm.loading { ProgressView() } }
    }

}

