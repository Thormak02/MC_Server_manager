from pathlib import Path

from fastapi import APIRouter, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from app.core.constants import ROLE_LABELS, UserRole


router = APIRouter(include_in_schema=False)

templates = Jinja2Templates(
    directory=str(Path(__file__).resolve().parents[1] / "templates")
)


def _server_status_view(server: object) -> dict[str, str]:
    from app.services.server_service import server_status_view

    return server_status_view(server)


def _sleep_delay_parts(seconds: object) -> dict[str, object]:
    from app.services.server_service import split_sleep_delay_seconds

    return split_sleep_delay_seconds(seconds)


# In Templates verfuegbar: {{ server_status_view(server).color }} etc.
templates.env.globals["server_status_view"] = _server_status_view
templates.env.globals["sleep_delay_parts"] = _sleep_delay_parts


def _avatar_schluessel(wert: object, ersatz: object = None) -> str | None:
    """Fuer das Kopf-Icon: brauchbarer Schluessel - sonst None (= kein Icon).

    Reihenfolge: eine ECHTE Mojang-UUID (Version 4) ist am genauesten, weil sie
    eine Umbenennung ueberlebt. Eine Offline-Mode-UUID (Version 3, aus dem Namen
    gehasht) kennt Mojang dagegen nicht - mit ihr zu fragen liefert garantiert
    nichts, also nehmen wir dann den Namen. Genau dieser Fall liess denselben
    Spieler in der Whitelist anders aussehen als in der OP-Liste.

    Verworfen wird alles, was gar kein Spieler sein kann. Die Online-Liste kommt
    aus dem Konsolen-Log, und die Join-Regex ist gierig: schreibt jemand
    "xy joined the game" in den CHAT, landet "<Name> xy" als vermeintlicher
    Spieler in der Liste. Auch Plugin-Praefixe ("[Essentials] Name"), Farbcodes
    und IP-Banns kommen so an - fuer die soll gar kein Kopf erscheinen.
    """
    from app.services.avatar_service import ist_online_uuid, normalisiere

    haupt = normalisiere(str(wert or ""))
    zweit = normalisiere(str(ersatz or ""))
    if haupt and len(haupt) == 32 and not ist_online_uuid(haupt):
        return zweit or haupt
    return haupt or zweit


templates.env.globals["avatar_schluessel"] = _avatar_schluessel


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
