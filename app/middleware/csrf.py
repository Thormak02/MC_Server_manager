from urllib.parse import urlparse

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import PlainTextResponse


class CSRFSameOriginMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, *, enabled: bool = True):
        super().__init__(app)
        self.enabled = enabled

    @staticmethod
    def _origin_from_url(value: str | None) -> str | None:
        if not value:
            return None
        parsed = urlparse(value)
        if not parsed.scheme or not parsed.netloc:
            return None
        return f"{parsed.scheme}://{parsed.netloc}".lower()

    async def dispatch(self, request: Request, call_next):
        if not self.enabled:
            return await call_next(request)

        if request.method.upper() in {"GET", "HEAD", "OPTIONS", "TRACE"}:
            return await call_next(request)

        session = request.scope.get("session")
        if not session or not session.get("user_id"):
            return await call_next(request)

        expected_origin = self._origin_from_url(str(request.base_url))
        origin = self._origin_from_url(request.headers.get("origin"))
        referer = self._origin_from_url(request.headers.get("referer"))

        if expected_origin and (origin == expected_origin or referer == expected_origin):
            return await call_next(request)

        return PlainTextResponse("CSRF validation failed.", status_code=403)
