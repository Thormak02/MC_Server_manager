#!/usr/bin/env python3
"""Aufnahme-Proxy fuer die Minecraft-Config-Phase (Phase-3-Vorbereitung).

Setzt sich zwischen einen echten (modded) Client und den echten Modpack-Server,
reicht alle Bytes durch UND protokolliert jedes Paket im Klartext. So bekommen wir
die exakte NeoForge-Aushandlung (u.a. das ``neoforge:register``-Manifest und die
Server-Registry-Daten), die wir zum Spoofen/Replay brauchen - und nebenbei die
Manifest-Kodierung, die das Dispatcher-Matching fixt.

WICHTIG - vorher am Ziel-Server (server.properties), damit der Mitschnitt LESBAR ist:
    online-mode=false
    network-compression-threshold=-1
(danach wieder zuruecksetzen!). Sonst sind die Bytes verschluesselt/komprimiert.

Benutzung (auf dem Server-Rechner, im Repo-Root):
    python scripts/capture_handshake.py --listen 25599 --backend 127.0.0.1:25591 --out atm10_capture
Dann den ATM10-Client auf  <server-ip>:25599  verbinden lassen. Nach ~5 s (drin oder
Abbruch) trennen. Ergebnis: atm10_capture.log (lesbar) + atm10_capture.jsonl (roh/hex).
Die .jsonl bitte teilen (oder auf die \\\\friedrichnas-Freigabe legen).
"""

from __future__ import annotations

import argparse
import json
import re
import socket
import threading
import time

_RESLOC = re.compile(rb"[a-z0-9_.\-]{2,}:[a-z0-9_./\-]{1,}")
_MAX_HEX = 4096  # pro Paket bis zu 4 KB Hex protokollieren


def read_varint(sock_buf: bytearray, start: int = 0):
    """VarInt aus einem Bytearray lesen -> (wert, laenge) oder None (unvollstaendig)."""
    result = 0
    for i in range(5):
        if start + i >= len(sock_buf):
            return None
        b = sock_buf[start + i]
        result |= (b & 0x7F) << (7 * i)
        if not b & 0x80:
            return result, i + 1
    raise ValueError("VarInt zu lang")


def read_string(payload: bytes, off: int):
    got = read_varint(bytearray(payload), off)
    if got is None:
        return None
    length, n = got
    s = off + n
    e = s + length
    if e > len(payload) or length < 0 or length > 4096:
        return None
    return payload[s:e].decode("utf-8", "replace"), e


def next_packet(sock: socket.socket, buf: bytearray):
    """Ein unkomprimiertes Paket lesen -> (raw_bytes, packet_id, payload). Blockt."""
    while True:
        got = read_varint(buf, 0)
        if got is not None:
            length, n = got
            total = n + length
            if length >= 0 and len(buf) >= total:
                raw = bytes(buf[:total])
                del buf[:total]
                body = raw[n:]
                pid_got = read_varint(bytearray(body), 0)
                if pid_got is None:
                    return raw, None, b""
                pid, pn = pid_got
                return raw, pid, body[pn:]
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("closed")
        buf.extend(chunk)


def _analyze(payload: bytes) -> dict:
    """Best-effort: fuehrenden Channel-String + alle Resource-Locations extrahieren."""
    info: dict = {}
    head = read_string(payload, 0)
    if head and _RESLOC.fullmatch(head[0].encode("utf-8", "ignore")):
        info["channel"] = head[0]
    locs = sorted({m.decode("ascii", "ignore") for m in _RESLOC.findall(payload)})
    if locs:
        info["reslocs"] = locs[:200]
    return info


def _pipe(src, dst, direction, log, lock, stop, idx_ref):
    buf = bytearray()
    try:
        while not stop.is_set():
            raw, pid, payload = next_packet(src, buf)
            dst.sendall(raw)  # transparent weiterreichen
            entry = {
                "dir": direction,
                "id": pid,
                "len": len(raw),
                "hex": raw[:_MAX_HEX].hex(),   # nur fuer die .log/.jsonl (gekuerzt)
                "raw": raw,                     # VOLLE Bytes fuer das .replay (in-memory)
                **_analyze(payload),
            }
            with lock:
                entry["idx"] = idx_ref[0]
                idx_ref[0] += 1
                log.append(entry)
    except (OSError, ConnectionError, ValueError):
        pass
    finally:
        stop.set()
        for s in (src, dst):
            try:
                s.close()
            except OSError:
                pass


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=int, required=True, help="Proxy-Port (Client verbindet hier)")
    ap.add_argument("--backend", required=True, help="host:port des echten Modpack-Servers")
    ap.add_argument("--out", default="capture", help="Datei-Prefix fuer .log/.jsonl")
    ap.add_argument("--max-seconds", type=float, default=30.0)
    args = ap.parse_args()

    bhost, bport = args.backend.rsplit(":", 1)
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("0.0.0.0", args.listen))
    listener.listen(1)
    print(f"[capture] warte auf Client an :{args.listen} -> Backend {bhost}:{bport} ...")

    client, addr = listener.accept()
    print(f"[capture] Client {addr} verbunden, oeffne Backend ...")
    backend = socket.create_connection((bhost, int(bport)), timeout=10)

    log: list = []
    lock = threading.Lock()
    stop = threading.Event()
    idx_ref = [0]
    threads = [
        threading.Thread(target=_pipe, args=(client, backend, "C->S", log, lock, stop, idx_ref), daemon=True),
        threading.Thread(target=_pipe, args=(backend, client, "S->C", log, lock, stop, idx_ref), daemon=True),
    ]
    for t in threads:
        t.start()

    deadline = time.monotonic() + args.max_seconds
    while not stop.is_set() and time.monotonic() < deadline:
        time.sleep(0.2)
    stop.set()
    for s in (client, backend):
        try:
            s.close()
        except OSError:
            pass
    time.sleep(0.3)

    log.sort(key=lambda e: e["idx"])
    # .replay: replay-faehiger Voll-Mitschnitt. Pro Paket: [dir:1][len:4 BE][raw].
    # dir 0 = S->C (spaeter an den neuen Client abspielen), 1 = C->S (Checkpoint).
    total = 0
    with open(f"{args.out}.replay", "wb") as rf:
        rf.write(b"MCRP\x01")  # Magic + Version
        for e in log:
            raw = e["raw"]
            rf.write(bytes([0 if e["dir"] == "S->C" else 1]))
            rf.write(len(raw).to_bytes(4, "big"))
            rf.write(raw)
            total += len(raw)
    for e in log:
        e.pop("raw", None)  # nicht in die (lesbare) jsonl/log schreiben
    with open(f"{args.out}.jsonl", "w", encoding="utf-8") as f:
        for e in log:
            f.write(json.dumps(e) + "\n")
    with open(f"{args.out}.log", "w", encoding="utf-8") as f:
        for e in log:
            ch = e.get("channel", "")
            f.write(f"[{e['dir']}] #{e['idx']:>3} id=0x{(e['id'] or 0):02X} len={e['len']:<6}"
                    f" {('channel=' + ch) if ch else ''}\n")
            if e.get("reslocs"):
                f.write(f"        reslocs: {', '.join(e['reslocs'][:40])}\n")
    print(f"[capture] {len(log)} Pakete ({total/1e6:.1f} MB) -> {args.out}.replay (+ .log/.jsonl)")


if __name__ == "__main__":
    main()
