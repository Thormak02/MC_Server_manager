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
