from app.core.constants import UserRole


ROLE_PRIORITY: dict[str, int] = {
    UserRole.VIEW_ONLY.value: 10,
    UserRole.MODERATOR.value: 20,
    UserRole.ADMIN.value: 30,
    UserRole.SUPER_ADMIN.value: 40,
}


def is_valid_role(role: str) -> bool:
    return role in ROLE_PRIORITY


def has_minimum_role(role: str, minimum_role: str) -> bool:
    return ROLE_PRIORITY.get(role, 0) >= ROLE_PRIORITY.get(minimum_role, 0)


def can_manage_users(role: str) -> bool:
    return role == UserRole.SUPER_ADMIN.value
