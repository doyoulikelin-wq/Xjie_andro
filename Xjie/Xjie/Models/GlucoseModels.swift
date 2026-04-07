import Foundation

// MARK: - 仪表板 & 血糖

struct DashboardHealth: Codable {
    let glucose: GlucoseDashboard?
    let kcal_today: Double?
    let meals_today: [MealItem]?
    let data_quality: DataQuality?
}

struct DataQuality: Codable {
    let glucose_gaps_hours: Double?
    let variability: String?
}

struct GlucoseDashboard: Codable {
    let last_24h: GlucoseSummary?
    let last_7d: GlucoseSummary?
}

struct GlucoseSummary: Codable {
    let window: String?
    let avg: Double?
    let tir_70_180_pct: Double?
    let min: Double?
    let max: Double?
    let variability: String?
    let gaps_hours: Double?
}

struct ProactiveMessage: Codable {
    let message: String?
    let has_rescue: Bool?
}

struct GlucosePoint: Codable, Identifiable {
    var id: String { ts }
    let ts: String
    let glucose_mgdl: Double
}

struct GlucoseRange: Codable {
    let min_ts: String?
    let max_ts: String?
}
