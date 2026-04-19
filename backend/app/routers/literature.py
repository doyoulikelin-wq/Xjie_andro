"""Literature & citation API routes.

- Public (authenticated user) endpoints:
    POST /api/literature/retrieve   — search citations for an arbitrary query
- Admin endpoints (require is_admin):
    GET  /api/literature/items       — list literature
    GET  /api/literature/claims      — list claims
    POST /api/literature/items       — manual create
    POST /api/literature/claims      — manual create
    PATCH /api/literature/claims/{id}    — toggle enabled / edit
    POST /api/literature/ingest      — trigger PubMed ingest job (sync, small batches)
    POST /api/literature/reembed     — re-embed all claims
    GET  /api/literature/jobs        — list recent ingest jobs
    GET  /api/literature/stats       — counts by topic / level
"""
from __future__ import annotations

import logging
from typing import Annotated

from fastapi import APIRouter, Body, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.deps import get_current_user_id, get_db, require_admin
from app.models.literature import Claim, IngestJob, Literature
from app.schemas.literature import (
    CitationBundle,
    ClaimCreate,
    ClaimRead,
    IngestJobRead,
    IngestRequest,
    LiteratureCreate,
    LiteratureRead,
    RetrievalRequest,
    RetrievalResponse,
)
from app.services.literature.ingest import ingest_pubmed_query, reembed_all
from app.services.literature.retrieval import retrieve_claims

logger = logging.getLogger(__name__)
router = APIRouter()


# ── User-facing retrieval ──────────────────────────────────────


@router.post("/retrieve", response_model=RetrievalResponse)
def retrieve(
    payload: RetrievalRequest,
    _user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
) -> RetrievalResponse:
    matches = retrieve_claims(
        db,
        query=payload.query,
        topics=payload.topics,
        min_evidence_level=payload.min_evidence_level,
        top_k=payload.top_k,
    )
    return RetrievalResponse(matches=matches, used_fallback=not matches)


# ── Admin: literature CRUD ─────────────────────────────────────


@router.get("/items", response_model=list[LiteratureRead])
def list_literature(
    topic: str | None = Query(None),
    evidence_level: str | None = Query(None),
    reviewed: bool | None = Query(None),
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[Literature]:
    stmt = select(Literature).order_by(Literature.id.desc()).limit(limit).offset(offset)
    if evidence_level:
        stmt = stmt.where(Literature.evidence_level == evidence_level)
    if reviewed is not None:
        stmt = stmt.where(Literature.reviewed.is_(reviewed))
    rows = db.execute(stmt).scalars().all()
    if topic:
        rows = [r for r in rows if topic in (r.topics or [])]
    return rows


@router.post("/items", response_model=LiteratureRead)
def create_literature(
    payload: LiteratureCreate,
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> Literature:
    if payload.pmid:
        existing = db.execute(select(Literature).where(Literature.pmid == payload.pmid)).scalars().first()
        if existing:
            raise HTTPException(status_code=409, detail="PMID already exists")
    lit = Literature(**payload.model_dump())
    db.add(lit)
    db.commit()
    db.refresh(lit)
    return lit


# ── Admin: claim CRUD ──────────────────────────────────────────


@router.get("/claims", response_model=list[ClaimRead])
def list_claims(
    literature_id: int | None = Query(None),
    topic: str | None = Query(None),
    evidence_level: str | None = Query(None),
    enabled: bool | None = Query(True),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[Claim]:
    stmt = select(Claim).order_by(Claim.id.desc()).limit(limit).offset(offset)
    if literature_id:
        stmt = stmt.where(Claim.literature_id == literature_id)
    if evidence_level:
        stmt = stmt.where(Claim.evidence_level == evidence_level)
    if enabled is not None:
        stmt = stmt.where(Claim.enabled.is_(enabled))
    rows = db.execute(stmt).scalars().all()
    if topic:
        rows = [r for r in rows if topic in (r.topics or [])]
    return rows


@router.post("/claims", response_model=ClaimRead)
def create_claim(
    payload: ClaimCreate,
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> Claim:
    lit = db.get(Literature, payload.literature_id)
    if not lit:
        raise HTTPException(status_code=404, detail="literature not found")
    claim = Claim(**payload.model_dump())
    db.add(claim)
    db.commit()
    db.refresh(claim)
    # Embed lazily — caller can invoke /reembed if missing.
    return claim


@router.patch("/claims/{claim_id}", response_model=ClaimRead)
def update_claim(
    claim_id: int,
    payload: Annotated[dict, Body(...)],
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> Claim:
    claim = db.get(Claim, claim_id)
    if not claim:
        raise HTTPException(status_code=404, detail="claim not found")
    allowed = {
        "claim_text",
        "claim_text_en",
        "exposure",
        "outcome",
        "effect_size",
        "direction",
        "population_summary",
        "confidence",
        "topics",
        "tags",
        "enabled",
    }
    for k, v in payload.items():
        if k in allowed:
            setattr(claim, k, v)
    db.commit()
    db.refresh(claim)
    return claim


# ── Admin: ingest pipeline ─────────────────────────────────────


@router.post("/ingest", response_model=IngestJobRead)
def trigger_ingest(
    payload: IngestRequest,
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> IngestJob:
    """Run a PubMed ingest job synchronously.

    For larger batches (>100), prefer the CLI worker:
    ``python -m app.workers.literature_ingest --query ... --topic ppgr``.
    """
    if payload.max_results > 100:
        raise HTTPException(
            status_code=400,
            detail="max_results>100 is not allowed via API; use the CLI worker for batch jobs.",
        )
    job = ingest_pubmed_query(
        db,
        query=payload.query,
        topic=payload.topic,
        max_results=payload.max_results,
        min_year=payload.min_year,
        auto_extract=payload.auto_extract,
        auto_embed=payload.auto_embed,
    )
    return job


@router.post("/reembed")
def reembed(
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    n = reembed_all(db)
    return {"reembedded": n}


@router.get("/jobs", response_model=list[IngestJobRead])
def list_jobs(
    limit: int = Query(20, ge=1, le=100),
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[IngestJob]:
    return (
        db.execute(select(IngestJob).order_by(IngestJob.id.desc()).limit(limit)).scalars().all()
    )


@router.get("/stats")
def stats(
    _admin: int = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    lit_total = db.execute(select(func.count(Literature.id))).scalar_one()
    claim_total = db.execute(select(func.count(Claim.id))).scalar_one()
    claim_enabled = db.execute(
        select(func.count(Claim.id)).where(Claim.enabled.is_(True))
    ).scalar_one()
    by_level = {
        row[0]: row[1]
        for row in db.execute(
            select(Claim.evidence_level, func.count(Claim.id)).group_by(Claim.evidence_level)
        ).all()
    }
    return {
        "literature_total": lit_total,
        "claim_total": claim_total,
        "claim_enabled": claim_enabled,
        "claim_by_evidence_level": by_level,
    }


# ── Citation lookup (for iOS popup) ────────────────────────────


@router.get("/claims/{claim_id}/citation", response_model=CitationBundle)
def citation_detail(
    claim_id: int,
    _user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
) -> CitationBundle:
    """Look up a single citation by claim id (used by iOS tap-to-expand)."""
    claim = db.get(Claim, claim_id)
    if not claim or not claim.enabled:
        raise HTTPException(status_code=404, detail="citation not found")
    lit = claim.literature
    from app.services.literature.retrieval import format_short_ref

    return CitationBundle(
        claim_id=claim.id,
        literature_id=lit.id,
        claim_text=claim.claim_text,
        evidence_level=claim.evidence_level,  # type: ignore[arg-type]
        short_ref=format_short_ref(lit),
        journal=lit.journal,
        year=lit.year,
        sample_size=lit.sample_size,
        confidence=claim.confidence,
        score=None,
    )
