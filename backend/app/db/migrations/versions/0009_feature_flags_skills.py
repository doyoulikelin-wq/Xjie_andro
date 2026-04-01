"""Add feature_flags and skills tables.

Revision ID: 0009_feature_flags_skills
Revises: 0008_summary_tasks
"""

import sqlalchemy as sa
from alembic import op

revision = "0009_feature_flags_skills"
down_revision = "0008_summary_tasks"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "feature_flags",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column("key", sa.String(64), unique=True, nullable=False, index=True),
        sa.Column("enabled", sa.Boolean, nullable=False, default=True),
        sa.Column("description", sa.String(256), nullable=False, server_default=""),
        sa.Column("rollout_pct", sa.Integer, nullable=False, server_default="100"),
        sa.Column("metadata_json", sa.JSON, nullable=False, server_default="{}"),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_table(
        "skills",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column("key", sa.String(64), unique=True, nullable=False, index=True),
        sa.Column("name", sa.String(128), nullable=False),
        sa.Column("description", sa.String(512), nullable=False, server_default=""),
        sa.Column("enabled", sa.Boolean, nullable=False, default=True),
        sa.Column("priority", sa.Integer, nullable=False, server_default="100"),
        sa.Column("trigger_hint", sa.Text, nullable=False, server_default=""),
        sa.Column("prompt_template", sa.Text, nullable=False, server_default=""),
        sa.Column("metadata_json", sa.JSON, nullable=False, server_default="{}"),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )

    # Seed default feature flags
    op.execute("""
        INSERT INTO feature_flags (key, enabled, description, rollout_pct) VALUES
        ('ai_chat', true, 'AI 对话功能', 100),
        ('health_summary', true, '健康报告 AI 摘要生成', 100),
        ('meal_vision', true, '膳食图像识别', 100),
        ('omics_analysis', true, '组学数据分析', 100),
        ('agent_proactive', true, 'Agent 主动消息推送', 100),
        ('indicator_trend', true, '指标趋势图', 100)
    """)

    # Seed default skills
    op.execute("""
        INSERT INTO skills (key, name, description, enabled, priority, trigger_hint, prompt_template) VALUES
        ('glucose_analysis', '血糖分析', '分析用户血糖数据，提供趋势解读和控糖建议', true, 10,
         '血糖,TIR,低血糖,高血糖,变异性,波动,控糖',
         '你擅长血糖数据分析。当用户的血糖数据可用时，务必引用具体数值（均值、TIR、变异性），给出个性化的控糖建议，指出问题时段和可能原因。'),

        ('diet_advice', '饮食建议', '基于用户饮食记录和血糖响应提供营养指导', true, 20,
         '饮食,吃,喝,热量,卡路里,碳水,蛋白质,脂肪,营养,食物,餐',
         '你擅长营养学分析。当用户询问饮食相关问题时，结合他们的餐食记录和血糖响应数据，给出具体的食物选择和份量建议。优先推荐低GI、高纤维的替代方案。'),

        ('exam_report', '体检报告解读', '解读体检报告中的异常指标，评估健康风险', true, 30,
         '体检,报告,检查,化验,指标,异常,偏高,偏低,肝功能,肾功能,血脂,胆固醇,甘油三酯,尿酸',
         '你擅长解读体检报告。当用户有体检数据时，重点分析异常指标，解释其临床意义，评估代谢风险（脂肪肝、糖尿病前期等），并给出改善建议和复查时间建议。'),

        ('omics_interpret', '组学报告解读', '解读代谢组学、蛋白组学等组学分析结果', true, 40,
         '组学,代谢组,蛋白组,基因,风险,生物标志物',
         '你擅长解读组学报告。当用户有代谢组学或蛋白组学数据时，用通俗易懂的语言解释生物标志物的含义、风险评估结果，并结合其他健康数据给出综合建议。'),

        ('fatty_liver', '脂肪肝风险评估', '评估脂肪肝风险并提供干预建议', true, 50,
         '脂肪肝,肝,转氨酶,ALT,AST,GGT,肝功能',
         '你擅长脂肪肝风险评估。结合用户的肝功能指标（ALT、AST、GGT）、BMI、腰围、血脂等数据，评估脂肪肝严重程度和进展风险，给出饮食运动干预方案。'),

        ('general_health', '日常健康咨询', '回答头疼、失眠等常见健康问题，引导到代谢健康角度', true, 90,
         '',
         '对于日常健康问题（头疼、失眠、疲劳等），先直接回答用户的问题，然后自然地引导到代谢健康的角度，比如这些症状可能与血糖波动、饮食习惯的关系。')
    """)


def downgrade() -> None:
    op.drop_table("skills")
    op.drop_table("feature_flags")
