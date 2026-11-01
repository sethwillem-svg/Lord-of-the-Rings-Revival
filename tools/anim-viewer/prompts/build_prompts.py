"""Build the per-rig prompt lists for `gen` from the minimum clip sets in specs/core/rendering-animation.md section 5c.

Run: python3 tools/anim-viewer/prompts/build_prompts.py
Writes prompts/<rig>.txt (one `clip.key | seconds | prompt` per line, what `gen --prompts` reads) and prompts/CLIPS.md.
Edit the tables below, not the generated files. Prompts describe body movement only: the models know no props,
appearance or creatures, so each race's character comes from a movement style added to every prompt.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# clip key -> (seconds, loops in game, movement prompt)
CLIPS = {
    'move.idle': (4, True, 'A person stands still in place, breathing and shifting their weight slightly'),
    'move.walk': (2.5, True, 'A person walks forward at a steady pace'),
    'move.run': (2, True, 'A person runs forward'),
    'pose.sit': (4, True, 'A person sits on the ground, resting'),
    'react.hurt': (1.5, False, 'A person is hit in the chest and staggers back a step'),
    'react.death': (3.5, False, 'A person is struck, collapses and falls to the ground, then lies still'),
    'attack.unarmed.1': (2, False, 'A person punches forward hard with the right fist'),
    'attack.sword.1': (2.5, False, 'A person raises the right arm high and chops down in front of them'),
    'attack.sword.2': (2.5, False, 'A person swings the right arm in a wide horizontal slash from right to left'),
    'hold.sword': (3, True, 'A person stands ready with the right arm held forward as if gripping a sword'),
    'defend.block.shield': (2, False, 'A person raises the left forearm in front of the body to block a blow, bracing'),
    'social.talk': (4, True, 'A person stands and talks, gesturing with both hands'),
    'emote.wave': (2.5, False, 'A person waves the right hand above the head'),
    'emote.taunt': (3, False, 'A person beats the chest with both fists and leans forward, shouting'),
    'work.farm': (4, True, 'A person bends forward and swings a hoe into the ground again and again'),
    'work.mine': (4, True, 'A person swings a pickaxe overhead and strikes the wall in front of them again and again'),
    'work.smith': (4, True, 'A person hammers down again and again on an anvil in front of them with the right arm'),
    'move.flee': (2, True, 'A person turns around and runs away in panic'),
    'emote.order': (2.5, False, 'A person points forward with the right arm and shouts a command'),
    'defend.brace.shield': (3, True, 'A person crouches slightly with the left shoulder forward, bracing behind a raised left forearm'),
    'hold.pike.lowered': (3, True, 'A person stands holding a long spear in both hands, lowered and pointed forward'),
    'ranged.bow.ready': (3, True, 'A person stands holding a bow out in the left hand with the right hand at the string, waiting'),
    'ranged.bow.draw': (2, False, 'A person raises a bow with the left arm and draws the string back to the cheek with the right hand'),
    'ranged.bow.release': (1.5, False, 'A person releases a bowstring so the right hand flies back, then lowers the bow'),
    'move.march': (2.5, True, 'A person marches forward in step with a straight back'),
    'ride.idle': (4, True, 'A person sits astride as if riding a horse, holding reins with both hands'),
    'ride.lance.couched': (3, True, 'A person sits astride as if riding a horse, a lance tucked under the right arm and pointed forward'),
    'emote.war_cry': (2.5, False, 'A person throws both arms wide and roars'),
    'move.charge': (2.5, True, 'A person charges forward at a run with the right arm raised'),
    'attack.smash.1': (3, False, 'A person raises both arms high overhead and smashes them down to the ground in front of them'),
    'attack.smash.2': (3, False, 'A person swings both arms in a wide sweeping swat from right to left'),
    'emote.shout': (2, False, 'A person cups the right hand to the mouth and shouts loudly'),
}

# movement style per rig (race character, as motion)
STYLE = {
    'man': '',
    'elf': 'moving lightly and gracefully with precise, flowing movements',
    'dwarf': 'with a low, wide stance and short, heavy, powerful movements',
    'hobbit': 'moving quickly and nimbly with small steps',
    'orc': 'hunched forward, moving aggressively with wild, savage movements',
    'olog': 'moving with huge, heavy, deliberate strength',
    'half_troll': 'hunched and heavy, moving with brutal, clumsy force',
    'troll': 'moving very slowly and heavily like a giant, with huge force',
}

BASE = ['move.idle', 'move.walk', 'move.run', 'pose.sit', 'react.hurt', 'react.death', 'attack.unarmed.1',
        'attack.sword.1', 'attack.sword.2', 'hold.sword', 'defend.block.shield', 'social.talk', 'emote.wave',
        'work.farm', 'work.smith']
FORMATION_ALL = ['move.flee', 'emote.order']
FORMATION_RIGID = ['defend.brace.shield', 'hold.pike.lowered', 'ranged.bow.ready', 'ranged.bow.draw',
                   'ranged.bow.release', 'move.march']
CAVALRY = ['ride.idle', 'ride.lance.couched']
LOOSE = ['emote.war_cry', 'move.charge', 'emote.taunt']
TROLL = ['move.idle', 'move.walk', 'move.run', 'move.charge', 'move.flee', 'react.hurt', 'react.death',
         'attack.smash.1', 'attack.smash.2', 'emote.war_cry']


def swap(lst, mapping):
    return [mapping.get(k, k) for k in lst]


# per spec section 5c (one rig per race, revision 4)
RIGS = {
    'man': BASE + FORMATION_ALL + FORMATION_RIGID + CAVALRY + LOOSE,
    'elf': BASE + FORMATION_ALL + FORMATION_RIGID,
    'dwarf': swap(BASE, {'work.farm': 'work.mine'}) + FORMATION_ALL + FORMATION_RIGID,
    'hobbit': BASE + FORMATION_ALL + ['emote.shout'],          # shirriffs: war cry played as a shout
    'orc': swap(BASE, {'emote.wave': 'emote.taunt', 'work.farm': 'work.mine'}) + FORMATION_ALL + FORMATION_RIGID + LOOSE,
    'olog': swap(BASE, {'emote.wave': 'emote.taunt', 'work.farm': 'work.mine'}) + LOOSE + ['attack.smash.1'],
    'half_troll': BASE + LOOSE + ['attack.smash.1', 'move.flee'],
    'troll': TROLL,
}


def dedupe(keys):
    seen, out = set(), []
    for k in keys:
        if k not in seen:
            seen.add(k)
            out.append(k)
    return out


def prompt_for(rig, key):
    text = CLIPS[key][2]
    return f'{text}, {STYLE[rig]}' if STYLE[rig] else text


def main():
    md = ['# Clips to generate per rig', '',
          'Generated by `build_prompts.py` from the minimum sets in `specs/core/rendering-animation.md` section 5c. '
          'Generate a rig with `gen --prompts tools/anim-viewer/prompts/<rig>.txt --count 3`.', '',
          '- **Loop** clips play on repeat in game. Neither Kimodo nor HY-Motion makes seamless loops, so pick takes '
          'whose start and end poses match, then trim and blend them before export.',
          '- `ride.*` clips are generated as a seated person; check them against the horse model.',
          '- The `horse` rig is not here: both models only make human motion.', '']
    total = 0
    for rig, keys in RIGS.items():
        keys = dedupe(keys)
        total += len(keys)
        with open(os.path.join(HERE, f'{rig}.txt'), 'w', encoding='utf-8') as f:
            f.write(f'# {rig}: {len(keys)} clips. Format: clip.key | seconds | prompt (generated by build_prompts.py)\n')
            for k in keys:
                f.write(f'{k} | {CLIPS[k][0]} | {prompt_for(rig, k)}\n')
        md += [f'## {rig} ({len(keys)} clips)', '', '| Clip | Seconds | Loop |', '| --- | --- | --- |']
        md += [f'| `{k}` | {CLIPS[k][0]} | {"yes" if CLIPS[k][1] else ""} |' for k in keys]
        md.append('')
    md.insert(4, f'Total: **{total} clips** over {len(RIGS)} rigs.')
    md.insert(5, '')
    with open(os.path.join(HERE, 'CLIPS.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(md))
    print(f'Wrote {len(RIGS)} prompt files, {total} clips.')


if __name__ == '__main__':
    main()
