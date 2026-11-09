import sqlite3, zlib, sys, time
sys.path.insert(0,'tools')
from build_db import norm
c=sqlite3.connect('/tmp/dict.db')
SQL="""SELECT k,d,hw,b,i FROM entries WHERE k>=?1 AND k<?2
UNION ALL SELECT e.k,e.d,e.hw,e.b,e.i FROM xkeys x JOIN entries e ON e.k=x.tk WHERE x.k>=?1 AND x.k<?2
ORDER BY 1 LIMIT 400"""
def search(q):
    k=norm(q); t=time.time()
    rows=c.execute(SQL,(k,k+'￿')).fetchall()
    # rank: exact first, then shorter
    rows.sort(key=lambda r:(r[0]!=k, len(r[0]), r[0], r[1]))
    groups={}
    for kk,d,hw,b,i in rows:
        h=hw or kk
        groups.setdefault(h,[]).append(d)
    return (time.time()-t)*1000, list(groups.items())[:12]
def body(b,i):
    data=zlib.decompress(c.execute("select data from blocks where id=?",(b,)).fetchone()[0]).decode().split('\x00')
    return data[i]
for q in ['dog','sho','chó','cho','giay','개','신발','学校','學校','학교','鞋','glue','접착제','keo']:
    ms,g=search(q); print(f'{q}: {ms:.1f}ms', g[:8])
r=c.execute("select k,d,hw,b,i from entries where k=?",(norm('학교'),)).fetchall()
for k,d,hw,b,i in r: print(d,hw,body(b,i)[:300])
r=c.execute("select k,d,hw,b,i from entries where k=?",(norm('glue'),)).fetchall()
for k,d,hw,b,i in r: print(d,hw,body(b,i)[:300])
