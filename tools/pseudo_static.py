import re,sys,json
def parse(path):
    src=open(path).read()
    i=src.rindex('    static {'); body=src[i:]
    pend=[]; arrays={}
    for line in body.split('\n'):
        m=re.match(r'\s*\(new "?\[?(\w+)"?\[(\d+)\]\)\[(\d+)\] = (.+);',line)
        if m:
            v=m.group(4).strip()
            v=re.sub(r'(?<=\d)[fdl]$','',v)
            try: v=float(v)
            except: pass
            pend.append(v); continue
        m=re.match(r'\s*(\w+) = new "?\[?\w+"?\[(\d+)\];',line)
        if m:
            arrays[m.group(1)]=pend; pend=[]
    for k,v in arrays.items():
        arrays[k]=[arrays[x] if isinstance(x,str) and x in arrays else x for x in v]
    return arrays
if __name__=='__main__':
    a=parse(sys.argv[1])
    for k in sys.argv[2:]:
        v=a.get(k); print(k, v if not (v and isinstance(v[0],list)) else [len(x) for x in v])
