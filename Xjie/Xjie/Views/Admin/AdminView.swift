import SwiftUI

/// 管理员后台 — 查看系统数据
struct AdminView: View {
    @StateObject private var vm = AdminViewModel()
    @State private var selectedTab = 0

    var body: some View {
        VStack(spacing: 0) {
            // Tab selector
            Picker("", selection: $selectedTab) {
                Text("概览").tag(0)
                Text("用户").tag(1)
                Text("对话").tag(2)
                Text("组学").tag(3)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // Content
            ScrollView {
                VStack(spacing: 12) {
                    switch selectedTab {
                    case 0: statsPanel
                    case 1: usersPanel
                    case 2: conversationsPanel
                    case 3: omicsPanel
                    default: EmptyView()
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 20)
            }
        }
        .background(Color.appBackground)
        .navigationTitle("管理后台")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.fetchAll() }
        .refreshable { await vm.fetchAll() }
        .overlay { if vm.loading { ProgressView() } }
        .alert("错误", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("好", role: .cancel) {}
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }

    // MARK: - Stats Panel

    private var statsPanel: some View {
        VStack(spacing: 12) {
            if let s = vm.stats {
                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                ], spacing: 12) {
                    statCard(icon: "person.2.fill", title: "总用户", value: "\(s.total_users)", color: .appPrimary)
                    statCard(icon: "flame.fill", title: "7天活跃", value: "\(s.active_users_7d)", color: .appWarning)
                    statCard(icon: "bubble.left.and.bubble.right.fill", title: "对话数", value: "\(s.total_conversations)", color: .appAccent)
                    statCard(icon: "text.bubble.fill", title: "消息数", value: "\(s.total_messages)", color: .appSuccess)
                    statCard(icon: "doc.fill", title: "组学上传", value: "\(s.total_omics_uploads)", color: Color(hex: "0F4C81"))
                    statCard(icon: "fork.knife", title: "餐食记录", value: "\(s.total_meals)", color: Color(hex: "1B96C9"))
                }
            } else {
                Text("加载中…").foregroundColor(.appMuted)
            }
        }
    }

    private func statCard(icon: String, title: String, value: String, color: Color) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
            Text(value)
                .font(.title.bold())
                .foregroundColor(.appText)
            Text(title)
                .font(.caption)
                .foregroundColor(.appMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(color.opacity(0.08))
        .cornerRadius(12)
    }

    // MARK: - Users Panel

    private var usersPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("共 \(vm.users.count) 个用户").font(.caption).foregroundColor(.appMuted)
            ForEach(vm.users) { user in
                userRow(user)
            }
        }
    }

    private func userRow(_ user: AdminUserItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: user.is_admin ? "person.badge.shield.checkmark" : "person.fill")
                    .foregroundColor(user.is_admin ? .appWarning : .appPrimary)
                Text(user.username ?? "未命名")
                    .font(.headline)
                if user.is_admin {
                    Text("管理员")
                        .font(.caption2).bold()
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.appWarning))
                }
                Spacer()
                Text("ID: \(user.id)")
                    .font(.caption)
                    .foregroundColor(.appMuted)
            }
            HStack(spacing: 16) {
                Label("\(user.phone)", systemImage: "phone")
                    .font(.caption)
                    .foregroundColor(.appMuted)
                Label("\(user.conversation_count) 对话", systemImage: "bubble.left")
                    .font(.caption)
                    .foregroundColor(.appMuted)
                Label("\(user.message_count) 消息", systemImage: "text.bubble")
                    .font(.caption)
                    .foregroundColor(.appMuted)
            }
            if let t = user.created_at {
                Text("注册: \(formatTime(t))")
                    .font(.caption2)
                    .foregroundColor(.appMuted)
            }
        }
        .cardStyle()
    }

    // MARK: - Conversations Panel

    private var conversationsPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("共 \(vm.conversations.count) 个对话").font(.caption).foregroundColor(.appMuted)
            ForEach(vm.conversations) { conv in
                conversationRow(conv)
            }
        }
    }

    private func conversationRow(_ conv: AdminConversationItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(conv.title ?? "新对话")
                    .font(.subheadline).bold()
                    .lineLimit(1)
                Spacer()
                Text("\(conv.message_count) 条")
                    .font(.caption)
                    .foregroundColor(.appAccent)
            }
            HStack {
                Label(conv.username ?? "--", systemImage: "person")
                    .font(.caption)
                    .foregroundColor(.appMuted)
                Spacer()
                if let t = conv.updated_at {
                    Text(formatTime(t))
                        .font(.caption2)
                        .foregroundColor(.appMuted)
                }
            }
        }
        .cardStyle()
    }

    // MARK: - Omics Panel

    private var omicsPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("共 \(vm.omicsUploads.count) 个上传").font(.caption).foregroundColor(.appMuted)
            ForEach(vm.omicsUploads) { item in
                omicsRow(item)
            }
        }
    }

    private func omicsRow(_ item: AdminOmicsItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "doc.badge.gearshape")
                    .foregroundColor(Color(hex: "0F4C81"))
                Text(item.file_name ?? "--")
                    .font(.subheadline).bold()
                    .lineLimit(1)
                Spacer()
                if let risk = item.risk_level {
                    Text(risk)
                        .font(.caption2).bold()
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(riskColor(risk)))
                }
            }
            HStack {
                Label(item.username ?? "--", systemImage: "person")
                    .font(.caption).foregroundColor(.appMuted)
                Spacer()
                Text(item.omics_type)
                    .font(.caption).foregroundColor(.appAccent)
                if let size = item.file_size {
                    Text(formatBytes(size))
                        .font(.caption).foregroundColor(.appMuted)
                }
            }
            if let summary = item.llm_summary, !summary.isEmpty {
                Text(summary)
                    .font(.caption)
                    .foregroundColor(.appText)
                    .lineLimit(3)
            }
            if let t = item.created_at {
                Text(formatTime(t))
                    .font(.caption2)
                    .foregroundColor(.appMuted)
            }
        }
        .cardStyle()
    }

    // MARK: - Helpers

    private func riskColor(_ risk: String) -> Color {
        switch risk.lowercased() {
        case "high", "高": return .appDanger
        case "medium", "中": return .appWarning
        default: return .appSuccess
        }
    }

    private func formatBytes(_ bytes: Int) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024) }
        return String(format: "%.1f MB", Double(bytes) / 1024 / 1024)
    }

    private func formatTime(_ iso: String) -> String {
        // Show date portion only for brevity
        if let idx = iso.firstIndex(of: "T") {
            return String(iso[iso.startIndex..<idx])
        }
        return String(iso.prefix(10))
    }
}
