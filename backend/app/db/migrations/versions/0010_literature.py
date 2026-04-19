"""literature citation database

Revision ID: 0010_literature
Revises: 0009_feature_flags_skills
Create Date: 2026-04-19
"""
from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0010_literature"
down_revision = "0009_feature_flags_skills"
branch_labels = None
depends_on = None


def _string_array() -> sa.types.TypeEngine:
    """ARRAY(String) on PG, TEXT on SQLite — matches app.db.compat.StringArray."""
    bind = op.get_bind()
    if bind.dialect.name == "postgresql":
        from sqlalchemy.dialects.postgresql import ARRAY

        return ARRAY(sa.String())
    return sa.Text()


def upgrade() -> None:
    arr = _string_array

    op.create_table(
        "literature",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("pmid", sa.String(32), unique=True, nullable=True),
        sa.Column("doi", sa.String(255), nullable=True),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("authors", arr(), nullable=False),
        sa.Column("journal", sa.String(255), nullable=True),
        sa.Column("year", sa.Integer(), nullable=True),
        sa.Column("language", sa.String(8), nullable=False, server_default="en"),
        sa.Column("evidence_level", sa.String(4), nullable=False),
        sa.Column("study_design", sa.String(64), nullable=True),
        sa.Column("sample_size", sa.Integer(), nullable=True),
        sa.Column("population", sa.Text(), nullable=True),
        sa.Column("conclusion_zh", sa.Text(), nullable=False),
        sa.Column("conclusion_en", sa.Text(), nullable=True),
        sa.Column("topics", arr(), nullable=False),
        sa.Column("source", sa.String(32), nullable=False, server_default="pubmed"),
        sa.Column("fetched_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("reviewed", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("reviewer", sa.String(64), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_literature_pmid", "literature", ["pmid"], unique=True)
    op.create_index("ix_literature_doi", "literature", ["doi"])
    op.create_index("ix_literature_year", "literature", ["year"])
    op.create_index("ix_literature_evidence_level", "literature", ["evidence_level"])
    op.create_index("ix_literature_year_level", "literature", ["year", "evidence_level"])

    op.create_table(
        "literature_claim",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "literature_id",
            sa.BigInteger(),
            sa.ForeignKey("literature.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("claim_text", sa.Text(), nullable=False),
        sa.Column("claim_text_en", sa.Text(), nullable=True),
        sa.Column("exposure", sa.String(255), nullable=False),
        sa.Column("outcome", sa.String(255), nullable=False),
        sa.Column("effect_size", sa.String(255), nullable=True),
        sa.Column("direction", sa.String(16), nullable=True),
        sa.Column("population_summary", sa.Text(), nullable=True),
        sa.Column("confidence", sa.String(8), nullable=False, server_default="medium"),
        sa.Column("topics", arr(), nullable=False),
        sa.Column("tags", arr(), nullable=False),
        sa.Column("evidence_level", sa.String(4), nullable=False),
        sa.Column("embedding", sa.JSON(), nullable=True),
        sa.Column("embedding_model", sa.String(64), nullable=True),
        sa.Column("enabled", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_claim_literature_id", "literature_claim", ["literature_id"])
    op.create_index("ix_claim_exposure", "literature_claim", ["exposure"])
    op.create_index("ix_claim_outcome", "literature_claim", ["outcome"])
    op.create_index("ix_claim_evidence_level", "literature_claim", ["evidence_level"])
    op.create_index("ix_claim_topic_level", "literature_claim", ["evidence_level", "enabled"])

    op.create_table(
        "literature_claim_concept",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "claim_id",
            sa.BigInteger(),
            sa.ForeignKey("literature_claim.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("role", sa.String(16), nullable=False),
        sa.Column("concept_key", sa.String(128), nullable=False),
        sa.Column("concept_label", sa.String(255), nullable=True),
        sa.UniqueConstraint("claim_id", "role", "concept_key", name="uq_claim_concept"),
    )
    op.create_index("ix_concept_claim_id", "literature_claim_concept", ["claim_id"])
    op.create_index("ix_concept_key", "literature_claim_concept", ["concept_key"])

    op.create_table(
        "literature_ingest_job",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("query", sa.Text(), nullable=False),
        sa.Column("topic", sa.String(32), nullable=False),
        sa.Column("status", sa.String(16), nullable=False, server_default="pending"),
        sa.Column("fetched_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("inserted_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("skipped_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("error", sa.Text(), nullable=True),
        sa.Column("meta", sa.JSON(), nullable=False, server_default="{}"),
        sa.Column("started_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("literature_ingest_job")
    op.drop_table("literature_claim_concept")
    op.drop_index("ix_claim_topic_level", table_name="literature_claim")
    op.drop_index("ix_claim_evidence_level", table_name="literature_claim")
    op.drop_index("ix_claim_outcome", table_name="literature_claim")
    op.drop_index("ix_claim_exposure", table_name="literature_claim")
    op.drop_index("ix_claim_literature_id", table_name="literature_claim")
    op.drop_table("literature_claim")
    op.drop_index("ix_literature_year_level", table_name="literature")
    op.drop_index("ix_literature_evidence_level", table_name="literature")
    op.drop_index("ix_literature_year", table_name="literature")
    op.drop_index("ix_literature_doi", table_name="literature")
    op.drop_index("ix_literature_pmid", table_name="literature")
    op.drop_table("literature")
