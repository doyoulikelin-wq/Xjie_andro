"""Ingestion pipeline: PubMed → LLM extraction → DB.

Idempotent: skips PMIDs that already exist. Writes an IngestJob audit row.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.literature import Claim, IngestJob, Literature
from app.services.literature.claim_extractor import (
    ExtractionResult,
    extract_claims,
)
from app.services.literature.embedding import embed_batch, embed_text
from app.services.literature.pubmed_fetcher import (
    PubMedRecord,
    fetch_records,
    infer_evidence_level,
    search_pmids,
)

logger = logging.getLogger(__name__)


def ingest_pubmed_query(
    db: Session,
    *,
    query: str,
    topic: str,
    max_results: int = 50,
    min_year: int | None = None,
    auto_extract: bool = True,
    auto_embed: bool = True,
    api_key: str | None = None,
) -> IngestJob:
    """Run a full ingestion job. Returns the persisted IngestJob row."""
    job = IngestJob(query=query, topic=topic, status="running")
    db.add(job)
    db.commit()
    db.refresh(job)

    try:
        pmids = search_pmids(query, max_results=max_results, min_year=min_year, api_key=api_key)
        # Filter out PMIDs already in DB
        existing = {
            row[0]
            for row in db.execute(select(Literature.pmid).where(Literature.pmid.in_(pmids))).all()
            if row[0]
        }
        new_pmids = [p for p in pmids if p not in existing]
        job.fetched_count = len(pmids)
        job.skipped_count = len(existing)
        db.commit()

        if not new_pmids:
            job.status = "ok"
            job.finished_at = datetime.now(timezone.utc)
            db.commit()
            return job

        records = fetch_records(new_pmids, api_key=api_key)
        inserted = 0
        for rec in records:
            try:
                if _insert_record(db, rec, topic=topic, auto_extract=auto_extract, auto_embed=auto_embed):
                    inserted += 1
            except Exception as exc:  # noqa: BLE001
                logger.exception("Failed to insert PMID %s: %s", rec.pmid, exc)
                db.rollback()
                continue

        job.inserted_count = inserted
        job.status = "ok"
        job.finished_at = datetime.now(timezone.utc)
        db.commit()
        return job

    except Exception as exc:  # noqa: BLE001
        logger.exception("Ingest job failed: %s", exc)
        db.rollback()
        job.status = "error"
        job.error = str(exc)[:1000]
        job.finished_at = datetime.now(timezone.utc)
        db.commit()
        return job


def _insert_record(
    db: Session,
    rec: PubMedRecord,
    *,
    topic: str,
    auto_extract: bool,
    auto_embed: bool,
) -> bool:
    """Insert a single PubMed record (+ extracted claims). Returns True if inserted."""
    evidence_level = infer_evidence_level(rec)
    extraction: ExtractionResult | None = None
    if auto_extract:
        extraction = extract_claims(rec)
        if extraction.skip:
            logger.info("Skip PMID %s: %s", rec.pmid, extraction.reason)
            return False
        if not extraction.claims:
            logger.info("Skip PMID %s: no claims extracted", rec.pmid)
            return False

    # ── Compose Literature row (NO abstract stored) ───────────
    lit = Literature(
        pmid=rec.pmid,
        doi=rec.doi,
        title=rec.title or "(no title)",
        authors=rec.authors or [],
        journal=rec.journal,
        year=rec.year,
        language=rec.language or "en",
        evidence_level=evidence_level,
        study_design=(extraction.study_design if extraction else None),
        sample_size=(extraction.sample_size if extraction else None),
        population=(extraction.population if extraction else None),
        # Use extracted Chinese conclusion if any, else literal title (fallback)
        conclusion_zh=(
            extraction.claims[0].claim_text if extraction and extraction.claims else rec.title
        ),
        conclusion_en=(
            extraction.claims[0].claim_text_en
            if extraction and extraction.claims and extraction.claims[0].claim_text_en
            else None
        ),
        topics=[topic],
        source="pubmed",
        reviewed=False,
    )
    db.add(lit)
    db.flush()

    if not extraction:
        db.commit()
        return True

    # ── Insert Claim rows ────────────────────────────────────
    claim_models: list[Claim] = []
    for ec in extraction.claims:
        topics = list({*ec.topics, topic})
        claim = Claim(
            literature_id=lit.id,
            claim_text=ec.claim_text,
            claim_text_en=ec.claim_text_en,
            exposure=ec.exposure[:255],
            outcome=ec.outcome[:255],
            effect_size=ec.effect_size,
            direction=ec.direction,
            population_summary=ec.population_summary,
            confidence=ec.confidence if ec.confidence in ("high", "medium", "low") else "medium",
            topics=topics,
            tags=ec.tags,
            evidence_level=evidence_level,
        )
        db.add(claim)
        claim_models.append(claim)

    db.flush()

    if auto_embed and claim_models:
        texts = [_claim_embedding_text(c) for c in claim_models]
        vectors, model_name = embed_batch(texts)
        for c, v in zip(claim_models, vectors):
            c.embedding = v
            c.embedding_model = model_name

    db.commit()
    return True


def _claim_embedding_text(claim: Claim) -> str:
    parts = [claim.claim_text, f"暴露:{claim.exposure}", f"结局:{claim.outcome}"]
    if claim.effect_size:
        parts.append(f"效应:{claim.effect_size}")
    if claim.tags:
        parts.append("标签:" + ",".join(claim.tags))
    if claim.claim_text_en:
        parts.append(claim.claim_text_en)
    return " | ".join(parts)


def reembed_all(db: Session) -> int:
    """Re-embed all enabled claims (e.g. after switching embedding model)."""
    rows = db.execute(select(Claim).where(Claim.enabled.is_(True))).scalars().all()
    if not rows:
        return 0
    texts = [_claim_embedding_text(c) for c in rows]
    vectors, model_name = embed_batch(texts)
    for c, v in zip(rows, vectors):
        c.embedding = v
        c.embedding_model = model_name
    db.commit()
    return len(rows)
