import hashlib
import unittest

from gateway.security import SessionSigner, TokenStore, parse_client_tokens


class SecurityTests(unittest.TestCase):
    def test_token_store_uses_constant_value_matching_behavior(self):
        store = TokenStore({"user-a": "secret-a", "user-b": "secret-b"})
        self.assertEqual("user-b", store.authenticate("secret-b"))
        self.assertIsNone(store.authenticate("wrong"))

    def test_token_store_supports_sha256_hashes(self):
        token = "private-token-value"
        digest = hashlib.sha256(token.encode("utf-8")).hexdigest()
        store = TokenStore(token_hashes={"user-hash": digest})
        self.assertEqual("user-hash", store.authenticate(token))
        self.assertIsNone(store.authenticate("wrong"))

    def test_session_round_trip_and_expiry(self):
        signer = SessionSigner("x" * 48, ttl_seconds=600)
        token, expires = signer.issue("user-a", now=1000)
        identity = signer.verify(token, now=1100)
        self.assertIsNotNone(identity)
        self.assertEqual("user-a", identity.user_id)
        self.assertEqual(expires, identity.expires_at)
        self.assertIsNone(signer.verify(token, now=expires))

    def test_parse_client_tokens_supports_legacy_fallback(self):
        self.assertEqual({"default": "abc"}, parse_client_tokens("", "abc"))


if __name__ == "__main__":
    unittest.main()
