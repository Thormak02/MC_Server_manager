import json
from typing import Any

from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from app.models.server_template import ServerTemplate


def list_templates(db: Session, server_type: str | None = None) -> list[ServerTemplate]:
    stmt = select(ServerTemplate).order_by(ServerTemplate.name.asc())
    if server_type:
        stmt = stmt.where(ServerTemplate.server_type == server_type)
    return list(db.scalars(stmt))


def get_template(db: Session, template_id: int) -> ServerTemplate | None:
    return db.get(ServerTemplate, template_id)


def _validate_properties(raw: str | None) -> str | None:
    if not raw:
        return None
    stripped = raw.strip()
    if not stripped:
        return None
    try:
        json.loads(stripped)
    except Exception as exc:  # pragma: no cover
        raise ValueError("default_properties_json ist kein gueltiges JSON.") from exc
    return stripped


def _unset_default_for_type(db: Session, server_type: str, template_id: int | None = None) -> None:
    stmt = update(ServerTemplate).where(ServerTemplate.server_type == server_type)
    if template_id is not None:
        stmt = stmt.where(ServerTemplate.id != template_id)
    db.execute(stmt.values(is_default=False))


def create_template(db: Session, payload: dict[str, Any]) -> ServerTemplate:
    default_properties_json = _validate_properties(payload.get("default_properties_json"))
    template = ServerTemplate(
        name=payload["name"],
        server_type=payload["server_type"],
        mc_version=payload["mc_version"],
        loader_version=payload.get("loader_version") or None,
        java_profile_id=payload.get("java_profile_id"),
        memory_min_mb=payload.get("memory_min_mb"),
        memory_max_mb=payload.get("memory_max_mb"),
        port_min=payload.get("port_min"),
        port_max=payload.get("port_max"),
        start_parameters=payload.get("start_parameters") or None,
        default_properties_json=default_properties_json,
        is_default=bool(payload.get("is_default")),
    )

    if template.is_default:
        _unset_default_for_type(db, template.server_type)

    db.add(template)
    db.commit()
    db.refresh(template)
    return template


def update_template(db: Session, template: ServerTemplate, payload: dict[str, Any]) -> ServerTemplate:
    default_properties_json = _validate_properties(payload.get("default_properties_json"))

    template.name = payload["name"]
    template.server_type = payload["server_type"]
    template.mc_version = payload["mc_version"]
    template.loader_version = payload.get("loader_version") or None
    template.java_profile_id = payload.get("java_profile_id")
    template.memory_min_mb = payload.get("memory_min_mb")
    template.memory_max_mb = payload.get("memory_max_mb")
    template.port_min = payload.get("port_min")
    template.port_max = payload.get("port_max")
    template.start_parameters = payload.get("start_parameters") or None
    template.default_properties_json = default_properties_json
    template.is_default = bool(payload.get("is_default"))

    if template.is_default:
        _unset_default_for_type(db, template.server_type, template.id)

    db.add(template)
    db.commit()
    db.refresh(template)
    return template


def delete_template(db: Session, template: ServerTemplate) -> None:
    db.execute(delete(ServerTemplate).where(ServerTemplate.id == template.id))
    db.commit()


def get_default_template(db: Session, server_type: str) -> ServerTemplate | None:
    stmt = select(ServerTemplate).where(
        ServerTemplate.server_type == server_type,
        ServerTemplate.is_default.is_(True),
    )
    return db.scalar(stmt)
