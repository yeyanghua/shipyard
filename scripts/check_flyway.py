"""Check flyway_schema_history to see why V5 migration failed."""
import pymysql

conn = pymysql.connect(host="localhost", port=3306, user="root", password="123456", database="shipyard")
try:
    cur = conn.cursor()
    cur.execute(
        "SELECT version, description, type, script, success, execution_time, installed_on "
        "FROM flyway_schema_history ORDER BY installed_rank"
    )
    print("=== flyway_schema_history ===")
    for row in cur.fetchall():
        print(row)

    print()
    print("=== shipyard DB tables ===")
    cur.execute("SHOW TABLES")
    for row in cur.fetchall():
        print(row[0])

    print()
    print("=== V5 actually applied? (show v5 table if exists) ===")
    # env table after V5 should have worker_url/worker_token_enc/k8s_namespace
    try:
        cur.execute("DESCRIBE env")
        print("--- env table structure ---")
        for col in cur.fetchall():
            print(col)
    except Exception as e:
        print("env table missing:", e)

    try:
        cur.execute("DESCRIBE worker")
        print("--- worker table structure ---")
        for col in cur.fetchall():
            print(col)
    except Exception as e:
        print("worker table missing:", e)
finally:
    conn.close()
