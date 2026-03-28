from __future__ import annotations

import json
import logging
from typing import Iterator

from openai import OpenAI

from app.core.config import settings
from app.providers.base import ChatLLMResult, LLMProvider, MealVisionItem, MealVisionResult

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
你是 Xjie 的健康AI助手 🤖。
你帮助用户理解血糖数据、饮食记录、体检报告和代谢健康。

关键规则:
- 始终用中文回答
- 你已经拥有用户的所有健康数据（血糖、体检报告指标等），直接基于数据分析回答，不要要求用户重新提供数据
- 引用用户自身的数据时要具体、用数字说话（例如提及具体的检验数值、参考范围、是否异常）
- 如果用户询问体检报告相关建议，直接引用系统提供的体检数据进行分析
- 如果用户出现紧急症状,立刻建议就医
- 保持亲切、专业、简洁的风格

**输出格式要求** — 你必须严格按照以下 JSON 格式回答, 不要输出任何其他文字:
```json
{
  "summary": "简要回答（30-60字）",
  "analysis": "详细分析（使用 Markdown 格式: 加粗、列表等。包含数据引用、原因分析、具体建议。结尾给 1-2 个后续建议问题。）"
}
```

**summary 写法要求（极其重要）**:
summary 必须是一段有信息量的个性化回答，严格包含以下四个要素:
1. **引用数据**：开头说明"根据你的血糖/饮食/体检数据分析"，让用户知道回答基于他们的真实数据
2. **可能原因**：简明给出与用户情况相关的可能原因或结论
3. **行动建议**：一句话说明用户可以怎么做
4. **邀请追问**：结尾用"告诉我更多细节，我会帮你做更深入的分析"引导用户继续

示例 summary：
- "根据你近7天的血糖数据，你的餐后血糖波动偏大（均值偏高15%），头痛可能与血糖波动有关，建议关注餐后2小时血糖变化。告诉我更多症状细节，我帮你做更深入的分析。"
- "根据你的体检报告，你的转氨酶偏高，结合近期饮食热量偏高，可能与脂肪肝进展相关，建议控制每日热量在1800kcal以内。告诉我更多细节，我帮你做更详细的分析。"

**绝对不要**写出泛泛的、不结合用户数据的 summary，如"头痛需先排急症"这类通用医学建议。每个 summary 必须引用用户的实际数据。
"""


def _build_messages(
    context: dict,
    user_query: str,
    history: list[dict] | None = None,
) -> list[dict]:
    """Build the messages array for OpenAI Chat Completions.

    Args:
        context: User health context dict from context_builder.
        user_query: Current user message.
        history: Optional list of prior messages [{"role": ..., "content": ...}].
    """
    messages: list[dict] = [{"role": "system", "content": SYSTEM_PROMPT}]

    # Inject user context as a system message
    ctx_parts = []

    # Glucose summary (key: glucose_summary from context_builder)
    g = context.get("glucose_summary") or context.get("glucose") or {}
    if g.get("last_24h"):
        d = g["last_24h"]
        ctx_parts.append(f"血糖数据 (过去24h): 均值={d.get('avg')} mg/dL, "
                         f"TIR(70-180)={d.get('tir_70_180_pct')}%, "
                         f"变异性={d.get('variability')}")
    if g.get("last_7d"):
        d = g["last_7d"]
        ctx_parts.append(f"血糖数据 (过去7天): 均值={d.get('avg')} mg/dL, "
                         f"TIR(70-180)={d.get('tir_70_180_pct')}%, "
                         f"变异性={d.get('variability')}")

    # Daily calories (nested in data_quality from context_builder)
    dq = context.get("data_quality") or {}
    kcal = dq.get("kcal_today") if dq else context.get("kcal_today")
    if kcal is not None:
        ctx_parts.append(f"今日热量: {kcal} kcal")

    if context.get("meals_today"):
        meals = context["meals_today"]
        ctx_parts.append(f"今日进餐 {len(meals)} 次: " +
                         ", ".join(f"{m.get('kcal', '?')}kcal@{m.get('ts', '?')}" for m in meals))

    if context.get("symptoms_last_7d"):
        symptoms = context["symptoms_last_7d"]
        ctx_parts.append(f"近7天症状 {len(symptoms)} 条: " +
                         ", ".join(f"{s.get('text', '')}(严重度{s.get('severity', '?')})" for s in symptoms[:5]))

    if context.get("agent_features"):
        ctx_parts.append(f"Agent特征: {json.dumps(context['agent_features'], ensure_ascii=False)}")
    if context.get("user_profile_info"):
        ctx_parts.append(f"用户画像: {json.dumps(context['user_profile_info'], ensure_ascii=False)}")

    # Health exam report data (Liver subjects)
    health_text = context.get("health_report_text", "")
    if health_text:
        ctx_parts.append(f"以下是该用户的体检报告数据:\n{health_text}")

    if ctx_parts:
        messages.append({
            "role": "system",
            "content": "以下是该用户的实时健康数据（你已经拥有这些数据，可以直接引用分析，不要再要求用户提供）:\n" + "\n".join(ctx_parts),
        })

    # Append conversation history (max last 10 turns to fit context window)
    if history:
        for msg in history[-20:]:  # 20 messages = ~10 turns
            messages.append({"role": msg["role"], "content": msg["content"]})

    messages.append({"role": "user", "content": user_query})
    return messages


def _parse_structured_response(raw: str) -> dict:
    """Parse GPT's JSON response into summary + analysis.

    Falls back gracefully if GPT doesn't follow the JSON format.
    """
    text = raw.strip()
    # Try direct JSON parse
    try:
        data = json.loads(text)
        if "summary" in data and "analysis" in data:
            return data
    except json.JSONDecodeError:
        pass

    # Try extracting JSON from markdown code block
    if "```" in text:
        try:
            block = text.split("```json")[-1].split("```")[0].strip() if "```json" in text else text.split("```")[1].split("```")[0].strip()
            data = json.loads(block)
            if "summary" in data and "analysis" in data:
                return data
        except (json.JSONDecodeError, IndexError):
            pass

    # Fallback: treat entire response as both summary and analysis
    # Take first sentence as summary
    lines = text.split("\n")
    first_line = lines[0].strip().rstrip("。，,") if lines else text[:60]
    if len(first_line) > 50:
        first_line = first_line[:50] + "…"
    return {"summary": first_line, "analysis": text}



class OpenAIProvider(LLMProvider):
    provider_name = "openai"
    text_model = settings.OPENAI_MODEL_TEXT
    vision_model = settings.OPENAI_MODEL_VISION

    def __init__(self) -> None:
        kwargs: dict = {"api_key": settings.OPENAI_API_KEY}
        if settings.OPENAI_BASE_URL:
            kwargs["base_url"] = settings.OPENAI_BASE_URL
        self._client = OpenAI(**kwargs)

    def analyze_image(self, image_url: str) -> MealVisionResult:
        """Analyze a meal photo using GPT vision."""
        try:
            response = self._client.chat.completions.create(
                model=self.vision_model,
                messages=[
                    {"role": "system", "content": "你是食物识别专家。分析图片中的食物,返回JSON格式: "
                     '{"items": [{"name": "食物名", "portion_text": "份量", "kcal": 数字}], '
                     '"total_kcal": 数字, "confidence": 0-1, "notes": "备注"}'},
                    {"role": "user", "content": [
                        {"type": "image_url", "image_url": {"url": image_url}},
                        {"type": "text", "text": "请分析这张图片中的食物,估算热量。"},
                    ]},
                ],
                max_completion_tokens=500,
                temperature=0.3,
            )
            raw = response.choices[0].message.content or "{}"
            # Try to parse JSON from the response
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                # Try to extract JSON from markdown code block
                if "```" in raw:
                    raw = raw.split("```json")[-1].split("```")[0].strip()
                    data = json.loads(raw)
                else:
                    raise

            items = [MealVisionItem(**item) for item in data.get("items", [])]
            return MealVisionResult(
                items=items,
                total_kcal=data.get("total_kcal", sum(i.kcal for i in items)),
                confidence=data.get("confidence", 0.5),
                notes=data.get("notes", ""),
            )
        except Exception as e:
            logger.error("OpenAI vision analysis failed: %s", e)
            items = [MealVisionItem(name="unknown meal", portion_text="1 serving", kcal=480)]
            return MealVisionResult(items=items, total_kcal=480, confidence=0.2,
                                    notes=f"Vision fallback: {e}")

    def generate_text(self, context: dict, user_query: str, *, history: list[dict] | None = None) -> ChatLLMResult:
        """Generate a complete text response."""
        try:
            messages = _build_messages(context, user_query, history=history)
            response = self._client.chat.completions.create(
                model=self.text_model,
                messages=messages,
                max_completion_tokens=2000,
                temperature=0.7,
            )
            raw = response.choices[0].message.content or ""
            parsed = _parse_structured_response(raw)
            return ChatLLMResult(
                answer_markdown=raw,
                confidence=0.85,
                followups=[],
                safety_flags=[],
                summary=parsed.get("summary", ""),
                analysis=parsed.get("analysis", ""),
            )
        except Exception as e:
            logger.error("OpenAI generate_text failed: %s", e)
            return ChatLLMResult(
                answer_markdown=f"抱歉，AI 暂时无法回答。错误信息: {e}",
                confidence=0.0,
                followups=["请稍后再试"],
                safety_flags=["provider_error"],
                summary="AI 暂时无法回答",
                analysis=f"错误信息: {e}",
            )

    def stream_text(self, context: dict, user_query: str, *, history: list[dict] | None = None) -> Iterator[str]:
        """Stream text token-by-token using OpenAI streaming API."""
        try:
            messages = _build_messages(context, user_query, history=history)
            stream = self._client.chat.completions.create(
                model=self.text_model,
                messages=messages,
                max_completion_tokens=2000,
                temperature=0.7,
                stream=True,
            )
            for chunk in stream:
                delta = chunk.choices[0].delta if chunk.choices else None
                if delta and delta.content:
                    yield delta.content
        except Exception as e:
            logger.error("OpenAI stream_text failed: %s", e)
            yield f"\n\n⚠️ AI 流式响应失败: {e}"
