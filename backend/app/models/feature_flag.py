"""Feature flags and AI skill definitions."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base
from app.db.compat import JSONB


class FeatureFlag(Base):
    """Global feature toggle.

    key examples: 'health_summary', 'meal_vision', 'omics_analysis', 'agent_proactive'
    """

    __tablename__ = "feature_flags"

    id: Mapped[int] = mapped_column(primary_key=True)
    key: Mapped[str] = mapped_column(String(64), unique=True, nullable=False, index=True)
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    description: Mapped[str] = mapped_column(String(256), nullable=False, default="")
    rollout_pct: Mapped[int] = mapped_column(Integer, nullable=False, default=100)  # 0-100 灰度百分比
    metadata_json: Mapped[dict] = mapped_column(JSONB, default=dict, nullable=False)  # 额外配置
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class Skill(Base):
    """AI skill / plugin definition.

    Each skill injects a section into the system prompt when enabled.
    Skills are ordered by `priority` (lower = injected first).
    """

    __tablename__ = "skills"

    id: Mapped[int] = mapped_column(primary_key=True)
    key: Mapped[str] = mapped_column(String(64), unique=True, nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(String(512), nullable=False, default="")
    enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    priority: Mapped[int] = mapped_column(Integer, nullable=False, default=100)  # 越小越先注入
    trigger_hint: Mapped[str] = mapped_column(
        Text, nullable=False, default=""
    )  # 触发关键词，逗号分隔，空=始终注入
    prompt_template: Mapped[str] = mapped_column(Text, nullable=False, default="")  # 注入到 system prompt 的内容
    metadata_json: Mapped[dict] = mapped_column(JSONB, default=dict, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
