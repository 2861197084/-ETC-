import pymysql
conn=pymysql.connect(host='127.0.0.1',port=3307,user='root',password='root',database='etc',charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute("SELECT start_time, updated_at, values_json FROM checkpoint_flow_forecast_5m WHERE checkpoint_id='CP001' AND fxlx='1' ORDER BY start_time DESC, updated_at DESC LIMIT 1")
    print('CP001-1 latest:', cur.fetchone())
    cur.execute("SELECT id, status, created_at, updated_at FROM forecast_request WHERE checkpoint_id='CP001' AND fxlx='1' ORDER BY created_at DESC LIMIT 5")
    print('CP001-1 latest reqs:')
    for r in cur.fetchall():
        print(' ', r)
conn.close()
