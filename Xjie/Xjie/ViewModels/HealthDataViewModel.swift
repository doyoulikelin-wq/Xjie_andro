import Foundation

@MainActor
final class HealthDataViewModel: ObservableObject {
    @Published var loading = false
    @Published var summary = ""
    @Published var summaryUpdatedAt = ""
    @Published var generatingSummary = false
    @Published var summaryProgress: Double = 0
    @Published var summaryStage: String = ""
    @Published var recordCount = 0
    @Published var examCount = 0
    @Published var showUploadSheet = false
    @Published var showDocumentPicker = false
    @Published var uploadDocType = "record"
    @Published var errorMessage: String?

    private let repository: HealthDataRepositoryProtocol

    init(repository: HealthDataRepositoryProtocol = HealthDataRepository()) {
        self.repository = repository
    }

    func fetchAll() async {
        loading = true
        defer { loading = false }

        let summaryRes = try? await repository.fetchSummary()
        guard !Task.isCancelled else { return }
        summary = summaryRes?.summary_text ?? ""
        if let updatedAt = summaryRes?.updated_at {
            if let date = Utils.parseISO(updatedAt) {
                summaryUpdatedAt = Utils.formatDate(updatedAt)
            }
        }
        recordCount = (try? await repository.fetchDocuments(docType: "record"))?.count ?? 0
        examCount = (try? await repository.fetchDocuments(docType: "exam"))?.count ?? 0
    }

    func generateSummary() async {
        guard !generatingSummary else { return }
        generatingSummary = true
        summaryProgress = 0
        summaryStage = "提交任务..."
        defer { generatingSummary = false; summaryStage = "" }
        do {
            let task = try await repository.generateSummaryAsync()
            let taskId = task.task_id

            while !Task.isCancelled {
                try await Task.sleep(for: .seconds(3))
                guard !Task.isCancelled else { return }

                let status = try await repository.getSummaryTask(taskId: taskId)
                summaryProgress = status.progress_pct ?? 0

                switch status.stage {
                case "l1":
                    summaryStage = "分析第 \(status.stage_current ?? 0)/\(status.stage_total ?? 0) 次检查..."
                case "l2":
                    summaryStage = "汇总第 \(status.stage_current ?? 0)/\(status.stage_total ?? 0) 年趋势..."
                case "l3":
                    summaryStage = "生成最终报告..."
                default:
                    summaryStage = "准备中..."
                }

                if status.status == "done" {
                    let result = try await repository.fetchSummary()
                    guard !Task.isCancelled else { return }
                    summary = result.summary_text ?? ""
                    summaryProgress = 1.0
                    return
                }

                if status.status == "failed" {
                    errorMessage = "生成失败: \(status.error_message ?? "未知错误")"
                    return
                }
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func uploadFile(data: Data, fileName: String) async {
        do {
            try await repository.uploadDocument(data: data, fileName: fileName, docType: uploadDocType)
            await fetchAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
