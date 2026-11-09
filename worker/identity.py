from __future__ import annotations

import argparse
import hashlib
import json
import os
import secrets
from pathlib import Path


def default_identity_path() -> Path:
    override = os.getenv("CNC_WORKER_IDENTITY_FILE", "").strip()
    if override:
        return Path(override).expanduser()

    local_app_data = os.getenv("LOCALAPPDATA", "").strip()
    if local_app_data:
        return Path(local_app_data) / "Yoshida" / "CNC-Master" / "worker-auth.json"

    return Path.home() / ".config" / "yoshida" / "cnc-master" / "worker-auth.json"


def load_or_create_worker_token(path: Path | None = None) -> str:
    target = path or default_identity_path()
    target.parent.mkdir(parents=True, exist_ok=True)

    if target.exists():
        try:
            payload = json.loads(target.read_text(encoding="utf-8-sig"))
            token = str(payload.get("worker_token", "")).strip()
            if len(token) >= 24:
                return token
        except (OSError, ValueError, TypeError):
            pass

    token = secrets.token_urlsafe(40)
    temp = target.with_suffix(target.suffix + ".tmp")
    temp.write_text(
        json.dumps({"worker_token": token}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    try:
        os.chmod(temp, 0o600)
    except OSError:
        pass
    temp.replace(target)
    return token


def token_sha256(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="CNC Master GPU worker local identity")
    parser.add_argument("--print-sha256", action="store_true")
    parser.add_argument("--print-path", action="store_true")
    args = parser.parse_args()

    path = default_identity_path()
    token = load_or_create_worker_token(path)

    if args.print_path:
        print(path)
    if args.print_sha256:
        print(token_sha256(token))

    if not args.print_path and not args.print_sha256:
        print("worker identity ready")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
