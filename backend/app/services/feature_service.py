"""Feature flag and skill runtime services.

- Feature flags are cached for 60s in-process to avoid DB hits on every request.
- Skills are loaded once per cache interval and matched against user query keywords.
"""

from __future__ import annotations

import re
import time
from typing import Sequence

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.feature_flag import FeatureFlag, Skill

# ── In-process cache ──────────────────────────────────────

_flag_cache: dict[str, bool] = {}
_flag_cache_ts: float = 0.0

_skill_cache: list[Skill] = []
_skill_cache_ts: float = 0.0

_CACHE_TTL = 60.0  # seconds


def _refresh_flags(db: Session) -> None:
    global _flag_cache, _flag_cache_ts
    if time.time() - _flag_cache_ts < _CACHE_TTL:
        return
    rows = db.execute(select(FeatureFlag)).scalars().all()
    _flag_cache = {r.key: r.enabled for r in rows}
    _flag_cache_ts = time.time()


def _refresh_skills(db: Session) -> None:
    global _skill_cache, _skill_cache_ts
    if time.time() - _skill_cache_ts < _CACHE_TTL:
        return
    rows = db.execute(
        select(Skill).where(Skill.enabled == True).order_by(Skill.priority)  # noqa: E712
    ).scalars().all()
    _skill_cache = list(rows)
    # Detach from session so cache survives session close
    for s in _skill_cache:
        db.expunge(s)
    _skill_cache_ts = time.time()


def invalidate_cache() -> None:
    """Force refresh on next access (call after admin updates)."""
    global _flag_cache_ts, _skill_cache_ts
    _flag_cache_ts = 0.0
    _skill_cache_ts = 0.0


# ── Public API ────────────────────────────────────────────


def is_feature_enabled(key: str, db: Session) -> bool:
    """Check if a feature flag is enabled. Defaults to True for unknown keys."""
    _refresh_flags(db)
    return _flag_cache.get(key, True)


def get_all_flags(db: Session) -> dict[str, bool]:
    """Return all flags as {key: enabled}."""
    _refresh_flags(db)
    return dict(_flag_cache)


def get_all_flags_full(db: Session) -> Sequence[FeatureFlag]:
    """Return full FeatureFlag objects for admin."""
    return db.execute(select(FeatureFlag).order_by(FeatureFlag.key)).scalars().all()


def get_all_skills(db: Session) -> Sequence[Skill]:
    """Return all skills for admin."""
    return db.execute(select(Skill).order_by(Skill.priority)).scalars().all()


def build_skill_prompt(user_query: str, db: Session) -> str:
    """Match enabled skills against user query and build extra prompt sections.

    Skills with empty trigger_hint are always included.
    Skills with trigger_hint keywords are only included if query matches.
    Returns a combined prompt string to inject after the base system prompt.
    """
    _refresh_skills(db)
    if not _skill_cache:
        return ""

    sections: list[str] = []
    query_lower = user_query.lower()

    for skill in _skill_cache:
        if not skill.prompt_template:
            continue

        if skill.trigger_hint.strip():
            # Check if any trigger keyword appears in the query
            keywords = [k.strip() for k in skill.trigger_hint.split(",") if k.strip()]
            pattern = "|".join(re.escape(k) for k in keywords)
            if not re.search(pattern, query_lower, re.IGNORECASE):
                continue

        sections.append(f"## 技能: {skill.name}\n{skill.prompt_template}")

    if not sections:
        return ""

    return "\n\n".join(sections)
