#!/usr/bin/env python3
"""Replay-Lobby (Phase 3, Meilenstein-Test).

Spielt einen zuvor mitgeschnittenen Modpack-Handshake (.replay) jedem frischen Client
vor: wir geben uns als der Modpack-Server aus, machen einen Offline-Login und spielen
dann die aufgezeichneten Server->Client-Pakete ab; an den Stellen, wo der echte Server
auf eine Client-Antwort wartete, warten wir auf ein Client-Paket. Ziel dieses Schritts:
herausfinden, ob ein echter ATM10-Client unsere Fake-Antworten akzeptiert und durch
NeoForges Mod-/Registry-Pruefung kommt (bis in die PLAY-Phase), OHNE dass die Mods bei
uns laufen.

Benutzung (Repo-Root, auf dem Server-Rechner):
    python scripts/replay_lobby.py --listen 25601 --replay atm10_capture.replay
Dann den ATM10-Client auf  <server-ip>:25601  verbinden. Beobachten: kommt er rein?
(Der Client-Crash-Report / die Disconnect-Meldung zeigt, wo es ggf. klemmt.)
"""

from __future__ import annotations

import argparse
import socket
import sys
import threading
import time

sys.path.insert(0, ".")  # Repo-Root fuer app.services.*

from app.services import mc_dispatch as mcd  # noqa: E402
from app.services import mc_play as pl  # noqa: E402
from app.services import mc_protocol as mp  # noqa: E402
from app.services import replay_service as rp  # noqa: E402

_DRAIN_TIMEOUT = 6.0
# 1.21.1 PLAY clientbound Keep Alive (bei Bedarf anpassen, falls Client trotzdem
# "timed out" fliegt).
_PLAY_KEEPALIVE_CB = pl.PLAY_CB_KEEP_ALIVE

# --- Phase-1-Plattform (unsere eigene Vanilla-Welt statt der aufgezeichneten) ---
_FLOOR_SECTION = 7            # bei 24 Sections -> Boden y 48..63
_SPAWN = (8.5, 64.0, 8.5)     # mittig auf Chunk (0,0), auf dem Boden
_GRID_RADIUS = 4             # 9x9-Chunk-Grid: Sodium mesht nur Chunks mit voll geladenem
                             # Nachbar-Ring -> ein groesseres Grid macht einen sichtbaren Kern frei
# Dummy-Spieler zum Testen der Entity-Sichtbarkeit (spaeter echte Spieler)
_DUMMY_EID = 424242
_DUMMY_UUID = b"MCSM-LOBBY-BOT!!"   # 16 Byte, offline reicht das
_DUMMY_POS = (8.5, 64.0, 13.5)      # ~5 Bloecke vor dem Spawn, auf der Plattform


class Reader:
    def __init__(self, sock: socket.socket):
        self.sock = sock
        self.buf = bytearray()

    def _fill(self, timeout: float) -> None:
        self.sock.settimeout(timeout)
        chunk = self.sock.recv(16384)
        if not chunk:
            raise ConnectionError("closed")
        self.buf.extend(chunk)

    def read_handshake(self, timeout: float = 10.0) -> mp.Handshake:
        while True:
            try:
                hs = mp.parse_handshake(bytes(self.buf))
                del self.buf[: hs.consumed]
                return hs
            except mp.IncompletePacket:
                self._fill(timeout)

    def read_packet(self, timeout: float = 10.0):
        while True:
            got = mcd.try_read_packet(bytes(self.buf))
            if got is not None:
                pid, fields, consumed = got
                del self.buf[:consumed]
                return pid, fields
            self._fill(timeout)


def handle(client: socket.socket, records, config_start: int) -> None:
    try:
        reader = Reader(client)
        hs = reader.read_handshake()
        print(f"[replay] Handshake: proto={hs.protocol_version} host={hs.server_address!r} "
              f"next_state={hs.next_state}")
        if hs.next_state == mp.NEXT_STATE_STATUS:
            print("[replay] Status-Ping - ignoriert.")
            return

        # --- Offline-Login ---
        pid, payload = reader.read_packet()
        if pid != mcd.LOGIN_START:
            print(f"[replay] erwartete LoginStart, bekam 0x{pid:02X}")
            return
        username, uuid16 = mcd.parse_login_start(payload)
        client.sendall(mcd.build_login_success(uuid16, username or "Player", hs.protocol_version))
        pid, _ = reader.read_packet()
        if pid != mcd.LOGIN_ACK:
            print(f"[replay] erwartete LoginAck, bekam 0x{pid:02X}")
            return
        print(f"[replay] Login ok ({username}). Spiele NUR die Config-Phase ab ...")

        # --- Nur die Config-Phase abspielen (bis zur PLAY-Grenze) ---
        play_login = rp.find_play_login(records)
        if play_login >= len(records):
            print("[replay] Kein PLAY-Login (0x2B) im Capture gefunden - abbruch.")
            return
        config_records = records[:play_login]
        steps = rp.build_steps(config_records, config_start)
        sent_bytes = 0
        for n, step in enumerate(steps):
            if step.send:
                for raw in step.send:
                    client.sendall(raw)
                    sent_bytes += len(raw)
            else:
                for _ in range(step.wait):
                    try:
                        reader.read_packet(_DRAIN_TIMEOUT)
                    except (OSError, ConnectionError):
                        print(f"[replay] Checkpoint {n}: kein Client-Paket (weiter).")
                        break
            if n % 25 == 0:
                print(f"[replay]   Schritt {n}/{len(steps)}, {sent_bytes/1e6:.1f} MB gesendet")
        print(f"[replay] Config abgespielt ({sent_bytes/1e6:.1f} MB). Client ist in PLAY.")

        # --- PLAY-Naht: EIGENE Vanilla-Plattform statt der aufgezeichneten ATM10-Welt ---
        # Login verbatim aus dem Capture (garantiert korrekte dimension_type-Indizes),
        # danach unsere selbst gebaute Welt: 5x5-Chunk-Grid um (0,0) - Sodium mesht eine
        # Section erst, wenn ihre Nachbarn geladen sind, sonst bleibt die Plattform unsichtbar.
        client.sendall(records[play_login].raw)
        client.sendall(pl.build_set_center_chunk(0, 0))
        # Chunks in einen Batch rahmen (Start -> N x ChunkData -> Finished), sonst
        # committet der Client nur den Spawn-Chunk und die Nachbarn bleiben ungemesht.
        client.sendall(pl.build_chunk_batch_start())
        n_chunks = 0
        for cx in range(-_GRID_RADIUS, _GRID_RADIUS + 1):
            for cz in range(-_GRID_RADIUS, _GRID_RADIUS + 1):
                client.sendall(pl.build_flat_chunk(cx, cz, floor_section_index=_FLOOR_SECTION))
                n_chunks += 1
        client.sendall(pl.build_chunk_batch_finished(n_chunks))
        sx, sy, sz = _SPAWN
        client.sendall(pl.build_sync_position(sx, sy, sz, teleport_id=1))
        client.sendall(pl.build_set_default_spawn(int(sx), int(sy), int(sz)))
        client.sendall(pl.build_game_event(pl.GAME_EVENT_WAIT_FOR_CHUNKS, 0.0))
        print(f"[replay] Eigene Vanilla-Plattform ({n_chunks} Chunks, gebatcht) gesendet.")

        # --- Dummy-Spieler sichtbar machen (Info-Update MUSS vor Spawn kommen) ---
        dx, dy, dz = _DUMMY_POS
        client.sendall(pl.build_player_info_update(_DUMMY_UUID, "Lobby-Bot"))
        client.sendall(pl.build_add_entity(_DUMMY_EID, _DUMMY_UUID, dx, dy, dz, yaw=180, head_yaw=180))
        client.sendall(pl.build_system_chat(mcd._nbt_text_component(
            "Willkommen in der Universal-Lobby! (modded Client, 0 Server-Mods)")))
        print("[replay] Dummy-Spieler + Willkommens-Chat gesendet. PLAY am Leben halten ...")

        # --- PLAY am Leben halten: regelmaessig Clientbound-Keep-Alive senden, damit
        # der Client nicht "timed out" fliegt; Client-Pakete (Bewegung, KA-Antwort)
        # verwerfen. So bleibst du in der (replay-ten ATM10-)Welt stehen. ---
        ka = 0
        last_ka = 0.0
        client.settimeout(1.0)
        while True:  # bis der Client selbst trennt
            now = time.monotonic()
            if now - last_ka >= 10.0:
                try:
                    client.sendall(pl.build_keep_alive(ka))
                except OSError:
                    break
                ka += 1
                last_ka = now
                print(f"[replay]   Keep-Alive #{ka} gesendet, Verbindung lebt.")
            # Client-Pakete draenen (nicht blockierend genug -> Timeout ist ok).
            got = mcd.try_read_packet(bytes(reader.buf))
            if got is not None:
                _pid, _f, consumed = got
                del reader.buf[:consumed]
                continue
            try:
                chunk = client.recv(16384)
            except socket.timeout:
                continue
            except OSError:
                break
            if not chunk:
                print("[replay] Client hat getrennt.")
                break
            reader.buf.extend(chunk)
        print("[replay] Verbindung beendet.")
    except (OSError, ConnectionError, mp.ProtocolError) as exc:
        print(f"[replay] Verbindungsfehler: {exc!r}")
    except Exception as exc:  # noqa: BLE001
        print(f"[replay] Fehler: {exc!r}")
    finally:
        try:
            client.close()
        except OSError:
            pass


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=int, required=True)
    ap.add_argument("--replay", required=True, help="Pfad zur .replay-Datei")
    args = ap.parse_args()

    records = rp.load_replay_file(args.replay)
    start = rp.find_config_start(records)
    s2c = sum(1 for r in records if r.to_client)
    print(f"[replay] {len(records)} Pakete geladen ({s2c} S->C), Config-Start bei #{start}.")

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("0.0.0.0", args.listen))
    listener.listen(5)
    print(f"[replay] warte auf Clients an :{args.listen} ...")
    while True:
        client, addr = listener.accept()
        print(f"\n[replay] Client {addr} verbunden.")
        threading.Thread(target=handle, args=(client, records, start), daemon=True).start()


if __name__ == "__main__":
    main()
