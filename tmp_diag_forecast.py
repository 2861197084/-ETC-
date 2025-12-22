import pymysql
from datetime import datetime

conn=pymysql.connect(host='127.0.0.1',port=3307,user='root',password='root',database='etc',charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute("SELECT status, COUNT(*) FROM forecast_request GROUP BY status")
    print('forecast_request status counts:', cur.fetchall())

    cur.execute("SELECT id, checkpoint_id, fxlx, status, err_msg, created_at, updated_at FROM forecast_request ORDER BY created_at DESC LIMIT 15")
    print('\nlatest 15 requests:')
    for r in cur.fetchall():
        print(r)

    cur.execute("SELECT checkpoint_id, fxlx, start_time, model_version, updated_at FROM checkpoint_flow_forecast_5m ORDER BY updated_at DESC LIMIT 10")
    rows=cur.fetchall()
    print('\nlatest 10 forecasts:')
    for r in rows:
        print(r)

conn.close()
