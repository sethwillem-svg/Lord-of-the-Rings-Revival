'use strict';
/* Each race fights its own way (npc_slots.js STYLE.fight), and shields change the clips: checked by measurement. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { samplePose, worldBones, point } = require('../gecko/sample.js');
const { strike, blade, FIST } = require('../gecko/strike.js');
const { classLength } = require('../build_npc.js');
const { RACES } = require('../npc_slots.js');
const M = require('./lib/motion.js');

const { shipped: built } = require('./lib/shipped.js');   // the files the game ships
const ATTACK = /^attack_(sword|dagger|unarmed)_\d+$/;   // one-handed: every style applies in full
const anim = (race, name) => built[race].animations.animations[name];
const attacks = (race) => Object.keys(built[race].animations.animations).filter((n) => ATTACK.test(n));
const mean = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;

/** A race's clip posed at every sample (test/lib/motion.js, cached). */
const framesOf = (race, n) => M.frames(anim(race, n), M.RATE, `${race}/${n}`);
/** How far a rig point carried by a bone travels between two times of a race's clip (px): through every sample between
 *  them, from the exact pose at the start to the exact pose at the end. */
function travel(race, n, bone, p, from, to) {
  const at = (t) => ({ t, p: point(worldBones(samplePose(anim(race, n), t))[bone], p) });
  const s = [at(from), ...M.track(framesOf(race, n), bone, p).filter((x) => x.t > from + 1e-9 && x.t < to - 1e-9), at(to)];
  return s.slice(1).reduce((d, x, i) => d + M.len(M.sub(x.p, s[i].p)), 0);
}
/** How far a rig point travels over a race's clip from `from` seconds on (px). */
const path = (race, n, bone, p, from = 0) => travel(race, n, bone, p, from, anim(race, n).animation_length);
/** How big the wind-up is: the length of the path the weapon tip travels from the authored swing's start (after any lead-in
 *  from standing, hits.json lead) to the hit — a bigger wind-up carries the weapon further before it strikes. */
function windup(race, n) {
  const h = built[race].hits[n];
  return travel(race, n, 'weapon_r', blade('right', classLength(n), 1), h.lead || 0, h.hit);
}

/** A posed rig point in the chest's own frame (the body bone's rest frame), so turning with the torso does not count as moving. */
function inChest(w, p) {
  const v = [0, 1, 2].map((i) => p[i] - w.body.p[i]);
  return [0, 1, 2].map((c) => w.body.R[c] * v[0] + w.body.R[3 + c] * v[1] + w.body.R[6 + c] * v[2]);
}

/** The race's attacks that swing the same clip as a man's, with at least 60% of the race's style kept (hits.json). */
function pairs(race, oneHanded = true) {
  const man = built.man.hits, h = built[race].hits;
  return Object.keys(h).filter((n) => (!oneHanded || ATTACK.test(n)) && man[n] && h[n].clip === man[n].clip && h[n].style >= 0.6);
}
/** At least 3 in 4 of the race's paired attacks (those `only` keeps) show the difference; names the ones that do not. */
function mostly(race, label, holds, oneHanded = true, only = () => true) {
  const ps = pairs(race, oneHanded).filter(only), fails = ps.filter((n) => !holds(n));
  assert.ok(ps.length >= 3, `${race}: only ${ps.length} attacks to compare`);
  assert.ok(fails.length <= ps.length / 4, `${race} ${label} on only ${ps.length - fails.length}/${ps.length}; not: ${fails.join(', ')}`);
}

test('every race keeps its fighting style: at least 60% of it on 80% of its attacks, and no attack ships below the quality bar', () => {
  for (const race of RACES) {
    const v = Object.entries(built[race].hits);
    assert.deepEqual(v.filter(([, x]) => x.missesBar).map(([n]) => n), [], `${race}: attacks that miss the bar`);
    if (race === 'man') continue;
    const kept = v.filter(([, x]) => x.style >= 0.6).length;
    assert.ok(kept >= 0.8 * v.length, `${race} keeps its style on ${kept}/${v.length}`);
  }
});

test('orcs swing wild: on the same swing as a man, a bigger wind-up and a free arm that flails more', () => {
  mostly('orc', 'winds up bigger', (n) => windup('orc', n) > windup('man', n) * 1.15);
  mostly('orc', 'flails the free arm more', (n) => path('orc', n, 'weapon_l', FIST.left, built.orc.hits[n].lead || 0) > path('man', n, 'weapon_l', FIST.left, built.man.hits[n].lead || 0) * 1.25);
});

test('elves strike at full stretch: on the same swing, the tip lands further out and the free arm is held out wide', () => {
  const reach = (r, n) => -strike(anim(r, n), classLength(n)).tip[2];
  mostly('elf', 'reaches further', (n) => reach('elf', n) > reach('man', n) + 0.5);
  // abduction: the free arm's shoulder-to-hand line against the chest's vertical, in the chest's frontal plane (degrees)
  const wide = (r, n) => {
    const w = worldBones(samplePose(anim(r, n), strike(anim(r, n), classLength(n)).hit));
    const sh = inChest(w, point(w.left_arm, [-4.5, 22.5, 0])), hand = inChest(w, point(w.weapon_l, FIST.left));
    return (Math.atan2(sh[0] - hand[0], sh[1] - hand[1]) * 180) / Math.PI;
  };
  mostly('elf', 'holds the free arm out from the chest (15° more)', (n) => wide('elf', n) > wide('man', n) + 15);
});

test('dwarves drive with the body: on the same swing, more torso twist than a man', () => {
  const twist = (r, n) => strike(anim(r, n), classLength(n)).bodyYawRange;
  mostly('dwarf', 'twists more', (n) => twist('dwarf', n) > twist('man', n) * 1.1, false);
});

test('hobbits strike low and quick: on the same swing, a lower strike and a quicker wind-up than a man', () => {
  mostly('hobbit', 'strikes lower', (n) => strike(anim('hobbit', n), classLength(n)).tip[1] < strike(anim('man', n), classLength(n)).tip[1] - 1);
  mostly('hobbit', 'winds up quicker (in time)', (n) => windupTime('hobbit', n) < windupTime('man', n) * 0.9, true, ownTempo);
});

// ---- tempo: how each race times its swing (STYLE.tempo) ----
/** Unarmed blows take man's tempo in every race (spec), so race-vs-man tempo checks leave them out. */
const ownTempo = (n) => !/^attack_unarmed_/.test(n);
/** Seconds from the authored swing's start (after any lead-in) to the hit. */
const windupTime = (race, n) => built[race].hits[n].hit - (built[race].hits[n].lead || 0);
/** The tip's top speed in the strike (px/s). */
const topSpeed = (race, n) => Math.max(...M.velocities(M.track(framesOf(race, n), 'weapon_r', blade('right', classLength(n), 1))).map((x) => x.speed));

test('elves swing swiftly: on the same swing, the wind-up to the hit takes under 90% of a man\u2019s time', () => {
  mostly('elf', 'winds up swiftly', (n) => windupTime('elf', n) < windupTime('man', n) * 0.9, false, ownTempo);
});

test('elves return to their ready stance: every attack ends within 3 px of the idle pose (weapon tip and both hands)', () => {
  const idle = worldBones(samplePose(built.elf.animations.animations.idle, 0));
  const pts = (w) => [point(w.weapon_r, blade('right', 1, 1)), point(w.weapon_r, FIST.right), point(w.weapon_l, FIST.left)];
  const bad = [];
  for (const n of Object.keys(built.elf.hits)) {
    const a = anim('elf', n), w = worldBones(samplePose(a, a.animation_length)), p0 = pts(idle), p1 = pts(w);
    const far = Math.max(...p1.map((p, i) => Math.hypot(...p.map((x, j) => x - p0[i][j]))));
    if (far > 3) bad.push(`${n}: ends ${far.toFixed(1)} px from the ready stance`);
  }
  assert.deepEqual(bad, []);
});

test('orcs strike fast and wide: on the same swing, a faster strike (10% more top speed) and a wider arc', () => {
  mostly('orc', 'strikes faster', (n) => topSpeed('orc', n) > topSpeed('man', n) * 1.1, false);
  mostly('orc', 'swings a wider arc', (n) => strike(anim('orc', n), classLength(n)).tipSweep > strike(anim('man', n), classLength(n)).tipSweep * 1.1, false);
});

test('dwarves swing heavy and overhead: the wind-up lifts the weapon over the head on 3 in 4 attacks, and takes longer than a man\u2019s', () => {
  const v = Object.keys(built.dwarf.hits).filter((n) => !/^attack_(unarmed|shield)/.test(n));
  const overhead = v.filter((n) => {
    const a = anim('dwarf', n), h = built.dwarf.hits[n];
    let top = -Infinity;
    for (let t = h.lead || 0; t <= h.hit; t += 1 / 60) top = Math.max(top, point(worldBones(samplePose(a, t)).weapon_r, blade('right', classLength(n), 1))[1]);
    return top > 32;   // above the top of the head
  });
  assert.ok(overhead.length >= 0.75 * v.length, `overhead on ${overhead.length}/${v.length}: not ${v.filter((n) => !overhead.includes(n)).join(', ')}`);
  mostly('dwarf', 'winds up slower', (n) => windupTime('dwarf', n) > windupTime('man', n) * 1.15, false, ownTempo);
});

// ---- shields ----
const SHIELDED = ['idle', 'walk', 'run', 'guard', 'march', 'attack_sword_1', 'attack_spear_1', 'attack_axe_1', 'attack_dagger_1'];

test('every movement clip and attack has a shield version', () => {
  for (const race of RACES) for (const n of SHIELDED) assert.ok(anim(race, n + '_shield'), `${race} ${n}_shield`);
});

test('with a shield the off arm holds it up in front of the chest and steady, whatever the other arm does', () => {
  for (const race of RACES) for (const n of SHIELDED) {
    const a = anim(race, n + '_shield'), pts = [];
    for (let t = 0; t <= a.animation_length + 1e-9; t += a.animation_length / 12) {
      const w = worldBones(samplePose(a, t));
      pts.push(inChest(w, point(w.weapon_l, FIST.left)));
    }
    for (const p of pts) assert.ok(p[2] < -3 && p[1] > 12 && p[1] < 26, `${race} ${n}_shield: shield hand not up in front (${p.map((x) => x.toFixed(1))})`);
    const spread = Math.max(...[0, 1, 2].map((i) => Math.max(...pts.map((p) => p[i])) - Math.min(...pts.map((p) => p[i]))));
    assert.ok(spread < 1, `${race} ${n}_shield: shield hand wanders ${spread.toFixed(1)} px`);
  }
});

test('the shield version swings the weapon exactly like the plain one (same hit)', () => {
  for (const race of RACES) for (const n of SHIELDED.filter((x) => x.startsWith('attack_'))) {
    const a = strike(anim(race, n), classLength(n)), b = strike(anim(race, n + '_shield'), classLength(n));
    assert.equal(a.hit, b.hit, `${race} ${n}`);
  }
});
