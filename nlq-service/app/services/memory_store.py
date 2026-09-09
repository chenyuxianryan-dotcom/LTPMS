"""Agent 长期记忆存储（SQLite，按 user_id 隔离）。

存非敏感偏好 / 常用查询 / 上下文摘要。键建议用命名空间（如 preference:xxx、
last_context、common_queries）。低频小量读写，用同步 sqlite3 + 锁保证线程安全，
不引入额外异步依赖。
"""

import sqlite3
import threading


class MemoryStore:
    def __init__(self, db_path: str):
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        with self._lock:
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS agent_memory (
                    user_id INTEGER NOT NULL,
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    PRIMARY KEY (user_id, key)
                )
                """
            )
            self._conn.commit()

    def get(self, user_id: int, key: str) -> str | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT value FROM agent_memory WHERE user_id = ? AND key = ?",
                (user_id, key),
            ).fetchone()
        return row[0] if row else None

    def set(self, user_id: int, key: str, value: str) -> None:
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO agent_memory (user_id, key, value, updated_at)
                VALUES (?, ?, ?, datetime('now'))
                ON CONFLICT(user_id, key) DO UPDATE
                    SET value = excluded.value, updated_at = excluded.updated_at
                """,
                (user_id, key, value),
            )
            self._conn.commit()

    def list(self, user_id: int) -> dict[str, str]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT key, value FROM agent_memory WHERE user_id = ? ORDER BY key",
                (user_id,),
            ).fetchall()
        return {k: v for k, v in rows}

    def delete(self, user_id: int, key: str) -> None:
        with self._lock:
            self._conn.execute(
                "DELETE FROM agent_memory WHERE user_id = ? AND key = ?",
                (user_id, key),
            )
            self._conn.commit()

    def close(self) -> None:
        with self._lock:
            self._conn.close()
