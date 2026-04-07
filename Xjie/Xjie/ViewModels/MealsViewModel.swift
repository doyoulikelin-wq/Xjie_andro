import SwiftUI
import PhotosUI

@MainActor
final class MealsViewModel: ObservableObject {
    @Published var loading = false
    @Published var meals: [MealItem] = []
    @Published var photos: [MealPhoto] = []
    @Published var uploading = false
    @Published var showPhotoPicker = false
    @Published var selectedPhoto: PhotosPickerItem?
    @Published var showManualInput = false
    @Published var manualKcal = ""
    @Published var errorMessage: String?
    /// PERF-03: 分页
    @Published var hasMore = true
    private let pageSize = APIConstants.pageSize

    private let api: APIServiceProtocol

    init(api: APIServiceProtocol = APIService.shared) {
        self.api = api
    }

    func fetchData() async {
        loading = true
        defer { loading = false }
        let mealsPath = URLBuilder.path("/api/meals", queryItems: [
            URLQueryItem(name: "limit", value: "\(pageSize)"),
            URLQueryItem(name: "offset", value: "0")
        ])
        async let m: [MealItem] = (try? await api.get(mealsPath)) ?? []
        async let p: [MealPhoto] = (try? await api.get("/api/dashboard/meals")) ?? []
        let fetchedMeals = await m
        let fetchedPhotos = await p
        guard !Task.isCancelled else { return }
        meals = fetchedMeals
        photos = fetchedPhotos
        hasMore = meals.count >= pageSize
    }

    /// PERF-03: 加载更多
    func loadMore() async {
        guard hasMore, !loading else { return }
        let offset = meals.count
        let path = URLBuilder.path("/api/meals", queryItems: [
            URLQueryItem(name: "limit", value: "\(pageSize)"),
            URLQueryItem(name: "offset", value: "\(offset)")
        ])
        let more: [MealItem] = (try? await api.get(path)) ?? []
        meals.append(contentsOf: more)
        hasMore = more.count >= pageSize
    }

    func uploadPhoto(item: PhotosPickerItem) async {
        uploading = true
        defer { uploading = false; selectedPhoto = nil }

        guard let data = try? await item.loadTransferable(type: Data.self) else { return }

        do {
            // Step 1: 获取上传凭证
            let ticket: MealUploadTicket = try await api.post(
                "/api/meals/photo/upload-url",
                body: PhotoUploadBody(filename: "meal.jpg", content_type: "image/jpeg")
            )

            // Step 2: 上传文件
            if let uploadUrl = ticket.upload_url, !uploadUrl.isEmpty {
                // SEC-02: 安全构建 URL
                guard let url = URL(string: uploadUrl) else {
                    throw APIError.invalidURL(uploadUrl)
                }
                var req = URLRequest(url: url)
                req.httpMethod = "PUT"
                req.httpBody = data
                req.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
                _ = try await APIService.shared.trustedSession.data(for: req)
            } else {
                _ = try await api.uploadFile(
                    "/api/meals/photo/upload", fileData: data, fileName: "meal.jpg", mimeType: "image/jpeg", formData: [:]
                )
            }

            // Step 3: 通知完成
            if let objectKey = ticket.object_key {
                try await api.postVoid(
                    "/api/meals/photo/complete",
                    body: PhotoCompleteBody(object_key: objectKey)
                )
            }

            await fetchData()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func addMealManual() async {
        guard let kcal = Int(manualKcal), kcal > 0 else { return }
        manualKcal = ""
        do {
            try await api.postVoid(
                "/api/meals",
                body: MealCreateBody(
                    meal_ts: ISO8601DateFormatter().string(from: Date()),
                    meal_ts_source: "manual",
                    kcal: kcal,
                    tags: [],
                    notes: ""
                )
            )
            await fetchData()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
