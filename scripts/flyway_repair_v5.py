"""Clear V5 failed migration from flyway_schema_history (Flyway 'repair' equivalent).

V5 was attempted once, success=0. We fix the SQL and clear the failed row
so the next Spring Boot startup will re-apply V5 cleanly.
"""
import pymysql

conn = pymysql.connect(host="localhost", port=3306, user="root", password="123456", database="shipyard")
try:
    cur = conn.cursor()
    # Show before
    cur.execute("SELECT version, description, success FROM flyway_schema_history WHERE version='5'")
    print("Before:", cur.fetchall())

    # Delete V5 failed row (Flyway repair semantics)
    cur.execute("DELETE FROM flyway_schema_history WHERE version='5' AND success=0")
    print(f"Deleted {cur.rowcount} row(s)")

    # Show after
    cur.execute("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank")
    print("After:", cur.fetchall())
    conn.commit()
finally:
    conn.close()
