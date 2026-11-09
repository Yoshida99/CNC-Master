from __future__ import annotations

import hashlib
import hmac
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from worker.mining_arbitration import LifecycleClient, MiningArbitrator


class FakeLifecycle:
    def __init__(self, *, start_code: str = "running", external_override: bool = False):
        self.actions: list[tuple[str, str]] = []
        self.start_code = start_code
        self.external_override = external_override
        self.counter = 0

    def submit(self, action: str) -> str:
        self.counter += 1
        request_id = f"{self.counter:032x}"
        self.actions.append((action, request_id))
        return request_id

    def wait_result(self, request_id: str, timeout_seconds: float) -> dict[str, object]:
        action = next(action for action, item_id in self.actions if item_id == request_id)
        code = "paused" if action == "pause" else self.start_code
        return {"request_id": request_id, "action": action, "code": code}

    def result_mtime(self, request_id: str) -> float:
        return 100.0

    def external_pause_or_stop_since(self, after_mtime: float, exclude_ids: set[str]) -> bool:
        return self.external_override


class MiningArbitrationTests(unittest.TestCase):
    def make_arbitrator(
        self,
        root: Path,
        lifecycle: FakeLifecycle,
        *,
        miner_probe,
    ) -> MiningArbitrator:
        return MiningArbitrator(
            enabled=True,
            lifecycle=lifecycle,
            pause_flag=root / "REMOTE_PAUSE.flag",
            stop_flag=root / "STOP.flag",
            lease_path=root / "lease.json",
            pause_timeout_seconds=5,
            resume_timeout_seconds=5,
            lease_seconds=420,
            miner_probe=miner_probe,
            clock=lambda: 1000.0,
            sleep=lambda _: None,
        )

    def test_preexisting_owner_pause_is_never_resumed(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            (root / "REMOTE_PAUSE.flag").write_text("manual", encoding="utf-8")
            lifecycle = FakeLifecycle()
            arbiter = self.make_arbitrator(root, lifecycle, miner_probe=lambda: True)

            lease = arbiter.acquire()

            self.assertFalse(lease.restore_required)
            self.assertEqual([], lifecycle.actions)
            self.assertTrue(arbiter.release(lease))
            self.assertEqual([], lifecycle.actions)

    def test_running_miner_is_paused_then_resumed(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            lifecycle = FakeLifecycle()
            states = iter([True, False, False])
            arbiter = self.make_arbitrator(root, lifecycle, miner_probe=lambda: next(states, False))

            lease = arbiter.acquire()
            restored = arbiter.release(lease)

            self.assertTrue(lease.restore_required)
            self.assertTrue(restored)
            self.assertEqual(["pause", "start"], [action for action, _ in lifecycle.actions])
            self.assertFalse((root / "lease.json").exists())

    def test_owner_override_after_ai_pause_prevents_resume(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            lifecycle = FakeLifecycle(external_override=True)
            states = iter([True, False, False])
            arbiter = self.make_arbitrator(root, lifecycle, miner_probe=lambda: next(states, False))

            lease = arbiter.acquire()
            restored = arbiter.release(lease)

            self.assertFalse(restored)
            self.assertEqual(["pause"], [action for action, _ in lifecycle.actions])
            self.assertFalse((root / "lease.json").exists())

    def test_failed_safety_resume_keeps_recovery_lease(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            lifecycle = FakeLifecycle(start_code="blocked_temperature")
            states = iter([True, False, False])
            arbiter = self.make_arbitrator(root, lifecycle, miner_probe=lambda: next(states, False))

            lease = arbiter.acquire()
            restored = arbiter.release(lease)

            self.assertFalse(restored)
            self.assertEqual(["pause", "start"], [action for action, _ in lifecycle.actions])
            saved = json.loads((root / "lease.json").read_text(encoding="utf-8"))
            self.assertTrue(saved["restore_required"])
            self.assertEqual(1030.0, saved["recover_after"])

    def test_lifecycle_envelope_matches_privileged_agent_hmac(self):
        with TemporaryDirectory() as td:
            root = Path(td)
            (root / "inbox").mkdir()
            (root / "outbox").mkdir()
            token = "123456789:" + "A" * 36
            owner_id = 42
            client = LifecycleClient(
                root=root,
                owner_id=owner_id,
                bot_token=token,
                clock=lambda: 1700000000.0,
                sleep=lambda _: None,
            )

            request_id = client.submit("pause")
            payload = json.loads((root / "inbox" / f"{request_id}.json").read_text("ascii"))

            wire = f"v1|{request_id}|pause|{owner_id}|1700000000".encode("ascii")
            key = hashlib.sha256(("YoshidaNOIDLocalLifecycle/v1|" + token).encode("ascii")).digest()
            expected = hmac.new(key, wire, hashlib.sha256).hexdigest()

            self.assertEqual(expected, payload["mac"])
            self.assertEqual("pause", payload["action"])
            self.assertEqual(owner_id, payload["owner_id"])


if __name__ == "__main__":
    unittest.main()
