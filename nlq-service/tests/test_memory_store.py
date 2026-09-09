"""长期记忆存储（MemoryStore，SQLite 按 user_id 隔离）的行为测试。"""

import os
import tempfile

import pytest

from app.services.memory_store import MemoryStore


@pytest.fixture
def store():
    # 不用 pytest tmp_path（Windows 沙箱下系统 Temp 目录拒绝访问），项目内建临时目录，
    # 用 TemporaryDirectory 保证退出自动清理。
    with tempfile.TemporaryDirectory(dir=".", prefix="tmp_mem_") as d:
        s = MemoryStore(os.path.join(d, "mem.db"))
        yield s
        s.close()


def test_set_get_roundtrip(store):
    store.set(1, "preference", "喜欢简洁")
    assert store.get(1, "preference") == "喜欢简洁"


def test_user_isolation(store):
    store.set(1, "k", "v1")
    store.set(2, "k", "v2")
    assert store.get(1, "k") == "v1"
    assert store.get(2, "k") == "v2"


def test_get_missing_returns_none(store):
    assert store.get(1, "nope") is None


def test_overwrite(store):
    store.set(1, "k", "a")
    store.set(1, "k", "b")
    assert store.get(1, "k") == "b"


def test_delete(store):
    store.set(1, "k", "v")
    store.delete(1, "k")
    assert store.get(1, "k") is None


def test_list_user_memories(store):
    store.set(1, "a", "1")
    store.set(1, "b", "2")
    store.set(2, "c", "3")
    assert store.list(1) == {"a": "1", "b": "2"}


def test_persistence_across_reopen():
    with tempfile.TemporaryDirectory(dir=".", prefix="tmp_mem_") as d:
        path = os.path.join(d, "mem.db")

        s1 = MemoryStore(path)
        s1.set(9, "last_context", "调 CS101 的实验项目")
        s1.close()

        s2 = MemoryStore(path)
        assert s2.get(9, "last_context") == "调 CS101 的实验项目"
        s2.close()
