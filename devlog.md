# Xjie iOS 开发日志 (DevLog)

> 项目：Xjie iOS App (SwiftUI)  
> 起始日期：2026-03-24  
> 源项目：微信小程序 → iOS 原生转换

---

## 2026-03-27 — v0.7.0 AI 体验全面升级

### 数据库修复 ✅
- `user_profiles` 表补齐 6 个缺失列: `display_name`, `sex`, `height_cm`, `weight_kg`, `liver_risk_level`, `cohort`
- `chat_messages` 表已包含 `analysis` 列（存储详细分析）

### AI 聊天修复 ✅
- **thread_id 缺失**: `ChatResult` 新增 `thread_id` 字段，iOS 可追踪会话
- **403 授权问题**: iOS 端自动开启 AI 授权 (login/signup 后 PATCH `/api/users/consent`)，403 时自动重试

### AI 系统提示词重写 ✅
- AI 助手命名「小杰」，温暖友好风格，像一个懂医学的朋友
- **数据感知策略**: 有数据直接引用分析；无数据绝不说"缺乏数据"，基于描述给建议
- 自然引导用户关注代谢健康话题（血糖、饮食、体检）
- 结构化 JSON 输出: `summary`(1-2 句) + `analysis`(详细 Markdown) + `followups` + `profile_extracted`

### 用户画像自动提取 ✅
- AI 从对话中提取用户信息（性别/年龄/身高/体重/昵称）
- 后端 `_apply_profile_extraction()` 自动写入 `user_profiles` 表
- 仅在字段为空时更新，不覆盖已有数据

### 可展开分析气泡 UI ✅
- `ChatView` 气泡显示 summary（简洁 1-2 句话）
- 助手消息下方「查看详细分析 ▸」按钮，点击展开完整 Markdown 分析
- 动画展开/收起，`@State expandedIDs` 追踪展开状态

### 修改文件

| 文件 | 变更 |
|---|---|
| `backend/app/providers/openai_provider.py` | 重写 SYSTEM_PROMPT、数据感知消息构建、结构化响应解析 |
| `backend/app/providers/base.py` | `ChatLLMResult` 增加 `profile_extracted` |
| `backend/app/schemas/chat.py` | `ChatResult` 增加 `summary` + `analysis` |
| `backend/app/routers/chat.py` | 新增 `_apply_profile_extraction()`，返回 summary+analysis |
| `Xjie/Models/ChatModels.swift` | `ChatResponse` 增加 `summary`/`analysis`，新增授权模型 |
| `Xjie/ViewModels/ChatViewModel.swift` | `ChatMessageItem` 加 `analysis` 字段，403 自动授权重试 |
| `Xjie/Views/Chat/ChatView.swift` | 可展开分析气泡 UI |
| `Xjie/ViewModels/LoginViewModel.swift` | 手机号验证修复 (email→phone) |

---

## 2026-03-26 — v0.6.0 P4+P5+P6 全部完成（39/39 ✅）

### P4 网络健壮性 (NET-01 ~ NET-04) ✅

**NET-01 网络状态监测**  
新建 `Utils/NetworkMonitor.swift`：`NWPathMonitor` 封装，@Published `isConnected` / `connectionType`。  
`XjieApp` 注入 `.environmentObject(networkMonitor)`，`MainTabView` 断网时显示全局 Banner（wifi.slash 图标 + "网络不可用"）。

**NET-02 请求重试策略**  
`APIService.request()` 增加 `retryCount` 参数，URLError（超时/断网）及 5xx 自动重试最多 2 次，指数退避 1s → 2s。

**NET-03 离线缓存**  
新建 `Utils/OfflineCacheManager.swift`：文件级 Codable 缓存（cachesDirectory/offline_cache/）。  
`HomeViewModel` 成功时缓存、失败时读取缓存 + `isOfflineData` 标记。

**NET-04 请求超时配置**  
`URLRequest.timeoutInterval` = `APIConstants.requestTimeout`(15s) / `APIConstants.uploadTimeout`(60s)。

### P5 代码质量 (CODE-01 ~ CODE-03) ✅

**CODE-01 抽取重复代码**  
- `Views/Shared/CSVTableView.swift` — ExamReportViews + MedicalRecordViews 共用 CSV 表格
- `Views/Shared/DocumentTagView.swift` — SourceTag / StatusTag / SourceDetailTag / StatusDetailTag 4 组件
- `Views/Shared/MetricItemView.swift` — HomeView + GlucoseView 共用指标卡片

**CODE-02 魔法数字常量化**  
新建 `Utils/Constants.swift`：`ChartConstants`（绘图参数）+ `APIConstants`（超时/分页）。  
GlucoseView Canvas、MealsViewModel、ChatViewModel 全面引用。

**CODE-03 移除/标记未使用代码**  
OmicsView 硬编码数据标记 `// TODO: CODE-03`；HealthDataView emoji 替换为 SF Symbol `brain.head.profile`。

### P6 生产就绪 (PROD-01 ~ PROD-06) ✅

**PROD-01 结构化日志**  
新建 `Utils/AppLogger.swift`：`os.Logger` 按 network/auth/data/ui 分类。APIService 关键路径已集成。

**PROD-02 崩溃上报**  
新建 `Utils/CrashReporter.swift`：`CrashReporting` 协议 + 默认实现（AppLogger 转发），可替换为 Crashlytics/Sentry。

**PROD-03 国际化 (i18n)**  
新建 `Resources/zh-Hans.lproj/Localizable.strings` + `Resources/en.lproj/Localizable.strings`（~150 键值对），覆盖标签栏、导航标题、通用文案。

**PROD-04 隐私清单**  
新建 `PrivacyInfo.xcprivacy`：声明健康信息 + 相册访问数据类型 + 文件时间戳 API 使用。

**PROD-05 CI/CD**  
新建 `.github/workflows/ci.yml`：GitHub Actions 自动构建 + 测试（macOS 15 + DerivedData 缓存）。

**PROD-06 App Store 准备（文档阶段）**  
隐私清单已就绪，i18n 基础已建立。应用图标/截图/描述待设计师介入。

### 新增文件 (12)

| 文件 | 用途 |
|---|---|
| `Utils/NetworkMonitor.swift` | NWPathMonitor 网络状态监测 |
| `Utils/OfflineCacheManager.swift` | 文件级离线缓存管理器 |
| `Utils/AppLogger.swift` | os.Logger 结构化日志 |
| `Utils/CrashReporter.swift` | 崩溃上报协议 + 默认实现 |
| `Utils/Constants.swift` | ChartConstants + APIConstants |
| `Views/Shared/CSVTableView.swift` | 可复用 CSV 表格组件 |
| `Views/Shared/DocumentTagView.swift` | 来源/状态标签组件 |
| `Views/Shared/MetricItemView.swift` | 指标卡片组件 |
| `PrivacyInfo.xcprivacy` | Apple 隐私清单 |
| `Resources/zh-Hans.lproj/Localizable.strings` | 中文本地化 |
| `Resources/en.lproj/Localizable.strings` | 英文本地化 |
| `.github/workflows/ci.yml` | GitHub Actions CI |

### 修改文件 (12)

`APIService.swift`、`XjieApp.swift`、`MainTabView.swift`、`HomeViewModel.swift`、`ExamReportViews.swift`、`MedicalRecordViews.swift`、`HomeView.swift`、`GlucoseView.swift`、`OmicsView.swift`、`HealthDataView.swift`、`MealsViewModel.swift`、`ChatViewModel.swift`

### 构建验证

- **BUILD SUCCEEDED** — 52 Swift 源文件
- **TEST SUCCEEDED** — 46 tests, 0 failures

---

## 2026-03-25 — v0.5.0 P3 性能优化完成

### PERF-01 DateFormatter 缓存 ✅

`Utils.swift` 顶层 `private let` 缓存 4 个 formatter（ISO8601 带/不带毫秒、yyyy-MM-dd HH:mm、HH:mm）。新增 `Utils.parseISO()` 统一入口。`HealthDataViewModel` / `MealsViewModel` / `GlucoseViewModel` 内联 formatter 同步替换。

### PERF-02 血糖图表数据预处理 ✅

`GlucoseViewModel` 新增 `chartData: [(Date, Double)]` 预计算属性，在 fetchPoints 成功后一次性解析。`GlucoseChartCanvas` 改为接收预计算数组，Canvas draw 闭包内零日期解析。

### PERF-03 列表分页加载 ✅

- `MealsViewModel`: pageSize=20 + offset 分页 + `loadMore()` + UI "加载更多"按钮
- `ChatViewModel`: 会话列表 pageSize=20 + `loadMoreConversations()` + 历史面板加载更多

### PERF-04 请求取消 (Task Cancellation) ✅

- `GlucoseViewModel`: `pointsTask` 存储引用，切换窗口时 cancel + 重建 Task
- 全部 ViewModel: await 后 `guard !Task.isCancelled else { return }` 守卫检查，页面消失后不更新 UI

涉及 ViewModel: Home、Glucose、Chat、Meals、HealthBrief、HealthData、ExamReport、MedicalRecord、Settings

### PERF-05 图片缓存（3 天 TTL）✅

- 新建 `Utils/ImageCacheManager.swift`: NSCache 内存缓存（100 张 / 50 MB）+ 磁盘缓存
- 3 天自动过期清理 `cleanExpired()` + `clearAll()` 公开方法
- 新建 `Views/Components/CachedAsyncImage.swift`: SwiftUI 组件，缓存优先 → 网络兜底

### 新增文件 (2)

| 文件 | 用途 |
|---|---|
| `Utils/ImageCacheManager.swift` | 图片缓存管理器（3 天 TTL） |
| `Views/Components/CachedAsyncImage.swift` | 带缓存的异步图片组件 |

### 修改文件 (11)

`Utils.swift`、`GlucoseViewModel.swift`、`GlucoseView.swift`、`HomeViewModel.swift`、`ChatViewModel.swift`、`ChatView.swift`、`MealsViewModel.swift`、`MealsView.swift`、`HealthBriefViewModel.swift`、`HealthDataViewModel.swift`、`ExamReportViewModels.swift`、`MedicalRecordViewModels.swift`、`SettingsViewModel.swift`

### 构建验证

- **BUILD SUCCEEDED** — 44 Swift 源文件
- **TEST SUCCEEDED** — 46 tests, 0 failures

---

## 2026-03-25 — v0.4.0 P2 UI/UX 完善完成

### UI-01 Dark Mode 全面适配 ✅

`Theme.swift`: `appBackground` → `systemBackground`、`appCardBg` → `secondarySystemBackground`、`appText` → `label`、`appMuted` → `secondaryLabel`。`CardStyle` 暗色模式自动移除阴影。

### UI-02 空状态页面 + UI-03 错误状态组件 ✅

新建 `Views/Components/`:
- `EmptyStateView.swift` — SF Symbol 图标 + 标题 + 副标题 + 可选操作按钮
- `ErrorStateView.swift` — 自动识别网络/认证/服务器错误，展示不同图标和文案，带重试按钮

已替换：HealthView、MealsView、ExamReportListView、MedicalRecordListView 的空状态

### UI-04 Accessibility 无障碍 ✅

30+ 硬编码 emoji 全部替换为 SF Symbols（`Image(systemName:)` / `Label(_:systemImage:)`）。所有可交互元素自动获得 VoiceOver 支持。

涉及文件：HomeView、ChatView、HealthDataView、HealthView、MealsView、SettingsView、OmicsView、ExamReportViews、MedicalRecordViews

### UI-05 弃用 API 替换 ✅

- `LoginView`: `.autocapitalization(.none)` → `.textInputAutocapitalization(.never)`
- `HealthDataView`: `UIDocumentPickerViewController(documentTypes:in:)` → `UTType` + `forOpeningContentTypes:`

### UI-06 启动画面 ✅

- Info.plist 添加 `UILaunchScreen` 配置
- 新建 `SplashView.swift`：品牌渐变背景 + Logo + 渐入缩放动画 (1.5s)
- `XjieApp.swift` 集成 ZStack 叠加 splash

### UI-07 iPad 自适应布局 ✅

`MainTabView` 使用 `@Environment(\.horizontalSizeClass)` 判断:
- iPhone (compact) → 保持 TabView
- iPad (regular) → NavigationSplitView + 侧边栏导航

### 新增文件 (3)

| 文件 | 用途 |
|---|---|
| `Views/Components/EmptyStateView.swift` | 通用空状态组件 |
| `Views/Components/ErrorStateView.swift` | 通用错误状态组件 |
| `Views/Components/SplashView.swift` | 启动品牌画面 |

### 修改文件 (13)

`Theme.swift`、`Info.plist`、`XjieApp.swift`、`MainTabView.swift`、`HomeView.swift`、`LoginView.swift`、`ChatView.swift`、`HealthDataView.swift`、`HealthView.swift`、`MealsView.swift`、`SettingsView.swift`、`OmicsView.swift`、`ExamReportViews.swift`、`MedicalRecordViews.swift`

### 构建验证

- **BUILD SUCCEEDED** — 42 Swift 源文件
- **TEST SUCCEEDED** — 46 tests, 0 failures

---

## 2026-03-25 — v0.2.0 P0 + P1 安全/架构重构完成

### 一、P0 安全与稳定性（全部完成 ✅）

| 编号 | 任务 | 状态 |
|---|---|---|
| SEC-01 | Token 迁移至 Keychain | ✅ `AuthManager` 全面改用 `KeychainHelper` |
| SEC-02 | 移除所有强制解包 (`!`) | ✅ `APIService`、`MealsViewModel` 中 URL/Response 均用 guard let |
| SEC-03 | BaseURL 环境配置 | ✅ `Environment.swift` — Info.plist 或 DEBUG fallback |
| SEC-04 | URL 参数安全构建 | ✅ `URLBuilder` 枚举 + `URLComponents`/`URLQueryItem` |
| ERR-01 | 清除空 catch 块 | ✅ 所有 ViewModel 增加 `@Published var errorMessage`，View 层 `.alert` 展示 |
| ERR-02 | Token 刷新并发竞态修复 | ✅ `refreshTask: Task<Void, Error>?` 排队机制 |
| BUG-01 | ChatMessage.id 存储属性 | ✅ `let id: String` + 自定义 `init(from decoder:)` |

### 二、P1 架构与可测试性（核心完成 ✅）

| 编号 | 任务 | 状态 |
|---|---|---|
| ARCH-01 | APIServiceProtocol 协议层 | ✅ `Services/APIServiceProtocol.swift` |
| ARCH-02 | ViewModel 依赖注入 | ✅ 所有 ViewModel 接收 `api: APIServiceProtocol` 参数 |
| ARCH-03 | ViewModel 从 View 拆分 | ✅ 10 个 ViewModel 独立至 `ViewModels/` 目录 |
| ARCH-04 | Models.swift 按 feature 拆分 | ✅ 6 个模型文件：Auth/Glucose/Meal/Health/Chat/Settings |
| ARCH-05 | Repository 层抽取 | ✅ `Repositories/HealthDataRepository.swift` |
| TEST-01 | 核心单元测试 | ✅ MockAPIService + Utils(22) + ChatMessage BUG-01 回归(4) |
| TEST-02 | ViewModel 单元测试 | ✅ Home(3) + Login(8) + Chat(6) + Glucose(3) = 20 tests |

---

## 2026-03-25 — v0.3.0 单元测试全覆盖

### 测试目标建立

- 新建 `XjieTests/` target（unit-test bundle，hosted in Xjie.app）
- xcscheme 更新 TestAction 引用

### 测试文件（7 个）

| 文件 | 测试数 | 覆盖内容 |
|---|---|---|
| MockAPIService.swift | — | 测试替身，符合 APIServiceProtocol |
| UtilsTests.swift | 22 | formatDate/formatTime/toFixed/glucoseColor/URLBuilder/MIMETypeHelper |
| ChatMessageTests.swift | 4 | BUG-01 回归：id decode/生成/稳定性 |
| HomeViewModelTests.swift | 3 | fetchData 成功/失败/loading |
| LoginViewModelTests.swift | 8 | 输入验证(3) + 登录成功(2) + 网络错误 + subjects 加载(2) |
| ChatViewModelTests.swift | 6 | 发消息/空消息/错误/新对话/历史加载 |
| GlucoseViewModelTests.swift | 3 | fetchRange/error/窗口切换 |

**合计：46 tests, 0 failures ✅**

### 附带修复

- 4 个 Model 文件 `Decodable` → `Codable`（MockAPIService 编码需要）
- ChatMessage 添加 memberwise `init(id:role:content:)`

### 三、新增文件清单（v0.2.0）

**Utils (2)**：`KeychainHelper.swift`、`MIMETypeHelper.swift`  
**Services (2)**：`Environment.swift`、`APIServiceProtocol.swift`  
**Models (6)**：`AuthModels.swift`、`GlucoseModels.swift`、`MealModels.swift`、`HealthModels.swift`、`ChatModels.swift`、`SettingsModels.swift`  
**Repositories (1)**：`HealthDataRepository.swift`  
**ViewModels (10)**：`HomeViewModel.swift`、`LoginViewModel.swift`、`GlucoseViewModel.swift`、`ChatViewModel.swift`、`HealthDataViewModel.swift`、`MedicalRecordViewModels.swift`、`ExamReportViewModels.swift`、`SettingsViewModel.swift`、`HealthBriefViewModel.swift`、`MealsViewModel.swift`

### 四、重写文件

- **AuthManager.swift**：全面 Keychain 存储
- **APIService.swift**：协议一致性、安全 URL、并发刷新
- **Models.swift**：内容拆分至 6 文件（保留空壳兼容）
- **Utils.swift**：新增 URLBuilder
- **10 个 View 文件**：移除内嵌 ViewModel、统一错误提示

### 五、LLM API 占位

以下位置标记了 `// TODO: [LLM API]`，等后端接口就绪后接入：
- `ChatViewModel.swift` — 对话请求/流式回复
- `HealthDataViewModel.swift` — AI 健康数据总结
- `HealthBriefViewModel.swift` — AI 摘要生成

### 六、编译验证

- **构建结果**：`BUILD SUCCEEDED` ✅（iPhone 17 Simulator, iOS 26.3.1）
- **39 个 Swift 源文件**
- **仅 1 个 deprecation warning**：`UIDocumentPickerViewController(documentTypes:)` — 计划在 UI-05 中更新

---

## 2026-03-24 — v0.1.0 初始转换完成

### 一、项目创建

从微信小程序（WXML + WXSS + JS）完整转换为 iOS 原生 SwiftUI 应用，保留全部架构和业务逻辑。

**转换映射**：

| 微信小程序 | iOS (SwiftUI) |
|---|---|
| `app.js globalData` | `AuthManager` @MainActor 单例 |
| `utils/api.js` (wx.request) | `APIService` actor (URLSession async/await) |
| `Page({data, onLoad, methods})` | SwiftUI View + @MainActor ViewModel (ObservableObject) |
| `tabBar` 4 标签 | `TabView` (首页/健康数据/多组学/AI助手) |
| Canvas 2D | SwiftUI `Canvas` + `GraphicsContext` |
| `wx.setStorageSync/getStorageSync` | `UserDefaults` |
| `wx.chooseMedia` | `PhotosPicker` (PhotosUI) |
| `wx.chooseMessageFile` | `UIDocumentPickerViewController` (UIViewControllerRepresentable) |
| `wx.showModal` (editable) | `.alert` + TextField |
| `wx.showActionSheet` | `.confirmationDialog` |

### 二、项目结构

```
Xjie/
├── Xjie.xcodeproj/          # Xcode 工程 + scheme
├── Xjie/
│   ├── App/
│   │   └── XjieApp.swift    # @main 入口，auth 路由
│   ├── Models/
│   │   └── Models.swift           # 30+ Codable 数据模型
│   ├── Services/
│   │   ├── AuthManager.swift      # 认证状态管理
│   │   └── APIService.swift       # HTTP 客户端 (JWT + 401 自动刷新)
│   ├── Utils/
│   │   ├── Theme.swift            # 颜色系统 + CardStyle
│   │   └── Utils.swift            # 日期格式化、血糖颜色
│   ├── Views/
│   │   ├── Home/                  # 首页仪表盘 + TabView
│   │   ├── Login/                 # 登录（Subject/Email 双模式）
│   │   ├── Glucose/               # 血糖曲线图（Canvas 绘制）
│   │   ├── Chat/                  # AI 对话（历史会话 + 追问建议）
│   │   ├── HealthData/            # 健康数据中心（AI 总结 + 上传）
│   │   ├── MedicalRecords/        # 病历列表 + 详情
│   │   ├── ExamReports/           # 体检报告列表 + 详情
│   │   ├── Omics/                 # 多组学（蛋白/代谢/基因）
│   │   ├── Health/                # 每日健康简报
│   │   ├── Meals/                 # 膳食记录（拍照 + 手动）
│   │   └── Settings/              # 设置（干预等级/同意书/登出）
│   ├── Assets.xcassets/           # 图标 + 主题色
│   ├── Preview Content/
│   └── Info.plist                 # ATS localhost 例外
```

### 三、编译验证

- **目标**：iOS 17.0+，iPhone + iPad
- **编译器**：Xcode 15.4+，Swift 5.0
- **构建结果**：`BUILD SUCCEEDED` ✅（iPhone 17 Simulator）
- **18 个 Swift 源文件**，约 2,837 行代码

### 四、技术栈

- **前端**：SwiftUI (iOS 17+)，MVVM 架构
- **后端**：FastAPI REST API (`http://localhost:8000`)
- **认证**：JWT Bearer Token，access 30min + refresh 7 天
- **网络**：URLSession async/await，自动 401 刷新重试

### 五、后端 API 接口

| 路由组 | 说明 |
|---|---|
| `/api/auth/` | 登录、注册、refresh、logout |
| `/api/dashboard/` | 仪表盘汇总 |
| `/api/glucose/` | 血糖数据（时间范围查询） |
| `/api/meals/` | 膳食记录（拍照上传 3 步流程） |
| `/api/chat/` | AI 对话、历史会话 |
| `/api/agent/` | 主动推送、每日简报、餐后补救 |
| `/api/health-data/` | AI 总结、文档上传/列表/详情/删除 |
| `/api/health-reports/` | AI 健康摘要 |
| `/api/settings/` | 用户设置、干预等级、同意书 |

### 六、已知问题（v0.1.0 代码审查）

经过完整代码审查，发现以下待修复项（详见 todolist.md）：

- 🔴 **安全**：Token 存 UserDefaults（应迁移 Keychain）
- 🔴 **错误处理**：5+ 处空 `catch {}` 吞掉错误，UI 无任何失败提示
- 🔴 **性能**：`ChatMessage.id` 计算属性导致无限重渲染
- 🔴 **生产就绪**：baseURL 硬编码 localhost，无环境配置
- 🟠 **架构**：单例硬耦合，无协议/DI，不可测试
- 🟠 **UI**：无 Dark Mode，无 Accessibility 标签
- 🟠 **网络**：无离线支持，无请求取消，URL 参数未编码
