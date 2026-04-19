"""CLI worker for batch literature ingestion.

Usage examples:

    # Single query
    python -m app.workers.literature_ingest \
        --query "continuous glucose monitor postprandial response[Title/Abstract]" \
        --topic ppgr --max 100

    # Run a curated YAML/JSON of seed queries
    python -m app.workers.literature_ingest --seed app/workers/literature_seeds.json

The seeds file is a JSON list of {"query","topic","max"} entries.
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from app.db.session import SessionLocal
from app.services.literature.ingest import ingest_pubmed_query, reembed_all

logger = logging.getLogger(__name__)


def _run_one(query: str, topic: str, max_results: int, min_year: int | None) -> None:
    db = SessionLocal()
    try:
        job = ingest_pubmed_query(
            db,
            query=query,
            topic=topic,
            max_results=max_results,
            min_year=min_year,
        )
        print(
            f"[{job.status}] topic={topic} fetched={job.fetched_count} "
            f"inserted={job.inserted_count} skipped={job.skipped_count} "
            f"job_id={job.id}",
            flush=True,
        )
        if job.error:
            print(f"  error: {job.error}", file=sys.stderr, flush=True)
    finally:
        db.close()


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    parser = argparse.ArgumentParser(description="Ingest PubMed literature into the citation DB.")
    parser.add_argument("--query", help="PubMed query string")
    parser.add_argument("--topic", help="Topic key (ppgr / omics / diet / ...)")
    parser.add_argument("--max", type=int, default=50, dest="max_results")
    parser.add_argument("--min-year", type=int, default=None)
    parser.add_argument("--seed", help="Path to JSON file with [{query,topic,max}] entries")
    parser.add_argument("--reembed", action="store_true", help="Re-embed all claims and exit")
    args = parser.parse_args()

    if args.reembed:
        db = SessionLocal()
        try:
            n = reembed_all(db)
            print(f"reembedded {n} claims")
        finally:
            db.close()
        return 0

    if args.seed:
        seeds = json.loads(Path(args.seed).read_text(encoding="utf-8"))
        for s in seeds:
            _run_one(
                query=s["query"],
                topic=s["topic"],
                max_results=int(s.get("max", 50)),
                min_year=s.get("min_year"),
            )
        return 0

    if not args.query or not args.topic:
        parser.error("--query and --topic are required when --seed is not provided")
    _run_one(args.query, args.topic, args.max_results, args.min_year)
    return 0


if __name__ == "__main__":
    sys.exit(main())
