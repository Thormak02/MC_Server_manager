from sqlalchemy import Boolean, ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class ServerPermission(Base):
    __tablename__ = "server_permissions"
    __table_args__ = (
        UniqueConstraint("user_id", "server_id", name="uq_server_permission_user_server"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    server_id: Mapped[int] = mapped_column(ForeignKey("servers.id", ondelete="CASCADE"))
    can_view: Mapped[bool] = mapped_column(Boolean, default=True)
    can_console: Mapped[bool] = mapped_column(Boolean, default=False)
    can_restart: Mapped[bool] = mapped_column(Boolean, default=False)
    can_edit_files: Mapped[bool] = mapped_column(Boolean, default=False)
    can_manage: Mapped[bool] = mapped_column(Boolean, default=False)

    user = relationship("User", back_populates="server_permissions")
    server = relationship("Server", back_populates="permissions")
