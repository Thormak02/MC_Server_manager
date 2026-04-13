from app.core.config import get_settings


def validate_password(password: str) -> None:
    settings = get_settings()
    if len(password) < max(1, settings.password_min_length):
        raise ValueError(f"Passwort muss mindestens {settings.password_min_length} Zeichen haben.")
    if settings.password_require_uppercase and not any(ch.isupper() for ch in password):
        raise ValueError("Passwort muss mindestens einen Grossbuchstaben enthalten.")
    if settings.password_require_lowercase and not any(ch.islower() for ch in password):
        raise ValueError("Passwort muss mindestens einen Kleinbuchstaben enthalten.")
    if settings.password_require_digit and not any(ch.isdigit() for ch in password):
        raise ValueError("Passwort muss mindestens eine Ziffer enthalten.")

