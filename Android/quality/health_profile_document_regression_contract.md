# 健康画像专项文档回归合同（Android）

日期：2026-07-15
依据：`/改进/7月15日-健康画像页面修改目标.docx`

## 最小复现与根因

- 旧页面以自由文本和局部条件分支处理画像，可能从缺单位或未确认测量猜测 BMI，目标缺少结构，长期用药没有稳定真实跳转，来源/历史能力边界也无法由测试约束。
- 根因是字段、派生、安全、候选动作和导航规则没有集中到可复用策略与命名回归中。

## 永久约束

1. BMI 只由 `user`、`clinician` 或 `verified_source` 确认且带单位的身高、体重派生；缺失、裸数字、非法范围或 `automatic` 值保持“待补充”，有效值展示来源与最新时间。
2. 基础资料覆盖出生日期、性别、身高、体重、血型、地区、生活方式；长期标签覆盖诊断、家族史、长期异常、风险因素、主动关注、关联计划。
3. 目标和安全候选不能接受，只能忽略；安全事实保存/删除必须显式二次确认。
4. 目标只能由用户主动创建/调整，且名称、状态、开始日期、关联指标必须完整并以结构化 JSON 出站。
5. 画像只显示服务端已确认用药摘要，过滤剂量/提醒/服药动作；`healthProfile.medication.open` 必须导航到真实 `Route.Medications`。
6. 来源、更新时间、当前版本和冲突如实展示；接口无逐条历史明细时明确说明。概览只有一个状态化主操作，不出现“永久保存”；X 年龄保持禁用。
7. 键盘滚动收起、未保存返回确认、多行内容、小屏、大字号、安全区和 TalkBack 属于同类回归范围。

## 覆盖矩阵

| 文档要求 | 实现入口 | 自动化合同 |
| --- | --- | --- |
| 字段全集与结构化目标 | `HealthProfileTrustPolicy.fields`、`PatientHistoryViewModel` | `healthProfileDocumentFieldsAndUserGoalsStayStructured` |
| 透明 BMI | `HealthProfileTrustPolicy.derivedBmi` | `confirmedMeasurementsDeriveBmiOnlyWithUnitsAndTransparentProvenance` |
| 候选/安全边界 | `canReviewCandidate` | `goalAndSafetyCandidatesCannotBeAcceptedDirectly` |
| 来源分类 | `sourceLabel` | `sourceClassificationCoversAllDocumentSourceFamiliesWithoutGuessing` |
| 单一主操作、用药真实路由、历史诚实披露 | `PatientHistoryScreen`、`MainScaffold` | `test_health_profile_uses_server_authority_and_explicit_confirmation` |
| 主体、版本、幂等 | Repository/ViewModel/API | 画像策略测试其余既有 6 项合同 |

## 已执行证据

- `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.xjie.app.feature.patienthistory.HealthProfileTrustPolicyTest'`：通过。
- JUnit XML 精确核对画像策略 `10/10`，`0 failed`、`0 skipped`。
- `python3 -m unittest tools.tests.test_health_trust_consumers.HealthTrustConsumerTests.test_health_profile_uses_server_authority_and_explicit_confirmation`：通过。
- `python3 tools/verify_jvm_test_inventory.py`：画像首轮更新后 source inventory 精确通过；最终总清单由稳定树门禁重新核对。

## 发布前仍需

- 最终稳定树执行完整 JVM exact inventory、`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 和 `git diff --check`。
- Android 真机签核第三方中文输入法、长内容、小屏/大字号、TalkBack、安全区、未保存返回、候选冲突及用药跳转返回。
- 当前服务端只提供当前事实版本，不提供逐条修订历史列表；客户端必须继续明确披露，不能伪造完整历史。
