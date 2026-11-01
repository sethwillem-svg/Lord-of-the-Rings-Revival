/** Seeded random source: FNV-1a hash of the seed text into mulberry32. Any seed (number or text) works. */
const fnv = (text) => {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
};

export function makeRng(seed) {
  const key = String(seed);
  let a = fnv(key);
  const next = () => {   // mulberry32
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const int = (lo, hi) => lo + Math.floor(next() * (hi - lo + 1));
  return {
    next,
    int,
    range: ([lo, hi]) => int(lo, hi),
    pick: (arr) => arr[Math.floor(next() * arr.length)],
    /** Pick a key of { style: weight }; weight 0 never comes up. */
    weighted: (weights) => {
      const keys = Object.keys(weights).filter((k) => weights[k] > 0);
      let r = next() * keys.reduce((s, k) => s + weights[k], 0);
      for (const k of keys) { r -= weights[k]; if (r < 0) return k; }
      return keys[keys.length - 1];
    },
    /** An independent stream for one part, so adding a part never reshuffles the others. */
    fork: (label) => makeRng(`${key}/${label}`),
  };
}
