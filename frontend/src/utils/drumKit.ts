/**
 * 鼓机皮肤音效引擎：Web Audio 合成鼓声（无音频资源）。
 * 只在用户点击等显式手势中调用；AudioContext 懒初始化，静音状态持久化。
 */
import { ref } from 'vue';

export type DrumVoice = 'kick' | 'tom' | 'snare' | 'hat' | 'rim';

const MUTE_KEY = 'ltpms_lab_drum_muted';
const MASTER_VOLUME = 0.16;
/** 行点击的鼓垫定音：五声音阶，每 10 行升一个八度（共两个八度） */
const PENTATONIC = [0, 3, 5, 7, 10];

export const drumMuted = ref(localStorage.getItem(MUTE_KEY) === '1');
/** 每触发一次声音自增，供页头 LED 灯带走位 */
export const drumHitTick = ref(0);

let ctx: AudioContext | null = null;
let master: GainNode | null = null;
let noiseBuffer: AudioBuffer | null = null;
let unavailable = false;

function ensureContext(): AudioContext | null {
  if (unavailable) return null;
  try {
    if (!ctx) {
      ctx = new AudioContext();
      master = ctx.createGain();
      master.gain.value = MASTER_VOLUME;
      master.connect(ctx.destination);
    }
    if (ctx.state === 'suspended') {
      void ctx.resume();
    }
    return ctx;
  } catch {
    unavailable = true;
    return null;
  }
}

function envelope(c: AudioContext, peak: number, decay: number): GainNode {
  const gain = c.createGain();
  const t = c.currentTime;
  gain.gain.setValueAtTime(peak, t);
  gain.gain.exponentialRampToValueAtTime(0.001, t + decay);
  return gain;
}

function getNoise(c: AudioContext): AudioBuffer {
  if (!noiseBuffer) {
    noiseBuffer = c.createBuffer(1, c.sampleRate, c.sampleRate);
    const data = noiseBuffer.getChannelData(0);
    for (let i = 0; i < data.length; i += 1) {
      data[i] = Math.random() * 2 - 1;
    }
  }
  return noiseBuffer;
}

function playKick(c: AudioContext, out: AudioNode): void {
  const t = c.currentTime;
  const osc = c.createOscillator();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(150, t);
  osc.frequency.exponentialRampToValueAtTime(46, t + 0.12);
  const gain = envelope(c, 0.9, 0.32);
  osc.connect(gain).connect(out);
  osc.start(t);
  osc.stop(t + 0.34);
}

function playTom(c: AudioContext, out: AudioNode, variant: number): void {
  const i = ((variant % 20) + 20) % 20;
  const step = PENTATONIC[i % PENTATONIC.length] + 12 * Math.floor((i % 10) / 5);
  const freq = 140 * 2 ** (step / 12);
  const t = c.currentTime;
  const osc = c.createOscillator();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(freq * 1.4, t);
  osc.frequency.exponentialRampToValueAtTime(freq, t + 0.08);
  const gain = envelope(c, 0.85, 0.22);
  osc.connect(gain).connect(out);
  osc.start(t);
  osc.stop(t + 0.25);
}

function playSnare(c: AudioContext, out: AudioNode): void {
  const t = c.currentTime;
  const noise = c.createBufferSource();
  noise.buffer = getNoise(c);
  const hp = c.createBiquadFilter();
  hp.type = 'highpass';
  hp.frequency.value = 1600;
  const noiseGain = envelope(c, 0.5, 0.18);
  noise.connect(hp).connect(noiseGain).connect(out);
  noise.start(t);
  noise.stop(t + 0.2);

  const osc = c.createOscillator();
  osc.type = 'triangle';
  osc.frequency.setValueAtTime(190, t);
  const oscGain = envelope(c, 0.35, 0.1);
  osc.connect(oscGain).connect(out);
  osc.start(t);
  osc.stop(t + 0.12);
}

function playHat(c: AudioContext, out: AudioNode): void {
  const t = c.currentTime;
  const noise = c.createBufferSource();
  noise.buffer = getNoise(c);
  const hp = c.createBiquadFilter();
  hp.type = 'highpass';
  hp.frequency.value = 7500;
  const gain = envelope(c, 0.28, 0.06);
  noise.connect(hp).connect(gain).connect(out);
  noise.start(t);
  noise.stop(t + 0.08);
}

function playRim(c: AudioContext, out: AudioNode): void {
  const t = c.currentTime;
  const osc = c.createOscillator();
  osc.type = 'triangle';
  osc.frequency.setValueAtTime(2100, t);
  const gain = envelope(c, 0.3, 0.04);
  osc.connect(gain).connect(out);
  osc.start(t);
  osc.stop(t + 0.06);
}

/** 触发一次鼓声；variant 用于行点击的定音（行序号） */
export function playDrum(voice: DrumVoice, variant = 0): void {
  drumHitTick.value += 1;
  if (drumMuted.value) return;
  const c = ensureContext();
  if (!c || !master) return;
  switch (voice) {
    case 'kick':
      playKick(c, master);
      break;
    case 'tom':
      playTom(c, master, variant);
      break;
    case 'snare':
      playSnare(c, master);
      break;
    case 'hat':
      playHat(c, master);
      break;
    case 'rim':
      playRim(c, master);
      break;
  }
}

export function toggleDrumMute(): void {
  drumMuted.value = !drumMuted.value;
  localStorage.setItem(MUTE_KEY, drumMuted.value ? '1' : '0');
  if (!drumMuted.value) {
    playDrum('hat');
  }
}
