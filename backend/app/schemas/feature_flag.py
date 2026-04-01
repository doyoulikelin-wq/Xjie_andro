"""Feature flag and Skill schemas."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel


# ── Feature Flags ─────────────────────────────────────────

class FeatureFlagOut(BaseModel):
    id: int
    key: str
    enabled: bool
    description: str = ""
    rollout_pct: int = 100
    updated_at: datetime | None = None


class FeatureFlagUpdate(BaseModel):
    enabled: bool | None = None
    description: str | None = None
    rollout_pct: int | None = None


class FeatureFlagCreate(BaseModel):
    key: str
    enabled: bool = True
    description: str = ""
    rollout_pct: int = 100


class FeatureFlagListOut(BaseModel):
    flags: list[FeatureFlagOut]


# Client-side simplified response: {key: enabled}
class FeatureFlagClientOut(BaseModel):
    flags: dict[str, bool]


# ── Skills ────────────────────────────────────────────────

class SkillOut(BaseModel):
    id: int
    key: str
    name: str
    description: str = ""
    enabled: bool = True
    priority: int = 100
    trigger_hint: str = ""
    prompt_template: str = ""
    updated_at: datetime | None = None


class SkillUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    enabled: bool | None = None
    priority: int | None = None
    trigger_hint: str | None = None
    prompt_template: str | None = None


class SkillCreate(BaseModel):
    key: str
    name: str
    description: str = ""
    enabled: bool = True
    priority: int = 100
    trigger_hint: str = ""
    prompt_template: str = ""


class SkillListOut(BaseModel):
    skills: list[SkillOut]
