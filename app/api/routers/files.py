from pathlib import Path
from typing import Annotated
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile, status
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import (
    ai_config_service,
    ai_provider_service,
    ai_service,
    audit_service,
)
from app.services.auth_service import get_current_user_from_session
from app.services.file_service import (
    add_access_entry,
    build_content_from_assistant,
    create_directory,
    create_text_file,
    delete_path,
    get_access_schema_key_label,
    get_assistant_payload,
    assistent_hinweis,
    get_download_file,
    get_whitelist_enabled,
    list_files,
    liste_mit_metadaten,
    SORTIERUNGEN,
    zaehle_oeffnung,
    list_access_entries,
    read_text_file,
    remove_access_entry,
    set_whitelist_enabled,
    update_op_level,
    upload_file as upload_server_file,
    write_text_file,
)
from app.services.process_service import send_console_command
from app.services.server_service import can_edit_server_files, can_view_server, get_server_by_id
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


def _normalize_access_tab(value: str | None) -> str:
    allowed = {"whitelist", "ops", "banned_players", "banned_ips"}
    normalized = (value or "").strip().lower()
    if normalized in allowed:
        return normalized
    return "whitelist"


def _zugang_erlaubt(request: Request, db: Session, current_user, server,
                    *, befehl: str | None, tab: str) -> bool:
    """Darf dieser Nutzer diese Zugangsaenderung SCHREIBEN?

    Die Zugriffsseite schreibt ops.json, banned-players.json, banned-ips.json und
    server.properties - und das ist der DAUERHAFTE Weg: genau diese Dateien liest
    Minecraft beim Start. Die Admin-Sperre fuer op/deop/pardon/whitelist off galt
    zunaechst nur fuer den fluechtigen Konsolenbefehl; ueber die Datei war sie mit
    ``can_edit_files`` umgehbar - ein Moderator konnte sich Operator mit Level 4
    eintragen, Banns aufheben und die Whitelist abschalten, wirksam nach dem naechsten
    Neustart.

    Geprueft wird deshalb VOR dem Schreiben, und zwar mit derselben Regel wie fuer die
    Konsole (``process_service.braucht_admin``) - eine zweite, eigene Liste wuerde
    irgendwann auseinanderlaufen.
    """
    from app.core.constants import UserRole
    from app.services.process_service import braucht_admin

    grund = braucht_admin(befehl or "")
    if not grund:
        return True
    if (getattr(current_user, "role", "") or "") in {UserRole.SUPER_ADMIN.value,
                                                     UserRole.ADMIN.value}:
        return True

    audit_service.log_action(
        db, action="server.access_change_denied", user_id=current_user.id,
        server_id=server.id, details=f"list={tab} befehl={befehl}")
    push_flash(request, f"Dafuer braucht es Admin-Rechte: {grund}", "error")
    return False


def _ziel_pfad(ordner: str | None, dateiname: str) -> str:
    """Wohin der Upload gehen wird - ohne die Datei anzufassen."""
    teil = (ordner or ".").replace("\\", "/").strip().strip("/")
    while teil.startswith("./"):
        teil = teil[2:]
    name = Path((dateiname or "").replace("\\", "/")).name
    return f"{teil}/{name}" if teil and teil != "." else name


def _als_text(rohdaten: bytes) -> str:
    """Der hochgeladene Inhalt als Text - notfalls mit Ersatzzeichen.

    Eine server.properties in einer anderen Kodierung soll die Whitelist-Pruefung nicht
    umgehen koennen; die gesuchte Zeile ist in jeder ASCII-nahen Kodierung lesbar.
    """
    try:
        return rohdaten.decode("utf-8", errors="replace")
    except AttributeError:
        return ""


def _dauerhafte_sperre(db: Session, current_user, server, pfad: str,
                       *, neuer_inhalt: str | None = None) -> str:
    """Braucht diese Dateiaenderung Admin-Rechte? Rueckgabe: der Grund, sonst "".

    Der Dateieditor war die dritte Tuer zu derselben Tat. Die Zugriffsseite prueft seit
    der letzten Runde, die Konsole und der Zeitplan auch - aber wer ops.json einfach im
    Editor oeffnete und sich mit Level 4 eintrug, kam an allem vorbei. Dasselbe fuer
    banned-players.json (Bann weg) und fuer white-list=false in server.properties.
    Geprueft wird VOR dem Schreiben und mit derselben Regel wie ueberall sonst.
    """
    from app.core.constants import UserRole
    from app.services.process_service import datei_braucht_admin, whitelist_wird_abgeschaltet

    grund = datei_braucht_admin(pfad)
    ist_properties = Path((pfad or "").replace("\\", "/")).name.lower() == "server.properties"
    if not grund and neuer_inhalt is not None and ist_properties:
        try:
            alt = read_text_file(server, pfad).content
        except ValueError:
            alt = ""   # gibt es noch nicht - dann war auch keine Whitelist an
        grund = whitelist_wird_abgeschaltet(pfad, alt, neuer_inhalt)
    if not grund:
        return ""
    if (getattr(current_user, "role", "") or "") in {UserRole.SUPER_ADMIN.value,
                                                     UserRole.ADMIN.value}:
        return ""
    audit_service.log_action(
        db, action="server.access_change_denied", user_id=current_user.id,
        server_id=server.id, details=f"datei={pfad}")
    return grund


def _datei_sperre_flash(request: Request, db: Session, current_user, server, pfad: str,
                        *, neuer_inhalt: str | None = None) -> bool:
    """Wie oben, nur mit Meldung fuer die HTML-Routen. True = gesperrt."""
    grund = _dauerhafte_sperre(db, current_user, server, pfad, neuer_inhalt=neuer_inhalt)
    if not grund:
        return False
    push_flash(request, f"Dafuer braucht es Admin-Rechte: {grund}", "error")
    return True


def _build_access_runtime_command(*, list_key: str, identity: str, action: str) -> str | None:
    normalized_identity = (identity or "").strip()
    if not normalized_identity:
        return None

    normalized_key = (list_key or "").strip().lower()
    normalized_action = (action or "").strip().lower()
    if normalized_action not in {"add", "remove"}:
        return None

    if normalized_key == "whitelist":
        return f"whitelist {'add' if normalized_action == 'add' else 'remove'} {normalized_identity}"
    if normalized_key == "ops":
        return f"{'op' if normalized_action == 'add' else 'deop'} {normalized_identity}"
    if normalized_key == "banned_players":
        return f"{'ban' if normalized_action == 'add' else 'pardon'} {normalized_identity}"
    if normalized_key == "banned_ips":
        return f"{'ban-ip' if normalized_action == 'add' else 'pardon-ip'} {normalized_identity}"
    return None


def _dateien_url(server_id: int, *, datei: str | None = None, modus: str | None = None,
                 sort: str | None = None, richtung: str | None = None,
                 suche: str | None = None) -> str:
    """Der Weg zurueck zur Dateiseite - MIT dem Zustand der Liste.

    Jede Ruecksprungstelle hatte ihre eigene URL zusammengebaut und dabei Sortierung,
    Richtung und Suche weggelassen. Wer nach Groesse sortiert hatte und zwischen
    Assistent und Freitext wechselte, sass danach wieder in der Standardsortierung.
    Nebenbei wird der Dateipfad hier richtig kodiert; vorher landete ein Pfad mit
    Leerzeichen roh im Location-Kopf.
    """
    paare = [(name, wert) for name, wert in (("file", datei), ("mode", modus),
                                             ("sort", sort), ("dir", richtung),
                                             ("q", suche)) if wert]
    basis = f"/servers/{server_id}/files"
    return f"{basis}?{urlencode(paare)}" if paare else basis


def _try_send_runtime_command(
    request: Request,
    *,
    db: Session,
    server,
    command: str | None,
    user_id: int,
) -> str:
    """Den Befehl auch am laufenden Server anwenden. Rueckgabe: eine Notiz, falls nicht.

    Die Notiz wird ZURUECKGEGEBEN und nicht geblitzt: ``push_flash`` haelt nur EINE
    Meldung in der Sitzung, und die Erfolgsmeldung danach hat sie ueberschrieben. Der
    Bedienende sah "Eintrag hinzugefuegt." und erfuhr nicht, dass der laufende Server
    davon nichts weiss - Datei und Server sagten Verschiedenes.
    """
    if not command:
        return ""
    ok, message = send_console_command(db, server, command, user_id)
    if ok:
        return ""
    return f" Am laufenden Server nicht angewandt: {message}"


@router.get("/servers/{server_id}/files", response_class=HTMLResponse)
def files_page(
    request: Request,
    server_id: int,
    file: str | None = None,
    mode: str | None = None,
    sort: str | None = None,
    dir: str | None = None,
    q: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    dateiliste = liste_mit_metadaten(server, db, sortierung=sort, richtung=dir,
                                     suche=q or "")
    available_files = [eintrag["pfad"] for eintrag in dateiliste["dateien"]]
    selected_file = (file or "").strip() or None
    file_content = None
    assistant_payload = None
    assistant_hinweis = ""
    selected_mode = (mode or "assistant").strip().lower()
    if selected_mode not in {"assistant", "raw"}:
        selected_mode = "assistant"

    if selected_file:
        try:
            file_content = read_text_file(server, selected_file)
            # Erst nach dem erfolgreichen Lesen zaehlen - ein Fehlversuch ist keine Nutzung.
            zaehle_oeffnung(db, server, selected_file)
            assistant_payload = get_assistant_payload(selected_file, file_content.content)
            if assistant_payload is None:
                # Frueher wurde hier stillschweigend auf Freitext umgeschaltet - der Knopf
                # "Assistent" sah damit schlicht kaputt aus.
                assistant_hinweis = assistent_hinweis(selected_file, file_content.content)
            if assistant_payload is None and selected_mode == "assistant":
                selected_mode = "raw"
        except ValueError as exc:
            push_flash(request, str(exc), "error")
            selected_file = None

    return templates.TemplateResponse(
        request,
        "files.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Dateien: {server.name}",
            server=server,
            available_files=available_files,
            dateiliste=dateiliste,
            sortierungen=SORTIERUNGEN,
            selected_file=selected_file,
            file_content=file_content,
            assistant_payload=assistant_payload,
            assistant_hinweis=assistant_hinweis,
            selected_mode=selected_mode,
            can_edit=can_edit_server_files(db, current_user, server),
        ),
    )


@router.get("/servers/{server_id}/access", response_class=HTMLResponse)
def access_page(
    request: Request,
    server_id: int,
    tab: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    selected_tab = _normalize_access_tab(tab)
    whitelist_entries: list[dict] = []
    ops_entries: list[dict] = []
    banned_players_entries: list[dict] = []
    banned_ips_entries: list[dict] = []
    try:
        whitelist_entries = list_access_entries(server, "whitelist")
        ops_entries = list_access_entries(server, "ops")
        banned_players_entries = list_access_entries(server, "banned_players")
        banned_ips_entries = list_access_entries(server, "banned_ips")
    except ValueError as exc:
        push_flash(request, f"Zugriffsdaten konnten nicht gelesen werden: {exc}", "error")

    return templates.TemplateResponse(
        request,
        "server_access.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"OP/Bans/Whitelist: {server.name}",
            server=server,
            can_edit=can_edit_server_files(db, current_user, server),
            selected_tab=selected_tab,
            tab_options=get_access_schema_key_label(),
            whitelist_enabled=get_whitelist_enabled(server),
            whitelist_entries=whitelist_entries,
            ops_entries=ops_entries,
            banned_players_entries=banned_players_entries,
            banned_ips_entries=banned_ips_entries,
        ),
    )


@router.post("/servers/{server_id}/access/entry-add")
def access_add_entry_action(
    request: Request,
    server_id: int,
    list_key: Annotated[str, Form()],
    identity: Annotated[str, Form()],
    op_level: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    tab = _normalize_access_tab(list_key)
    befehl = _build_access_runtime_command(list_key=tab, identity=identity, action="add")
    if not _zugang_erlaubt(request, db, current_user, server, befehl=befehl, tab=tab):
        return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}",
                                status_code=303)

    try:
        add_access_entry(server, tab, identity, op_level=op_level if tab == "ops" else None)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)

    notiz = _try_send_runtime_command(
        request, db=db, server=server, command=befehl, user_id=current_user.id)

    audit_service.log_action(
        db,
        action="server.access_entry_add",
        user_id=current_user.id,
        server_id=server.id,
        details=(
            f"list={tab} identity={identity.strip()} level={str(op_level).strip()}"
            if tab == "ops" and op_level is not None
            else f"list={tab} identity={identity.strip()}"
        ),
    )
    push_flash(request, "Eintrag hinzugefuegt." + notiz, "success")
    return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)


@router.post("/servers/{server_id}/access/op-level-update")
def access_update_op_level_action(
    request: Request,
    server_id: int,
    identity: Annotated[str, Form()],
    op_level: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    # Das OP-Level zu aendern ist dieselbe Tat wie 'op' - es bestimmt, wie viel ein
    # Operator darf (Level 4 erlaubt u.a. /stop und /op). Also dieselbe Schranke.
    if not _zugang_erlaubt(request, db, current_user, server,
                           befehl=f"op {identity.strip()}", tab="ops"):
        return RedirectResponse(url=f"/servers/{server_id}/access?tab=ops",
                                status_code=303)

    try:
        update_op_level(server, identity, op_level)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/access?tab=ops", status_code=303)

    audit_service.log_action(
        db,
        action="server.op_level_update",
        user_id=current_user.id,
        server_id=server.id,
        details=f"identity={identity.strip()} level={op_level.strip()}",
    )
    push_flash(request, "OP-Level aktualisiert.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/access?tab=ops", status_code=303)


@router.post("/servers/{server_id}/access/entry-remove")
def access_remove_entry_action(
    request: Request,
    server_id: int,
    list_key: Annotated[str, Form()],
    identity: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    tab = _normalize_access_tab(list_key)
    befehl = _build_access_runtime_command(list_key=tab, identity=identity,
                                           action="remove")
    if not _zugang_erlaubt(request, db, current_user, server, befehl=befehl, tab=tab):
        return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}",
                                status_code=303)

    try:
        remove_access_entry(server, tab, identity)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)

    notiz = _try_send_runtime_command(
        request, db=db, server=server, command=befehl, user_id=current_user.id)

    audit_service.log_action(
        db,
        action="server.access_entry_remove",
        user_id=current_user.id,
        server_id=server.id,
        details=f"list={tab} identity={identity.strip()}",
    )
    push_flash(request, "Eintrag entfernt." + notiz, "success")
    return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)


@router.post("/servers/{server_id}/access/whitelist-toggle")
def access_whitelist_toggle_action(
    request: Request,
    server_id: int,
    enabled: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    target_state = str(enabled or "").strip().lower() in {"1", "true", "on", "yes"}
    befehl = f"whitelist {'on' if target_state else 'off'}"
    # Nur das AUSSCHALTEN ist Admin-Sache (es oeffnet den Server fuer alle) - das
    # entscheidet braucht_admin, hier wird nur gefragt.
    if not _zugang_erlaubt(request, db, current_user, server, befehl=befehl,
                           tab="whitelist"):
        return RedirectResponse(url=f"/servers/{server_id}/access?tab=whitelist",
                                status_code=303)

    set_whitelist_enabled(server, target_state)
    notiz = _try_send_runtime_command(
        request, db=db, server=server, command=befehl, user_id=current_user.id)
    audit_service.log_action(
        db,
        action="server.whitelist_toggle",
        user_id=current_user.id,
        server_id=server.id,
        details=f"enabled={target_state}",
    )
    push_flash(
        request,
        ("Whitelist aktiviert." if target_state else "Whitelist deaktiviert.") + notiz,
        "success",
    )
    return RedirectResponse(url=f"/servers/{server_id}/access?tab=whitelist", status_code=303)


@router.post("/servers/{server_id}/files/save")
def save_file_action(
    request: Request,
    server_id: int,
    relative_path: Annotated[str, Form()],
    content: Annotated[str, Form()],
    liste_sort: Annotated[str | None, Form(alias="__liste_sort")] = None,
    liste_dir: Annotated[str | None, Form(alias="__liste_dir")] = None,
    liste_q: Annotated[str | None, Form(alias="__liste_q")] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    if _datei_sperre_flash(request, db, current_user, server, relative_path,
                           neuer_inhalt=content):
        return RedirectResponse(url=_dateien_url(server_id, datei=relative_path, modus="raw",
                                                 sort=liste_sort, richtung=liste_dir,
                                                 suche=liste_q), status_code=303)

    try:
        write_text_file(server, relative_path, content)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=_dateien_url(server_id, datei=relative_path, modus="raw",
                                                 sort=liste_sort, richtung=liste_dir,
                                                 suche=liste_q), status_code=303)

    audit_service.log_action(
        db,
        action="server.file_edit",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} mode=raw",
    )
    push_flash(request, f"Datei '{relative_path}' gespeichert.", "success")
    return RedirectResponse(url=_dateien_url(server_id, datei=relative_path, modus="raw",
                                             sort=liste_sort, richtung=liste_dir,
                                             suche=liste_q), status_code=303)


@router.post("/servers/{server_id}/files/assistant-save")
async def save_file_assistant_action(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    form = await request.form()
    relative_path = str(form.get("relative_path", "")).strip()
    # Der Zustand der Dateiliste gehoert nicht zu den Assistentenfeldern - sonst landet
    # "sort" als Schluessel in einer .properties.
    liste_sort = str(form.get("__liste_sort") or "") or None
    liste_dir = str(form.get("__liste_dir") or "") or None
    liste_q = str(form.get("__liste_q") or "") or None
    data = {key: str(value) for key, value in form.items()
            if key not in {"relative_path", "__liste_sort", "__liste_dir", "__liste_q"}}

    # YAML wird nicht neu erzeugt, sondern im vorhandenen Text ersetzt - dafuer braucht
    # der Assistent den Stand, der gerade auf der Platte liegt.
    try:
        aktueller_inhalt = read_text_file(server, relative_path).content
    except ValueError:
        aktueller_inhalt = None

    try:
        content = build_content_from_assistant(relative_path, data, aktueller_inhalt)
        gesperrt = _dauerhafte_sperre(db, current_user, server, relative_path,
                                      neuer_inhalt=content)
        if gesperrt:
            raise ValueError(f"Dafuer braucht es Admin-Rechte: {gesperrt}")
        write_text_file(server, relative_path, content)
    except (ValueError, TypeError, KeyError, Exception) as exc:
        push_flash(request, f"Assistent-Speichern fehlgeschlagen: {exc}", "error")
        return RedirectResponse(
            url=_dateien_url(server_id, datei=relative_path, modus="assistant",
                             sort=liste_sort, richtung=liste_dir, suche=liste_q),
            status_code=303,
        )

    audit_service.log_action(
        db,
        action="server.file_edit",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} mode=assistant",
    )
    push_flash(request, f"Datei '{relative_path}' via Assistent gespeichert.", "success")
    return RedirectResponse(
        url=_dateien_url(server_id, datei=relative_path, modus="assistant",
                         sort=liste_sort, richtung=liste_dir, suche=liste_q),
        status_code=303,
    )


@router.get("/servers/{server_id}/files/download")
def download_file_action(
    request: Request,
    server_id: int,
    path: str,
    sort: str | None = None,
    dir: str | None = None,
    q: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        target = get_download_file(server, path)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=_dateien_url(server_id, sort=sort, richtung=dir,
                                                 suche=q), status_code=303)

    audit_service.log_action(
        db,
        action="server.file_download",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={Path(path).as_posix()}",
    )
    return FileResponse(path=str(target), filename=target.name, media_type="application/octet-stream")


@router.post("/servers/{server_id}/files/upload")
async def upload_file_action(
    request: Request,
    server_id: int,
    upload: UploadFile = File(...),
    target_dir: Annotated[str, Form()] = ".",
    overwrite: Annotated[bool, Form()] = False,
    liste_sort: Annotated[str | None, Form(alias="__liste_sort")] = None,
    liste_dir: Annotated[str | None, Form(alias="__liste_dir")] = None,
    liste_q: Annotated[str | None, Form(alias="__liste_q")] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    zurueck = _dateien_url(server_id, sort=liste_sort, richtung=liste_dir, suche=liste_q)
    filename = (upload.filename or "").strip()
    if not filename:
        push_flash(request, "Bitte eine Datei auswaehlen.", "error")
        return RedirectResponse(url=zurueck, status_code=303)

    try:
        payload = await upload.read()
    except Exception as exc:  # noqa: BLE001
        push_flash(request, f"Upload fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url=zurueck, status_code=303)

    # Eine hochgeladene ops.json ueberschreibt die vorhandene - derselbe Weg wie der
    # Editor, also dieselbe Schranke. Geprueft wird vor dem Schreiben, deshalb wird der
    # Zielpfad hier vorab gebildet.
    ziel = _ziel_pfad(target_dir, filename)
    if _datei_sperre_flash(request, db, current_user, server, ziel,
                           neuer_inhalt=_als_text(payload)):
        return RedirectResponse(url=zurueck, status_code=303)

    try:
        relative_path = upload_server_file(
            server,
            target_dir=(target_dir or ".").strip() or ".",
            original_filename=filename,
            content_bytes=payload,
            overwrite=overwrite,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=zurueck, status_code=303)

    audit_service.log_action(
        db,
        action="server.file_upload",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} size={len(payload)}",
    )
    push_flash(request, f"Datei hochgeladen: {relative_path}", "success")
    return RedirectResponse(url=_dateien_url(server_id, datei=relative_path, modus="raw",
                                             sort=liste_sort, richtung=liste_dir,
                                             suche=liste_q), status_code=303)


@router.post("/servers/{server_id}/directories")
def create_directory_action(
    request: Request,
    server_id: int,
    relative_dir: Annotated[str, Form()],
    liste_sort: Annotated[str | None, Form(alias="__liste_sort")] = None,
    liste_dir: Annotated[str | None, Form(alias="__liste_dir")] = None,
    liste_q: Annotated[str | None, Form(alias="__liste_q")] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    zurueck = _dateien_url(server_id, sort=liste_sort, richtung=liste_dir, suche=liste_q)
    try:
        created = create_directory(server, relative_dir)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=zurueck, status_code=303)

    audit_service.log_action(
        db,
        action="server.directory_create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={created}",
    )
    push_flash(request, f"Ordner erstellt: {created}", "success")
    return RedirectResponse(url=zurueck, status_code=303)


@router.post("/servers/{server_id}/files/create-text")
def create_text_file_action(
    request: Request,
    server_id: int,
    relative_path: Annotated[str, Form()],
    initial_content: Annotated[str | None, Form()] = None,
    liste_sort: Annotated[str | None, Form(alias="__liste_sort")] = None,
    liste_dir: Annotated[str | None, Form(alias="__liste_dir")] = None,
    liste_q: Annotated[str | None, Form(alias="__liste_q")] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    if _datei_sperre_flash(request, db, current_user, server, relative_path,
                           neuer_inhalt=initial_content or ""):
        return RedirectResponse(url=_dateien_url(server_id, sort=liste_sort,
                                                 richtung=liste_dir, suche=liste_q),
                                status_code=303)

    try:
        created = create_text_file(server, relative_path, initial_content or "")
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=_dateien_url(server_id, sort=liste_sort,
                                                 richtung=liste_dir, suche=liste_q),
                                status_code=303)

    audit_service.log_action(
        db,
        action="server.file_create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={created}",
    )
    push_flash(request, f"Textdatei erstellt: {created}", "success")
    return RedirectResponse(url=_dateien_url(server_id, datei=created, modus="raw",
                                             sort=liste_sort, richtung=liste_dir,
                                             suche=liste_q), status_code=303)


@router.post("/servers/{server_id}/files/delete")
def delete_file_action(
    request: Request,
    server_id: int,
    relative_path: Annotated[str, Form()],
    recursive: Annotated[bool, Form()] = False,
    liste_sort: Annotated[str | None, Form(alias="__liste_sort")] = None,
    liste_dir: Annotated[str | None, Form(alias="__liste_dir")] = None,
    liste_q: Annotated[str | None, Form(alias="__liste_q")] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    zurueck = _dateien_url(server_id, sort=liste_sort, richtung=liste_dir, suche=liste_q)
    if _datei_sperre_flash(request, db, current_user, server, relative_path,
                           neuer_inhalt=""):
        return RedirectResponse(url=zurueck, status_code=303)

    try:
        deleted = delete_path(server, relative_path, recursive=recursive)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=zurueck, status_code=303)

    audit_service.log_action(
        db,
        action="server.file_delete",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={deleted} recursive={recursive}",
    )
    push_flash(request, f"Geloescht: {deleted}", "success")
    return RedirectResponse(url=zurueck, status_code=303)


@router.post("/api/servers/{server_id}/files/upload", response_class=JSONResponse)
async def api_upload_file_action(
    request: Request,
    server_id: int,
    upload: UploadFile = File(...),
    target_dir: Annotated[str, Form()] = ".",
    overwrite: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_edit_server_files(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    try:
        payload = await upload.read()
    except Exception as exc:  # noqa: BLE001
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    gesperrt = _dauerhafte_sperre(db, current_user, server,
                                  _ziel_pfad(target_dir, upload.filename or ""),
                                  neuer_inhalt=_als_text(payload))
    if gesperrt:
        return JSONResponse(status_code=403,
                            content={"detail": f"Dafuer braucht es Admin-Rechte: {gesperrt}"})

    try:
        relative_path = upload_server_file(
            server,
            target_dir=(target_dir or ".").strip() or ".",
            original_filename=upload.filename or "",
            content_bytes=payload,
            overwrite=overwrite,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="server.file_upload",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} size={len(payload)}",
    )
    return JSONResponse({"status": "ok", "path": relative_path, "size": len(payload)})


@router.delete("/api/servers/{server_id}/files", response_class=JSONResponse)
def api_delete_file_action(
    request: Request,
    server_id: int,
    path: str = Query(...),
    recursive: bool = Query(False),
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_edit_server_files(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    gesperrt = _dauerhafte_sperre(db, current_user, server, path, neuer_inhalt="")
    if gesperrt:
        return JSONResponse(status_code=403,
                            content={"detail": f"Dafuer braucht es Admin-Rechte: {gesperrt}"})

    try:
        deleted = delete_path(server, path, recursive=recursive)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="server.file_delete",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={deleted} recursive={recursive}",
    )
    return JSONResponse({"status": "ok", "path": deleted})


@router.post("/api/servers/{server_id}/directories", response_class=JSONResponse)
def api_create_directory_action(
    request: Request,
    server_id: int,
    relative_dir: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_edit_server_files(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    try:
        created = create_directory(server, relative_dir)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="server.directory_create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={created}",
    )
    return JSONResponse({"status": "ok", "path": created})


# -------------------------------------------------- KI-Konfigurationsassistent

def _ki_ziel(request: Request, server_id: int, datei: str, db: Session):
    """(Nutzer, Server, Inhalt) oder (None, Antwort, None).

    Verlangt Schreibrechte: die Vorschlaege wandern in das Formular, und ein Vorschlag,
    den man nicht anwenden kann, waere Geld fuer nichts.
    """
    current_user = _require_user(request, db)
    if current_user is None:
        return None, JSONResponse(status_code=401, content={"detail": "Not authenticated"}), None
    server = get_server_by_id(db, server_id)
    if server is None:
        return None, JSONResponse(status_code=404, content={"detail": "Server not found"}), None
    if not can_edit_server_files(db, current_user, server):
        return None, JSONResponse(status_code=403, content={"detail": "Forbidden"}), None

    pfad = (datei or "").strip()
    if not ai_config_service.unterstuetzt(pfad):
        return None, JSONResponse(status_code=400, content={
            "detail": "Fuer diese Datei gibt es keinen KI-Assistenten. Er kann nur "
                      "Dateien vorschlagen, deren Schluessel der Manager lesen kann "
                      "(.properties) - sonst liesse sich ein Vorschlag nicht gegen den "
                      "Istzustand pruefen."}), None
    try:
        inhalt = read_text_file(server, pfad).content
    except ValueError as exc:
        return None, JSONResponse(status_code=400, content={"detail": str(exc)}), None
    return current_user, server, inhalt


@router.get("/api/servers/{server_id}/files/ki-vorschau", response_class=JSONResponse)
def files_ki_vorschau(
    request: Request,
    server_id: int,
    file: str | None = None,
    db: Session = Depends(get_db),
):
    """Was wuerde an die KI gesendet? Kostet nichts."""
    current_user, server, inhalt = _ki_ziel(request, server_id, file or "", db)
    if current_user is None:
        return server

    try:
        sicht = ai_config_service.vorschau(db, server, file or "", inhalt)
    except Exception as exc:  # noqa: BLE001
        return JSONResponse(status_code=500,
                            content={"detail": f"Vorschau nicht moeglich: {exc}"})
    return JSONResponse(sicht)


@router.post("/api/servers/{server_id}/files/ki-vorschlag", response_class=JSONResponse)
def files_ki_vorschlag(
    request: Request,
    server_id: int,
    file: Annotated[str | None, Form()] = None,
    ziel: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    """Vorschlaege holen. Es wird NICHTS geschrieben - sie landen nur im Formular."""
    current_user, server, inhalt = _ki_ziel(request, server_id, file or "", db)
    if current_user is None:
        return server

    try:
        ergebnis = ai_config_service.schlage_vor(
            db, user=current_user, server=server, pfad=file or "", inhalt=inhalt,
            ziel=(ziel or ""))
    except ai_service.KiAbgelehnt as exc:
        return JSONResponse(status_code=409, content={"detail": str(exc), "abgelehnt": True})
    except ai_provider_service.KiFehler as exc:
        return JSONResponse(status_code=502, content={"detail": str(exc)})

    audit_service.log_action(
        db, action="ai.config_suggest", user_id=current_user.id, server_id=server.id,
        details=f"datei={file} vorschlaege={len(ergebnis.get('aenderungen') or [])} "
                f"abgelehnt={len(ergebnis.get('abgelehnt') or [])} "
                f"modell={ergebnis.get('modell')}")
    return JSONResponse(ergebnis)
