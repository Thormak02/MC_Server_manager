import socket

from app.services import port_service


def test_is_port_free_detects_bound_port():
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind(("0.0.0.0", 0))
    listener.listen(1)
    bound_port = listener.getsockname()[1]
    try:
        # Belegter, lauschender Port -> nicht frei (auch unter Windows, da
        # is_port_free bewusst ohne SO_REUSEADDR bindet).
        assert port_service.is_port_free(bound_port) is False
    finally:
        listener.close()


def test_is_port_free_rejects_out_of_range():
    assert port_service.is_port_free(0) is False
    assert port_service.is_port_free(70000) is False


def test_allocate_skips_used_and_returns_host_free_port(client):
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        taken = Server(
            name="port-taker",
            slug="port-taker",
            server_type="paper",
            mc_version="1.20.1",
            base_path="C:/tmp/port-taker",
            port=25565,
        )
        db.add(taken)
        db.commit()

        allocated = port_service.allocate_server_port(db)
        assert 25565 <= allocated <= 25999
        assert allocated != 25565  # bereits in der DB vergeben
        assert port_service.is_port_free(allocated) is True

        # Bevorzugter, freier und nicht vergebener Port wird uebernommen.
        preferred = port_service.allocate_server_port(db, preferred=25800)
        assert preferred == 25800
