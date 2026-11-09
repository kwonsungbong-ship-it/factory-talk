"""Minimal StarDict reader (.ifo/.idx/.dict[.dz])."""
import gzip, os, struct

def read_stardict(prefix):
    ifo = {}
    with open(prefix + '.ifo', encoding='utf-8') as f:
        for line in f:
            if '=' in line:
                k, v = line.rstrip('\n').split('=', 1)
                ifo[k] = v
    with open(prefix + '.idx', 'rb') as f:
        idx = f.read()
    data = None
    for cand in (prefix + '.dict', prefix + '.dict.dz', prefix + '.dz'):
        if os.path.exists(cand):
            if cand.endswith('.dz'):
                with gzip.open(cand, 'rb') as g:
                    data = g.read()
            else:
                with open(cand, 'rb') as g:
                    data = g.read()
            break
    if data is None:
        raise FileNotFoundError(prefix)
    off64 = ifo.get('idxoffsetbits') == '64'
    fmt, sz = ('>QI', 12) if off64 else ('>II', 8)
    i, n = 0, len(idx)
    while i < n:
        j = idx.index(b'\0', i)
        word = idx[i:j].decode('utf-8', 'replace')
        off, size = struct.unpack(fmt, idx[j + 1:j + 1 + sz])
        i = j + 1 + sz
        yield word, data[off:off + size].decode('utf-8', 'replace')
