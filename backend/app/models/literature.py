"""Literature & claim database models for the citation/RAG system.

Stores published research findings as one-line claims with metadata,
to be cited when the AI delivers interventions or conclusions.

Compliance: only store self-authored one-line summaries + metadata.
We do NOT store full abstracts or any copyrighted text.
"""
from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base
from app.db.compat import JSONB, StringArray


# ── Evidence levels ────────────────────────────────────────────
# L1: meta-analysis / systematic review / large RCT
# L2: cohort study (n>1000) / smaller RCT
# L3: case-control / mechanism studies
# L4: review / expert consensus / case report
EVIDENCE_LEVELS = ("L1", "L2", "L3", "L4")

# Topics covered in MVP
# ppgr     : CGM × food × postprandial glucose response
# omics    : metabolomics × chronic disease (diabetes / MASLD / CV / aging)
TOPIC_VALUES = ("ppgr", "omics", "diet", "exercise", "sleep", "drug", "general")


class Literature(Base):
    """A published research paper (metadata only, no copyrighted text)."""

    __tablename__ = "literature"

    id: Mapped[int] = mapped_column(primary_key=True)
    pmid: Mapped[str | None] = mapped_column(String(32), unique=True, index=True, nullable=True)
    doi: Mapped[str | None] = mapped_column(String(255), index=True, nullable=True)
    title: Mapped[str] = mapped_column(Text, nullable=False)
    authors: Mapped[list[str]] = mapped_column(StringArray, default=list, nullable=False)
    journal: Mapped[str | None] = mapped_column(String(255), nullable=True)
    year: Mapped[int | None] = mapped_column(Integer, index=True, nullable=True)
    language: Mapped[str] = mapped_column(String(8), default="en", nullable=False)  # en/zh
    evidence_level: Mapped[str] = mapped_column(String(4), index=True, nullable=False)  # L1-L4
    study_design: Mapped[str | None] = mapped_column(String(64), nullable=True)
    sample_size: Mapped[int | None] = mapped_column(Integer, nullable=True)
    population: Mapped[str | None] = mapped_column(Text, nullable=True)
    # one-line conclusion authored by us (Chinese), summarises the paper.
    # NOT the original abstract — to comply with copyright.
    conclusion_zh: Mapped[str] = mapped_column(Text, nullable=False)
    conclusion_en: Mapped[str | None] = mapped_column(Text, nullable=True)
    topics: Mapped[list[str]] = mapped_column(StringArray, default=list, nullable=False)
    # provenance
    source: Mapped[str] = mapped_column(String(32), default="pubmed", nullable=False)
    fetched_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    reviewed: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    reviewer: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    claims: Mapped[list["Claim"]] = relationship(
        "Claim", back_populates="literature", cascade="all, delete-orphan"
    )


Index("ix_literature_year_level", Literature.year, Literature.evidence_level)


class Claim(Base):
    """A single actionable conclusion extracted from a paper.

    One paper can yield multiple claims (e.g. effect on HbA1c AND on weight).
    """

    __tablename__ = "literature_claim"

    id: Mapped[int] = mapped_column(primary_key=True)
    literature_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("literature.id", ondelete="CASCADE"), index=True, nullable=False
    )
    # one-line claim in Chinese, e.g. "燕麦早餐使糖尿病前期人群餐后血糖峰值降低 18%"
    claim_text: Mapped[str] = mapped_column(Text, nullable=False)
    claim_text_en: Mapped[str | None] = mapped_column(Text, nullable=True)
    # exposure / intervention (e.g. "燕麦", "二甲双胍", "16:8 TRE")
    exposure: Mapped[str] = mapped_column(String(255), index=True, nullable=False)
    # outcome / endpoint (e.g. "餐后血糖峰值", "HbA1c", "MASLD 风险")
    outcome: Mapped[str] = mapped_column(String(255), index=True, nullable=False)
    # effect size (e.g. "-18%", "HR 0.72 (95% CI 0.6-0.85)")
    effect_size: Mapped[str | None] = mapped_column(String(255), nullable=True)
    direction: Mapped[str | None] = mapped_column(String(16), nullable=True)  # decrease/increase/null
    population_summary: Mapped[str | None] = mapped_column(Text, nullable=True)
    confidence: Mapped[str] = mapped_column(String(8), default="medium", nullable=False)  # high/medium/low
    topics: Mapped[list[str]] = mapped_column(StringArray, default=list, nullable=False)
    tags: Mapped[list[str]] = mapped_column(StringArray, default=list, nullable=False)
    # cached evidence_level (denormalised from literature for query speed)
    evidence_level: Mapped[str] = mapped_column(String(4), index=True, nullable=False)
    # JSON array of floats, length = embedding dim (e.g. 1536 for text-embedding-3-small)
    embedding: Mapped[list[float] | None] = mapped_column(JSONB, nullable=True)
    embedding_model: Mapped[str | None] = mapped_column(String(64), nullable=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    literature: Mapped[Literature] = relationship("Literature", back_populates="claims")


Index("ix_claim_topic_level", Claim.evidence_level, Claim.enabled)


class ClaimConceptMap(Base):
    """Optional normalised concept tagging (food / disease / metric).

    Allows fast lookup like "all claims involving 'oat' as exposure".
    """

    __tablename__ = "literature_claim_concept"

    id: Mapped[int] = mapped_column(primary_key=True)
    claim_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("literature_claim.id", ondelete="CASCADE"), index=True, nullable=False
    )
    role: Mapped[str] = mapped_column(String(16), nullable=False)  # exposure / outcome / population
    concept_key: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    concept_label: Mapped[str | None] = mapped_column(String(255), nullable=True)

    __table_args__ = (
        UniqueConstraint("claim_id", "role", "concept_key", name="uq_claim_concept"),
    )


class IngestJob(Base):
    """Audit log for batch ingestion runs (PubMed → DB)."""

    __tablename__ = "literature_ingest_job"

    id: Mapped[int] = mapped_column(primary_key=True)
    query: Mapped[str] = mapped_column(Text, nullable=False)
    topic: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="pending", nullable=False)
    fetched_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    inserted_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    skipped_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    meta: Mapped[dict[str, Any]] = mapped_column(JSONB, default=dict, nullable=False)
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
