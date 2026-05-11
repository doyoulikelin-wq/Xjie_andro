"""Device token registration and push notification endpoints."""

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.deps import get_current_user_id, get_db
from app.models.device_token import DeviceToken

router = APIRouter()


class RegisterTokenRequest(BaseModel):
    token: str
    platform: str = "ios"  # "ios" | "android"
    provider: str | None = None  # apns | fcm | hms | mipush | oppo | vivo | meizu
    extras: str | None = None


class RegisterTokenResponse(BaseModel):
    ok: bool


@router.post("/device-token", response_model=RegisterTokenResponse)
def register_device_token(
    payload: RegisterTokenRequest,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    """Register or update a device token for push notifications (iOS APNs / Android multi-vendor)."""
    uid = int(user_id)

    # Default provider when not provided (legacy iOS clients)
    provider = payload.provider or ("apns" if payload.platform == "ios" else None)

    # Check if this token already exists
    existing = db.scalars(
        select(DeviceToken).where(DeviceToken.token == payload.token)
    ).first()

    if existing:
        # Re-assign to current user if needed, and ensure active
        existing.user_id = uid
        existing.is_active = True
        existing.platform = payload.platform
        existing.provider = provider
        existing.extras = payload.extras
        db.add(existing)
    else:
        dt = DeviceToken(
            user_id=uid,
            token=payload.token,
            platform=payload.platform,
            provider=provider,
            extras=payload.extras,
        )
        db.add(dt)

    db.commit()
    return RegisterTokenResponse(ok=True)


@router.delete("/device-token", response_model=RegisterTokenResponse)
def unregister_device_token(
    token: str,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    """Deactivate a device token (e.g. on logout)."""
    existing = db.scalars(
        select(DeviceToken).where(
            DeviceToken.token == token,
            DeviceToken.user_id == int(user_id),
        )
    ).first()

    if existing:
        existing.is_active = False
        db.add(existing)
        db.commit()

    return RegisterTokenResponse(ok=True)
