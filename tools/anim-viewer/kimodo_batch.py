"""Generate a batch of motion clips (Kimodo or HY-Motion) and save them as BVH files into a folder.

Point the Mocap Rig Viewer's "Watch folder" at the same folder and each clip appears on the model as soon as it is
saved. Put a .bbmodel in the folder too and the viewer loads it on its own.

Backends:
  kimodo   (default) any Gradio app serving Kimodo: a Hugging Face Space id (default hugging-apps/kimodo-motion)
           or a local one, --space http://localhost:7860. Needs: pip install gradio_client. For Spaces, set HF_TOKEN
           so ZeroGPU uses your account's quota.
  hymotion a local HY-Motion 1.0 install (github.com/Tencent-Hunyuan/HY-Motion-1.0, weights in ckpts/, `git lfs pull`
           done). --hy-repo PATH or HY_MOTION_REPO. Runs its local_infer.py once for the whole batch and converts each
           .npz to BVH as it appears. HY-Motion picks its own seeds: --count sets samples per prompt, --seeds is ignored.

Examples:
  python3 kimodo_batch.py "A person swings both hands overhead and slams them down to the ground"
  python3 kimodo_batch.py --count 5 --duration 3 "A person swings a sword in a wide horizontal slash"
  python3 kimodo_batch.py --seeds 7 42 "A person thrusts a spear forward"
  python3 kimodo_batch.py --prompts prompts.txt --count 10   # one prompt per line, seeds 1..10 each
  python3 kimodo_batch.py --space http://localhost:7860 --count 5 "A person ..."       # Kimodo running locally
  python3 kimodo_batch.py --backend hymotion --hy-repo ~/HY-Motion-1.0 --count 4 "A person ..."

Files are named <prompt-slug>_s<seed>.bvh (Kimodo) or <prompt-slug>_hy<n>.bvh (HY-Motion), with a .json beside each
holding the source model, prompt and settings. Each file is
written as .part and renamed when complete, so the viewer never reads half a file.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

DEFAULT_SPACE = 'hugging-apps/kimodo-motion'
DEFAULT_OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'clips')


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


def save_sidecar(out, name, info):
    with open(os.path.join(out, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump(info, f, indent=1)


def run_hymotion(args, prompts):
    """One local_infer.py run for the whole batch; convert each .npz to BVH in args.out as soon as it is complete."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from hymotion_to_bvh import convert, load_template
    hy = os.path.expanduser(args.hy_repo or os.environ.get('HY_MOTION_REPO', ''))
    if not hy or not os.path.isfile(os.path.join(hy, 'local_infer.py')):
        sys.exit('Set --hy-repo (or HY_MOTION_REPO) to the HY-Motion-1.0 folder that contains local_infer.py.')
    model = os.path.join(hy, 'ckpts', 'tencent', args.hy_model)
    if not os.path.isfile(os.path.join(model, 'latest.ckpt')):
        sys.exit(f'No weights at {model}. Download them as described in {hy}/ckpts/README.md.')
    template = load_template(hy)
    if args.seeds:
        print('Note: HY-Motion chooses its own seeds; --seeds is ignored, using --count samples per prompt.')
    frames = max(10, round(args.duration * 30))
    with tempfile.TemporaryDirectory() as tmp:
        inp, outd = os.path.join(tmp, 'in'), os.path.join(tmp, 'out')
        os.makedirs(inp)
        ids = {}
        with open(os.path.join(inp, 'prompts.txt'), 'w', encoding='utf-8') as f:
            for i, p in enumerate(prompts, 1):
                f.write(f'{p.replace("#", " ")}#{frames}#{i}\n')   # line format: prompt#frames#id
                ids[f'{i:08d}'] = p
        cmd = [args.hy_python, 'local_infer.py', '--model_path', model, '--input_text_dir', inp, '--output_dir', outd,
               '--disable_duration_est', '--disable_rewrite', '--num_seeds', str(args.count), '--cfg_scale', str(args.hy_cfg)]
        print('Running HY-Motion:', ' '.join(cmd))
        proc = subprocess.Popen(cmd, cwd=hy)
        done, sizes, saved = set(), {}, 0
        while True:
            alive = proc.poll() is None
            for root, _, files in os.walk(outd):
                for fn in sorted(files):
                    path = os.path.join(root, fn)
                    if not fn.endswith('.npz') or path in done:
                        continue
                    size = os.path.getsize(path)
                    if alive and sizes.get(path) != size:   # wait until the file stops growing
                        sizes[path] = size
                        continue
                    done.add(path)
                    idx, _, sample = fn[:-4].rpartition('_')
                    prompt = ids.get(idx, idx)
                    name = f'{slug(prompt)}_hy{int(sample) + 1 if sample.isdigit() else sample}'
                    try:
                        n = convert(path, template, os.path.join(args.out, name + '.bvh'))
                    except Exception as e:
                        print(f'{fn}: conversion failed: {e}')
                        continue
                    save_sidecar(args.out, name, {'source': 'hymotion', 'model': args.hy_model, 'prompt': prompt,
                                                  'sample': sample, 'duration': args.duration, 'cfg_scale': args.hy_cfg})
                    saved += 1
                    print(f'saved {name}.bvh ({n} frames)')
            if not alive:
                break
            time.sleep(2)
    if proc.returncode:
        print(f'HY-Motion exited with code {proc.returncode}.')
    print(f'Done: {saved} clip(s) in {os.path.abspath(args.out)}')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('prompt', nargs='*', help='motion prompt(s); start with "A person ..."')
    ap.add_argument('--prompts', help='text file with one prompt per line')
    ap.add_argument('--out', default=DEFAULT_OUT, help=f'folder to write .bvh files into (default {DEFAULT_OUT}, what `view` watches)')
    ap.add_argument('--seeds', nargs='+', type=int, help='exact seeds to generate')
    ap.add_argument('--count', type=int, default=1, help='takes per prompt, seeds 1..COUNT (ignored with --seeds)')
    ap.add_argument('--duration', type=float, default=3.0, help='seconds per clip (max 10)')
    ap.add_argument('--steps', type=int, default=100, help='diffusion steps')
    ap.add_argument('--text-cfg', type=float, default=2.0, help='text guidance weight')
    ap.add_argument('--no-postprocess', action='store_true', help='skip Kimodo foot-skate cleanup')
    ap.add_argument('--space', default=DEFAULT_SPACE, help=f'Kimodo: Space id or local URL (default {DEFAULT_SPACE})')
    ap.add_argument('--backend', choices=['kimodo', 'hymotion'], default='kimodo', help='which model generates (default kimodo)')
    ap.add_argument('--hy-repo', help='HY-Motion: path to the HY-Motion-1.0 repo (or set HY_MOTION_REPO)')
    ap.add_argument('--hy-model', default='HY-Motion-1.0', choices=['HY-Motion-1.0', 'HY-Motion-1.0-Lite'],
                    help='HY-Motion: model (1.0 needs about 26 GB VRAM, Lite about 24 GB)')
    ap.add_argument('--hy-cfg', type=float, default=5.0, help='HY-Motion: guidance scale (default 5)')
    ap.add_argument('--hy-python', default=sys.executable, help='HY-Motion: python of the environment HY-Motion is installed in')
    args = ap.parse_args()

    prompts = list(args.prompt)
    if args.prompts:
        prompts += [line.strip() for line in open(args.prompts, encoding='utf-8') if line.strip() and not line.startswith('#')]
    if not prompts:
        ap.error('give at least one prompt or --prompts FILE')
    seeds = args.seeds or list(range(1, args.count + 1))

    os.makedirs(args.out, exist_ok=True)
    ignore = os.path.join(args.out, '.gitignore')
    if os.path.abspath(args.out) == DEFAULT_OUT and not os.path.exists(ignore):
        with open(ignore, 'w') as f:
            f.write('*\n')   # keeps generated clips out of git
    if args.backend == 'hymotion':
        run_hymotion(args, prompts)
        return

    try:
        from gradio_client import Client
    except ImportError:
        sys.exit('gradio_client is not installed: pip install gradio_client')
    print(f'Connecting to {args.space} ...')
    token = os.environ.get('HF_TOKEN')
    try:
        client = Client(args.space, token=token)        # gradio_client 2.x
    except TypeError:
        client = Client(args.space, hf_token=token)     # gradio_client 1.x and older
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
            save_sidecar(args.out, name, {'source': 'kimodo', 'space': args.space, 'endpoint': endpoint, **kwargs})
            print(f'saved in {time.time() - t0:.0f} s')
    print(f'Done. Files are in {os.path.abspath(args.out)}')


if __name__ == '__main__':
    main()
