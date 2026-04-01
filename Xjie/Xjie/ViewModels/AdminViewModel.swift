import Foundation

// MARK: - Admin Models

struct AdminStats: Decodable {
    let total_users: Int
    let active_users_7d: Int
    let total_conversations: Int
    let total_messages: Int
    let total_omics_uploads: Int
    let total_meals: Int
}

struct AdminUserItem: Decodable, Identifiable {
    let id: Int
    let phone: String
    let username: String?
    let is_admin: Bool
    let created_at: String?
    let conversation_count: Int
    let message_count: Int
    let last_active: String?
}

struct AdminConversationItem: Decodable, Identifiable {
    let id: Int
    let user_id: Int
    let username: String?
    let title: String?
    let message_count: Int
    let created_at: String?
    let updated_at: String?
}

struct AdminOmicsItem: Decodable, Identifiable {
    let id: Int
    let user_id: Int
    let username: String?
    let omics_type: String
    let file_name: String?
    let file_size: Int?
    let risk_level: String?
    let llm_summary: String?
    let created_at: String?
}

struct FeatureTokenDetail: Decodable {
    let prompt_tokens: Int
    let completion_tokens: Int
    let total_tokens: Int
    let call_count: Int
}

struct AdminTokenStats: Decodable {
    let total_prompt_tokens: Int
    let total_completion_tokens: Int
    let total_tokens: Int
    let total_calls: Int
    let summary_task_tokens: Int
    let summary_task_count: Int
    let by_feature: [String: FeatureTokenDetail]
}

struct UserTokenItem: Decodable, Identifiable {
    var id: Int { user_id }
    let user_id: Int
    let username: String?
    let phone: String
    let audit_tokens: Int
    let audit_calls: Int
    let summary_tokens: Int
    let summary_calls: Int
    let total_tokens: Int
}

struct SummaryTaskItem: Decodable, Identifiable {
    var id: String { task_id }
    let task_id: String
    let user_id: Int
    let username: String?
    let status: String
    let stage: String?
    let token_used: Int
    let created_at: String?
    let updated_at: String?
}

struct AdminTokenDetails: Decodable {
    let by_user: [UserTokenItem]
    let recent_tasks: [SummaryTaskItem]
}

// MARK: - ViewModel

@MainActor
final class AdminViewModel: ObservableObject {
    @Published var stats: AdminStats?
    @Published var tokenStats: AdminTokenStats?
    @Published var tokenDetails: AdminTokenDetails?
    @Published var users: [AdminUserItem] = []
    @Published var conversations: [AdminConversationItem] = []
    @Published var omicsUploads: [AdminOmicsItem] = []
    @Published var featureFlags: [AdminFeatureFlag] = []
    @Published var skills: [AdminSkill] = []
    @Published var loading = false
    @Published var errorMessage: String?

    private let api: APIServiceProtocol

    init(api: APIServiceProtocol = APIService.shared) {
        self.api = api
    }

    func fetchStats() async {
        loading = true
        defer { loading = false }
        do {
            stats = try await api.get("/api/admin/stats")
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fetchUsers() async {
        do {
            users = try await api.get("/api/admin/users?page=1&size=100")
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fetchConversations() async {
        do {
            conversations = try await api.get("/api/admin/conversations?page=1&size=100")
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fetchOmics() async {
        do {
            omicsUploads = try await api.get("/api/admin/omics?page=1&size=100")
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fetchTokenStats() async {
        do {
            async let statsReq: AdminTokenStats = api.get("/api/admin/token-stats")
            async let detailsReq: AdminTokenDetails = api.get("/api/admin/token-stats/details")
            tokenStats = try await statsReq
            tokenDetails = try await detailsReq
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fetchAll() async {
        loading = true
        defer { loading = false }
        async let s: Void = fetchStats()
        async let u: Void = fetchUsers()
        async let c: Void = fetchConversations()
        async let o: Void = fetchOmics()
        async let t: Void = fetchTokenStats()
        async let f: Void = fetchFlags()
        async let sk: Void = fetchSkills()
        _ = await (s, u, c, o, t, f, sk)
    }

    // MARK: - Feature Flags CRUD

    func fetchFlags() async {
        do {
            let resp: AdminFeatureFlagList = try await api.get("/api/admin/feature-flags")
            featureFlags = resp.flags
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleFlag(_ flag: AdminFeatureFlag) async {
        do {
            let body = AdminFeatureFlagUpdate(enabled: !flag.enabled)
            let _: AdminFeatureFlag = try await api.patch("/api/admin/feature-flags/\(flag.id)", body: body)
            await fetchFlags()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateFlag(_ flag: AdminFeatureFlag, desc: String, pct: Int) async {
        do {
            let body = AdminFeatureFlagUpdate(description: desc, rollout_pct: pct)
            let _: AdminFeatureFlag = try await api.patch("/api/admin/feature-flags/\(flag.id)", body: body)
            await fetchFlags()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createFlag(key: String, description: String) async {
        do {
            let body = AdminFeatureFlagCreate(key: key, description: description)
            let _: AdminFeatureFlag = try await api.post("/api/admin/feature-flags", body: body)
            await fetchFlags()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteFlag(_ flag: AdminFeatureFlag) async {
        do {
            try await api.deleteVoid("/api/admin/feature-flags/\(flag.id)")
            await fetchFlags()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Skills CRUD

    func fetchSkills() async {
        do {
            let resp: AdminSkillList = try await api.get("/api/admin/skills")
            skills = resp.skills
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleSkill(_ skill: AdminSkill) async {
        do {
            let body = AdminSkillUpdate(enabled: !skill.enabled)
            let _: AdminSkill = try await api.patch("/api/admin/skills/\(skill.id)", body: body)
            await fetchSkills()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateSkill(_ skill: AdminSkill, name: String, description: String, priority: Int, triggerHint: String, promptTemplate: String) async {
        do {
            let body = AdminSkillUpdate(name: name, description: description, priority: priority, trigger_hint: triggerHint, prompt_template: promptTemplate)
            let _: AdminSkill = try await api.patch("/api/admin/skills/\(skill.id)", body: body)
            await fetchSkills()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createSkill(key: String, name: String, triggerHint: String, promptTemplate: String) async {
        do {
            let body = AdminSkillCreate(key: key, name: name, trigger_hint: triggerHint, prompt_template: promptTemplate)
            let _: AdminSkill = try await api.post("/api/admin/skills", body: body)
            await fetchSkills()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteSkill(_ skill: AdminSkill) async {
        do {
            try await api.deleteVoid("/api/admin/skills/\(skill.id)")
            await fetchSkills()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
