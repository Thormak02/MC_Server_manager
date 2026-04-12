from enum import StrEnum


class UserRole(StrEnum):
    SUPER_ADMIN = "super_admin"
    ADMIN = "admin"
    MODERATOR = "moderator"
    VIEW_ONLY = "view_only"


ROLE_LABELS = {
    UserRole.SUPER_ADMIN.value: "Super Admin",
    UserRole.ADMIN.value: "Admin",
    UserRole.MODERATOR.value: "Moderator",
    UserRole.VIEW_ONLY.value: "View Only",
}

DEFAULT_SERVER_STATUS = "stopped"

SERVER_STATUSES = [
    "stopped",
    "offline",
    "starting",
    "running",
    "stopping",
    "restarting",
    "crashed",
    "error",
    "backup_running",
    "provisioning",
]
