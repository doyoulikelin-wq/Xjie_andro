from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class DeviceToken(Base):
    """APNs device token registered by an iOS client."""

    __tablename__ = "device_tokens"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("user_account.id"), nullable=False, index=True
    )
    token: Mapped[str] = mapped_column(String(512), nullable=False, unique=True)
    # platform: "ios" | "android"
    platform: Mapped[str] = mapped_column(String(16), nullable=False, default="ios")
    # provider: APNs="apns"; Android: "fcm"|"hms"|"mipush"|"oppo"|"vivo"|"meizu"
    provider: Mapped[str | None] = mapped_column(String(16), nullable=True)
    # Optional Android per-vendor app id / region (e.g. HMS region, Mi app id)
    extras: Mapped[str | None] = mapped_column(String(255), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_at: Mapped[DateTime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[DateTime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
