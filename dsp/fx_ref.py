"""Reference implementation of the Wave effects chain. The Kotlin EffectsProcessor is a line-by-line port of this file."""
import math

UNDERWATER, MUFFLE, REVERB, ECHO, EIGHT_D = 0, 1, 2, 3, 4
EPS = 0.0005

class Biquad:
    def __init__(self, fs, fc, q):
        w0 = 2 * math.pi * fc / fs
        c = math.cos(w0); alpha = math.sin(w0) / (2 * q)
        a0 = 1 + alpha
        self.b0 = (1 - c) / 2 / a0; self.b1 = (1 - c) / a0; self.b2 = self.b0
        self.a1 = -2 * c / a0; self.a2 = (1 - alpha) / a0
        self.z1 = 0.0; self.z2 = 0.0
    def run(self, x):
        y = self.b0 * x + self.z1
        self.z1 = self.b1 * x - self.a1 * y + self.z2
        self.z2 = self.b2 * x - self.a2 * y
        return y
    def clear(self): self.z1 = 0.0; self.z2 = 0.0

def one_pole(fs, fc): return 1 - math.exp(-2 * math.pi * fc / fs)

COMB = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617]
ALLP = [556, 441, 341, 225]
SPREAD = 23

class Effects:
    def __init__(self, fs):
        self.fs = fs
        self.k = 1 - math.exp(-1 / (0.03 * fs))
        self.s = [0.0] * 5
        self.target = [0.0] * 5
        self.was = [False] * 5
        sc = fs / 44100.0
        # underwater
        self.wd = [[0.0] * 256, [0.0] * 256]; self.wi = 0; self.wph = 0.0
        self.wlp = [Biquad(fs, 1100, 0.9), Biquad(fs, 1100, 0.9)]
        # muffle
        self.mlp = [Biquad(fs, 700, 0.7071), Biquad(fs, 700, 0.7071)]
        # 8D
        self.ring = [0.0] * 128; self.ri = 0; self.theta = 0.0
        self.a4k = one_pole(fs, 4000); self.a25k = one_pole(fs, 2500)
        self.far = [0.0, 0.0]; self.back = [0.0, 0.0]
        # echo
        self.D = int(round(0.32 * fs))
        self.eb = [[0.0] * self.D, [0.0] * self.D]; self.ei = 0
        self.ed = [0.0, 0.0]; self.aD = one_pole(fs, 3500)
        # reverb
        self.cl = [[int(round(t * sc)) + (SPREAD * ch) for t in COMB] for ch in (0, 1)]
        self.al = [[int(round(t * sc)) + (SPREAD * ch) for t in ALLP] for ch in (0, 1)]
        self.cbuf = [[[0.0] * n for n in self.cl[ch]] for ch in (0, 1)]
        self.cidx = [[0] * 8 for _ in (0, 1)]; self.cfil = [[0.0] * 8 for _ in (0, 1)]
        self.abuf = [[[0.0] * n for n in self.al[ch]] for ch in (0, 1)]
        self.aidx = [[0] * 4 for _ in (0, 1)]

    def clear_water(self):
        self.wd = [[0.0] * 256, [0.0] * 256]; self.wi = 0
        for b in self.wlp: b.clear()
    def clear_muffle(self):
        for b in self.mlp: b.clear()
    def clear_8d(self):
        self.ring = [0.0] * 128; self.far = [0.0, 0.0]; self.back = [0.0, 0.0]
    def clear_echo(self):
        self.eb = [[0.0] * self.D, [0.0] * self.D]; self.ed = [0.0, 0.0]
    def clear_reverb(self):
        self.cbuf = [[[0.0] * n for n in self.cl[ch]] for ch in (0, 1)]
        self.cfil = [[0.0] * 8 for _ in (0, 1)]
        self.abuf = [[[0.0] * n for n in self.al[ch]] for ch in (0, 1)]

    @staticmethod
    def soft_clip(x):
        a = abs(x)
        if a <= 0.9: return x
        y = 0.9 + 0.1 * math.tanh((a - 0.9) / 0.1)
        return y if x > 0 else -y

    def frame(self, l, r):
        fs = self.fs; k = self.k; s = self.s; t = self.target
        for i in range(5): s[i] += (t[i] - s[i]) * k
        act = [s[i] > EPS or t[i] > 0.0 for i in range(5)]
        for i in range(5):
            if self.was[i] and not act[i]:
                [self.clear_water, self.clear_muffle, self.clear_reverb, self.clear_echo, self.clear_8d][i]()
            self.was[i] = act[i]
        any_act = any(act)
        if not any_act: return l, r

        # ---- underwater: gently moving delay, then a low-pass, blended in by the strength
        if act[UNDERWATER]:
            su = s[UNDERWATER]
            self.wd[0][self.wi] = l; self.wd[1][self.wi] = r
            self.wph += 2 * math.pi * 0.4 / fs
            if self.wph > 2 * math.pi: self.wph -= 2 * math.pi
            d = su * 0.0014 * fs * (0.5 + 0.5 * math.sin(self.wph))
            pos = self.wi - d
            i0 = math.floor(pos); fr = pos - i0
            i0 %= 256; i1 = (i0 + 1) % 256
            dl = self.wd[0][i0] * (1 - fr) + self.wd[0][i1] * fr
            dr = self.wd[1][i0] * (1 - fr) + self.wd[1][i1] * fr
            self.wi = (self.wi + 1) % 256
            fl = self.wlp[0].run(dl); fr_ = self.wlp[1].run(dr)
            l = dl * (1 - su) + fl * su; r = dr * (1 - su) + fr_ * su

        # ---- muffle: low-pass blended in
        if act[MUFFLE]:
            sm = s[MUFFLE]
            fl = self.mlp[0].run(l); fr_ = self.mlp[1].run(r)
            l = l * (1 - sm) + fl * sm; r = r * (1 - sm) + fr_ * sm

        # ---- 8D: the sound circles around the head
        if act[EIGHT_D]:
            s8 = s[EIGHT_D]
            m = (l + r) * 0.5
            self.ring[self.ri] = m
            self.theta += 2 * math.pi * 0.11 / fs
            if self.theta > 2 * math.pi: self.theta -= 2 * math.pi
            depth = 0.3 + 0.7 * s8
            p = math.sin(self.theta) * depth
            front = math.cos(self.theta)
            itd = abs(p) * 0.00055 * fs
            dlft = itd if p > 0 else 0.0
            drgt = itd if p < 0 else 0.0
            def tap(dly):
                pos = self.ri - dly
                i0 = math.floor(pos); fr = pos - i0
                i0 %= 128; i1 = (i0 + 1) % 128
                return self.ring[i0] * (1 - fr) + self.ring[i1] * fr
            left = tap(dlft); right = tap(drgt)
            self.ri = (self.ri + 1) % 128
            ang = (p + 1) * math.pi / 4
            left *= math.cos(ang) * 1.41421356; right *= math.sin(ang) * 1.41421356
            self.far[0] += self.a4k * (left - self.far[0]); self.far[1] += self.a4k * (right - self.far[1])
            if p > 0: left += (self.far[0] - left) * 0.6 * abs(p)
            if p < 0: right += (self.far[1] - right) * 0.6 * abs(p)
            self.back[0] += self.a25k * (left - self.back[0]); self.back[1] += self.a25k * (right - self.back[1])
            if front < 0:
                bk = -front * 0.5 * depth
                left += (self.back[0] - left) * bk; right += (self.back[1] - right) * bk
            mix = min(1.0, s8 * 5.0)
            l = l * (1 - mix) + left * mix; r = r * (1 - mix) + right * mix

        # ---- echo: ping-pong repeats
        if act[ECHO]:
            se = s[ECHO]
            i = self.ei
            eL = self.eb[0][i]; eR = self.eb[1][i]
            self.ed[0] += self.aD * (eL - self.ed[0]); self.ed[1] += self.aD * (eR - self.ed[1])
            self.eb[0][i] = l + self.ed[1] * 0.42
            self.eb[1][i] = r + self.ed[0] * 0.42
            self.ei = (i + 1) % self.D
            l += se * 0.6 * eL; r += se * 0.6 * eR

        # ---- reverb (Freeverb style)
        if act[REVERB]:
            sr = s[REVERB]
            inp = (l + r) * 0.015
            outs = [0.0, 0.0]
            for ch in (0, 1):
                acc = 0.0
                for c in range(8):
                    buf = self.cbuf[ch][c]; ix = self.cidx[ch][c]
                    o = buf[ix]
                    self.cfil[ch][c] = o * 0.92 + self.cfil[ch][c] * 0.08
                    buf[ix] = inp + self.cfil[ch][c] * 0.88
                    self.cidx[ch][c] = (ix + 1) % len(buf)
                    acc += o
                for a in range(4):
                    buf = self.abuf[ch][a]; ix = self.aidx[ch][a]
                    bo = buf[ix]
                    out = -acc + bo
                    buf[ix] = acc + bo * 0.5
                    self.aidx[ch][a] = (ix + 1) % len(buf)
                    acc = out
                outs[ch] = acc
            l += sr * 0.6 * outs[0]; r += sr * 0.6 * outs[1]

        return self.soft_clip(l), self.soft_clip(r)
