import Foundation

// MARK: - Feature Flags (公共 API)

/// GET /api/feature-flags → { flags: { key: enabled } }
struct FeatureFlagClientResponse: Decodable {
    let flags: [String: Bool]
}

// MARK: - Admin Feature Flags

struct AdminFeatureFlag: Codable, Identifiable {
    let id: Int
    let key: String
    var enabled: Bool
    var description: String
    var rollout_pct: Int
    let updated_at: String?
}

struct AdminFeatureFlagList: Decodable {
    let flags: [AdminFeatureFlag]
}

struct AdminFeatureFlagUpdate: Encodable {
    var enabled: Bool?
    var description: String?
    var rollout_pct: Int?
}

struct AdminFeatureFlagCreate: Encodable {
    let key: String
    var enabled: Bool = true
    var description: String = ""
    var rollout_pct: Int = 100
}

// MARK: - Admin Skills

struct AdminSkill: Codable, Identifiable, Hashable {
    let id: Int
    let key: String
    var name: String
    var description: String
    var enabled: Bool
    var priority: Int
    var trigger_hint: String
    var prompt_template: String
    let updated_at: String?

    static func == (lhs: AdminSkill, rhs: AdminSkill) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

struct AdminSkillList: Decodable {
    let skills: [AdminSkill]
}

struct AdminSkillUpdate: Encodable {
    var name: String?
    var description: String?
    var enabled: Bool?
    var priority: Int?
    var trigger_hint: String?
    var prompt_template: String?
}

struct AdminSkillCreate: Encodable {
    let key: String
    let name: String
    var description: String = ""
    var enabled: Bool = true
    var priority: Int = 100
    var trigger_hint: String = ""
    var prompt_template: String = ""
}
