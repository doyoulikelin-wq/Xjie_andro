"""Retrieval & citation formatting.

For 500 claims we keep retrieval simple: load all enabled claims with
matching evidence/topic filters into memory and run cosine similarity
in Python. This avoids pgvector setup and works on SQLite tests.
When the corpus grows >5K, swap this layer for pgvector.

Hybrid scoring: cosine(embedding) + keyword overlap boost. The boost is
critical when the embedding service is unavailable and we fall back to
the local hash-based pseudo-embedding.
"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.models.literature import EVIDENCE_LEVELS, Claim, Literature
from app.schemas.literature import CitationBundle
from app.services.literature.embedding import cosine_similarity, embed_text

logger = logging.getLogger(__name__)


# Minimum combined score to consider a claim relevant.
SCORE_THRESHOLD = 0.30
# Per-keyword overlap boost.
KEYWORD_BOOST = 0.18
# Stop words removed before keyword extraction.
_STOP = {
    "的", "了", "是", "我", "你", "他", "她", "它", "和", "与", "或",
    "吗", "呢", "啊", "吧", "在", "有", "为", "从", "到", "都",
    "the", "a", "an", "is", "are", "and", "or", "of", "to", "in",
    "for", "on", "with", "by", "this", "that",
}
_TOKEN_RE = re.compile(r"[\u4e00-\u9fa5]+|[a-zA-Z][a-zA-Z0-9]*")


@dataclass
class _Candidate:
    claim: Claim
    literature: Literature
    score: float


def _evidence_floor(min_level: str) -> set[str]:
    if min_level not in EVIDENCE_LEVELS:
        min_level = "L4"
    idx = EVIDENCE_LEVELS.index(min_level)
    return set(EVIDENCE_LEVELS[: idx + 1])  # e.g. min_level=L2 → {L1, L2}


def _keywords(text: str) -> list[str]:
    out: list[str] = []
    for tok in _TOKEN_RE.findall(text or ""):
        t = tok.lower()
        if t in _STOP:
            continue
        # For Chinese tokens longer than 2 chars, also add bigrams to widen recall
        if len(t) >= 2:
            out.append(t)
        if "\u4e00" <= t[:1] <= "\u9fa5" and len(t) >= 3:
            for i in range(len(t) - 1):
                out.append(t[i : i + 2])
    return out


def _claim_haystack(claim: Claim) -> str:
    parts = [
        claim.claim_text,
        claim.claim_text_en or "",
        claim.exposure,
        claim.outcome,
        " ".join(claim.tags or []),
        " ".join(claim.topics or []),
    ]
    return " ".join(p for p in parts if p).lower()


def retrieve_claims(
    db: Session,
    *,
    query: str,
    topics: list[str] | None = None,
    min_evidence_level: str = "L4",
    top_k: int = 5,
    threshold: float | None = None,
) -> list[CitationBundle]:
    """Return top-K matching CitationBundles for the query."""
    threshold = SCORE_THRESHOLD if threshold is None else threshold
    allowed_levels = _evidence_floor(min_evidence_level)

    stmt = (
        select(Claim)
        .options(joinedload(Claim.literature))
        .where(Claim.enabled.is_(True))
        .where(Claim.evidence_level.in_(allowed_levels))
    )
    rows = db.execute(stmt).scalars().all()
    if not rows:
        return []

    if topics:
        topic_set = set(topics)
        rows = [r for r in rows if topic_set.intersection(r.topics or [])]
        if not rows:
            return []

    q_vec, _model = embed_text(query)
    q_keywords = _keywords(query)

    cands: list[_Candidate] = []
    for c in rows:
        cos = cosine_similarity(q_vec, c.embedding) if c.embedding else 0.0
        # Keyword overlap boost
        haystack = _claim_haystack(c)
        boost = 0.0
        seen: set[str] = set()
        for kw in q_keywords:
            if kw in seen:
                continue
            if kw in haystack:
                boost += KEYWORD_BOOST
                seen.add(kw)
        score = max(cos, 0.0) + boost
        if score < threshold:
            continue
        cands.append(_Candidate(claim=c, literature=c.literature, score=score))

    cands.sort(key=lambda x: x.score, reverse=True)
    return [_to_bundle(c) for c in cands[:top_k]]


def _to_bundle(cand: _Candidate) -> CitationBundle:
    lit = cand.literature
    return CitationBundle(
        claim_id=cand.claim.id,
        literature_id=lit.id,
        claim_text=cand.claim.claim_text,
        evidence_level=cand.claim.evidence_level,  # type: ignore[arg-type]
        short_ref=format_short_ref(lit),
        journal=lit.journal,
        year=lit.year,
        sample_size=lit.sample_size,
        confidence=cand.claim.confidence,
        score=round(cand.score, 4),
    )


def format_short_ref(lit: Literature) -> str:
    """e.g. 'Segal et al., Cell 2015'."""
    if lit.authors:
        first = lit.authors[0]
        # Use last name only when possible
        last_name = first.split()[-1] if first else ""
        suffix = " et al." if len(lit.authors) > 1 else ""
        prefix = f"{last_name}{suffix}"
    else:
        prefix = "Anonymous"
    journal = lit.journal or ""
    year = f" {lit.year}" if lit.year else ""
    if journal:
        return f"{prefix}, {journal}{year}".strip()
    return f"{prefix}{year}".strip()


def build_citation_block(citations: list[CitationBundle]) -> str:
    """Plain-text block to append to AI prompts.

    Returns empty string if no citations.
    Each line: [N] short_ref — claim_text (level)
    """
    if not citations:
        return ""
    lines = []
    for i, c in enumerate(citations, start=1):
        lines.append(
            f"[{i}] {c.short_ref} ({c.evidence_level}, n={c.sample_size or '?'}) — {c.claim_text}"
        )
    return "\n".join(lines)
