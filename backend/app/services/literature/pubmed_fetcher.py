"""PubMed E-utilities client.

Docs: https://www.ncbi.nlm.nih.gov/books/NBK25497/

We use:
- esearch.fcgi  → list of PMIDs for a query
- efetch.fcgi   → XML metadata for a list of PMIDs

Compliance:
- Only metadata + abstract is fetched (abstract used internally for LLM
  extraction, then DISCARDED — never stored in DB).
- We respect the 3 req/s rate limit (no API key) or 10 req/s with key.
"""
from __future__ import annotations

import logging
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

import httpx

logger = logging.getLogger(__name__)

ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi"
EFETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi"

DEFAULT_TIMEOUT = 30
RATE_LIMIT_SLEEP = 0.4  # ~3 req/s


@dataclass
class PubMedRecord:
    pmid: str
    title: str
    abstract: str  # in-memory only, do not persist
    authors: list[str] = field(default_factory=list)
    journal: str | None = None
    year: int | None = None
    doi: str | None = None
    publication_types: list[str] = field(default_factory=list)
    language: str = "en"


def search_pmids(
    query: str,
    *,
    max_results: int = 50,
    min_year: int | None = None,
    api_key: str | None = None,
) -> list[str]:
    """Return PMID list for a PubMed query."""
    term = query
    if min_year:
        term = f"({query}) AND ({min_year}:3000[dp])"
    params = {
        "db": "pubmed",
        "term": term,
        "retmax": str(max_results),
        "retmode": "json",
        "sort": "relevance",
    }
    if api_key:
        params["api_key"] = api_key
    resp = httpx.get(ESEARCH_URL, params=params, timeout=DEFAULT_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    return data.get("esearchresult", {}).get("idlist", []) or []


def fetch_records(
    pmids: list[str], *, api_key: str | None = None, batch_size: int = 50
) -> list[PubMedRecord]:
    """Fetch metadata + abstract for the given PMIDs.

    Abstracts live in memory only; callers MUST NOT persist them.
    """
    out: list[PubMedRecord] = []
    for i in range(0, len(pmids), batch_size):
        batch = pmids[i : i + batch_size]
        params = {
            "db": "pubmed",
            "id": ",".join(batch),
            "retmode": "xml",
            "rettype": "abstract",
        }
        if api_key:
            params["api_key"] = api_key
        time.sleep(RATE_LIMIT_SLEEP)
        resp = httpx.get(EFETCH_URL, params=params, timeout=DEFAULT_TIMEOUT)
        resp.raise_for_status()
        out.extend(_parse_pubmed_xml(resp.text))
    return out


def _text(node: ET.Element | None) -> str:
    if node is None:
        return ""
    return "".join(node.itertext()).strip()


def _parse_pubmed_xml(xml_str: str) -> list[PubMedRecord]:
    root = ET.fromstring(xml_str)
    records: list[PubMedRecord] = []
    for art in root.findall(".//PubmedArticle"):
        pmid = _text(art.find(".//PMID"))
        if not pmid:
            continue
        title = _text(art.find(".//ArticleTitle"))
        abstract_parts: list[str] = []
        for ab in art.findall(".//Abstract/AbstractText"):
            label = ab.attrib.get("Label")
            text = _text(ab)
            if not text:
                continue
            abstract_parts.append(f"{label}: {text}" if label else text)
        abstract = "\n".join(abstract_parts)

        authors: list[str] = []
        for au in art.findall(".//AuthorList/Author"):
            ln = _text(au.find("LastName"))
            fn = _text(au.find("ForeName"))
            if ln:
                authors.append((f"{fn} {ln}".strip()) if fn else ln)

        journal = _text(art.find(".//Journal/Title")) or None
        year_node = art.find(".//Journal/JournalIssue/PubDate/Year")
        medline_year = art.find(".//Journal/JournalIssue/PubDate/MedlineDate")
        year: int | None = None
        if year_node is not None and year_node.text:
            try:
                year = int(year_node.text[:4])
            except ValueError:
                year = None
        elif medline_year is not None and medline_year.text:
            try:
                year = int(medline_year.text[:4])
            except ValueError:
                year = None

        doi: str | None = None
        for aid in art.findall(".//ArticleIdList/ArticleId"):
            if aid.attrib.get("IdType") == "doi" and aid.text:
                doi = aid.text.strip()
                break

        pub_types = [
            _text(pt) for pt in art.findall(".//PublicationTypeList/PublicationType") if _text(pt)
        ]
        language_node = art.find(".//Language")
        language = (_text(language_node) or "eng")[:3]
        # normalise
        lang_map = {"eng": "en", "chi": "zh", "zho": "zh"}
        lang = lang_map.get(language.lower(), language.lower()[:2] or "en")

        records.append(
            PubMedRecord(
                pmid=pmid,
                title=title,
                abstract=abstract,
                authors=authors,
                journal=journal,
                year=year,
                doi=doi,
                publication_types=pub_types,
                language=lang,
            )
        )
    return records


# ── Evidence level classification ─────────────────────────────


def infer_evidence_level(record: PubMedRecord) -> str:
    """Heuristic L1-L4 classification from publication type + sample size cues."""
    pts_lower = {pt.lower() for pt in record.publication_types}

    if any(
        kw in pts_lower
        for kw in (
            "meta-analysis",
            "systematic review",
        )
    ):
        return "L1"
    if "randomized controlled trial" in pts_lower:
        # Treat large RCTs as L1, smaller as L2 — without sample size we conservatively say L2
        return "L1" if "multicenter study" in pts_lower else "L2"
    if any(
        kw in pts_lower
        for kw in (
            "clinical trial",
            "cohort studies",
            "observational study",
            "comparative study",
        )
    ):
        return "L2"
    if any(
        kw in pts_lower
        for kw in (
            "case reports",
            "case-control studies",
        )
    ):
        return "L3"
    if any(kw in pts_lower for kw in ("review", "editorial", "comment", "guideline")):
        return "L4"
    return "L3"
