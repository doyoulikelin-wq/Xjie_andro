import Foundation

/// 功能开关管理服务 — 应用启动时拉取，本地缓存
@MainActor
final class FeatureFlagService: ObservableObject {
    static let shared = FeatureFlagService()

    @Published private(set) var flags: [String: Bool] = [:]
    @Published private(set) var loaded = false

    private let api: APIServiceProtocol
    private var lastFetch: Date = .distantPast
    private let refreshInterval: TimeInterval = 300 // 5 min

    init(api: APIServiceProtocol = APIService.shared) {
        self.api = api
    }

    /// 功能是否启用，未知 key 默认 true
    func isEnabled(_ key: String) -> Bool {
        flags[key] ?? true
    }

    /// 启动时或切换前台时调用
    func fetchIfNeeded() async {
        guard Date().timeIntervalSince(lastFetch) > refreshInterval else { return }
        await fetch()
    }

    func fetch() async {
        do {
            let resp: FeatureFlagClientResponse = try await api.get("/api/feature-flags")
            flags = resp.flags
            loaded = true
            lastFetch = Date()
        } catch {
            // Keep stale cache; log only
            print("[FeatureFlagService] fetch failed: \(error.localizedDescription)")
        }
    }
}
