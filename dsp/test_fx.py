import math, random
import fx_ref as F

def run(fs, seconds, gen, schedule=None, targets=None):
    fx = F.Effects(fs)
    n = int(fs * seconds)
    out_l, out_r, in_l, in_r = [], [], [], []
    if targets: fx.target = list(targets)
    for i in range(n):
        if schedule: 
            for (t0, tg) in schedule:
                if i == int(t0 * fs): fx.target = list(tg)
        l, r = gen(i)
        in_l.append(l); in_r.append(r)
        a, b = fx.frame(l, r)
        out_l.append(a); out_r.append(b)
    return in_l, in_r, out_l, out_r

def rms(x): return math.sqrt(sum(v*v for v in x)/max(1,len(x)))
def max_jump(x): return max(abs(x[i]-x[i-1]) for i in range(1,len(x)))
FS = 22050
sine = lambda f, a=0.5: (lambda i: (a*math.sin(2*math.pi*f*i/FS),)*2)
ok = True
def check(name, cond, info=""):
    global ok
    print(("PASS " if cond else "FAIL ")+name+("  "+info if info else ""))
    ok = ok and cond

# 1. everything off -> exactly the input
il, ir, ol, orr = run(FS, 1.0, sine(300))
check("bypass is bit-exact when all effects are off", il == ol and ir == orr)

# 2. no clicks when an effect is switched on or off mid-song (300 Hz sine, 0.5 amplitude)
natural = 2*math.pi*300/FS*0.5       # largest step of the clean sine
for idx, name in enumerate(["underwater","muffle","reverb","echo","8D"]):
    on = [0.0]*5; on[idx] = 0.8
    _,_,ol,_ = run(FS, 3.0, sine(300), schedule=[(1.0,on),(2.0,[0.0]*5)])
    j = max_jump(ol)
    check(f"{name}: switching on/off makes no click", j < natural*2.2, f"max step {j:.4f} vs sine {natural:.4f}")

# 3. strength slider sweep: smooth, no zipper
for idx, name in enumerate(["underwater","muffle","reverb","echo","8D"]):
    sched=[]
    for k in range(40):
        t=[0.0]*5; t[idx]=k/39; sched.append((0.5+k*0.05,t))
    _,_,ol,_ = run(FS, 3.0, sine(300), schedule=sched)
    j = max_jump(ol)
    check(f"{name}: moving the strength slider is smooth", j < natural*2.2, f"max step {j:.4f}")

# 4. never clips: full-scale noise, everything on
random.seed(1)
noise = lambda i: ((random.random()*2-1)*0.98,)*2
_,_,ol,orr = run(FS, 1.0, noise, targets=[1,1,1,1,1])
check("output stays inside +-1 with every effect at full strength", max(max(abs(v) for v in ol), max(abs(v) for v in orr)) <= 1.0, f"peak {max(max(abs(v) for v in ol), max(abs(v) for v in orr)):.4f}")

# 5. low-pass effects really cut highs and keep lows
for idx,name in [(F.MUFFLE,"muffle"),(F.UNDERWATER,"underwater")]:
    t=[0.0]*5; t[idx]=1.0
    _,_,lo,_ = run(FS, 1.5, sine(150,0.4), targets=t)
    _,_,hi,_ = run(FS, 1.5, sine(5000,0.4), targets=t)
    a=rms(lo[FS//2:]); b=rms(hi[FS//2:])
    check(f"{name}: keeps 150 Hz, removes 5 kHz", a > 0.2 and b < 0.04, f"150Hz rms {a:.3f}, 5kHz rms {b:.4f}")

# 6. echo: an impulse comes back after ~0.32 s, then again quieter
def impulse(i): return ((0.8,0.8) if i==10 else (0.0,0.0))
_,_,ol,_ = run(FS, 1.2, impulse, targets=[0,0,0,1,0])
peaks=[i for i in range(11,len(ol)-1) if abs(ol[i])>0.05 and abs(ol[i])>=abs(ol[i-1]) and abs(ol[i])>=abs(ol[i+1])]
t1=peaks[0]/FS if peaks else None
check("echo: first repeat arrives at about 0.32 s", t1 is not None and abs(t1-0.32)<0.02, f"first repeat at {t1}")

# 7. reverb: a tail that fades away
_,_,ol,_ = run(FS, 4.0, impulse, targets=[0,0,1,0,0])
e1=rms(ol[FS//10:FS//2]); e2=rms(ol[FS*3:FS*4])
check("reverb: adds a tail that fades", e1>0.002 and e2<e1*0.6, f"early {e1:.4f}, late {e2:.4f}")

# 8. 8D: the sound moves between the ears slowly
_,_,ol,orr = run(FS, 10.0, sine(300,0.4), targets=[0,0,0,0,1.0])
win=FS//4
bal=[ (rms(ol[i:i+win])-rms(orr[i:i+win]))/(rms(ol[i:i+win])+rms(orr[i:i+win])+1e-9) for i in range(FS, len(ol)-win, win)]
swing=max(bal)-min(bal)
check("8D: sound travels left <-> right", swing>0.8, f"balance swing {swing:.2f}")
check("8D: no wobble (smooth output)", max_jump(ol)<natural*2.0, f"max step {max_jump(ol):.4f}")

# 9. mixed: all five together, music-like signal, no clicks when strengths change
def music(i): 
    v=0.25*math.sin(2*math.pi*220*i/FS)+0.2*math.sin(2*math.pi*660*i/FS)+0.1*math.sin(2*math.pi*1700*i/FS)
    return (v,v*0.9)
sched=[(1.0,[0.5]*5),(2.0,[1.0,0.2,0.7,0.5,0.9]),(3.0,[0.0]*5)]
_,_,ol,_ = run(FS, 4.0, music, schedule=sched)
base=max_jump([music(i)[0] for i in range(FS)])
check("all effects together: no clicks when strengths change", max_jump(ol) < base*2.6, f"max step {max_jump(ol):.4f} vs source {base:.4f}")

print("\nALL REFERENCE TESTS PASSED" if ok else "\nSOME TESTS FAILED")
