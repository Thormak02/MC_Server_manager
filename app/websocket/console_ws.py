from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.db.session import SessionLocal
from app.models.user import User
from app.services.console_service import console_service
from app.services.server_service import can_view_server, get_server_by_id


router = APIRouter(include_in_schema=False)


@router.websocket("/ws/servers/{server_id}/console")
async def console_websocket(websocket: WebSocket, server_id: int) -> None:
    await websocket.accept()

    with SessionLocal() as db:
        try:
            session = websocket.session
        except AssertionError:
            session = None
        raw_user_id = session.get("user_id") if session else None
        if raw_user_id is None:
            await websocket.send_text("[SYSTEM] Nicht authentifiziert.")
            await websocket.close(code=1008)
            return

        try:
            user_id = int(raw_user_id)
        except (TypeError, ValueError):
            await websocket.close(code=1008)
            return

        user = db.get(User, user_id)
        if user is None or not user.is_active:
            await websocket.close(code=1008)
            return

        server = get_server_by_id(db, server_id)
        if server is None or not can_view_server(db, user, server):
            await websocket.close(code=1008)
            return

    console_service.register_websocket(server_id, websocket)
    try:
        for line in console_service.get_recent_lines(server_id, limit=200):
            await websocket.send_text(line)

        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        console_service.unregister_websocket(server_id, websocket)
