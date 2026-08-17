"""Auto-Installation der Java-Laufzeit (Adoptium/Temurin) – netzwerkfrei."""

from types import SimpleNamespace


class _StubScalars:
    def __init__(self, rows):
        self._rows = rows

    def all(self):
        return self._rows


class _StubDB:
    """Minimaler DB-Ersatz: db.scalars(select(...)).all() -> rows."""

    def __init__(self, rows):
        self._rows = rows

    def scalars(self, *_args, **_kwargs):
        return _StubScalars(self._rows)


def test_best_profile_for_major_respects_requirement(tmp_path):
    from app.services import java_runtime_service as jrs

    java = tmp_path / "java.exe"
    java.write_text("x", encoding="utf-8")
    profile = SimpleNamespace(java_path=str(java), version_label="Java 25", is_default=False)
    db = _StubDB([profile])

    assert jrs._best_profile_for_major(db, 21) is profile  # 25 >= 21
    assert jrs._best_profile_for_major(db, 26) is None  # 25 < 26


def test_ensure_java_available_skips_download_when_present(tmp_path):
    from app.services import java_runtime_service as jrs

    java = tmp_path / "java.exe"
    java.write_text("x", encoding="utf-8")
    profile = SimpleNamespace(java_path=str(java), version_label="Java 25", is_default=False)
    db = _StubDB([profile])

    # Kompatibles Java vorhanden -> kein Download, kein Netzwerkzugriff.
    ok, msg = jrs.ensure_java_available(db, 25)
    assert ok is True and msg == ""


def test_install_java_from_adoptium_skips_when_already_installed(tmp_path, monkeypatch):
    from app.services import java_runtime_service as jrs

    root = tmp_path / "java"
    exe = root / "temurin-25" / "jdk-25.0.4+7" / "bin" / "java.exe"
    exe.parent.mkdir(parents=True)
    exe.write_text("x", encoding="utf-8")
    monkeypatch.setattr(jrs, "managed_java_root", lambda: root)

    ok, msg, path = jrs.install_java_from_adoptium(25)
    assert ok is True and path == exe.resolve() and "vorhanden" in msg


def test_resolve_winget_executable_returns_str_or_none():
    from app.services import java_runtime_service as jrs

    result = jrs._resolve_winget_executable()
    assert result is None or isinstance(result, str)


def test_install_java_from_adoptium_atomic_success(tmp_path, monkeypatch):
    """Download + Entpacken laufen atomar: nur ein vollstaendiges JDK landet in
    dest, der Temp-Ordner wird aufgeraeumt (netzwerkfrei via Mocks)."""
    import json as _json
    import zipfile as _zip
    from types import SimpleNamespace

    from app.services import java_runtime_service as jrs

    monkeypatch.setattr(jrs, "get_settings", lambda: SimpleNamespace(data_dir=tmp_path))

    meta = [{"binary": {"package": {"link": "https://example/jdk.zip", "name": "jdk.zip"}}}]

    class _Resp:
        def __init__(self, data):
            self._data = data

        def read(self):
            return self._data

        def __enter__(self):
            return self

        def __exit__(self, *_a):
            return False

    monkeypatch.setattr(
        jrs.urllib.request,
        "urlopen",
        lambda req, timeout=30: _Resp(_json.dumps(meta).encode("utf-8")),
    )

    def _fake_download(url, target, timeout=1200):
        target.parent.mkdir(parents=True, exist_ok=True)
        with _zip.ZipFile(target, "w") as zf:
            zf.writestr("jdk-25.0.4+7/bin/java.exe", "fake-java")

    monkeypatch.setattr(jrs, "_download_to_file", _fake_download)

    ok, msg, path = jrs.install_java_from_adoptium(25)
    assert ok is True, msg
    assert path is not None and path.name == "java.exe" and path.exists()
    assert (tmp_path / "java" / "temurin-25" / "jdk-25.0.4+7" / "bin" / "java.exe").exists()
    # Temp-Ordner wurde aufgeraeumt.
    assert not (tmp_path / "tmp" / "java-install-25").exists()
