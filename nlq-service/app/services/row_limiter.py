import re

MAX_ROWS = 500


def ensure_limit(sql: str) -> str:
    """确保 SQL 带 LIMIT，无则追加，超过 500 则改写为 500"""
    pattern = re.compile(r"\bLIMIT\s+(\d+)\s*$", re.IGNORECASE)
    match = pattern.search(sql.strip())

    if match:
        current_limit = int(match.group(1))
        if current_limit > MAX_ROWS:
            return sql[:match.start()] + f" LIMIT {MAX_ROWS}"
        return sql

    # 没有 LIMIT，追加
    return f"{sql.rstrip(';').strip()} LIMIT {MAX_ROWS}"
