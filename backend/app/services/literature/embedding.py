"""Embedding service for claim semantic search.

We use the OpenAI-compatible embeddings endpoint configured via
``OPENAI_BASE_URL``. Kimi (Moonshot) does not yet expose a stable
embedding model, so we fall back to a deterministic local hash-based
"pseudo-embedding" when no embedding endpoint is available — good enough
for MVP filtering while preserving the same retrieval code path.
"""
from __future__ import annotations

import hashlib
import logging
import math
import os

from openai import OpenAI

from app.core.config import settings

logger = logging.getLogger(__name__)


DEFAULT_MODEL = os.getenv("LITERATURE_EMBEDDING_MODEL", "text-embedding-3-small")
DEFAULT_DIM = 1536  # text-embedding-3-small dimension
HASH_DIM = 256  # fallback dim


def _embedding_client() -> OpenAI | None:
    """Return an OpenAI-compatible client only if the configured endpoint
    is *not* Kimi (which doesn't host the standard embeddings API)."""
    base = (settings.OPENAI_BASE_URL or "").lower()
    if "moonshot" in base or "kimi" in base:
        return None
    if not settings.OPENAI_API_KEY:
        return None
    kwargs: dict = {"api_key": settings.OPENAI_API_KEY}
    if settings.OPENAI_BASE_URL:
        kwargs["base_url"] = settings.OPENAI_BASE_URL
    return OpenAI(**kwargs)


def embed_text(text: str) -> tuple[list[float], str]:
    """Return (vector, model_name)."""
    text = (text or "").strip()
    if not text:
        text = "empty"
    client = _embedding_client()
    if client is not None:
        try:
            resp = client.embeddings.create(model=DEFAULT_MODEL, input=text)
            return list(resp.data[0].embedding), DEFAULT_MODEL
        except Exception as exc:  # noqa: BLE001
            logger.warning("Embedding API failed (%s) — falling back to local hash", exc)
    return _hash_embedding(text), "local-hash-v1"


def embed_batch(texts: list[str]) -> tuple[list[list[float]], str]:
    if not texts:
        return [], DEFAULT_MODEL
    client = _embedding_client()
    if client is not None:
        try:
            resp = client.embeddings.create(model=DEFAULT_MODEL, input=texts)
            vectors = [list(item.embedding) for item in resp.data]
            return vectors, DEFAULT_MODEL
        except Exception as exc:  # noqa: BLE001
            logger.warning("Embedding batch API failed (%s) — falling back to local hash", exc)
    return [_hash_embedding(t) for t in texts], "local-hash-v1"


def cosine_similarity(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


# ── Fallback: bag-of-character-trigrams hashed into HASH_DIM dims ──


def _hash_embedding(text: str, dim: int = HASH_DIM) -> list[float]:
    text = text.lower()
    vec = [0.0] * dim
    tokens = _ngrams(text, 3)
    for tok in tokens:
        h = int(hashlib.md5(tok.encode("utf-8")).hexdigest(), 16)
        idx = h % dim
        sign = 1.0 if (h >> 31) & 1 else -1.0
        vec[idx] += sign
    # L2 normalise
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [v / norm for v in vec]


def _ngrams(text: str, n: int) -> list[str]:
    text = "".join(ch for ch in text if not ch.isspace())
    if len(text) < n:
        return [text] if text else []
    return [text[i : i + n] for i in range(len(text) - n + 1)]
