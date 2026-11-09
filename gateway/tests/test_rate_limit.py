import unittest

from gateway.rate_limit import SlidingWindowRateLimiter


class RateLimiterTests(unittest.TestCase):
    def test_blocks_after_limit_and_recovers_after_window(self):
        limiter = SlidingWindowRateLimiter(limit=2, window_seconds=10)
        self.assertEqual((True, 0), limiter.allow("u", now=1))
        self.assertEqual((True, 0), limiter.allow("u", now=2))
        allowed, retry = limiter.allow("u", now=3)
        self.assertFalse(allowed)
        self.assertGreaterEqual(retry, 1)
        self.assertEqual((True, 0), limiter.allow("u", now=12))


if __name__ == "__main__":
    unittest.main()
