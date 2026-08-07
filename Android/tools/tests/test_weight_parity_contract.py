from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app"
ROUTES = MAIN / "navigation" / "Routes.kt"
NAVIGATION = MAIN / "navigation" / "MainScaffold.kt"
POLICY = MAIN / "feature" / "weight" / "WeightDashboardPolicy.kt"
REPOSITORY = MAIN / "feature" / "weight" / "WeightRepository.kt"
VIEW_MODEL = MAIN / "feature" / "weight" / "WeightViewModel.kt"
SCREEN = MAIN / "feature" / "weight" / "WeightScreen.kt"
API = MAIN / "core" / "network" / "api" / "WeightApi.kt"
FIXTURE = (
    ROOT
    / "app"
    / "src"
    / "debug"
    / "java"
    / "com"
    / "xjie"
    / "app"
    / "core"
    / "quality"
    / "DebugUiAutomationTransport.kt"
)
UI_TEST = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "com"
    / "xjie"
    / "app"
    / "feature"
    / "weight"
    / "WeightDashboardUiTest.kt"
)
CONTRACT = ROOT / "quality" / "weight_regression_contract.md"


class WeightParityContractTests(unittest.TestCase):
    def test_weight_quick_action_uses_dedicated_root_route(self) -> None:
        routes = ROUTES.read_text(encoding="utf-8")
        navigation = NAVIGATION.read_text(encoding="utf-8")

        self.assertIn('data object Weight : Route("weight")', routes)
        self.assertIn('"weight" -> Route.Weight.path', navigation)
        self.assertIn("composable(Route.Weight.path)", navigation)
        self.assertIn("WeightScreen(", navigation)
        self.assertNotIn('"weight" -> Route.HealthData.path', navigation)

    def test_weight_policy_and_wire_surface_fail_closed_on_untrusted_sources(self) -> None:
        policy = POLICY.read_text(encoding="utf-8")
        repository = REPOSITORY.read_text(encoding="utf-8")
        view_model = VIEW_MODEL.read_text(encoding="utf-8")
        api = API.read_text(encoding="utf-8")

        for required in (
            'const val WEIGHT_SOURCE_METRIC = "bodyWeight"',
            '"manual" -> WeightEvidenceSource.Manual',
            '"document" -> WeightEvidenceSource.ConfirmedReport',
            '"device", "health_connect"',
            '"apple_health"',
            "!point.source_id.isNullOrBlank()",
            "point.source_metric",
            "else -> null",
            "today.minusMonths(3)",
            "if (date.isAfter(today))",
            "latestWeightKg = latest?.weightKg",
            "admittedProfileHeight(snapshot.profileHeightCm)",
            "fun bodyMassIndex",
            "value.isFinite()",
        ):
            self.assertIn(required, policy)

        self.assertEqual(api.count("@Tag owner: AuthManager.AccountScopeSnapshot"), 3)
        for endpoint in (
            '@GET("api/health-data/indicators/trend")',
            '@GET("api/users/me")',
            '@POST("api/health-data/indicators/manual")',
        ):
            self.assertIn(endpoint, api)
        for required in (
            "requireCurrent(owner)",
            "requireValidMutationResponse(body, response)",
            'response.source.equals("manual", ignoreCase = true)',
        ):
            self.assertIn(required, repository)
        for required in (
            "AuthManager.AccountScopeSnapshot",
            "WeightRequestToken",
            "authManager.isCurrent(token.owner)",
            "账号或健康主体已变化",
        ):
            self.assertIn(required, view_model)

    def test_weight_ui_covers_states_inputs_back_large_text_and_deterministic_fixture(self) -> None:
        screen = SCREEN.read_text(encoding="utf-8")
        fixture = FIXTURE.read_text(encoding="utf-8")
        ui_test = UI_TEST.read_text(encoding="utf-8")
        contract = CONTRACT.read_text(encoding="utf-8")

        for required in (
            "WeightScreenPhase.Loading",
            "WeightScreenPhase.Error",
            "WeightScreenPhase.Empty",
            'testTag("weight.loading")',
            'testTag("weight.error")',
            'testTag("weight.trend.empty")',
            'testTag("weight.height.sheet")',
            'testTag("weight.height.close")',
            'testTag("weight.picker.sheet")',
            'testTag("weight.guidance.close")',
            "LocalConfiguration.current.fontScale >= 1.25f",
            "maxWidth < 330.dp",
            "BackHandler",
            "CustomAccessibilityAction(\"上一个体重记录\")",
            "CustomAccessibilityAction(\"下一个体重记录\")",
            "NumberPicker",
            "WeightDashboardPolicy.appendHeightDigit",
            "数据边界：只展示服务器已确认入库",
        ):
            self.assertIn(required, screen)
        self.assertNotIn("TextField(", screen)

        for required in (
            '"GET" to "/api/health-data/indicators/trend" -> weightTrend()',
            '"POST" to "/api/health-data/indicators/manual" -> manualIndicator(request)',
            '"source":"document"',
            '"source":"manual"',
            '"source":"device"',
            '"source_metric":"bodyWeight"',
            '"source_id":"bodyWeight-hc-ui-stable"',
        ):
            self.assertIn(required, fixture)
        for required in (
            "quickActionOpensTrustedWeightDetailAndInputSheetsReturnSafely",
            'onNodeWithTag("weight.dashboard")',
            'hasTestTag("weight.height.sheet")',
            'hasTestTag("weight.picker.sheet")',
            "数据范围异常，请填写正确数字。",
            'closeAppOwnedModal("weight.height.close")',
            'closeAppOwnedModal("weight.guidance.close")',
            "pressBack()",
        ):
            self.assertIn(required, ui_test)
        for required in (
            "Minimum reproduction",
            "Confirmed root cause",
            "Permanent invariants",
            "Sibling entry points and states",
            "Verification plan",
        ):
            self.assertIn(required, contract)


if __name__ == "__main__":
    unittest.main()
