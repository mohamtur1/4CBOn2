"""Shared Supabase data client for the Vercel functions.

Split out so api/gate.py and api/auth.py cannot drift into two different ideas
of how to build a client. Uses the service-role key: these functions run
server-side only, and the tables they touch have anon read revoked.
"""
import os

from auth_core import AuthConfigError, service_role_key
from gate_core import GateError


def supabase_client():
    """Service-role client. Raises GateError if it cannot be built, so callers
    fail closed rather than returning an unauthenticated client."""
    url = (os.environ.get("SUPABASE_URL") or "").strip()
    try:
        key = service_role_key()
    except AuthConfigError:
        key = ""
    if not url or not key:
        raise GateError(
            "SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SECRET_KEY)"
            " are not configured")
    # Imported here, not at module scope: create_client validates the key format
    # and raises SupabaseException on a malformed one, and that must surface as
    # a denial rather than as an import error at cold start.
    from supabase import create_client
    try:
        return create_client(url, key)
    except Exception as exc:
        raise GateError(f"could not construct the Supabase client: {type(exc).__name__}") from exc
