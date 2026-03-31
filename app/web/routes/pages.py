from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from app.core.constants import ROLE_LABELS, UserRole


router = APIRouter(include_in_schema=False)

templates = Jinja2Templates(
    directory=str(Path(__file__).resolve().parents[1] / "templates")
)


def push_flash(request: Request, message: str, kind: str = "info") -> None:
    request.session["_flash"] = {"message": message, "kind": kind}


def pop_flash(request: Request) -> dict[str, str] | None:
    return request.session.pop("_flash", None)


def build_context(
    request: Request,
    *,
    current_user: object | None = None,
    **extra: object,
) -> dict[str, object]:
    context: dict[str, object] = {
        "request": request,
        "current_user": current_user,
        "flash": pop_flash(request),
        "role_labels": ROLE_LABELS,
        "all_roles": [role.value for role in UserRole],
    }
    context.update(extra)
    return context


@router.get("/")
def home(request: Request) -> RedirectResponse:
    if request.session.get("user_id"):
        return RedirectResponse(url="/dashboard", status_code=303)
    return RedirectResponse(url="/login", status_code=303)
