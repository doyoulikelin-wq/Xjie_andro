from __future__ import annotations

import hashlib
import hmac
from datetime import datetime, timezone
from typing import Any
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.cgm_integration import CGMDeviceBinding
from app.models.glucose import GlucoseReading
from app.schemas.cgm import CGMPatientDataIn


def parse_cgm_payload(payload: Any) -> list[CGMPatientDataIn]:
    """Normalize external payload to a list of patient CGM data blocks."""
    patients: list[dict] | None = None
    if isinstance(payload, list):
        patients = payload
    elif isinstance(payload, dict):
        if isinstance(payload.get("data"), list):
            patients = payload["data"]
        elif isinstance(payload.get("patients"), list):
            patients = payload["patients"]
        elif isinstance(payload.get("list"), list):
            patients = payload["list"]
        elif isinstance(payload.get("recordList"), list):
            patients = [payload]

    if patients is None:
        raise ValueError("Unsupported payload shape. Expect list or {data|patients|list:[...]} wrapper.")

    result: list[CGMPatientDataIn] = []
    for row in patients:
        result.append(CGMPatientDataIn.model_validate(row))
    return result


def verify_signature(
    *,
    raw_body: bytes,
    secret: str | None,
    timestamp: str | None,
    signature: str | None,
    allow_unsigned: bool,
) -> bool:
    """Verify HMAC SHA-256 signature.

    Signature spec for this project:
    `hex(hmac_sha256(secret, f"{timestamp}.{raw_body_utf8}"))`.
    Header accepts either `<hex>` or `sha256=<hex>`.
    """
    if not secret:
        return allow_unsigned

    if not timestamp or not signature:
        return False

    received = signature.strip().lower()
    if received.startswith("sha256="):
        received = received.removeprefix("sha256=")

    mac = hmac.new(secret.encode("utf-8"), timestamp.encode("utf-8") + b"." + raw_body, hashlib.sha256)
    expected = mac.hexdigest().lower()
    return hmac.compare_digest(expected, received)


def parse_device_time(device_time: str, device_timezone: str) -> datetime:
    """Parse device local time to UTC."""
    text = device_time.strip()
    dt: datetime
    try:
        dt = datetime.fromisoformat(text.replace(" ", "T"))
    except ValueError:
        dt = datetime.strptime(text, "%Y-%m-%d %H:%M:%S")

    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=ZoneInfo(device_timezone))
    return dt.astimezone(timezone.utc)


def _resolve_binding_user_id(db: Session, provider: str, row: CGMPatientDataIn):
    keys = [
        ("device_id", row.deviceId),
        ("device_sn", row.deviceSn),
        ("phone", row.phone),
    ]
    for field_name, value in keys:
        if not value:
            continue
        stmt = (
            select(CGMDeviceBinding)
            .where(
                CGMDeviceBinding.provider == provider,
                CGMDeviceBinding.is_active.is_(True),
                getattr(CGMDeviceBinding, field_name) == value,
            )
            .limit(1)
        )
        binding = db.execute(stmt).scalars().first()
        if binding:
            return binding.user_id
    return None


def ingest_cgm_records(
    db: Session,
    *,
    provider: str,
    source_name: str,
    device_timezone: str,
    patients: list[CGMPatientDataIn],
) -> dict:
    inserted_points = 0
    skipped_points = 0
    unknown_bindings = 0
    errors: list[dict] = []

    for i, patient in enumerate(patients):
        user_id = _resolve_binding_user_id(db, provider, patient)
        if not user_id:
            unknown_bindings += 1
            errors.append(
                {
                    "patient_index": i,
                    "error": "BINDING_NOT_FOUND",
                    "deviceId": patient.deviceId,
                    "deviceSn": patient.deviceSn,
                    "phone": patient.phone,
                }
            )
            continue

        for j, record in enumerate(patient.recordList):
            try:
                ts = parse_device_time(record.deviceTime, device_timezone=device_timezone)
                glucose = int(round(float(record.eventData)))
            except Exception as exc:  # noqa: BLE001
                skipped_points += 1
                errors.append(
                    {
                        "patient_index": i,
                        "record_index": j,
                        "error": "PARSE_ERROR",
                        "detail": str(exc),
                    }
                )
                continue

            if glucose < 20 or glucose > 600:
                skipped_points += 1
                errors.append(
                    {
                        "patient_index": i,
                        "record_index": j,
                        "error": "OUT_OF_RANGE",
                        "glucose": glucose,
                    }
                )
                continue

            duplicate = db.execute(
                select(GlucoseReading.id).where(
                    GlucoseReading.user_id == user_id,
                    GlucoseReading.ts == ts,
                    GlucoseReading.source == source_name,
                )
            ).first()
            if duplicate:
                skipped_points += 1
                continue

            db.add(
                GlucoseReading(
                    user_id=user_id,
                    ts=ts,
                    glucose_mgdl=glucose,
                    source=source_name,
                    meta={
                        "provider": provider,
                        "device_id": patient.deviceId,
                        "device_sn": patient.deviceSn,
                        "time_offset": record.timeOffset,
                    },
                )
            )
            inserted_points += 1

            # Trigger anomaly push check for extreme values
            if glucose > 180 or glucose < 70:
                try:
                    from app.workers.push_tasks import check_glucose_anomaly
                    check_glucose_anomaly.delay(user_id, float(glucose), ts.isoformat())
                except Exception:
                    pass  # Push is best-effort, don't block ingestion

    db.commit()
    return {
        "provider": provider,
        "received_patients": len(patients),
        "inserted_points": inserted_points,
        "skipped_points": skipped_points,
        "unknown_bindings": unknown_bindings,
        "errors": errors,
    }
