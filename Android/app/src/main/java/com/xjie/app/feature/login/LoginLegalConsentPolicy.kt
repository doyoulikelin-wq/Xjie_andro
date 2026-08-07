package com.xjie.app.feature.login

import com.xjie.app.feature.settings.XAgeComplianceSection
import com.xjie.app.feature.settings.XAgeSupportCompliancePolicy

object LoginLegalConsentPolicy {
    const val REQUIRED_MESSAGE = "请阅读并同意《用户协议》和《隐私政策》后再注册"

    val userAgreementSections: List<XAgeComplianceSection> = listOf(
        XAgeComplianceSection(
            title = "服务说明",
            content = "“小捷”提供健康档案、健康数据记录、趋势展示、提醒和健康管理相关服务。应用中的健康管理内容仅供参考，不构成诊断、处方、治疗建议或紧急医疗服务。",
        ),
        XAgeComplianceSection(
            title = "账号与使用",
            content = "请使用真实、合法的信息注册并妥善保管账号和密码。不得利用本服务发布违法、有害或侵犯他人权益的内容，也不得干扰服务的正常运行。",
        ),
        XAgeComplianceSection(
            title = "健康信息与服务边界",
            content = "你应自行判断录入、上传和同步的信息是否准确、完整。出现紧急症状、身体明显不适或需要诊疗时，请及时联系医疗机构或当地急救服务。",
        ),
        XAgeComplianceSection(
            title = "服务变更与终止",
            content = "我们可能基于服务运营、安全或法律要求更新功能或协议，并通过应用内合理方式告知。你可随时停止使用服务、退出登录或按页面指引申请注销账号。",
        ),
        XAgeComplianceSection(
            title = "联系我们",
            content = "如对本协议、账号或服务有疑问，可通过应用内意见反馈或 ${XAgeSupportCompliancePolicy.SUPPORT_EMAIL} 联系我们。",
        ),
    )

    val privacyPolicySections: List<XAgeComplianceSection>
        get() = XAgeSupportCompliancePolicy.privacySections

    fun canSubmit(
        isSignup: Boolean,
        acceptedUserAgreement: Boolean,
        acceptedPrivacyPolicy: Boolean,
    ): Boolean = !isSignup || (acceptedUserAgreement && acceptedPrivacyPolicy)
}
