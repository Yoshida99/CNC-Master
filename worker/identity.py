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


def legacy_token_paths() -> list[Path]:
    paths: list[Path] = []
    override = os.getenv("CNC_WORKER_LEGACY_TOKEN_FILE", "").strip()
    if override:
        paths.append(Path(override).expanduser())

    local_app_data = os.getenv("LOCALAPPDATA", "").strip()
    if local_app_data:
        # M3.3 initial private-beta setup used this raw-token file.
        paths.append(Path(local_app_data) / "CNC-Master" / "secrets" / "gpu-worker.token")
    return paths


def _read_existing_identity(path: Path) -> str | None:
    if not path.exists():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        token = str(payload.get("worker_token", "")).strip()
        return token if len(token) >= 24 else None
    except (OSError, ValueError, TypeError):
        return None


def _read_legacy_token() -> str | None:
    for path in legacy_token_paths():
        try:
            token = path.read_text(encoding="utf-8-sig").strip()
        except OSError:
            continue
        if len(token) >= 24:
            return token
    return None


def load_or_create_worker_token(path: Path | None = None) -> str:
    target = path or default_identity_path()
    target.parent.mkdir(parents=True, exist_ok=True)

    existing = _read_existing_identity(target)
    if existing:
        return existing

    # Preserve the already paired Render worker credential during migration.
    token = _read_legacy_token() or secrets.token_urlsafe(40)

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
