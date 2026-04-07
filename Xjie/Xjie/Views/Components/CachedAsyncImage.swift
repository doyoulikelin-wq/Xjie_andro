import SwiftUI

/// PERF-05: 带缓存的异步图片组件 — 优先使用 3 天磁盘缓存
struct CachedAsyncImage<Placeholder: View>: View {
    let url: URL?
    @ViewBuilder let placeholder: () -> Placeholder

    @State private var image: UIImage?
    @State private var isLoading = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                placeholder()
            }
        }
        .task(id: url) {
            guard let url else { return }
            // 1) 缓存命中
            if let cached = ImageCacheManager.shared.image(for: url) {
                image = cached
                return
            }
            // 2) 网络加载
            isLoading = true
            defer { isLoading = false }
            do {
                let (data, _) = try await APIService.shared.trustedSession.data(from: url)
                guard !Task.isCancelled else { return }
                if let img = UIImage(data: data) {
                    ImageCacheManager.shared.store(img, for: url)
                    image = img
                }
            } catch {
                // 加载失败保持 placeholder
            }
        }
    }
}
