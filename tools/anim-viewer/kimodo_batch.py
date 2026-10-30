"""Generate a batch of Kimodo clips on a Hugging Face Space and save the BVH files into a folder.

Point the Mocap Rig Viewer's "Watch folder" at the same folder and each clip appears on the model as soon as it is
saved. Put a .bbmodel in the folder too and the viewer loads it on its own.

Setup (once):  pip install gradio_client
Token:         set HF_TOKEN to a Hugging Face token so ZeroGPU uses your account's quota (anonymous quota is small).

Examples:
  python3 kimodo_batch.py --out clips "A person swings both hands overhead and slams them down to the ground"
  python3 kimodo_batch.py --out clips --count 5 --duration 3 "A person swings a sword in a wide horizontal slash"
  python3 kimodo_batch.py --out clips --seeds 7 42 "A person thrusts a spear forward"
  python3 kimodo_batch.py --out clips --prompts prompts.txt --count 10   # one prompt per line, seeds 1..10 each

Files are named <prompt-slug>_s<seed>.bvh, with a .json beside each holding the prompt and settings. Each file is
written as .part and renamed when complete, so the viewer never reads half a file.
"""
import argparse
import json
import os
import re
import shutil
import sys
import time

DEFAULT_SPACE = 'hugging-apps/kimodo-motion'


def slug(text, n=40):
    s = re.sub(r'[^a-z0-9]+', '_', text.lower()).strip('_')
    return s[:n].rstrip('_') or 'clip'


def find_endpoint(client):
    """The Space's named endpoint that takes a 'prompt' parameter, and that endpoint's parameter names."""
    api = client.view_api(return_format='dict', print_info=False)
    for name, info in api.get('named_endpoints', {}).items():
        params = [p.get('parameter_name') for p in info.get('parameters', [])]
        if 'prompt' in params:
            return name, params
    sys.exit('No endpoint with a "prompt" parameter on this Space. Endpoints: ' + ', '.join(api.get('named_endpoints', {})))


def find_bvh(value):
    """Walk a Gradio result (paths, dicts, lists) and return the first local .bvh path."""
    if isinstance(value, str):
        return value if value.lower().endswith('.bvh') and os.path.exists(value) else None
    if isinstance(value, dict):
        for k in ('path', 'value', 'name'):
            hit = find_bvh(value.get(k))
            if hit:
                return hit
        return None
    if isinstance(value, (list, tuple)):
        for v in value:
            hit = find_bvh(v)
            if hit:
                return hit
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('prompt', nargs='*', help='motion prompt(s); start with "A person ..."')
    ap.add_argument('--prompts', help='text file with one prompt per line')
    ap.add_argument('--out', default='kimodo_clips', help='folder to write .bvh files into (watch this in the viewer)')
    ap.add_argument('--seeds', nargs='+', type=int, help='exact seeds to generate')
    ap.add_argument('--count', type=int, default=1, help='takes per prompt, seeds 1..COUNT (ignored with --seeds)')
    ap.add_argument('--duration', type=float, default=3.0, help='seconds per clip (max 10)')
    ap.add_argument('--steps', type=int, default=100, help='diffusion steps')
    ap.add_argument('--text-cfg', type=float, default=2.0, help='text guidance weight')
    ap.add_argument('--no-postprocess', action='store_true', help='skip Kimodo foot-skate cleanup')
    ap.add_argument('--space', default=DEFAULT_SPACE, help=f'Hugging Face Space id (default {DEFAULT_SPACE})')
    args = ap.parse_args()

    prompts = list(args.prompt)
    if args.prompts:
        prompts += [line.strip() for line in open(args.prompts, encoding='utf-8') if line.strip() and not line.startswith('#')]
    if not prompts:
        ap.error('give at least one prompt or --prompts FILE')
    seeds = args.seeds or list(range(1, args.count + 1))

    try:
        from gradio_client import Client
    except ImportError:
        sys.exit('gradio_client is not installed: pip install gradio_client')

    os.makedirs(args.out, exist_ok=True)
    print(f'Connecting to {args.space} ...')
    client = Client(args.space, hf_token=os.environ.get('HF_TOKEN'))
    endpoint, params = find_endpoint(client)
    print(f'Endpoint {endpoint}, parameters: {", ".join(params)}')

    total, done = len(prompts) * len(seeds), 0
    for prompt in prompts:
        for seed in seeds:
            done += 1
            name = f'{slug(prompt)}_s{seed}'
            settings = {'prompt': prompt, 'seed': seed, 'duration': args.duration, 'diffusion_steps': args.steps,
                        'text_cfg': args.text_cfg, 'postprocess': not args.no_postprocess, 'post_process': not args.no_postprocess}
            kwargs = {k: v for k, v in settings.items() if k in params}
            t0 = time.time()
            print(f'[{done}/{total}] {name} ...', end=' ', flush=True)
            try:
                result = client.predict(api_name=endpoint, **kwargs)
            except Exception as e:   # one failed take should not stop the batch
                print(f'failed: {e}')
                continue
            src = find_bvh(result)
            if not src:
                print('no .bvh in the result')
                continue
            dst = os.path.join(args.out, name + '.bvh')
            shutil.copyfile(src, dst + '.part')
            os.replace(dst + '.part', dst)
            with open(os.path.join(args.out, name + '.json'), 'w', encoding='utf-8') as f:
                json.dump({'space': args.space, 'endpoint': endpoint, **kwargs}, f, indent=1)
            print(f'saved in {time.time() - t0:.0f} s')
    print(f'Done. Files are in {os.path.abspath(args.out)}')


if __name__ == '__main__':
    main()
