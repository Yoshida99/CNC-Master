from __future__ import annotations

import hashlib
import hmac
import json
import logging
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
from dataclasses import asdict, dataclass
from typing import Callable, Protocol


logger = logging.getLogger("cnc.gpu_worker.arbitration")

ACTIONS = frozenset({"start", "pause", "stop"})
RESULT_CODES = frozenset({
    "running",
    "already_running",
    "paused",
    "stopped",
    "already_stopped",
    "blocked_temperature",
    "blocked_guard",
    "blocked_connection",
    "failed",
})
ID_RE = re.compile(r"^[a-f0-9]{32}$")


class ArbitrationError(RuntimeError):
    pass


class LifecycleProtocol(Protocol):
    def submit(self, action: str) -> str: ...
    def wait_result(self, request_id: str, timeout_seconds: float) -> dict[str, object]: ...
    def result_mtime(self, request_id: str) -> float: ...
    def external_pause_or_stop_since(self, after_mtime: float, exclude_ids: set[str]) -> bool: ...


@dataclass(frozen=True)
class MiningLease:
    restore_required: bool
    pause_request_id: str = ""
    pause_result_mtime: float = 0.0
    created_at: float = 0.0
    recover_after: float = 0.0


class LifecycleClient:
    def __init__(
        self,
        *,
        root: Path,
        owner_id: int,
        bot_token: str,
        clock: Callable[[], float] = time.time,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        if owner_id <= 0:
            raise ArbitrationError("Mining lifecycle owner is not configured")
        if len(bot_token) < 30 or ":" not in bot_token:
            raise ArbitrationError("Mining lifecycle credential is invalid")
        self.root = Path(root)
        self.owner_id = owner_id
        seed = ("YoshidaNOIDLocalLifecycle/v1|" + bot_token).encode("ascii")
        self._secret_key = hashlib.sha256(seed).digest()
        self.clock = clock
        self.sleep = sleep

    @classmethod
    def from_local_config(cls) -> "LifecycleClient":
        root = Path(os.getenv(
            "CNC_MINING_LIFECYCLE_ROOT",
            r"C:\ProgramData\YoshidaMiningMonitor\lifecycle",
        ))
        owner_path = Path(os.getenv(
            "CNC_MINING_OWNER_CONFIG",
            str(root / "owner.json"),
        ))
        env_path = Path(os.getenv(
            "CNC_MINING_ENV_FILE",
            r"D:\Projects\YoshidaMiningMonitor\.env",
        ))

        try:
            owner_document = json.loads(owner_path.read_text(encoding="utf-8-sig"))
            owner_id = int(owner_document["owner_id"])
        except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
            raise ArbitrationError("Mining lifecycle owner config is unavailable") from exc

        token = _read_env_value(env_path, "TELEGRAM_BOT_TOKEN")
        return cls(root=root, owner_id=owner_id, bot_token=token)

    def submit(self, action: str) -> str:
        if action not in ACTIONS:
            raise ArbitrationError("Unsupported mining lifecycle action")

        inbox = self.root / "inbox"
        if not inbox.is_dir() or inbox.is_symlink() or self.root.is_symlink():
            raise ArbitrationError("Trusted mining lifecycle agent is unavailable")

        request_id = secrets.token_hex(16)
        issued_at = int(self.clock())
        wire = f"v1|{request_id}|{action}|{self.owner_id}|{issued_at}".encode("ascii")
        mac = hmac.new(self._secret_key, wire, hashlib.sha256).hexdigest()
        payload = {
            "version": 1,
            "request_id": request_id,
            "action": action,
            "owner_id": self.owner_id,
            "issued_at": issued_at,
            "mac": mac,
        }
        encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("ascii")
        if len(encoded) > 512:
            raise ArbitrationError("Mining lifecycle request is oversized")

        name = request_id + ".json"
        staging = inbox / ("." + name + ".tmp")
        final = inbox / name
        try:
            fd = os.open(
                staging,
                os.O_CREAT | os.O_EXCL | os.O_WRONLY | getattr(os, "O_BINARY", 0),
                0o600,
            )
            with os.fdopen(fd, "wb") as file:
                file.write(encoded)
                file.flush()
                os.fsync(file.fileno())
            if final.exists() or final.is_symlink():
                raise ArbitrationError("Mining lifecycle request collision")
            os.replace(staging, final)
        finally:
            try:
                staging.unlink(missing_ok=True)
            except OSError:
                pass
        return request_id

    def wait_result(self, request_id: str, timeout_seconds: float) -> dict[str, object]:
        if not ID_RE.fullmatch(request_id):
            raise ArbitrationError("Invalid mining lifecycle request id")
        deadline = time.monotonic() + max(1.0, timeout_seconds)
        result_path = self.root / "outbox" / f"{request_id}.json"

        while time.monotonic() < deadline:
            if result_path.is_file() and not result_path.is_symlink():
                try:
                    if result_path.stat().st_size > 512:
                        raise ArbitrationError("Mining lifecycle result is oversized")
                    value = json.loads(result_path.read_text(encoding="utf-8-sig"))
                except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
                    raise ArbitrationError("Mining lifecycle result is invalid") from exc

                if (
                    not isinstance(value, dict)
                    or value.get("request_id") != request_id
                    or value.get("action") not in ACTIONS
                    or value.get("code") not in RESULT_CODES
                ):
                    raise ArbitrationError("Mining lifecycle result failed validation")
                return value
            self.sleep(0.25)

        raise ArbitrationError("Mining lifecycle request timed out")

    def result_mtime(self, request_id: str) -> float:
        path = self.root / "outbox" / f"{request_id}.json"
        try:
            return path.stat().st_mtime
        except OSError:
            return self.clock()

    def external_pause_or_stop_since(self, after_mtime: float, exclude_ids: set[str]) -> bool:
        for directory_name in ("inbox", "claimed", "outbox"):
            directory = self.root / directory_name
            if not directory.is_dir():
                continue
            try:
                files = list(directory.glob("*.json"))[-96:]
            except OSError:
                continue
            for path in files:
                if path.stem in exclude_ids or not ID_RE.fullmatch(path.stem) or path.is_symlink():
                    continue
                try:
                    stat = path.stat()
                    if stat.st_mtime <= after_mtime or stat.st_size > 512:
                        continue
                    value = json.loads(path.read_text(encoding="utf-8-sig"))
                except (OSError, ValueError, TypeError, json.JSONDecodeError):
                    continue
                if isinstance(value, dict) and value.get("action") in {"pause", "stop"}:
                    return True
        return False


class MiningArbitrator:
    def __init__(
        self,
        *,
        enabled: bool,
        lifecycle: LifecycleProtocol | None,
        pause_flag: Path,
        stop_flag: Path,
        lease_path: Path,
        pause_timeout_seconds: float = 70.0,
        resume_timeout_seconds: float = 70.0,
        lease_seconds: float = 420.0,
        miner_probe: Callable[[], bool] | None = None,
        clock: Callable[[], float] = time.time,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.enabled = enabled
        self.lifecycle = lifecycle
        self.pause_flag = Path(pause_flag)
        self.stop_flag = Path(stop_flag)
        self.lease_path = Path(lease_path)
        self.pause_timeout_seconds = max(5.0, pause_timeout_seconds)
        self.resume_timeout_seconds = max(5.0, resume_timeout_seconds)
        self.lease_seconds = max(120.0, lease_seconds)
        self.miner_probe = miner_probe or _miner_running
        self.clock = clock
        self.sleep = sleep

    @classmethod
    def from_env(cls) -> "MiningArbitrator":
        enabled = _env_bool("CNC_GPU_ARBITRATION_ENABLED", False)
        local_app_data = Path(os.getenv("LOCALAPPDATA", str(Path.home())))
        lifecycle: LifecycleProtocol | None = None
        if enabled:
            lifecycle = LifecycleClient.from_local_config()

        return cls(
            enabled=enabled,
            lifecycle=lifecycle,
            pause_flag=Path(os.getenv(
                "CNC_MINING_PAUSE_FLAG",
                r"D:\Mining\Parano1d\Fl4shMiner\REMOTE_PAUSE.flag",
            )),
            stop_flag=Path(os.getenv(
                "CNC_MINING_STOP_FLAG",
                r"D:\Mining\Parano1d\STOP.flag",
            )),
            lease_path=Path(os.getenv(
                "CNC_MINING_LEASE_FILE",
                str(local_app_data / "Yoshida" / "CNC-Master" / "mining-arbitration.json"),
            )),
            pause_timeout_seconds=float(os.getenv("CNC_MINING_PAUSE_TIMEOUT_SECONDS", "70")),
            resume_timeout_seconds=float(os.getenv("CNC_MINING_RESUME_TIMEOUT_SECONDS", "70")),
            lease_seconds=float(os.getenv("CNC_MINING_LEASE_SECONDS", "420")),
        )

    def acquire(self) -> MiningLease:
        if not self.enabled:
            return MiningLease(restore_required=False)
        lifecycle = self._require_lifecycle()

        # Never override an owner pause/stop that existed before this AI request.
        if self.stop_flag.exists() or self.pause_flag.exists() or not self.miner_probe():
            return MiningLease(restore_required=False)

        now = self.clock()
        request_id = lifecycle.submit("pause")
        lease = MiningLease(
            restore_required=True,
            pause_request_id=request_id,
            created_at=now,
            recover_after=now + self.lease_seconds,
        )
        self._write_lease(lease)

        try:
            result = lifecycle.wait_result(request_id, self.pause_timeout_seconds)
            code = str(result.get("code", ""))
            if code != "paused":
                if code == "already_stopped":
                    self._clear_lease()
                    return MiningLease(restore_required=False)
                raise ArbitrationError(f"Mining pause was rejected: {code or 'failed'}")

            pause_mtime = lifecycle.result_mtime(request_id)
            lease = MiningLease(
                restore_required=True,
                pause_request_id=request_id,
                pause_result_mtime=pause_mtime,
                created_at=now,
                recover_after=now + self.lease_seconds,
            )
            self._write_lease(lease)

            deadline = time.monotonic() + 8.0
            while self.miner_probe() and time.monotonic() < deadline:
                self.sleep(0.25)
            if self.miner_probe():
                raise ArbitrationError("Mining process did not become idle after pause")

            logger.info("mining_paused_for_ai request_id=%s", request_id)
            return lease
        except Exception:
            # If the pause may already have taken effect, keep the lease so the
            # recovery task can safely attempt a later restore.
            if not self.pause_flag.exists() and self.miner_probe():
                self._clear_lease()
            raise

    def release(self, lease: MiningLease) -> bool:
        if not self.enabled or not lease.restore_required:
            return True
        lifecycle = self._require_lifecycle()

        # Owner STOP always wins. Never auto-clear it.
        if self.stop_flag.exists():
            logger.warning("mining_resume_skipped_manual_stop")
            self._clear_lease()
            return False

        # Any separate PAUSE/STOP intent after our own pause is treated as an
        # explicit owner override and must win over automatic restoration.
        if lifecycle.external_pause_or_stop_since(
            lease.pause_result_mtime,
            {lease.pause_request_id},
        ):
            logger.warning("mining_resume_skipped_owner_override")
            self._clear_lease()
            return False

        # Small quiet period narrows the race with an owner control arriving
        # immediately after AI generation completes.
        self.sleep(1.0)
        if self.stop_flag.exists() or lifecycle.external_pause_or_stop_since(
            lease.pause_result_mtime,
            {lease.pause_request_id},
        ):
            logger.warning("mining_resume_skipped_owner_override")
            self._clear_lease()
            return False

        request_id = lifecycle.submit("start")
        try:
            result = lifecycle.wait_result(request_id, self.resume_timeout_seconds)
            code = str(result.get("code", ""))
            if code in {"running", "already_running"}:
                self._clear_lease()
                logger.info("mining_resumed_after_ai request_id=%s", request_id)
                return True

            logger.error("mining_resume_deferred code=%s", code or "failed")
            self._defer_recovery(lease, code or "failed")
            return False
        except Exception:
            self._defer_recovery(lease, "exception")
            raise

    def recover_expired(self) -> bool:
        if not self.enabled or not self.lease_path.is_file():
            return True
        lease = self._read_lease()
        if lease is None:
            return False
        if self.clock() < lease.recover_after:
            return True
        try:
            return self.release(lease)
        except Exception as exc:
            logger.error("mining_recovery_failed error=%s", type(exc).__name__)
            return False

    def _defer_recovery(self, lease: MiningLease, code: str) -> None:
        updated = MiningLease(
            restore_required=lease.restore_required,
            pause_request_id=lease.pause_request_id,
            pause_result_mtime=lease.pause_result_mtime,
            created_at=lease.created_at,
            recover_after=self.clock() + 30.0,
        )
        self._write_lease(updated)
        logger.warning("mining_recovery_scheduled code=%s", code)

    def _write_lease(self, lease: MiningLease) -> None:
        self.lease_path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.lease_path.with_suffix(self.lease_path.suffix + ".tmp")
        document = {"version": 1, **asdict(lease)}
        tmp.write_text(json.dumps(document, separators=(",", ":")), encoding="utf-8")
        os.replace(tmp, self.lease_path)

    def _read_lease(self) -> MiningLease | None:
        try:
            value = json.loads(self.lease_path.read_text(encoding="utf-8"))
            if value.get("version") != 1:
                return None
            return MiningLease(
                restore_required=bool(value["restore_required"]),
                pause_request_id=str(value.get("pause_request_id", "")),
                pause_result_mtime=float(value.get("pause_result_mtime", 0.0)),
                created_at=float(value.get("created_at", 0.0)),
                recover_after=float(value.get("recover_after", 0.0)),
            )
        except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError):
            logger.error("mining_lease_invalid")
            return None

    def _clear_lease(self) -> None:
        try:
            self.lease_path.unlink(missing_ok=True)
        except OSError:
            logger.warning("mining_lease_cleanup_failed")

    def _require_lifecycle(self) -> LifecycleProtocol:
        if self.lifecycle is None:
            raise ArbitrationError("Mining lifecycle integration is not configured")
        return self.lifecycle


def _read_env_value(path: Path, key: str) -> str:
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except OSError as exc:
        raise ArbitrationError("Mining lifecycle credential file is unavailable") from exc
    prefix = key + "="
    matches = [line[len(prefix):].strip() for line in lines if line.startswith(prefix)]
    if len(matches) != 1:
        raise ArbitrationError("Mining lifecycle credential is unavailable")
    return matches[0]


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _miner_running() -> bool:
    if os.name != "nt":
        return False
    command = (
        "if(Get-Process -Name fl4shminer -ErrorAction SilentlyContinue)"
        "{exit 0}else{exit 1}"
    )
    try:
        result = subprocess.run(
            ["powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=5,
            check=False,
        )
        return result.returncode == 0
    except (OSError, subprocess.SubprocessError):
        raise ArbitrationError("Unable to inspect mining process state")


def main() -> int:
    arbitrator = MiningArbitrator.from_env()
    if "--recover" in os.sys.argv:
        return 0 if arbitrator.recover_expired() else 2
    lease = arbitrator._read_lease()
    print(json.dumps({
        "enabled": arbitrator.enabled,
        "lease_present": lease is not None,
        "restore_required": bool(lease and lease.restore_required),
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
