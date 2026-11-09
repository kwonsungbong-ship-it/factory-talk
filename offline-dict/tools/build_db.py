#!/usr/bin/env python3
"""Build the offline dictionary SQLite database.

Sources
  --ovdp    folder of dynamotn/stardict-vi (OVDP StarDict: en-vi, vi-en, ko-vi, vi-ko, zh-vi)
  --cedict  CC-CEDICT text file (cedict_ts.u8)
  --kedict  cc-kedict kedict.yml

Output: SQLite file with tables
  dicts(id, code, src, dst, name, fmt)
  entries(id, d, hw, body BLOB zlib)
  keys(k, e)   -- normalized search keys -> entry id
"""
import argparse, json, os, re, sqlite3, sys, unicodedata, zlib
from collections import defaultdict, OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from stardict import read_stardict  # noqa: E402

# id, code, src, dst, name, fmt
DICTS = [
    (1, 'en-vi', 'en', 'vi', '영→베', 'ovdp'),
    (2, 'vi-en', 'vi', 'en', '베→영', 'ovdp'),
    (3, 'ko-vi', 'ko', 'vi', '한→베', 'ovdp'),
    (4, 'vi-ko', 'vi', 'ko', '베→한', 'ovdp'),
    (5, 'zh-vi', 'zh', 'vi', '중→베', 'ovdp'),
    (6, 'zh-en', 'zh', 'en', '중→영', 'cedict'),
    (7, 'ko-en', 'ko', 'en', '한→영', 'kedict'),
    (8, 'en-ko', 'en', 'ko', '영→한', 'list'),
    (9, 'en-zh', 'en', 'zh', '영→중', 'list'),
    (10, 'zh-ko', 'zh', 'ko', '중→한 한자어', 'list'),
    (11, 'ko-zh', 'ko', 'zh', '한→중 한자어', 'list'),
]
OVDP_FILES = {
    'en-vi': 'en-vi/star_anhviet', 'vi-en': 'vi-en/star_vietanh',
    'ko-vi': 'ko-vi/star_hanviet', 'vi-ko': 'vi-ko/star_viethan',
    'zh-vi': 'zh-vi/star_trungviet',
}
DICT_ID = {d[1]: d[0] for d in DICTS}

HANGUL = re.compile(r'[가-힣]')
CJK = re.compile(r'^[㐀-鿿豈-﫿\U00020000-\U0002ffff]+$')


def norm(s):
    """Search key normalization. MUST match Dict.norm() in the Android app."""
    s = s.strip().lower().replace('đ', 'd')
    s = unicodedata.normalize('NFD', s)
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    s = unicodedata.normalize('NFC', s)
    return re.sub(r'\s+', ' ', s)


# ---------- pinyin ----------
TONE = {'a': 'āáǎà', 'e': 'ēéěè', 'i': 'īíǐì', 'o': 'ōóǒò', 'u': 'ūúǔù', 'ü': 'ǖǘǚǜ'}


def pinyin_syll(s):
    m = re.match(r'^([A-Za-zü:]+?)([1-5])$', s)
    if not m:
        return s.replace('u:', 'ü')
    base, t = m.group(1).replace('u:', 'ü').replace('v', 'ü'), int(m.group(2))
    if t == 5:
        return base
    low = base.lower()
    if 'a' in low:
        i = low.index('a')
    elif 'e' in low:
        i = low.index('e')
    elif 'ou' in low:
        i = low.index('o')
    else:
        i = max((k for k, c in enumerate(low) if c in 'aeiouü'), default=-1)
        if i < 0:
            return base
    c = low[i]
    mark = TONE[c][t - 1]
    if base[i].isupper():
        mark = mark.upper()
    return base[:i] + mark + base[i + 1:]


def pinyin(p):
    return ' '.join(pinyin_syll(x) for x in p.split())


# ---------- helpers ----------
SKIP_DEF = re.compile(r'^(variant of|old variant|archaic variant|CL:|surname |see |used in |abbr\. for|erhua variant|Japanese variant|\(old\)|\(Tw\) variant)', re.I)


def short_pieces(text):
    """English gloss text -> short pieces usable as reverse-lookup keys."""
    text = re.sub(r'\([^)]*\)', ' ', text)
    text = re.sub(r'\[[^\]]*\]', ' ', text)
    text = re.sub(r'<[^>]*>', ' ', text)
    out = []
    for p in re.split(r'[;,/]', text):
        p = p.strip().strip(':').strip().strip('.').strip()
        p = re.sub(r'\s+', ' ', p).lower()
        if not p or not re.match(r"^[a-z][a-z' \-]*$", p):
            continue
        if p.startswith('to '):
            p = p[3:]
        elif p.startswith('a ') or p.startswith('an '):
            p = p.split(' ', 1)[1]
        if 0 < len(p.split()) <= 3 and len(p) >= 2:
            out.append(p)
    return out


class Builder:
    """Bodies are zlib-compressed in blocks of neighbouring entries (much better ratio than per row)."""
    BLOCK = 16384

    def __init__(self, path):
        if os.path.exists(path):
            os.remove(path)
        self.db = sqlite3.connect(path)
        c = self.db
        c.execute('PRAGMA journal_mode=OFF')
        c.execute('PRAGMA synchronous=OFF')
        c.execute('PRAGMA page_size=4096')
        c.execute('CREATE TABLE dicts(id INTEGER PRIMARY KEY, code TEXT, src TEXT, dst TEXT, name TEXT, fmt TEXT, cnt INTEGER)')
        # k: normalized headword (search key); hw NULL when identical to k
        c.execute('CREATE TABLE entries(k TEXT, id INTEGER, d INTEGER, hw TEXT, b INTEGER, i INTEGER, PRIMARY KEY(k, id)) WITHOUT ROWID')
        c.execute('CREATE TABLE blocks(id INTEGER PRIMARY KEY, data BLOB)')
        # extra search keys (e.g. traditional <-> simplified Chinese): key -> target headword key
        c.execute('CREATE TABLE xkeys(k TEXT, tk TEXT, PRIMARY KEY(k, tk)) WITHOUT ROWID')
        c.execute('CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)')
        self.next_id = 1
        self.counts = defaultdict(int)
        self.rows, self.keyrows = [], []
        self.blk_id, self.blk, self.blk_size, self.blk_dict = 1, [], 0, None

    def close_block(self):
        if self.blk:
            data = zlib.compress('\x00'.join(self.blk).encode('utf-8'), 9)
            self.db.execute('INSERT INTO blocks VALUES(?,?)', (self.blk_id, data))
            self.blk_id += 1
            self.blk, self.blk_size = [], 0

    def add(self, dcode, hw, body, extra_keys=()):
        hw = hw.strip()
        if not hw or not body:
            return
        if not isinstance(body, str):
            body = json.dumps(body, ensure_ascii=False, separators=(',', ':'))
        body = body.replace('\x00', ' ')
        d = DICT_ID[dcode]
        if self.blk_dict != d or self.blk_size > self.BLOCK:
            self.close_block()
            self.blk_dict = d
        eid = self.next_id
        self.next_id += 1
        k = norm(hw)
        self.rows.append((k, eid, d, None if hw == k else hw, self.blk_id, len(self.blk)))
        self.blk.append(body)
        self.blk_size += len(body)
        for x in extra_keys:
            x = norm(x) if x else ''
            if x and x != k:
                self.keyrows.append((x, k))
        self.counts[dcode] += 1
        if len(self.rows) > 20000:
            self.flush()

    def flush(self):
        self.db.executemany('INSERT INTO entries VALUES(?,?,?,?,?,?)', self.rows)
        self.db.executemany('INSERT OR IGNORE INTO xkeys VALUES(?,?)', self.keyrows)
        self.rows, self.keyrows = [], []

    def finish(self, meta):
        self.close_block()
        self.flush()
        for d in DICTS:
            self.db.execute('INSERT INTO dicts VALUES(?,?,?,?,?,?,?)', d + (self.counts.get(d[1], 0),))
        for k, v in meta.items():
            self.db.execute('INSERT INTO meta VALUES(?,?)', (k, str(v)))
        self.db.commit()
        self.db.execute('VACUUM')
        self.db.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ovdp', required=True)
    ap.add_argument('--cedict', required=True)
    ap.add_argument('--kedict', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--version', default='dev')
    a = ap.parse_args()

    b = Builder(a.out)

    # ---- CC-CEDICT ----
    cedict = OrderedDict()   # simp -> {'t': trad, 'r': [[pinyin, [defs]]]}
    trad2simp = {}
    line_re = re.compile(r'^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$')
    with open(a.cedict, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#'):
                continue
            m = line_re.match(line)
            if not m:
                continue
            trad, simp, py, defs = m.groups()
            defs = [d for d in defs.split('/') if d]
            e = cedict.setdefault(simp, {'t': set(), 'r': []})
            if trad != simp:
                e['t'].add(trad)
            e['r'].append([pinyin(py), defs])
            trad2simp.setdefault(trad, simp)
    print('cedict', len(cedict))

    # ---- OVDP ----
    hanja_map = defaultdict(list)   # hanja (trad) -> [(ko word, gloss)]
    en_ko = defaultdict(lambda: {'k': OrderedDict(), 'x': []})
    hanja_re = re.compile(r'^-?\s*(\S+)\s*\[([^\]]+)\]\s*(.*)$')
    for code, rel in OVDP_FILES.items():
        n = 0
        for hw, body in read_stardict(os.path.join(a.ovdp, rel)):
            body = body.strip()
            if not body:
                continue
            extra = []
            if code == 'zh-vi':
                if hw in trad2simp:
                    extra.append(trad2simp[hw])
                if hw in cedict:
                    extra.extend(cedict[hw]['t'])
            b.add(code, hw, body, extra)
            n += 1
            if code == 'ko-vi':
                first = body.split('\n', 1)[0]
                m = hanja_re.match(first)
                if m and m.group(1) == hw and CJK.match(m.group(2)):
                    gloss = re.sub(r'[{}]', '', m.group(3)).strip()
                    if not gloss:
                        for ln in body.split('\n')[1:]:
                            ln = ln.strip('-= ').strip()
                            if ln:
                                gloss = re.sub(r'[{}]', '', ln)
                                break
                    hanja_map[m.group(2)].append((hw, gloss[:80]))
            elif code == 'vi-ko':
                for ln in body.split('\n'):
                    m = re.match(r'^-\{([^}]+)\}(.*)$', ln.strip())
                    if not m:
                        continue
                    en, ko = m.group(1).strip(), m.group(2).strip()
                    if not HANGUL.search(ko):
                        continue
                    for p in short_pieces(en) or [en.lower()]:
                        if len(p.split()) > 4:
                            continue
                        x = en_ko[p]['x']
                        if ko not in x and len(x) < 6:
                            x.append(ko)
        print(code, n)

    # ---- CEDICT entries + en-zh reverse ----
    # popularity proxy: how many headwords contain this 1-2 character string
    from collections import Counter
    pop = Counter()
    for simp in cedict:
        for n in (1, 2):
            for j in range(len(simp) - n + 1):
                pop[simp[j:j + n]] += 1
    en_zh = defaultdict(list)
    for simp, e in cedict.items():
        body = {'t': sorted(e['t']), 'r': e['r']}
        b.add('zh-en', simp, body, list(e['t']))
        for ri, (py, defs) in enumerate(e['r']):
            for di, d in enumerate(defs):
                if SKIP_DEF.match(d):
                    continue
                for pi, p in enumerate(short_pieces(d)):
                    exact = 0 if p == d.strip().lower() else 3
                    score = ri * 3 + di * 10 + pi * 2 + exact + len(simp) - min(pop.get(simp, 0), 60) / 6
                    en_zh[p].append((score, simp, py, '; '.join(defs[:3])))

    # ---- cc-kedict ----
    import yaml
    with open(a.kedict, encoding='utf-8') as f:
        ke = yaml.safe_load(f)
    kedict = OrderedDict()
    for it in ke or []:
        w = str(it.get('word') or '').strip()
        if not w:
            continue
        e = kedict.setdefault(w, {'r': it.get('romaja') or '', 's': []})
        defs, exs = [], []
        for d in it.get('defs') or []:
            dd = str(d.get('def') or '').strip().lstrip(':').strip()
            if dd:
                defs.append(dd)
            for ex in d.get('examples') or []:
                if ex.get('example'):
                    exs.append([str(ex.get('example')), str(ex.get('translation') or '')])
        if defs:
            e['s'].append({'p': it.get('pos') or '', 'd': defs, 'e': exs[:3]})
            for di, dd in enumerate(defs):
                for p in short_pieces(dd):
                    k = en_ko[p]['k']
                    if w not in k and len(k) < 15:
                        k[w] = dd[:80]
    for w, e in kedict.items():
        if e['s']:
            b.add('ko-en', w, e)
    print('kedict', len(kedict))

    # ---- en-ko (derived) ----
    for en in sorted(en_ko):
        v = en_ko[en]
        items = [{'w': w, 'n': n} for w, n in v['k'].items()]
        if items or v['x']:
            b.add('en-ko', en, {'k': items, 'x': v['x']})

    # ---- en-zh (derived) ----
    for en in sorted(en_zh):
        seen, items = set(), []
        for score, simp, py, d in sorted(en_zh[en]):
            if simp in seen:
                continue
            seen.add(simp)
            items.append({'w': simp, 'p': py, 'n': d[:60]})
            if len(items) >= 8:
                break
        b.add('en-zh', en, {'k': items})

    # ---- zh-ko / ko-zh via hanja (Sino-Korean words whose hanja is a CEDICT headword) ----
    ko_zh = defaultdict(list)
    zh_ko = OrderedDict()   # simp -> {'h': set(hanja), 'k': OrderedDict(ko -> gloss)}
    for hanja, kos in hanja_map.items():
        simp = trad2simp.get(hanja) or (hanja if hanja in cedict else None)
        if not simp:
            continue
        ce = cedict[simp]
        py = ce['r'][0][0]
        d = '; '.join(ce['r'][0][1][:3])
        z = zh_ko.setdefault(simp, {'h': set(), 'k': OrderedDict()})
        z['h'].add(hanja)
        for kw, g in kos:
            if kw not in z['k']:
                z['k'][kw] = g
                ko_zh[kw].append({'w': simp, 'p': py, 'n': (hanja + ' · ' if hanja != simp else '') + d[:80]})
    for simp, z in zh_ko.items():
        hs = sorted(h for h in z['h'] if h != simp)
        b.add('zh-ko', simp, {'k': [{'w': w, 'n': g} for w, g in z['k'].items()], 'h': ' '.join(hs)}, hs)
    for kw, items in ko_zh.items():
        b.add('ko-zh', kw, {'k': items[:8]})
    print('zh-ko', len(zh_ko), 'ko-zh', len(ko_zh))

    b.finish({'version': a.version})
    print('counts', dict(b.counts))


if __name__ == '__main__':
    main()
