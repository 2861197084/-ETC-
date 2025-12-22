import pymysql

conn=pymysql.connect(host='127.0.0.1',port=3307,user='root',password='root',database='etc',charset='utf8mb4')
with conn.cursor() as cur:
    for cp,fx in [('CP001','1'),('CP001','2'),('CP004','1'),('CP004','2')]:
        cur.execute("SELECT checkpoint_id, fxlx, start_time, updated_at, values_json FROM checkpoint_flow_forecast_5m WHERE checkpoint_id=%s AND fxlx=%s ORDER BY start_time DESC, updated_at DESC LIMIT 1", (cp,fx))
        r=cur.fetchone()
        print('\nlatest forecast', cp, fx, ':', r[0], r[1], r[2], r[3], 'values_prefix=', str(r[4])[:60] if r else None)
        cur.execute("SELECT id, status, created_at, updated_at FROM forecast_request WHERE checkpoint_id=%s AND fxlx=%s ORDER BY created_at DESC LIMIT 3", (cp,fx))
        print('latest requests', cp, fx, ':')
        for rr in cur.fetchall():
            print(' ', rr)
conn.close()
