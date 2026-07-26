import pytest

from . import KVStore


class FakeClock:
    """A deterministic monotonic clock; ``advance`` moves it forward, ``__call__`` reads it."""

    def __init__(self, t: float = 0.0) -> None:
        self.t = t

    def advance(self, dt: float) -> None:
        self.t += dt

    def __call__(self) -> float:
        return self.t


def test_set_get_delete_and_missing():
    kv = KVStore()
    kv.set("a", 1)
    assert kv.get("a") == 1
    assert kv.get("missing") is None
    assert kv.delete("a") is True
    assert kv.get("a") is None
    assert kv.delete("a") is False  # already gone


def test_set_overwrites_value():
    kv = KVStore()
    kv.set("k", "first")
    kv.set("k", "second")
    assert kv.get("k") == "second"


def test_len_and_contains_count_live_keys():
    kv = KVStore()
    kv.set("a", 1)
    kv.set("b", 2)
    assert len(kv) == 2
    assert "a" in kv
    assert "z" not in kv


def test_ttl_expires_on_get():
    clock = FakeClock()
    kv = KVStore(clock=clock)
    kv.set("session", "token", ttl=10)
    assert kv.get("session") == "token"
    clock.advance(10)  # deadline reached (>= is expired)
    assert kv.get("session") is None


def test_ttl_reflected_in_len_and_contains():
    clock = FakeClock()
    kv = KVStore(clock=clock)
    kv.set("a", 1, ttl=5)
    kv.set("b", 2)  # no ttl
    assert len(kv) == 2
    clock.advance(6)
    assert "a" not in kv
    assert len(kv) == 1


def test_non_expiring_key_survives():
    clock = FakeClock()
    kv = KVStore(clock=clock)
    kv.set("permanent", 42)
    clock.advance(1_000_000)
    assert kv.get("permanent") == 42


def test_delete_of_expired_key_returns_false():
    clock = FakeClock()
    kv = KVStore(clock=clock)
    kv.set("a", 1, ttl=1)
    clock.advance(2)
    assert kv.delete("a") is False


def test_transaction_commits_atomically():
    kv = KVStore()
    kv.set("existing", 0)
    with kv.transaction():
        kv.set("a", 1)
        kv.set("b", 2)
        kv.delete("existing")
    assert kv.get("a") == 1
    assert kv.get("b") == 2
    assert kv.get("existing") is None


def test_transaction_rolls_back_on_exception_and_reraises():
    kv = KVStore()
    kv.set("keep", "original")
    with pytest.raises(ValueError, match="boom"), kv.transaction():
        kv.set("keep", "changed")
        kv.set("new", 99)
        raise ValueError("boom")
    # none of the buffered writes were applied
    assert kv.get("keep") == "original"
    assert kv.get("new") is None


def test_buffered_writes_not_visible_inside_block():
    kv = KVStore()
    kv.set("k", "committed")
    with kv.transaction():
        kv.set("k", "buffered")
        # reads see the pre-transaction (committed) state, not the buffer
        assert kv.get("k") == "committed"
        assert kv.get("fresh") is None
        kv.set("fresh", 1)
        assert kv.get("fresh") is None
    assert kv.get("k") == "buffered"
    assert kv.get("fresh") == 1
