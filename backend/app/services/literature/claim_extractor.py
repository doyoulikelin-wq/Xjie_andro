"""LLM-based extraction of structured claims from PubMed abstracts.

Compliance: the abstract is sent to the LLM, but only our self-authored
one-line conclusion + structured fields are persisted. The original
abstract text is NEVER stored.
"""
from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass

from openai import OpenAI

from app.core.config import settings
from app.services.literature.pubmed_fetcher import PubMedRecord

logger = logging.getLogger(__name__)


EXTRACTION_SYSTEM_PROMPT = """\
你是一名医学循证证据抽取专家，专注于代谢健康（CGM、餐后血糖、代谢组学、慢病、饮食）。

# 任务
给你一篇英文/中文医学文献的标题与摘要，你需要：
1. 用一句话中文复述论文的最关键结论（不要照抄摘要原文）。
2. 抽取结构化字段。
3. 如果论文不属于以下主题，则直接返回 {"skip": true, "reason": "..."}。
   主题白名单: ppgr / omics / diet / exercise / sleep / drug
4. 如果论文是综述/社论/无明确干预-结局结论，可以 skip。

# 主题定义
- ppgr: CGM × 食物 × 餐后血糖响应
- omics: 代谢组学 × 慢病风险（糖尿病、MASLD/脂肪肝、心血管、老龄化）
- diet: 饮食模式 × 慢病结局
- exercise / sleep / drug: 同名

# 输出格式（严格 JSON，不要任何额外文字）
{
  "skip": false,
  "claims": [
    {
      "claim_text": "一句话中文结论（自己写的，不要照抄），需包含具体数字效应量",
      "claim_text_en": "one-sentence English conclusion (your own wording)",
      "exposure": "干预/暴露名称（中文，如 燕麦、二甲双胍、16:8 间歇性禁食）",
      "outcome": "结局名称（中文，如 餐后血糖峰值、HbA1c、MASLD 风险）",
      "effect_size": "具体效应量字符串，如 -18% 或 HR 0.72 (95% CI 0.6-0.85)",
      "direction": "decrease | increase | neutral | mixed",
      "population_summary": "研究人群一句话描述（中文）",
      "confidence": "high | medium | low",
      "topics": ["ppgr"],
      "tags": ["关键词1","关键词2"]
    }
  ],
  "study_design": "RCT / Cohort / Meta-analysis / Cross-sectional / Mechanism / 等",
  "sample_size": 1234,
  "population": "整体人群一句话描述"
}

# 规则
- 一篇论文最多产出 3 条 claim，只挑最具操作性的结论。
- effect_size 必须是从摘要里直接读到的真实数字，不要编造。读不到就用空字符串。
- claim_text 必须是自己撰写的中文一句话（≤80 字），用于直接给用户看。
- 严禁拷贝摘要原文连续 10 字以上的片段（版权要求）。
"""


@dataclass
class ExtractedClaim:
    claim_text: str
    claim_text_en: str | None
    exposure: str
    outcome: str
    effect_size: str | None
    direction: str | None
    population_summary: str | None
    confidence: str
    topics: list[str]
    tags: list[str]


@dataclass
class ExtractionResult:
    skip: bool
    reason: str | None
    claims: list[ExtractedClaim]
    study_design: str | None
    sample_size: int | None
    population: str | None


def _build_user_prompt(record: PubMedRecord) -> str:
    parts = [
        f"PMID: {record.pmid}",
        f"Title: {record.title}",
        f"Journal: {record.journal or 'N/A'}",
        f"Year: {record.year or 'N/A'}",
        f"Publication types: {', '.join(record.publication_types) or 'N/A'}",
        "Abstract:",
        record.abstract or "(no abstract available)",
    ]
    return "\n".join(parts)


_JSON_BLOCK_RE = re.compile(r"\{[\s\S]*\}")


def _parse_extraction_json(raw: str) -> dict:
    raw = raw.strip()
    if raw.startswith("```"):
        # strip ```json fences
        raw = re.sub(r"^```(?:json)?\s*", "", raw)
        raw = re.sub(r"\s*```$", "", raw)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        m = _JSON_BLOCK_RE.search(raw)
        if not m:
            raise
        return json.loads(m.group(0))


def extract_claims(record: PubMedRecord, *, client: OpenAI | None = None) -> ExtractionResult:
    """Run LLM extraction. Returns ExtractionResult; skip=True means skip this paper."""
    if not record.abstract:
        return ExtractionResult(
            skip=True, reason="no_abstract", claims=[], study_design=None, sample_size=None, population=None
        )

    client = client or _make_client()
    if client is None:
        # No LLM configured — surface as skip so the worker doesn't crash.
        return ExtractionResult(
            skip=True, reason="no_llm_provider", claims=[], study_design=None, sample_size=None, population=None
        )

    model = settings.OPENAI_MODEL_TEXT
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": EXTRACTION_SYSTEM_PROMPT},
            {"role": "user", "content": _build_user_prompt(record)},
        ],
        response_format={"type": "json_object"},
        **settings.llm_temperature_kwargs(model),
    )
    content = resp.choices[0].message.content or "{}"
    try:
        data = _parse_extraction_json(content)
    except Exception:  # noqa: BLE001
        logger.warning("Failed to parse extraction JSON for PMID %s: %s", record.pmid, content[:200])
        return ExtractionResult(
            skip=True, reason="parse_error", claims=[], study_design=None, sample_size=None, population=None
        )

    if data.get("skip"):
        return ExtractionResult(
            skip=True,
            reason=data.get("reason") or "model_skip",
            claims=[],
            study_design=None,
            sample_size=None,
            population=None,
        )

    raw_claims = data.get("claims") or []
    claims: list[ExtractedClaim] = []
    for c in raw_claims[:3]:
        try:
            claims.append(
                ExtractedClaim(
                    claim_text=str(c["claim_text"]).strip(),
                    claim_text_en=(c.get("claim_text_en") or None),
                    exposure=str(c["exposure"]).strip(),
                    outcome=str(c["outcome"]).strip(),
                    effect_size=(c.get("effect_size") or None),
                    direction=(c.get("direction") or None),
                    population_summary=(c.get("population_summary") or None),
                    confidence=(c.get("confidence") or "medium"),
                    topics=[t for t in (c.get("topics") or []) if isinstance(t, str)],
                    tags=[t for t in (c.get("tags") or []) if isinstance(t, str)],
                )
            )
        except (KeyError, TypeError, ValueError) as exc:
            logger.debug("Drop malformed claim in PMID %s: %s", record.pmid, exc)
            continue

    return ExtractionResult(
        skip=False,
        reason=None,
        claims=claims,
        study_design=data.get("study_design"),
        sample_size=_safe_int(data.get("sample_size")),
        population=data.get("population"),
    )


def _safe_int(v) -> int | None:
    try:
        return int(v) if v is not None else None
    except (TypeError, ValueError):
        return None


def _make_client() -> OpenAI | None:
    if not settings.OPENAI_API_KEY:
        return None
    kwargs: dict = {"api_key": settings.OPENAI_API_KEY}
    if settings.OPENAI_BASE_URL:
        kwargs["base_url"] = settings.OPENAI_BASE_URL
    return OpenAI(**kwargs)
