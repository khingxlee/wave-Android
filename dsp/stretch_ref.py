"""Reference implementation of the Wave time-stretch / pitch engine (WSOLA + resampler).
The Kotlin Stretcher is a line-by-line port of this file."""
import math
import numpy as np

class Stretcher:
    def __init__(self, fs, nc):
        self.fs = fs; self.nc = nc
        N = 1024
        while N < 0.04 * fs: N *= 2
        self.N = N; self.Hs = N // 2; self.D = self.Hs // 2
        n = np.arange(N)
        self.win = 0.5 - 0.5 * np.cos(2 * np.pi * n / N)
        self.win0 = self.win.copy(); self.win0[:self.Hs] = 1.0
        self.tau = 0.07
        self.aF = 1 - math.exp(-(self.Hs / fs) / self.tau)       # per-frame smoothing
        self.aP = 1 - math.exp(-1.0 / (self.tau * fs))           # per-sample smoothing
        self.speed_t = 1.0; self.pitch_t = 1.0
        self.reset()

    def reset(self):
        nc, N, Hs = self.nc, self.N, self.Hs
        self.x = np.zeros((nc, 0)); self.x_base = 0            # input FIFO and the absolute index of x[:,0]
        self.a = 0.0; self.prev = -1; self.k = 0
        self.acc = np.zeros((nc, N))
        self.s1 = np.zeros((nc, 0)); self.s1_base = 0           # stage-1 output FIFO
        self.frame_in = []; self.frame_base = 0                 # chosen input start of each frame
        self.rp = 0.0                                          # read position in stage-1 (absolute)
        self.speed_c = self.speed_t; self.pitch_c = self.pitch_t  # smoothed values
        self.P = self.pitch_t
        self.out_total = 0                                      # final frames produced since reset
        self.cps = []                                           # (final frame index, input position)
        self.started = False

    def set_targets(self, speed, pitch):
        self.speed_t = float(speed); self.pitch_t = float(pitch)
        if not self.started:
            self.speed_c = self.speed_t; self.pitch_c = self.pitch_t; self.P = self.pitch_t

    # ---- stage 1: WSOLA time scaling
    def _frame_ready(self):
        center = int(round(self.a))
        return self.x_base + self.x.shape[1] >= center + self.D + self.N and self.x_base <= max(0, center - self.D)

    def _process_frames(self):
        N, Hs, D = self.N, self.Hs, self.D
        while True:
            in_end = self.x_base + self.x.shape[1]
            center = int(round(self.a))
            if in_end < center + D + N: break
            first = (self.k == 0)
            # smoothed speed / pitch -> time-scale factor for this frame
            self.speed_c += (self.speed_t - self.speed_c) * (0 if first else self.aF)
            self.pitch_c += (self.pitch_t - self.pitch_c) * (0 if first else self.aF)
            T = self.speed_c / max(self.P, 1e-3)
            if first:
                s = max(0, center)
            else:
                lo = max(0, center - D); hi = center + D
                if abs(T - 1.0) < 1e-4:
                    s = max(0, min(center, hi))
                else:
                    cont = self.prev + Hs
                    ref = self._mono(cont, Hs)
                    best = -1e30; bs = lo
                    for c in range(lo, hi + 1, 4):
                        cand = self._mono(c, Hs)
                        sc = float(np.dot(cand[::4], ref[::4])) / math.sqrt(float(np.dot(cand[::4], cand[::4])) + 1e-9)
                        if sc > best: best = sc; bs = c
                    best = -1e30; s = bs
                    for c in range(max(lo, bs - 3), min(hi, bs + 3) + 1):
                        cand = self._mono(c, Hs)
                        sc = float(np.dot(cand, ref)) / math.sqrt(float(np.dot(cand, cand)) + 1e-9)
                        if sc > best: best = sc; s = c
            seg = self.x[:, s - self.x_base: s - self.x_base + N] * (self.win0 if first else self.win)
            self.acc += seg
            self.s1 = np.concatenate([self.s1, self.acc[:, :Hs]], axis=1)
            self.acc = np.concatenate([self.acc[:, Hs:], np.zeros((self.nc, Hs))], axis=1)
            self.frame_in.append(s)
            self.prev = s
            self.a += Hs * T
            self.k += 1
        # discard input nobody needs any more
        keep_from = max(0, min(self.prev if self.prev >= 0 else 0, int(round(self.a)) - D) - self.N)
        if keep_from - self.x_base > 4 * self.N:
            cut = keep_from - self.x_base
            self.x = self.x[:, cut:]; self.x_base += cut

    def _mono(self, start, n):
        i = start - self.x_base
        return self.x[:, i:i + n].mean(axis=0)

    def _in_pos(self, s1_index):
        f = int(s1_index // self.Hs)
        j = f - self.frame_base
        if j < 0: j = 0
        if j >= len(self.frame_in): j = len(self.frame_in) - 1
        return self.frame_in[j] + (s1_index - f * self.Hs)

    # ---- stage 2: resample by the pitch ratio (cubic), glide the ratio per sample
    def _resample(self, max_out=None):
        outs = []
        s1_end = self.s1_base + self.s1.shape[1]
        n_est = int((s1_end - self.rp) / max(self.P, 0.05)) + 8
        if n_est <= 0: return np.zeros((self.nc, 0))
        res = np.zeros((self.nc, n_est)); m = 0
        rp = self.rp; P = self.P; Pt = self.pitch_t; aP = self.aP
        last_cp = self.out_total
        while True:
            i = int(math.floor(rp))
            if i + 2 >= s1_end: break
            t = rp - i; o = i - self.s1_base
            y0 = self.s1[:, o - 1] if o - 1 >= 0 else self.s1[:, o]
            y1 = self.s1[:, o]; y2 = self.s1[:, o + 1]; y3 = self.s1[:, o + 2]
            res[:, m] = y1 + 0.5 * t * (y2 - y0 + t * (2 * y0 - 5 * y1 + 4 * y2 - y3 + t * (3 * (y1 - y2) + y3 - y0)))
            m += 1
            if (self.out_total + m) - last_cp >= 256:
                self.cps.append((self.out_total + m, self._in_pos(rp))); last_cp = self.out_total + m
            rp += P; P += (Pt - P) * aP
            if m >= n_est: break
        self.rp = rp; self.P = P
        self.out_total += m
        # drop stage-1 samples that are behind the read pointer
        drop = int(math.floor(self.rp)) - 2 - self.s1_base
        if drop > 4 * self.N:
            self.s1 = self.s1[:, drop:]; self.s1_base += drop
            fdrop = (self.s1_base // self.Hs) - self.frame_base
            if fdrop > 2:
                self.frame_in = self.frame_in[fdrop - 2:]; self.frame_base += fdrop - 2
        return res[:, :m]

    def process(self, block):
        """block: (nc, n) float array. Returns (nc, m) float array of output."""
        self.started = True
        self.x = np.concatenate([self.x, block], axis=1)
        self._process_frames()
        out = self._resample()
        if self.cps and len(self.cps) > 64: self.cps = self.cps[-64:]
        return out

    def finish(self):
        """Push silence so everything still inside the engine comes out."""
        pad = np.zeros((self.nc, 3 * self.N))
        return self.process(pad)

    def media_pos_frames(self, out_frame):
        """Input position (frames) for a final-output frame index."""
        cps = self.cps
        if not cps: return float(out_frame)
        if out_frame <= cps[0][0]:
            f0, p0 = cps[0]
            return p0 - (f0 - out_frame) * (self.speed_c)
        for i in range(1, len(cps)):
            if out_frame <= cps[i][0]:
                f0, p0 = cps[i - 1]; f1, p1 = cps[i]
                return p0 + (p1 - p0) * (out_frame - f0) / max(1, f1 - f0)
        f1, p1 = cps[-1]
        return p1 + (out_frame - f1) * self.speed_c
