# Xjie 项目功能与架构总结

> 项目名称：Xjie（小杰）—— AI 驱动的代谢健康管理平台  
> 更新时间：2026年3月31日  
> 版本状态：生产环境运行中

---

## 一、项目概述

Xjie 是一款面向代谢健康管理的全栈 AI 平台，核心场景为**连续血糖监测（CGM）数据管理 + AI 健康助手**，覆盖 iOS 原生客户端、微信小程序和云端后端三端。

### 1.1 产品定位

| 维度 | 描述 |
|------|------|
| **目标用户** | 代谢健康关注者、CGM 佩戴用户、脂肪肝临床研究受试者 |
| **核心价值** | 血糖趋势可视化 + AI 膳食分析 + 主动健康干预 + 多组学数据整合 |
| **AI 角色** | "助手小捷" —— 温暖专业的护理型 AI 助手 |
| **品牌色系** | 青绿 #00C9A7 → 深蓝 #1565C0 渐变 |

### 1.2 系统架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                       客户端层                                │
│  ┌─────────────────────┐    ┌──────────────────────────────┐ │
│  │   iOS App (SwiftUI) │    │  微信小程序 (WXML/WXSS/JS)    │ │
│  │   iOS 17+ / iPad    │    │  wx24a461ae309ac297          │ │
│  └─────────┬───────────┘    └──────────────┬───────────────┘ │
└────────────┼───────────────────────────────┼─────────────────┘
             │ HTTPS                         │ HTTPS
             ▼                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     后端服务层                                 │
│  ┌────────────────────────────────────────────────────────┐  │
│  │            FastAPI (Python 3.11+)                      │  │
│  │  ┌──────────┬──────────┬──────────┬────────────────┐   │  │
│  │  │ Auth/JWT │ Routers  │ Services │ LLM Providers  │   │  │
│  │  │ bcrypt   │ 12个路由  │ 业务逻辑  │ Kimi K2.5     │   │  │
│  │  └──────────┴──────────┴──────────┴────────────────┘   │  │
│  │  ┌──────────┬──────────┬──────────┐                    │  │
│  │  │ Celery   │ ETL      │ Agent    │                    │  │
│  │  │ 异步任务  │ 数据管道  │ 规则引擎  │                    │  │
│  │  └──────────┴──────────┴──────────┘                    │  │
│  └────────────────────────┬───────────────────────────────┘  │
└───────────────────────────┼──────────────────────────────────┘
                            │
┌───────────────────────────┼──────────────────────────────────┐
│                     数据层                                    │
│  ┌─────────────────────┐  │  ┌─────────────────────────────┐ │
│  │   TimescaleDB        │◄─┘  │  Redis                     │ │
│  │   PostgreSQL 扩展    │      │  Token 黑名单 / Celery     │ │
│  │   Port: 35432       │      │  Broker                    │ │
│  └─────────────────────┘      └─────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 部署环境

| 组件 | 技术 | 位置 |
|------|------|------|
| **服务器** | 阿里云 ECS | 8.130.213.44 |
| **后端容器** | Docker | xjie-backend → xjie-api |
| **数据库** | TimescaleDB (PostgreSQL) | Docker 容器, port 35432 |
| **HTTPS** | Nginx + mkcert 自签证书 | 服务器端 |
| **代码仓库** | GitHub | doyoulikelin-wq/XJie_IOS |
| **CI/CD** | Git Pull → Docker Build → 滚动部署 | SSH 自动化 |

---

## 二、功能模块详解

### 2.1 认证与用户管理

| 功能 | 描述 | API |
|------|------|-----|
| **受试者登录** | 选择研究队列受试者 ID 一键登录，首次自动创建用户 + 导入数据 | `POST /api/auth/login-subject` |
| **手机号注册/登录** | 手机号 + 密码，bcrypt 加密存储 | `POST /api/auth/signup`, `login` |
| **微信登录** | 小程序端 `wx.login()` 获取 code → 后端 jscode2session | `POST /api/auth/wx-login` |
| **JWT Token** | Access Token (30分钟) + Refresh Token (7天)，自动刷新 | `POST /api/auth/refresh` |
| **Token 黑名单** | Redis 内存级黑名单，支持即时注销 | 中间件拦截 |
| **登录限速** | 10次/分钟速率限制，防暴力破解 | 中间件 |

**用户设置：**
- 干预级别：L1（温和）/ L2（标准）/ L3（积极）
- AI 聊天授权开关
- 数据上传授权开关
- 每日提醒上限
- 自动升级策略

### 2.2 血糖监测模块

**数据来源：**

| 来源 | 方式 | 说明 |
|------|------|------|
| **Dexcom Clarity CSV** | 手动上传 / 受试者自动导入 | 解析 EGV 行，支持 mmol/L → mg/dL 自动转换 |
| **CGM 设备 API** | Webhook 实时推送 | HMAC-SHA256 签名验证，设备绑定关联用户 |
| **手动导入** | 上传 CSV 文件 | 通用格式支持 |

**核心指标：**

| 指标 | 计算方式 | 展示 |
|------|---------|------|
| **平均血糖** | 时间窗口内算术平均 | 数值 + 单位 |
| **TIR（目标范围时间）** | 70-180 mg/dL 范围内占比 | 百分比 |
| **血糖变异性** | 变异系数 CV，分级 low/medium/high | 标签 |
| **数据间隙** | 连续读数间隔 > 阈值的累计时长 | 小时 |
| **最高/最低值** | 窗口内极值 | 数值 |

**可视化：**
- iOS：SwiftUI Canvas 2D 自绘曲线图
- 小程序：wx.createCanvasContext 绘制
- 时间窗口：24h / 7d / 全部
- 目标范围绿色背景带 (70-180)
- 参考线：70, 140, 180 mg/dL

### 2.3 AI 智能对话（助手小捷）

**技术架构：**

```
用户消息
    │
    ▼
┌──────────────┐
│  安全检测      │ ← 检测紧急症状关键词（胸痛、昏厥、呼吸困难等）
│  safety_service│   命中 → 返回紧急就医模板
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  上下文构建    │ ← context_builder 聚合：
│  Context Build │   血糖摘要 + 近期膳食 + 症状 + 用户画像 + 
│               │   特征快照 + 健康报告 + 组学 + 近期对话
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  健康检测      │ ← ~70个中文医学关键词正则匹配
│  Query Class. │   健康类 → 启用思考模式
│               │   非健康类 → 禁用思考模式
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Kimi K2.5   │ ← temperature=1.0, max_tokens=16000
│  LLM 调用    │   非流式（同步）/ 流式（SSE）
│               │   JSON 结构化输出
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  结果后处理    │ ← 提取用户画像、记录 Token 消耗、
│  Post Process │   存储对话历史、记录审计日志
└──────────────┘
```

**AI 能力特性：**

| 特性 | 实现方式 |
|------|---------|
| **数据感知对话** | 自动注入最新血糖/膳食/健康数据作为上下文 |
| **思考模式** | Kimi K2.5 thinking 能力，健康类问题自动启用 |
| **安全防护** | 紧急症状自动拦截，返回就医指引 |
| **对话记忆** | 多轮对话持久化，支持历史对话回溯 |
| **结构化输出** | JSON 格式：摘要 + 分析 + 回答 + 置信度 + 追问建议 |
| **流式传输** | SSE (Server-Sent Events) 实时推送响应 |
| **Token 审计** | 每次 LLM 调用记录 prompt/completion tokens，按功能分类 |

**对话功能：**
- 同步对话 `POST /api/chat`
- 流式对话 `POST /api/chat/stream`（SSE）
- 会话列表 `GET /api/chat/conversations`
- 会话详情 `GET /api/chat/conversations/{id}`
- 推荐追问问题（前端展示可点击）

### 2.4 膳食管理模块

**膳食记录流程：**

```
拍照/相册选择                    手动记录
      │                            │
      ▼                            ▼
获取上传凭证                  输入热量/标签/备注
POST /meals/photo/upload-url        │
      │                            │
      ▼                            │
上传图片到服务器                    │
PUT 上传 URL                       │
      │                            │
      ▼                            │
通知上传完成                       │
POST /meals/photo/complete          │
      │                            │
      ▼                            │
┌──────────────┐                   │
│ Kimi Vision  │                   │
│ 图像分析      │                   │
│ 食物识别      │                   │
│ 热量估算      │                   │
│ 置信度评估    │                   │
└──────┬───────┘                   │
       │                           │
       ▼                           ▼
┌──────────────┐          ┌──────────────┐
│ 血糖推断用餐  │          │  创建膳食记录  │
│ 时间          │          │  POST /meals  │
│ (glucose slope│          └──────────────┘
│  scoring)    │
└──────┬───────┘
       │
       ▼
  创建膳食记录
  (附带照片分析结果)
```

**Vision AI 输出：**
- 食物品类列表（名称 + 重量 + 热量）
- 总热量估算 (kcal)
- 置信度评分
- 营养备注

### 2.5 主动式 Agent 引擎

**设计理念：Observe → Understand → Plan → Act**

Agent 是一个纯规则引擎（无 LLM 调用），基于 FeatureSnapshot 和 GlucoseReading 做实时数学计算和决策。

| 端点 | 功能 | 触发条件 |
|------|------|---------|
| `GET /api/agent/today` | **每日代谢天气** | 用户打开健康页 |
| `GET /api/agent/weekly` | **每周回顾** | 用户查看周报 |
| `POST /api/agent/premeal-sim` | **餐前模拟** | 用户输入预计热量 |
| `GET /api/agent/rescue` | **餐后补救** | 系统检测餐后血糖飙升 |
| `GET /api/agent/proactive` | **主动消息** | 首页气泡展示 |

**每日简报内容：**
- 当前血糖状态（数值 + 趋势 + 标签）
- 24h TIR 百分比
- 风险时间窗（高风险/中风险时段）
- 今日目标建议
- 待处理补救项

**干预策略分级：**

| 级别 | 名称 | 触发阈值 | 每日提醒上限 | 特点 |
|------|------|---------|------------|------|
| **L1** | 温和 | 仅高风险 | 1次/天 | 最低干扰 |
| **L2** | 标准 (默认) | 中风险+ | 2次/天 | 平衡模式，2天连续异常自动升级 |
| **L3** | 积极 | 中风险+ | 4次/天 | 最高频率，每餐建议 1-2 条 |

### 2.6 健康数据管理

**支持的文档类型：**

| 类别 | 来源格式 | AI 处理 |
|------|---------|---------|
| **病例记录** | 拍照/CSV/PDF | LLM 提取结构化 CSV 表格 |
| **体检报告** | 拍照/CSV/PDF | LLM 提取 + 异常指标标注 |
| **脂肪肝研究数据** | XLS (初始/结束阶段) | 自动解析对比 |

**AI 健康总结：**
- 聚合所有已上传文档
- Kimi K2.5 生成综合健康摘要
- 流式 SSE 输出
- Token 消耗审计（feature: health_summary）

**异常指标检测：**
- 自动标注异常值（↑↓符号 / "异常"文字）
- 异常指标卡片高亮展示
- 参考范围对比

### 2.7 多组学数据模块

| 组学类型 | 状态 | 功能 |
|---------|------|------|
| **代谢组学** | ✅ 已实现 | CSV/XLSX/PDF 上传 → LLM 分析 → 风险等级 + 代谢物列表 |
| **蛋白组学** | 🔲 UI 占位 | CRP、TNF-α、IL-6、Adiponectin |
| **基因组学** | 🔲 UI 占位 | TCF7L2、FTO、APOE、MTHFR |

**代谢组学分析输出：**
- 综合摘要
- 详细分析报告
- 风险等级（低/中/高）
- 代谢物列表（名称、数值、单位、状态）

### 2.8 CGM 设备集成

```
CGM 设备
    │
    │ 实时数据
    ▼
┌──────────────────┐
│  Webhook 端点     │ POST /api/integrations/cgm/ingest
│  HMAC-SHA256 验签 │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  数据解析与映射   │ deviceTime → UTC
│  设备绑定关联     │ phone/SN/ID → user_id
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  批量写入         │ GlucoseReading (source: cgm_device_api)
│  TimescaleDB     │ 去重 + 错误追踪
└──────────────────┘
```

**设备绑定管理：**
- 按 provider 分类
- 支持 phone / device_sn / device_id 三种关联方式
- 唯一约束确保不重复绑定

### 2.9 管理后台

| 功能模块 | 数据 |
|---------|------|
| **系统概览** | 总用户数、7日活跃、总对话/消息/组学/膳食数 |
| **用户管理** | 用户列表、对话/消息统计、管理员权限切换 |
| **对话管理** | 全部对话列表、消息数、时间排序 |
| **组学管理** | 全部上传记录、风险等级、LLM 摘要 |
| **Token 统计** | 按功能分类的 Token 消耗：chat / meal_vision / health_summary / metabolomics |

### 2.10 ETL 数据管道

```
原始数据                    特征计算
    │                          │
    ▼                          │
┌──────────────┐              │
│ glucose_etl  │              │
│ Clarity CSV  │──────────┐   │
│ 解析导入      │          │   │
└──────────────┘          │   │
                          ▼   │
┌──────────────┐    ┌──────────────┐
│ meal_etl     │───▶│ feature_     │
│ 膳食数据导入  │    │ compute      │
│ 校正关联      │    │ TIR/CV/      │
└──────────────┘    │ kcal_slope   │
                    │ 24h/7d/28d   │
                    └──────┬───────┘
                           │
                           ▼
                    FeatureSnapshot
                    (JSONB 存储)
```

**支持的特征窗口：** 24h / 7d / 28d

**计算特征：**
- 血糖均值、最大/最小值
- TIR 百分比 (70-180)
- 变异系数 CV
- 热量斜率 (kcal slope)
- 数据间隙统计

---

## 三、技术栈详解

### 3.1 后端技术栈

| 层级 | 技术 | 版本/说明 |
|------|------|---------|
| **Web 框架** | FastAPI | 异步 ASGI，自动 OpenAPI 文档 |
| **ORM** | SQLAlchemy 2.0 | 声明式模型，JSONB/Array 类型支持 |
| **数据校验** | Pydantic v2 | Request/Response Schema |
| **数据库** | TimescaleDB | PostgreSQL 扩展，时序数据优化 |
| **缓存/队列** | Redis | Token 黑名单 + Celery Broker |
| **异步任务** | Celery | 照片处理异步化 |
| **数据库迁移** | Alembic | 版本化 Schema 迁移 |
| **认证** | JWT + bcrypt | Access/Refresh Token 双令牌 |
| **LLM** | Kimi K2.5 (月之暗面) | 对话 + Vision + 思考模式 |
| **容器化** | Docker | 一键构建部署 |
| **日志** | 结构化 JSON | 生产环境 JSON 格式，开发环境可读格式 |

**核心依赖：**
- `fastapi`, `uvicorn` — Web 服务
- `sqlalchemy[asyncio]` — 数据库 ORM
- `pydantic-settings` — 配置管理
- `bcrypt` — 密码哈希
- `python-jose[cryptography]` — JWT
- `openai` — LLM API 客户端
- `celery[redis]` — 异步任务
- `openpyxl`, `pandas` — 数据处理
- `httpx` — 异步 HTTP 客户端

### 3.2 iOS 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **UI 框架** | SwiftUI | 声明式 UI，iOS 17+ |
| **架构模式** | MVVM | View ↔ ViewModel ↔ APIService |
| **网络层** | URLSession (Actor) | 并发安全，自动重试 + Token 刷新 |
| **安全存储** | Keychain | Token 加密存储 (SEC-01) |
| **图片缓存** | 双层缓存 | 内存 NSCache + 磁盘文件 (PERF-05) |
| **离线支持** | OfflineCacheManager | JSON 文件本地缓存 (NET-03) |
| **网络监控** | NWPathMonitor | WiFi/蜂窝/有线检测 (NET-01) |
| **图表** | Canvas 2D | 原生绘制血糖曲线 |
| **设备适配** | iPhone + iPad | TabView + NavigationSplitView |

**架构分层：**

```
┌─────────────────────┐
│    Views (SwiftUI)   │ 纯展示层，@StateObject/@ObservedObject 绑定
├─────────────────────┤
│   ViewModels         │ @MainActor，状态管理 + 业务逻辑
├─────────────────────┤
│   APIService (Actor) │ RESTful 客户端，JWT 自动注入
├─────────────────────┤
│   AuthManager        │ @MainActor 单例，Keychain 存取
├─────────────────────┤
│   NetworkMonitor     │ NWPathMonitor，连接状态监听
│   OfflineCacheManager│ 离线缓存管理
│   ImageCacheManager  │ 双层图片缓存
└─────────────────────┘
```

### 3.3 微信小程序技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **框架** | 微信原生 MINA | WXML + WXSS + JS |
| **App ID** | wx24a461ae309ac297 | — |
| **网络** | wx.request | 封装 api.js，自动 Bearer Token |
| **存储** | wx.Storage | Token 持久化 |
| **图表** | Canvas 2D | 血糖曲线自绘 |
| **认证** | wx.login() | 微信 OAuth → 后端 jscode2session |

---

## 四、数据模型总览

### 4.1 核心实体关系

```
User (user_account)
 ├── UserProfile (1:1)
 ├── Consent (1:1)
 ├── UserSettings (1:1)
 ├── GlucoseReading (1:N) ← 时序数据，索引 (user_id, ts)
 ├── Meal (1:N)
 │    └── MealPhoto (1:1 可选)
 ├── Conversation (1:N)
 │    └── ChatMessage (1:N)
 ├── HealthDocument (1:N)
 │    ├── 病例 (doc_type=record)
 │    └── 体检 (doc_type=exam)
 ├── HealthSummary (1:1)
 ├── OmicsUpload (1:N)
 │    └── OmicsModelTask (1:N)
 ├── CGMDeviceBinding (1:N)
 ├── AgentState (1:1)
 ├── AgentAction (1:N)
 │    └── OutcomeFeedback (1:1)
 ├── FeatureSnapshot (1:N) ← 24h/7d/28d 窗口
 ├── ActivityLog (1:N) ← 行为审计
 ├── LLMAuditLog (1:N) ← Token 消耗审计
 └── Symptom (1:N)
```

### 4.2 完整数据模型列表

| 模型 | 数据库表 | 关键字段 | 用途 |
|------|---------|---------|------|
| **User** | `user_account` | phone, username, password(hashed), is_admin | 用户账号 |
| **UserProfile** | — | subject_id, display_name, sex, age, height, weight, liver_risk, cohort | 用户画像 |
| **Consent** | — | allow_ai_chat, allow_data_upload, version | 隐私授权 |
| **UserSettings** | — | intervention_level(L1/2/3), daily_reminder_limit, allow_auto_escalation | 干预配置 |
| **GlucoseReading** | — | ts, glucose_mgdl, source, meta(JSONB) | 血糖时序 |
| **Meal** | — | meal_ts, meal_ts_source, kcal, tags[], notes, photo_id | 膳食记录 |
| **MealPhoto** | — | image_object_key, exif_ts, status, vision_json(JSONB), calorie_estimate | 膳食照片 |
| **Conversation** | — | title, message_count | 对话元数据 |
| **ChatMessage** | — | seq, role, content, analysis, meta(JSONB) | 消息内容 |
| **HealthDocument** | — | doc_type, source_type, name, hospital, csv_data(JSONB), abnormal_flags(JSONB) | 健康文档 |
| **HealthSummary** | — | summary_text | AI 健康摘要 |
| **OmicsUpload** | — | omics_type, file_name, llm_summary, llm_analysis, risk_level, meta(JSONB) | 组学数据 |
| **OmicsModelTask** | — | model_type, status, parameters(JSONB), result(JSONB) | 分析任务 |
| **CGMDeviceBinding** | — | provider, phone, device_sn, device_id, is_active | 设备绑定 |
| **AgentState** | — | current_goal(JSONB), risk_windows_today(JSONB), active_plan(JSONB) | Agent 状态 |
| **AgentAction** | — | action_type, priority, payload(JSONB), reason_evidence(JSONB) | 干预记录 |
| **OutcomeFeedback** | — | user_feedback, objective_outcome(JSONB) | 反馈闭环 |
| **FeatureSnapshot** | — | window(24h/7d/28d), features(JSONB) | 特征快照 |
| **ActivityLog** | — | action, detail(JSONB), ip_address, user_agent | 行为审计 |
| **LLMAuditLog** | — | provider, model, feature, latency_ms, prompt_tokens, completion_tokens | Token 审计 |
| **Symptom** | — | ts, severity, text | 症状记录 |

---

## 五、API 端点完整清单

### 5.1 认证路由 `/api/auth/`

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/subjects` | 获取可选受试者列表 |
| POST | `/login-subject` | 受试者 ID 登录 |
| POST | `/signup` | 手机号注册 |
| POST | `/login` | 手机号登录 |
| POST | `/refresh` | 刷新 Token |
| POST | `/wx-login` | 微信登录 |

### 5.2 用户路由 `/api/`

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/me` | 获取当前用户信息 |
| PATCH | `/consent` | 更新隐私授权 |
| GET | `/settings` | 获取干预设置 |
| PATCH | `/settings` | 更新干预设置 |

### 5.3 血糖路由 `/api/glucose/`

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/samples` | 可用样本 CSV 列表 |
| GET | `/meal-samples` | 可用膳食 CSV 列表 |
| POST | `/import-sample` | 导入样本 CSV |
| POST | `/import` | 上传并导入 CSV |
| GET | `/range` | 数据时间范围 |
| GET | `/` | 查询血糖读数（支持降采样） |
| GET | `/summary` | 统计汇总（24h/7d/30d） |

### 5.4 膳食路由 `/api/meals/`

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/photo/upload-url` | 获取上传凭证 |
| PUT | `/photo/mock-upload/{key}` | Mock 上传 |
| POST | `/photo/complete` | 完成上传触发分析 |
| GET | `/photo/{id}` | 照片详情 |
| POST | `/` | 创建膳食记录 |
| PATCH | `/{id}` | 更新膳食记录 |
| GET | `/` | 列表查询 |

### 5.5 对话路由 `/api/chat/`

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/` | 同步对话 |
| POST | `/stream` | 流式对话 (SSE) |
| GET | `/conversations` | 会话列表 |
| GET | `/conversations/{id}` | 会话详情 |

### 5.6 健康数据路由 `/api/health-data/`

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/upload` | 上传文档（LLM 提取） |
| GET | `/documents` | 文档列表 |
| GET | `/documents/{id}` | 文档详情 |
| DELETE | `/documents/{id}` | 删除文档 |
| GET | `/summary` | 获取 AI 总结 |
| POST | `/summary/generate` | 生成 AI 总结 |

### 5.7 健康报告路由 `/api/health-reports/`

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/` | 获取脂肪肝研究检查数据 |
| GET | `/ai-summary` | AI 摘要（SSE 流式） |

### 5.8 Agent 路由 `/api/agent/`

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/today` | 每日代谢天气 |
| GET | `/weekly` | 每周回顾 |
| POST | `/premeal-sim` | 餐前模拟预测 |
| GET | `/rescue` | 餐后补救检查 |
| GET | `/proactive` | 主动消息 |
| GET | `/actions` | Agent 行为列表 |
| POST | `/feedback` | 用户反馈 |

### 5.9 其他路由

| 路由 | 方法 | 路径 | 功能 |
|------|------|------|------|
| **仪表板** | GET | `/api/dashboard/health` | 首页健康概览 |
| **仪表板** | GET | `/api/dashboard/meals` | 近期膳食照片 |
| **ETL** | POST | `/api/etl/run-batch` | 批量 ETL 处理 |
| **ETL** | GET | `/api/etl/features/{id}` | 特征快照查询 |
| **ETL** | POST | `/api/etl/features/recompute` | 重新计算特征 |
| **活动** | GET | `/api/activity` | 行为审计日志 |
| **CGM** | POST | `/api/integrations/cgm/ingest` | CGM Webhook |
| **CGM** | GET/POST/DEL | `/api/integrations/cgm/bindings` | 设备绑定管理 |
| **组学** | POST | `/api/omics/metabolomics/upload` | 代谢组学上传 |
| **组学** | GET | `/api/omics/metabolomics/history` | 上传历史 |
| **管理** | GET | `/api/admin/stats` | 系统统计 |
| **管理** | GET | `/api/admin/users` | 用户列表 |
| **管理** | GET | `/api/admin/conversations` | 对话列表 |
| **管理** | GET | `/api/admin/omics` | 组学列表 |
| **管理** | GET | `/api/admin/token-stats` | Token 消耗统计 |
| **管理** | PATCH | `/api/admin/users/{id}/admin` | 管理员权限 |

---

## 六、安全架构

| 安全措施 | 实现 | 位置 |
|---------|------|------|
| **密码加密** | bcrypt (salt + hash) | security.py |
| **Token 认证** | JWT (HS256), 30min access + 7d refresh | security.py |
| **Token 吊销** | Redis 内存黑名单 | token_blacklist.py |
| **登录限速** | 10次/分钟 per IP | auth.py |
| **CGM 签名验证** | HMAC-SHA256 | cgm_service.py |
| **Keychain 存储** | iOS Keychain Services | KeychainHelper.swift |
| **HTTPS** | Nginx + mkcert 自签证书 | 服务器端 |
| **CORS** | 白名单限制 | config.py |
| **隐私合规** | 用户授权开关 + PrivacyInfo.xcprivacy | Consent 模型 + iOS |
| **请求日志** | 中间件记录 method/path/status/duration + request-ID | middleware.py |
| **LLM 审计** | 每次 LLM 调用记录 Token 消耗和功能分类 | LLMAuditLog |
| **紧急安全** | 症状关键词检测 → 就医指引 | safety_service.py |

---

## 七、项目特色总结

### 7.1 核心差异化优势

| 特色 | 说明 |
|------|------|
| **数据驱动的 AI 对话** | 不是通用聊天机器人，而是基于用户实时血糖、膳食、体检数据的**个性化健康顾问** |
| **主动式 Agent 引擎** | 纯规则引擎实现 Observe→Understand→Plan→Act 决策闭环，零 LLM 成本 |
| **多组学整合** | 代谢组学 + 蛋白组学 + 基因组学多维度健康画像 |
| **三端覆盖** | iOS 原生 + 微信小程序 + 管理后台，统一后端 API |
| **研究与临床兼顾** | 同时支持受试者队列（CGM 队列 / 脂肪肝队列）和普通用户 |
| **Kimi K2.5 思考模式** | 健康类问题自动启用深度思考，提升回答质量 |
| **Token 全链路审计** | chat / meal_vision / health_summary / metabolomics 多维度成本追踪 |
| **干预级别自适应** | L1/L2/L3 三级策略，用户可自选，支持自动升级 |

### 7.2 当前功能完成度

| 模块 | 状态 | 完成度 |
|------|------|--------|
| 认证系统（手机号/受试者/微信） | ✅ 完成 | 100% |
| 血糖监测（导入/可视化/统计） | ✅ 完成 | 100% |
| AI 对话（多轮/流式/上下文感知） | ✅ 完成 | 100% |
| 膳食管理（拍照/Vision/手动） | ✅ 完成 | 100% |
| 主动 Agent（简报/模拟/补救） | ✅ 完成 | 100% |
| 健康数据（上传/提取/AI总结） | ✅ 完成 | 100% |
| 管理后台（统计/用户/Token） | ✅ 完成 | 100% |
| CGM 设备集成（Webhook/绑定） | ✅ 完成 | 100% |
| ETL 管道（特征计算） | ✅ 完成 | 100% |
| 代谢组学分析 | ✅ 完成 | 100% |
| 蛋白组学/基因组学 | 🔲 占位 | 10% |
| 微信小程序 | ✅ 基础完成 | 85% |

---

## 八、文件结构

```
XJie_IOS/
├── backend/                          # 后端服务
│   ├── app/
│   │   ├── main.py                   # FastAPI 应用入口
│   │   ├── core/                     # 核心模块
│   │   │   ├── config.py             #   环境配置
│   │   │   ├── security.py           #   认证加密
│   │   │   ├── deps.py               #   依赖注入
│   │   │   ├── intervention.py       #   干预策略
│   │   │   ├── token_blacklist.py    #   Token 黑名单
│   │   │   ├── middleware.py         #   请求中间件
│   │   │   └── logging.py           #   日志配置
│   │   ├── routers/                  # API 路由 (12个)
│   │   │   ├── auth.py               #   认证
│   │   │   ├── users.py, me.py       #   用户
│   │   │   ├── glucose.py            #   血糖
│   │   │   ├── meals.py              #   膳食
│   │   │   ├── chat.py               #   AI 对话
│   │   │   ├── health_data.py        #   健康数据
│   │   │   ├── health_reports.py     #   健康报告
│   │   │   ├── agent.py              #   Agent
│   │   │   ├── dashboard.py          #   仪表板
│   │   │   ├── etl.py                #   ETL
│   │   │   ├── activity.py           #   活动日志
│   │   │   ├── cgm.py                #   CGM 集成
│   │   │   ├── omics.py              #   多组学
│   │   │   └── admin.py              #   管理后台
│   │   ├── models/                   # 数据模型 (18+ 模型)
│   │   ├── schemas/                  # Pydantic Schema
│   │   ├── services/                 # 业务逻辑层
│   │   │   ├── agent_service.py      #   Agent 引擎
│   │   │   ├── meal_service.py       #   膳食处理
│   │   │   ├── glucose_service.py    #   血糖计算
│   │   │   ├── cgm_service.py        #   CGM 接入
│   │   │   ├── safety_service.py     #   安全检测
│   │   │   ├── context_builder.py    #   LLM 上下文
│   │   │   ├── inference_service.py  #   推断服务
│   │   │   ├── activity_service.py   #   活动记录
│   │   │   └── etl/                  #   ETL 管道
│   │   ├── providers/                # LLM Provider
│   │   │   ├── base.py               #   抽象接口
│   │   │   ├── openai_provider.py    #   Kimi/OpenAI
│   │   │   ├── gemini_provider.py    #   Gemini
│   │   │   ├── mock_provider.py      #   Mock
│   │   │   └── factory.py            #   工厂方法
│   │   ├── workers/                  # 异步任务
│   │   │   ├── celery_app.py
│   │   │   └── tasks.py
│   │   └── db/                       # 数据库配置
│   ├── tests/                        # 测试 (46 tests passing)
│   ├── Dockerfile                    # Docker 构建
│   ├── alembic.ini                   # 数据库迁移
│   └── pyproject.toml                # Python 依赖
│
├── Xjie/                             # iOS App (SwiftUI)
│   └── Xjie/
│       ├── XjieApp.swift             # App 入口
│       ├── Views/                    # 视图层 (15+ 视图)
│       ├── ViewModels/               # ViewModel (12+ VM)
│       ├── Models/                   # 数据模型
│       ├── Services/                 # 网络 + 缓存 + 安全
│       ├── Utilities/                # 工具类
│       └── Assets.xcassets/          # 品牌资源
│
├── pages/                            # 微信小程序页面 (12页)
├── utils/                            # 小程序工具
├── app.js / app.json / app.wxss      # 小程序入口
│
├── data/                             # 研究数据
│   ├── glucose/                      # CGM 数据 (36+ 受试者)
│   └── raw/                          # 原始数据
├── fatty_liver_data_raw/             # 脂肪肝研究数据
└── images/                           # 品牌图片资源
```

---

*文档生成时间：2026-03-31*
