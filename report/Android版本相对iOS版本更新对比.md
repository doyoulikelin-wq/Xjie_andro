# Android 版本相对 iOS 版本更新对比

> 日期：2026-05-08  
> 对比基线：仓库中 `Xjie/Xjie` 的 SwiftUI iOS 应用代码，以及 `function.md` 中记录的 iOS v1.6.0 功能。  
> 对比对象：仓库中 `Android/app/src/main/java/com/xjie/app` 的 Kotlin / Jetpack Compose Android 应用代码。  
> 说明：本文只把代码中已经实现或明确接入的能力列为 Android 更新；仅存在于 demo、报告或后端但 Android UI 未调用的能力，会单独标注为“未完全落地”。

## 1. 总体结论

Android 版本不是简单把 iOS UI 复制一遍，而是完成了一次“Android 原生化 + 部分功能重组 + 医疗档案能力增强”的移植。整体上，Android 版已经覆盖 iOS 主链路：登录、首页、血糖、膳食、健康数据、AI 对话、多组学、心情、设置、管理后台、离线提示和 Token 自动刷新。

相对 iOS 版本，Android 版本最明显的更新有三类：

1. **新增“病史整理”完整页面**：从 iOS 的病例/体检上传与 AI 摘要，升级为医生摘要、资料证据、关键异常值、缺失项和 8 类结构化病史字段。
2. **Android 原生工程体系更系统**：使用 Compose、Hilt、Retrofit、OkHttp Authenticator、StateFlow、EncryptedSharedPreferences、DataStore、Vico Charts，代码组织更接近现代 Android 工程。
3. **面向国内 Android 生态预留推送能力**：抽象了 HMS、荣耀、小米、OPPO、VIVO、魅族等厂商 push provider，并支持向后端登记 provider 字段；但当前 provider 仍是 stub，真正厂商 SDK 尚未接入。

同时，Android 版本也有一些还没有追平 iOS 的点：iPad/大屏适配没有 iOS 的 NavigationSplitView 级别；多组学真实文件上传 UI 和组学文献证据展示弱于 iOS；管理后台没有技能管理 UI；Android 客户端测试尚未落地。

## 2. iOS 版本基线

iOS 版本已经是一个功能较完整的 SwiftUI 应用，主要能力如下：

| 模块 | iOS 代码位置 | 基线能力 |
|---|---|---|
| 主导航 | `Xjie/Xjie/Views/Home/MainTabView.swift` | iPhone 使用 TabView，iPad 使用 NavigationSplitView 侧边栏；全局离线横幅 |
| 首页 | `Xjie/Xjie/Views/Home/HomeView.swift`, `Xjie/Xjie/ViewModels/HomeViewModel.swift` | 仪表盘 KPI、离线缓存、快捷入口 |
| 登录认证 | `Xjie/Xjie/Services/AuthManager.swift`, `Xjie/Xjie/Utils/KeychainHelper.swift` | JWT 登录、Keychain 存 token、登录态管理 |
| 网络层 | `Xjie/Xjie/Services/APIService.swift` | URLSession actor、Bearer token、401 自动 refresh、指数退避重试、上传超时 |
| 血糖 | `Xjie/Xjie/Views/Glucose/GlucoseView.swift`, `Xjie/Xjie/ViewModels/GlucoseViewModel.swift` | CGM 曲线、统计、时间范围切换、异常值展示 |
| 膳食 | `Xjie/Xjie/Views/Meals/MealsView.swift`, `Xjie/Xjie/ViewModels/MealsViewModel.swift` | 拍照识别、手动录入、列表分页 |
| 健康数据 | `Xjie/Xjie/Views/HealthData/HealthDataView.swift` | AI 健康总结、指标趋势、历史病例/体检、文件上传 |
| 病例/体检详情 | `Xjie/Xjie/Views/MedicalRecords/MedicalRecordViews.swift`, `Xjie/Xjie/Views/ExamReports/ExamReportViews.swift` | 文档列表、详情、AI 整理、CSV 表格、原件查看、删除 |
| AI 对话 | `Xjie/Xjie/Views/Chat/ChatView.swift`, `Xjie/Xjie/ViewModels/ChatViewModel.swift` | 多会话、流式输出、详细分析展开、追问、文献引用 |
| 文献引用 | `Xjie/Xjie/Views/Chat/CitationFootnoteView.swift` | 参考文献脚注、证据等级、详情 sheet |
| 多组学 | `Xjie/Xjie/Views/Omics/OmicsView.swift`, `Xjie/Xjie/ViewModels/OmicsViewModel.swift` | 演示模式、真实代谢组上传、组学故事、组学文献证据 |
| 心情 | `Xjie/Xjie/Views/Health/MoodLogView.swift` | 5 时段 emoji 打卡、心情趋势和血糖关联 |
| 推送 | `Xjie/Xjie/Services/PushNotificationManager.swift` | APNs token 注册与后端同步 |
| 管理后台 | `Xjie/Xjie/Views/Admin/AdminView.swift` | 概览、用户、对话、组学、Token、开关、技能 7 个 Tab |

## 3. Android 当前实现概览

Android 版本采用 Kotlin + Compose 重建，目录结构更接近 Android 分层：`feature/*` 放业务页面，`core/network` 放 Retrofit/OkHttp，`core/model` 放序列化模型，`core/storage` 放 token 与离线缓存，`navigation` 放路由。

| 模块 | Android 代码位置 | 当前能力 |
|---|---|---|
| 顶层导航 | `Android/app/src/main/java/com/xjie/app/navigation/AppNavGraph.kt` | 根据登录态切换 Login/Main，已登录进入 MainScaffold |
| 主框架 | `Android/app/src/main/java/com/xjie/app/navigation/MainScaffold.kt` | 4 Tab 底部导航：首页、健康数据、多组学、助手小捷；含离线横幅 |
| 登录 | `Android/app/src/main/java/com/xjie/app/feature/login/*` | 手机号/账号登录、受试者选择、登录态写入 |
| 网络 | `Android/app/src/main/java/com/xjie/app/core/network/*` | Retrofit + OkHttp，Default/Upload/LLM/Refresh 四类 client，401 自动 refresh |
| 安全存储 | `Android/app/src/main/java/com/xjie/app/core/storage/TokenStore.kt` | EncryptedSharedPreferences 保存 access token、refresh token、subject id |
| 首页 | `Android/app/src/main/java/com/xjie/app/feature/home/*` | 主动提醒、健康概览、快捷入口、干预建议 |
| 血糖 | `Android/app/src/main/java/com/xjie/app/feature/glucose/*` | 血糖曲线、统计、图表交互 |
| 膳食 | `Android/app/src/main/java/com/xjie/app/feature/meals/*` | 餐食列表、拍照/上传入口、缓存管理 |
| 健康数据 | `Android/app/src/main/java/com/xjie/app/feature/healthdata/*` | AI 健康总结、指标趋势、病例/体检列表、上传、聚焦跳转 |
| 病史整理 | `Android/app/src/main/java/com/xjie/app/feature/patienthistory/*` | 医生摘要、证据概览、关键异常值、缺失任务、结构化病史编辑 |
| AI 对话 | `Android/app/src/main/java/com/xjie/app/feature/chat/*` | 多会话、历史、详细分析、追问、文献引用、病史整理入口 |
| 多组学 | `Android/app/src/main/java/com/xjie/app/feature/omics/*` | 蛋白组、代谢组、基因组、微生态、三系统联动演示数据展示 |
| 心情 | `Android/app/src/main/java/com/xjie/app/feature/mood/*` | 5 时段心情打卡、趋势和关联分析 |
| 推送 | `Android/app/src/main/java/com/xjie/app/core/push/*` | 多厂商 push provider 抽象，后端 token 注册；provider 尚为 stub |
| 管理后台 | `Android/app/src/main/java/com/xjie/app/feature/admin/*` | 概览、用户、会话、组学、Token、特性开关 6 个 Tab |

## 4. Android 相对 iOS 的新增功能

### 4.1 病史整理：从“文档管理”升级为“医生可读档案”

这是 Android 版本最实质的新增。iOS 版本已经有历史病例、体检上传、AI 摘要和原件查看，但没有一个独立的“给医生看的结构化病史整理”页面。Android 新增 `PatientHistoryScreen.kt`，并从聊天页直接引导进入。

Android 病史整理包含：

| 能力 | Android 实现 |
|---|---|
| 医生摘要 | `doctor_summary` 可编辑，保存后用于医生阅读 |
| 资料证据概览 | 显示历史病例数量、历史体检数量、最新日期、完整度 |
| 关键异常数值 | `PatientHistoryMetric` 展示异常指标、数值、单位、时间、来源，支持跳转健康数据核对 |
| 缺失任务 | 自动列出缺失字段，引导补病例、补体检、上传资料 |
| 结构化字段 | 既往明确诊断、手术或住院史、长期/当前用药、过敏或不良反应、近一年重要异常检查、本次就诊重点关注、家族史、生活方式风险因素 |
| 状态标签 | 已确认、待核对、明确无、未填写、有资料 |
| 来源标签 | 患者填写、资料提取、两者结合、系统汇总 |
| 患者核对 | 每个字段有“已由患者核对”开关 |

支撑后端接口位于 `backend/app/routers/health_data.py` 的 `/api/health-data/patient-history`，后端服务 `backend/app/services/patient_history_service.py` 负责默认字段、完整度、缺失项、证据概览和关键指标整理。

结论：**Android 版在“医疗沟通材料”方向比 iOS 版明显前进了一步。**

### 4.2 Chat 首页新增“病史整理”入口

Android 的 `ChatScreen.kt` 在欢迎态下新增“病史整理”卡片，文案明确说明：把过往诊断、用药、过敏和关键异常检查整理成给医生看的摘要。这个入口把 AI 对话从“问答”引向“结构化档案整理”，是体验上的重要更新。

iOS 的 ChatView 有文献引用、追问、分析展开，但没有同等的病史整理入口。

### 4.3 健康数据支持聚焦跳转

Android 新增 `Route.HealthDataFocus`，病史整理页点击“核对”或“去补资料”时，可以跳转到健康数据页并高亮 records、exams、upload 或 indicator 区域。

iOS 健康数据页已经有病例/体检/上传入口，但没有这种跨页面带 focus 参数的核对闭环。

### 4.4 国内 Android 厂商推送抽象

iOS 使用 APNs；Android 新增 `PushDispatcher.kt` 和 `PushProvider.kt`，按 `Build.MANUFACTURER` 选择 hms、honor、mipush、oppo、vivo、meizu，并向后端登记 `platform=android`、`provider` 和设备信息。

注意：当前 `HmsPushProvider`、`MiPushProvider`、`OppoPushProvider` 等都继承 `StubPushProvider`，`isAvailable()` 返回 false，说明这是**推送架构预留**，不是完整厂商 SDK 接入。它相对 iOS 的更新点是“抽象和后端字段已准备好”，不是“已可真实收厂商推送”。

### 4.5 Android 原生安全存储

iOS 使用 Keychain；Android 使用 `EncryptedSharedPreferences` + `MasterKey` 保存 access token、refresh token、subject id。这个不是业务新增，但属于 Android 版补齐平台安全能力。

## 5. Android 相对 iOS 的增强与重构

### 5.1 工程架构更 Android 原生

iOS 版本主要是 SwiftUI + ObservableObject + actor APIService + 手动单例。Android 版本重构为：

| 层 | Android 技术 |
|---|---|
| UI | Jetpack Compose + Material3 |
| 导航 | Navigation Compose |
| DI | Hilt |
| 网络 | Retrofit + OkHttp |
| JSON | kotlinx.serialization |
| 状态 | ViewModel + StateFlow |
| 安全存储 | EncryptedSharedPreferences |
| 偏好存储 | DataStore |
| 图表 | Vico |
| 图片 | Coil |

这使 Android 版在依赖注入、网络 client 分类、测试可替换性方面比 iOS 手动单例更规整。

### 5.2 网络层分出四类 OkHttpClient

Android 的 `NetworkModule.kt` 明确区分：

| Client | 用途 |
|---|---|
| Default | 普通 API，挂鉴权、重试、Authenticator |
| Upload | 文件上传，读写超时 60 秒 |
| Llm | AI 长请求，读写超时 90 秒 |
| Refresh | token refresh 专用，不挂鉴权拦截器，避免循环 |

iOS 的 `APIService.swift` 也有超时、重试、401 refresh 和 upload，但 Android 通过 Hilt qualifier 分 client，更贴近大型 Android 项目的组织方式。

### 5.3 Token 刷新并发控制对齐 iOS，但实现方式更 Android 化

iOS 用 actor 内的 `refreshTask` 合并并发 401；Android 用 OkHttp `Authenticator` + Kotlin `Mutex`。两端能力基本对齐：多个并发 401 只触发一次 refresh，refresh 失败后 logout。

### 5.4 离线缓存与离线横幅对齐

iOS 有 `OfflineCacheManager.swift` 和 `MainTabView.swift` 离线横幅；Android 对应 `OfflineCache.kt` 和 `OfflineBanner`，首页仓库也使用离线缓存回退。Android 版不是新增，而是把 iOS 的离线体验移植到了 Android 原生缓存目录和 Compose UI。

### 5.5 首页主动提醒更产品化

Android 首页有 `ProactiveMessages.kt` 本地 18 条主动关怀/引导文案，用于轮播式提醒。iOS 的主动干预更多依赖后端 agent 和 APNs 推送。Android 这个变化更像“打开 App 内的主动引导”，增强了日常使用感。

### 5.6 健康总结展示更强调“简明版/详细版”

Android `HealthDataScreen.kt` 会从完整 summary 中抽取“简明版 · 核心摘要”，再支持展开“详细版”。iOS 版本也展示 AI 健康总结，但 Android 在 UI 上更明确地区分摘要和详细分析。

## 6. Android 已追平或基本对齐 iOS 的能力

| 能力 | 对齐情况 |
|---|---|
| 4 个主 Tab | iOS 和 Android 都是首页、健康数据、多组学、助手小捷 |
| 登录与自动刷新 | 两端都有 JWT refresh 和失败登出 |
| 血糖模块 | Android 已有血糖数据、图表和统计展示 |
| 膳食模块 | Android 已有餐食列表、拍照/上传入口和 repository/API 层 |
| 健康数据 | Android 已有 AI 总结、指标趋势、病例/体检、上传入口 |
| AI 对话 | Android 已有多会话、历史、详细分析展开、追问、文献引用 |
| 文献引用详情 | Android `CitationSection` + `CitationDetailDialog` 已实现证据等级、期刊、年份、样本量、可信度 |
| 心情打卡 | Android 已有 5 时段心情打卡和关联分析 |
| 多组学演示 | Android 已有蛋白组、代谢组、基因组、微生态、三系统联动 demo 展示 |
| 管理后台核心数据 | Android 已有总览、用户、会话、组学、Token、特性开关 |

## 7. Android 尚未追平或弱于 iOS 的地方

### 7.1 大屏适配弱于 iOS

iOS 在 `MainTabView.swift` 中按 horizontal size class 切换：iPhone 使用 TabView，iPad 使用 NavigationSplitView 侧边栏。Android 目前看到的是统一底部导航，没有针对 tablet / foldable 的 NavigationRail、NavigationDrawer 或双栏布局。

### 7.2 多组学真实上传 UI 弱于 iOS

iOS `OmicsView.swift` 有“上传真实代谢组数据”卡片和 `MetabolomicsFilePicker`，`OmicsViewModel.swift` 也有上传并分析的逻辑。Android 的 `OmicsApi.kt` 虽然声明了 `uploadMetabolomics`，但当前 `OmicsScreen.kt`/`OmicsViewModel.kt` 主要只调用 demo 数据接口，没有看到文件选择和上传 UI。

结论：Android 多组学展示能力基本对齐，但真实上传分析流程还没有追平 iOS。

### 7.3 组学文献证据展示弱于 iOS

iOS 多组学页面中 `StoryCitations` 会调用 `/api/literature/retrieve`，为代谢物/蛋白/菌群故事展示文献证据。Android 有 `LiteratureApi.kt`，聊天页也有引用详情，但 Android 多组学页面未看到对 `LiteratureApi` 的调用。

结论：Android 的聊天文献引用已对齐，组学页面文献引用尚未追平 iOS。

### 7.4 管理后台缺少技能管理 UI

iOS `AdminView.swift` 有 7 个 Tab，包含“技能”管理，可新增/编辑技能。Android `AdminViewModel.kt` 当前只有总览、用户、会话、组学、Token、特性开关 6 个 Tab。

需要注意：Android 的 `AdminApi.kt` 已经声明了 skills CRUD，`FeatureFlagModels.kt` 也有 `AdminSkill` 相关模型，但 `AdminScreen.kt` 还没有技能管理界面。也就是说：**API 层已准备，UI 未完成。**

### 7.5 推送 provider 还只是抽象层

Android 的厂商推送设计比 iOS 更适合国内生态，但当前 provider 都是 stub；如果要称为“已支持华为/小米/OPPO/VIVO/魅族推送”，还需要接入各厂商 SDK，并让 `isAvailable()`、`register()`、`unregister()` 真正工作。

### 7.6 客户端测试仍未补齐

Android `build.gradle.kts` 已配置 JUnit、MockK、Turbine、coroutines-test、MockWebServer、AndroidX test、Espresso，但仓库中未看到 `src/test` 或 `src/androidTest` 的实际测试文件。iOS 侧已有 Chat、Glucose、Login、Home、Utils 等单元测试，Android 测试覆盖需要补齐。

## 8. 后端为 Android 更新提供的关键支撑

Android 的新增并非只在客户端，后端也已经有对应接口或模型支撑：

| Android 更新 | 后端支撑 |
|---|---|
| 病史整理 | `backend/app/routers/health_data.py` 的 `GET/PUT /patient-history`，`backend/app/services/patient_history_service.py` |
| 病史写入 AI 上下文 | `backend/app/services/context_builder.py` 会提取 patient history 上下文 |
| 推送 provider 字段 | `backend/app/routers/push.py`、device token provider/extras 字段 |
| 文献引用 | `backend/app/routers/literature.py`、`backend/app/services/literature/retrieval.py` |
| 多组学 demo | `backend/app/routers/omics.py`、`backend/app/services/omics_demo.py` |
| 心情打卡 | `backend/app/routers/mood.py` |
| 管理后台 | `backend/app/routers/admin.py` |
| 功能开关/技能系统 | `backend/app/services/feature_service.py`、admin skills/feature-flags API |

这说明 Android 版的很多更新不是孤立 UI，而是围绕后端已有能力做了移动端重新组织。

## 9. 按产品价值排序的更新清单

| 排名 | 更新 | 价值判断 |
|---|---|---|
| 1 | 病史整理 | 最大新增，从健康数据上传走向医生可读档案，是产品差异化核心 |
| 2 | Chat 到病史整理入口 | 把 AI 对话和结构化档案连接起来，形成更强闭环 |
| 3 | 健康数据 focus 跳转 | 让“缺失项/异常值核对”可操作，不只是提示 |
| 4 | Android 原生网络/DI 架构 | 提升稳定性、可维护性和后续测试可能性 |
| 5 | 文献引用在 Android Chat 对齐 | 保留医学可信度表达，不让 Android 降级成普通聊天 |
| 6 | 厂商推送抽象 | 为国内 Android 生态准备，但 SDK 接入还未完成 |
| 7 | 首页主动提醒 | 增强打开 App 后的陪伴感和引导感 |
| 8 | 多组学 demo 对齐 | 展示力强，但上传和文献证据仍需补齐 |

## 10. 建议下一步

### P0：补齐 Android 版的关键未完成项

1. 为多组学页补文件选择、上传、上传进度和分析结果展示，对齐 iOS `MetabolomicsFilePicker`。
2. 为多组学故事补文献证据区块，复用 Android 已有 `LiteratureApi` 和聊天页 `CitationDetailDialog`。
3. 在 Android 管理后台补“技能管理”Tab，API 和模型已经存在，主要缺 UI 与 ViewModel 状态。
4. 明确厂商 push 当前状态：如果暂未接 SDK，UI/文档中应标注为“待接入”，避免误判为已可推送。

### P1：把新增的病史整理变成核心闭环

1. 在健康数据页增加“病史整理”显性入口，而不仅从 Chat 欢迎态进入。
2. 将病史整理的关键字段来源与健康文档详情更紧密地互相跳转。
3. 增加“医生摘要导出/分享”能力，形成就诊前材料包。
4. 为 `PatientHistoryViewModel` 和 `/patient-history` API 增加测试。

### P2：提升 Android 发布质量

1. 增加 Android 单元测试：TokenAuthenticator、HealthDataViewModel、PatientHistoryViewModel、ChatViewModel。
2. 增加 Compose UI smoke test：登录、首页、病史整理、聊天。
3. 针对大屏增加 NavigationRail/双栏布局，追平 iOS iPad 体验。
4. 完成 release API base URL、签名、崩溃采集和 R8 mapping 上传链路。

## 11. 一句话总结

Android 版本整体上已经追平 iOS 的主功能，并在“病史整理 + 医生可读摘要 + 健康数据核对闭环”上实现了明显更新；但多组学上传/组学文献证据、管理后台技能 UI、大屏适配和真实厂商推送 SDK 仍需继续补齐。Android 版当前最值得强调的不是“换了一个平台”，而是“把 Xjie 从健康数据 App 往医疗沟通和精准健康档案方向推进了一步”。