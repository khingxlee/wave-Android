import math, numpy as np
from stretch_ref import Stretcher
ok = True
def check(name, cond, info=""):
    global ok; ok = ok and cond
    print(("PASS " if cond else "FAIL ")+name+("  "+info if info else ""))

def tone(fs, secs, f0=220.0, stereo=True):
    n = int(fs*secs); t = np.arange(n)/fs
    x = sum((0.5/(h))*np.sin(2*np.pi*f0*h*t + 0.3*h) for h in range(1,7))
    x = 0.4*x/np.max(np.abs(x))
    return np.vstack([x, x*0.9]) if stereo else x[None,:]

def run(fs, x, speed, pitch, chunk=2048, schedule=None):
    st = Stretcher(fs, x.shape[0]); st.set_targets(speed, pitch)
    outs = []
    for i in range(0, x.shape[1], chunk):
        if schedule:
            for (t0, sp, pi_) in schedule:
                if i <= int(t0*fs) < i+chunk: st.set_targets(sp, pi_)
        outs.append(st.process(x[:, i:i+chunk]))
    return st, np.concatenate(outs, axis=1)

def dom_freq(y, fs):
    seg = y[len(y)//4: 3*len(y)//4]
    seg = seg*np.hanning(len(seg))
    sp = np.abs(np.fft.rfft(seg, 1<<18)); k = np.argmax(sp)
    return k*fs/(1<<18)

for fs in (22050, 44100):
    print(f"--- sample rate {fs}")
    x = tone(fs, 8.0)
    # a. neutral = untouched
    st, y = run(fs, x, 1.0, 1.0)
    m = min(y.shape[1], x.shape[1]) - 4000
    check("neutral: output is the input, unchanged", np.max(np.abs(y[:, :m] - x[:, :m])) < 1e-9, f"max diff {np.max(np.abs(y[:, :m]-x[:, :m])):.2e}")
    # b. speed only
    for sp in (0.6, 0.75, 1.5, 2.0):
        st, y = run(fs, x, sp, 1.0)
        ratio = (y.shape[1] + 0) / (x.shape[1] / sp)
        f_in = dom_freq(x[0], fs); f_out = dom_freq(y[0], fs)
        check(f"speed {sp}: right length, pitch kept", 0.93 < ratio < 1.03 and abs(f_out/f_in-1) < 0.006, f"length ratio {ratio:.3f}, freq ratio {f_out/f_in:.4f}")
    # c. pitch only
    for semi in (-12, -5, 3, 7, 12):
        P = 2 ** (semi/12)
        st, y = run(fs, x, 1.0, P)
        ratio = y.shape[1] / x.shape[1]
        f_in = dom_freq(x[0], fs); f_out = dom_freq(y[0], fs)
        check(f"pitch {semi:+d} semitones: right pitch, same length", abs(f_out/f_in/P-1) < 0.008 and 0.93 < ratio < 1.03, f"freq ratio {f_out/f_in:.4f} (want {P:.4f}), length ratio {ratio:.3f}")
    # d. both
    st, y = run(fs, x, 1.25, 2**(-7/12))
    f_in = dom_freq(x[0], fs); f_out = dom_freq(y[0], fs)
    check("speed 1.25 with pitch -7: both right", abs(f_out/f_in/(2**(-7/12))-1) < 0.01 and 0.93 < y.shape[1]/(x.shape[1]/1.25) < 1.03, f"freq {f_out/f_in:.4f}, length {y.shape[1]/(x.shape[1]/1.25):.3f}")

fs = 22050
x = tone(fs, 12.0)
def max_step(y): return float(np.max(np.abs(np.diff(y[0]))))
def d2_crest(y):
    d = np.diff(y[0], 2); return float(np.max(np.abs(d)) / (np.sqrt(np.mean(d*d)) + 1e-12))
# e. smooth while the values move
st, base = run(fs, x, 0.8, 1.0)
sched = [(1.0+0.05*i, 0.8 + 0.7*(i/119.0), 2**((-4+8*(i/119.0))/12)) for i in range(120)]
st, sw = run(fs, x, 0.8, 2**(-4/12), schedule=sched)
sched_s = [(1.0+0.05*i, 0.6 + 1.2*(i/119.0), 1.0) for i in range(120)]
st, sws = run(fs, x, 0.6, 1.0, schedule=sched_s)
ref_step = max_step(run(fs, x, 1.0, 1.0)[1])
check("moving tempo and pitch together: no clicks", max_step(sw) < ref_step*1.9, f"max step {max_step(sw):.4f} vs untouched {ref_step:.4f}")
check("moving tempo alone: no clicks", max_step(sws) < ref_step*1.9, f"max step {max_step(sws):.4f} vs untouched {ref_step:.4f}")
check("no crackle spikes in the second difference", d2_crest(sw) < d2_crest(base)*2.0 and d2_crest(sws) < d2_crest(base)*2.0, f"crest {d2_crest(sw):.1f}/{d2_crest(sws):.1f} vs {d2_crest(base):.1f}")
# abrupt jumps of the target (the slider was flicked) are still smooth
st, jump = run(fs, x, 1.0, 1.0, schedule=[(3.0, 0.7, 2**(-5/12)), (6.0, 1.6, 2**(6/12)), (9.0, 1.0, 1.0)])
check("sudden jumps of the targets: no clicks", max_step(jump) < ref_step*1.9, f"max step {max_step(jump):.4f}")

# f. position tracking: clicks at known input times
fs = 22050
n = int(fs*10); x = 0.05*tone(fs, 10.0)
marks = [int(fs*t) for t in (1.0, 2.5, 4.0, 5.5, 7.0)]
for mk in marks: x[:, mk:mk+3] += 0.9
for sp, pi_ in ((1.0,1.0),(0.7,1.0),(1.4,2**(3/12))):
    st = Stretcher(fs, 2); st.set_targets(sp, pi_)
    outs=[]; 
    for i in range(0, n, 2048): outs.append(st.process(x[:, i:i+2048]))
    y = np.concatenate(outs, axis=1)
    # find output click times
    hp = np.abs(np.diff(y[0], 2)); idx = [i for i in range(1,len(hp)-1) if hp[i] > 0.25 and hp[i] >= hp[i-1] and hp[i] >= hp[i+1]]
    # merge close detections
    found=[]; 
    for i in idx:
        if not found or i - found[-1] > fs*0.2: found.append(i)
    errs=[]
    for f in found:
        pos = st.media_pos_frames(f)
        errs.append(min(abs(pos - mk) for mk in marks)/fs)
    check(f"position tracking (speed {sp}, pitch {pi_:.2f}): within 40 ms", max(errs) < 0.04 and len(found) >= 3, f"worst error {max(errs)*1000:.1f} ms, clicks found {len(found)}")

# g. tail: finish() brings out what is still inside
fs = 22050; x = tone(fs, 3.0)
st = Stretcher(fs, 2); st.set_targets(1.0, 1.0)
y = np.concatenate([st.process(x[:, i:i+2048]) for i in range(0, x.shape[1], 2048)] + [st.finish()], axis=1)
check("finishing returns the whole song", y.shape[1] >= x.shape[1] and np.max(np.abs(y[:, :x.shape[1]-10] - x[:, :x.shape[1]-10])) < 1e-9, f"{y.shape[1]} frames out for {x.shape[1]} in")
print("\nALL ENGINE TESTS PASSED" if ok else "\nSOME ENGINE TESTS FAILED")
