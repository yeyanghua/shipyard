"""Verify V5 migration result: env table has 3 new columns, flyway_schema_history shows V5 success."""
import pymysql

conn = pymysql.connect(host="localhost", port=3306, user="root", password="123456", database="shipyard")
try:
    cur = conn.cursor()
    print("=== flyway_schema_history ===")
    cur.execute("SELECT version, description, success, execution_time FROM flyway_schema_history ORDER BY installed_rank")
    for row in cur.fetchall():
        print(f"  v{row[0]:3s} success={row[2]} time={row[3]}ms - {row[1]}")

    print()
    print("=== env table ===")
    cur.execute("DESCRIBE env")
    for col in cur.fetchall():
        print(f"  {col[0]:20s} {col[1]:20s} null={col[2]} default={col[4]}")

    print()
    print("=== worker table ===")
    cur.execute("DESCRIBE worker")
    for col in cur.fetchall():
        print(f"  {col[0]:20s} {col[1]:20s} null={col[2]} default={col[4]}")
finally:
    conn.close()
