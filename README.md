# Xjie Android — 智能代谢健康管理 App

基于 Kotlin / Jetpack Compose 的 Android 客户端，与 iOS 版共用同一套 FastAPI 后端。功能涵盖：CGM 血糖追踪、膳食拍照识别、AI 健康对话「小捷」、用药提醒、关怀模式（老年人/家人）。

## 📥 下载安装

**最新 APK（公开下载）**：

👉 <https://www.jianjieaitech.com/download/Xjie_latest.apk>

安装步骤：
1. 用浏览器打开上面的链接，下载 `Xjie_latest.apk`
2. 首次安装请在 **设置 → 安全 → 允许此来源** 中授权浏览器/文件管理器
3. 点击 APK 完成安装；首次启动后允许「通知」「精确闹钟」「相机」「位置」等权限以获得完整功能

> 该 APK 为开发签名（debug‑signed），仅用于内部测试；正式上架前会切换为 release‑signed 渠道。

## 项目结构

```
XJie_And/
├── Android/                         ← Android App (Kotlin + Jetpack Compose + Hilt)
│   ├── app/src/main/java/com/xjie/app/
│   │   ├── feature/                 ← 业务功能模块（medication, glucose, chat, ...）
│   │   ├── core/                    ← 模型 / 网络 / 数据库
│   │   ├── navigation/              ← Nav 路由
│   │   └── ui/                      ← 主题、组件
│   └── app/build.gradle.kts
├── backend/                         ← FastAPI 后端 (与 iOS 仓库共用)
│   ├── app/routers/                 ← 路由 (auth, glucose, meals, chat, medications ...)
│   ├── app/models/                  ← 数据库模型
│   ├── app/services/                ← 业务逻辑 + 对话上下文构建
│   └── app/providers/               ← LLM Provider (Kimi / OpenAI / Gemini)
├── docker-compose.yml
└── demo/                            ← Web 演示
```

## 技术栈

| 层级       | 技术                                            |
| ---------- | ----------------------------------------------- |
| Android 端 | Kotlin + Jetpack Compose + Hilt + Coroutines    |
| 通知       | UNUserNotificationCenter (iOS) / AlarmManager.setAlarmClock (Android) |
| 后端       | FastAPI + SQLAlchemy 2.0 + Pydantic v2          |
| 数据库     | TimescaleDB (PostgreSQL 16)                     |
| 缓存       | Redis 7                                         |
| LLM        | Kimi (Moonshot) / OpenAI / Gemini               |
| 部署       | Docker + 阿里云 ECS                             |

## 核心功能

- **手机号登录**: 注册/登录，JWT 认证
- **血糖监测**: CGM 数据导入，24h/7d 曲线，TIR 统计
- **膳食记录**: 拍照上传 → AI 视觉识别热量 → 记录
- **AI 助手「小捷」**: 友好对话风格，基于血糖+膳食+用药上下文智能分析
- **我的用药**: 手动录入药品名称/剂量/频次/提醒时间/疗程，本地闹钟提醒（vivo 等深度 ROM 走 `setAlarmClock` 通道），同步注入到 LLM 对话上下文
- **关怀模式**: 老年人随机情感关怀通知、家人协同
- **代理系统**: 每日简报、餐前模拟、血糖救援、周评

## API 端点（新增用药模块）

| 路由                          | 功能       |
| ----------------------------- | ---------- |
| GET    /api/medications        | 用药列表   |
| POST   /api/medications        | 新增用药   |
| PATCH  /api/medications/{id}   | 修改用药   |
| DELETE /api/medications/{id}   | 删除用药   |

录入的用药信息会写入服务端 `medication` 表（字段：name / dosage / frequency / instructions / schedule_times[] / course_start / course_end / enabled / user_id FK），并在每次对话时由 `app/services/context_builder.py::_get_current_medications` 注入到 `app/providers/openai_provider.py::_build_messages` 的系统消息，让 AI 在回答时主动考虑药物相互作用、副作用、用药时机。

## 开发者指引

```bash
# 构建 debug APK
cd Android
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:assembleDebug

# 安装到设备
~/Library/Android/sdk/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
```
