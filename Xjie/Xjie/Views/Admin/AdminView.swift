import SwiftUI

/// 管理员后台 — 查看系统数据
struct AdminView: View {
    @StateObject private var vm = AdminViewModel()
    @State private var selectedTab = 0
    @State private var showFlagCreate = false
    @State private var showSkillCreate = false
    @State private var editingSkill: AdminSkill?

    // New flag form
    @State private var newFlagKey = ""
    @State private var newFlagDesc = ""

    // New skill form
    @State private var newSkillKey = ""
    @State private var newSkillName = ""
    @State private var newSkillHint = ""
    @State private var newSkillPrompt = ""

    var body: some View {
        VStack(spacing: 0) {
            // Tab selector — use ScrollView for more tabs
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    tabButton("概览", tag: 0)
                    tabButton("用户", tag: 1)
                    tabButton("对话", tag: 2)
                    tabButton("组学", tag: 3)
                    tabButton("Token", tag: 4)
                    tabButton("开关", tag: 5)
                    tabButton("技能", tag: 6)
                }
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 8)

            // Content
            ScrollView {
                VStack(spacing: 12) {
                    switch selectedTab {
                    case 0: statsPanel
                    case 1: usersPanel
                    case 2: conversationsPanel
                    case 3: omicsPanel
                    case 4: tokenPanel
                    case 5: flagsPanel
                    case 6: skillsPanel
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

    // MARK: - Token Panel

    private var tokenPanel: some View {
        VStack(spacing: 12) {
            if let t = vm.tokenStats {
                // Total summary
                VStack(spacing: 8) {
                    Text("Token 消耗总览")
                        .font(.headline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        tokenCard(title: "总 Token", value: formatNumber(t.total_tokens), color: .appPrimary)
                        tokenCard(title: "总调用次数", value: formatNumber(t.total_calls), color: .appAccent)
                        tokenCard(title: "Prompt Token", value: formatNumber(t.total_prompt_tokens), color: Color(hex: "1B96C9"))
                        tokenCard(title: "Completion Token", value: formatNumber(t.total_completion_tokens), color: Color(hex: "0F4C81"))
                        tokenCard(title: "摘要任务 Token", value: formatNumber(t.summary_task_tokens), color: Color(hex: "8b5cf6"))
                        tokenCard(title: "摘要任务数", value: formatNumber(t.summary_task_count), color: Color(hex: "f97316"))
                    }
                }
                .cardStyle()

                // Per-feature breakdown
                VStack(alignment: .leading, spacing: 8) {
                    Text("按功能分布")
                        .font(.headline)
                    ForEach(Array(t.by_feature.keys.sorted()), id: \.self) { feature in
                        if let detail = t.by_feature[feature] {
                            featureTokenRow(feature: feature, detail: detail)
                        }
                    }
                }
                .cardStyle()
            } else {
                Text("加载中…").foregroundColor(.appMuted)
            }

            // Per-user breakdown
            if let details = vm.tokenDetails {
                VStack(alignment: .leading, spacing: 8) {
                    Text("按用户分布")
                        .font(.headline)
                    ForEach(details.by_user) { user in
                        userTokenRow(user)
                    }
                }
                .cardStyle()

                // Recent tasks
                VStack(alignment: .leading, spacing: 8) {
                    Text("近期摘要任务")
                        .font(.headline)
                    ForEach(details.recent_tasks) { task in
                        summaryTaskRow(task)
                    }
                }
                .cardStyle()
            }
        }
    }

    private func tokenCard(title: String, value: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .foregroundColor(color)
            Text(title)
                .font(.caption)
                .foregroundColor(.appMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(color.opacity(0.08))
        .cornerRadius(10)
    }

    private func featureTokenRow(feature: String, detail: FeatureTokenDetail) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(featureDisplayName(feature))
                    .font(.subheadline.bold())
                Text("\(formatNumber(detail.call_count)) 次调用")
                    .font(.caption)
                    .foregroundColor(.appMuted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(formatNumber(detail.total_tokens))
                    .font(.subheadline.bold())
                    .foregroundColor(.appPrimary)
                Text("P: \(formatNumber(detail.prompt_tokens)) / C: \(formatNumber(detail.completion_tokens))")
                    .font(.caption2)
                    .foregroundColor(.appMuted)
            }
        }
        .padding(.vertical, 6)
    }

    private func featureDisplayName(_ feature: String) -> String {
        switch feature {
        case "chat": return "AI 对话"
        case "meal_vision": return "膳食图像分析"
        case "health_summary": return "健康报告摘要"
        case "agent": return "智能 Agent"
        case "omics_analysis": return "组学分析"
        default: return feature
        }
    }

    private func userTokenRow(_ user: UserTokenItem) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(user.username ?? "未命名")
                    .font(.subheadline.bold())
                Text(user.phone)
                    .font(.caption)
                    .foregroundColor(.appMuted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(formatNumber(user.total_tokens))
                    .font(.subheadline.bold())
                    .foregroundColor(.appPrimary)
                HStack(spacing: 8) {
                    Text("Audit: \(formatNumber(user.audit_tokens))")
                        .font(.caption2)
                    Text("摘要: \(formatNumber(user.summary_tokens))")
                        .font(.caption2)
                }
                .foregroundColor(.appMuted)
            }
        }
        .padding(.vertical, 6)
    }

    private func summaryTaskRow(_ task: SummaryTaskItem) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(task.username ?? "未知用户")
                    .font(.subheadline.bold())
                Text(task.task_id.prefix(8) + "…")
                    .font(.caption.monospaced())
                    .foregroundColor(.appMuted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(taskStatusText(task.status))
                    .font(.caption2).bold()
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(taskStatusColor(task.status)))
                HStack(spacing: 4) {
                    Text("\(formatNumber(task.token_used)) tokens")
                        .font(.caption2)
                    if let t = task.created_at {
                        Text(formatTime(t))
                            .font(.caption2)
                    }
                }
                .foregroundColor(.appMuted)
            }
        }
        .padding(.vertical, 6)
    }

    private func taskStatusText(_ status: String) -> String {
        switch status {
        case "done": return "完成"
        case "failed": return "失败"
        case "running": return "运行中"
        case "pending": return "等待中"
        default: return status
        }
    }

    private func taskStatusColor(_ status: String) -> Color {
        switch status {
        case "done": return .appSuccess
        case "failed": return .appDanger
        case "running": return .appWarning
        default: return .appMuted
        }
    }

    private func formatNumber(_ n: Int) -> String {
        if n >= 1_000_000 { return String(format: "%.1fM", Double(n) / 1_000_000) }
        if n >= 1_000 { return String(format: "%.1fK", Double(n) / 1_000) }
        return "\(n)"
    }

    // MARK: - Tab Button

    private func tabButton(_ title: String, tag: Int) -> some View {
        Button {
            selectedTab = tag
        } label: {
            Text(title)
                .font(.subheadline.weight(selectedTab == tag ? .bold : .regular))
                .foregroundColor(selectedTab == tag ? .white : .appText)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selectedTab == tag ? Color.appPrimary : Color.clear)
                .cornerRadius(8)
        }
    }

    // MARK: - Feature Flags Panel

    private var flagsPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("功能开关 (\(vm.featureFlags.count))")
                    .font(.headline)
                Spacer()
                Button {
                    showFlagCreate = true
                } label: {
                    Label("新增", systemImage: "plus.circle.fill")
                        .font(.subheadline)
                }
            }

            ForEach(vm.featureFlags) { flag in
                flagRow(flag)
            }

            if vm.featureFlags.isEmpty {
                Text("暂无开关").foregroundColor(.appMuted).frame(maxWidth: .infinity)
            }
        }
        .sheet(isPresented: $showFlagCreate) {
            createFlagSheet
        }
    }

    private func flagRow(_ flag: AdminFeatureFlag) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(flag.key)
                    .font(.subheadline.bold().monospaced())
                Spacer()
                Button {
                    Task { await vm.toggleFlag(flag) }
                } label: {
                    Text(flag.enabled ? "启用" : "禁用")
                        .font(.caption2.bold())
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(flag.enabled ? Color.appSuccess : Color.appMuted))
                }
            }
            if !flag.description.isEmpty {
                Text(flag.description)
                    .font(.caption)
                    .foregroundColor(.appMuted)
            }
            HStack {
                Text("灰度: \(flag.rollout_pct)%")
                    .font(.caption2)
                    .foregroundColor(.appMuted)
                Spacer()
                if let t = flag.updated_at {
                    Text(formatTime(t))
                        .font(.caption2)
                        .foregroundColor(.appMuted)
                }
            }
            HStack(spacing: 12) {
                Spacer()
                Button("删除") {
                    Task { await vm.deleteFlag(flag) }
                }
                .font(.caption)
                .foregroundColor(.appDanger)
            }
        }
        .cardStyle()
    }

    private var createFlagSheet: some View {
        NavigationView {
            Form {
                TextField("Key (如: new_feature)", text: $newFlagKey)
                TextField("描述", text: $newFlagDesc)
            }
            .navigationTitle("新增开关")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showFlagCreate = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") {
                        Task {
                            await vm.createFlag(key: newFlagKey, description: newFlagDesc)
                            newFlagKey = ""
                            newFlagDesc = ""
                            showFlagCreate = false
                        }
                    }
                    .disabled(newFlagKey.isEmpty)
                }
            }
        }
    }

    // MARK: - Skills Panel

    private var skillsPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("技能管理 (\(vm.skills.count))")
                    .font(.headline)
                Spacer()
                Button {
                    showSkillCreate = true
                } label: {
                    Label("新增", systemImage: "plus.circle.fill")
                        .font(.subheadline)
                }
            }

            ForEach(vm.skills) { skill in
                skillRow(skill)
            }

            if vm.skills.isEmpty {
                Text("暂无技能").foregroundColor(.appMuted).frame(maxWidth: .infinity)
            }
        }
        .sheet(isPresented: $showSkillCreate) {
            createSkillSheet
        }
        .sheet(item: $editingSkill) { skill in
            editSkillSheet(skill)
        }
    }

    private func skillRow(_ skill: AdminSkill) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("P\(skill.priority)")
                    .font(.caption.bold().monospaced())
                    .foregroundColor(.appAccent)
                Text(skill.name)
                    .font(.subheadline.bold())
                Spacer()
                Button {
                    Task { await vm.toggleSkill(skill) }
                } label: {
                    Text(skill.enabled ? "启用" : "禁用")
                        .font(.caption2.bold())
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(skill.enabled ? Color.appSuccess : Color.appMuted))
                }
            }
            Text(skill.key)
                .font(.caption.monospaced())
                .foregroundColor(.appMuted)
            if !skill.trigger_hint.isEmpty {
                Text("触发: \(skill.trigger_hint)")
                    .font(.caption)
                    .foregroundColor(Color(hex: "1B96C9"))
                    .lineLimit(2)
            }
            if !skill.prompt_template.isEmpty {
                Text(skill.prompt_template)
                    .font(.caption2)
                    .foregroundColor(.appMuted)
                    .lineLimit(3)
            }
            HStack(spacing: 12) {
                Spacer()
                Button("编辑") {
                    editingSkill = skill
                }
                .font(.caption)
                .foregroundColor(.appPrimary)
                Button("删除") {
                    Task { await vm.deleteSkill(skill) }
                }
                .font(.caption)
                .foregroundColor(.appDanger)
            }
        }
        .cardStyle()
    }

    private var createSkillSheet: some View {
        NavigationView {
            Form {
                Section("基本信息") {
                    TextField("Key (如: new_skill)", text: $newSkillKey)
                    TextField("名称", text: $newSkillName)
                }
                Section("触发关键词 (逗号分隔)") {
                    TextField("如: 血糖,glucose,CGM", text: $newSkillHint)
                }
                Section("Prompt 模板") {
                    TextEditor(text: $newSkillPrompt)
                        .frame(minHeight: 120)
                }
            }
            .navigationTitle("新增技能")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showSkillCreate = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建") {
                        Task {
                            await vm.createSkill(key: newSkillKey, name: newSkillName, triggerHint: newSkillHint, promptTemplate: newSkillPrompt)
                            newSkillKey = ""
                            newSkillName = ""
                            newSkillHint = ""
                            newSkillPrompt = ""
                            showSkillCreate = false
                        }
                    }
                    .disabled(newSkillKey.isEmpty || newSkillName.isEmpty)
                }
            }
        }
    }

    private func editSkillSheet(_ skill: AdminSkill) -> some View {
        SkillEditSheet(skill: skill) { name, desc, priority, hint, prompt in
            Task {
                await vm.updateSkill(skill, name: name, description: desc, priority: priority, triggerHint: hint, promptTemplate: prompt)
                editingSkill = nil
            }
        } onCancel: {
            editingSkill = nil
        }
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

// MARK: - Skill Edit Sheet

struct SkillEditSheet: View {
    let skill: AdminSkill
    let onSave: (String, String, Int, String, String) -> Void
    let onCancel: () -> Void

    @State private var name: String
    @State private var desc: String
    @State private var priority: String
    @State private var triggerHint: String
    @State private var promptTemplate: String

    init(skill: AdminSkill, onSave: @escaping (String, String, Int, String, String) -> Void, onCancel: @escaping () -> Void) {
        self.skill = skill
        self.onSave = onSave
        self.onCancel = onCancel
        _name = State(initialValue: skill.name)
        _desc = State(initialValue: skill.description)
        _priority = State(initialValue: "\(skill.priority)")
        _triggerHint = State(initialValue: skill.trigger_hint)
        _promptTemplate = State(initialValue: skill.prompt_template)
    }

    var body: some View {
        NavigationView {
            Form {
                Section("基本信息") {
                    TextField("名称", text: $name)
                    TextField("描述", text: $desc)
                    TextField("优先级 (数字越小越优先)", text: $priority)
                        .keyboardType(.numberPad)
                }
                Section("触发关键词 (逗号分隔)") {
                    TextField("如: 血糖,glucose,CGM", text: $triggerHint)
                }
                Section("Prompt 模板") {
                    TextEditor(text: $promptTemplate)
                        .frame(minHeight: 150)
                }
            }
            .navigationTitle("编辑技能")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        onSave(name, desc, Int(priority) ?? skill.priority, triggerHint, promptTemplate)
                    }
                    .disabled(name.isEmpty)
                }
            }
        }
    }
}
