from __future__ import annotations

import time
from collections import defaultdict, deque


class SlidingWindowRateLimiter:
    def __init__(self, limit: int, window_seconds: int) -> None:
        self.limit = max(1, limit)
        self.window_seconds = max(1, window_seconds)
        self._events: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, key: str, now: float | None = None) -> tuple[bool, int]:
        current = time.monotonic() if now is None else now
        cutoff = current - self.window_seconds
        bucket = self._events[key]

        while bucket and bucket[0] <= cutoff:
            bucket.popleft()

        if len(bucket) >= self.limit:
            retry_after = max(1, int(bucket[0] + self.window_seconds - current) + 1)
            return False, retry_after

        bucket.append(current)
        return True, 0
