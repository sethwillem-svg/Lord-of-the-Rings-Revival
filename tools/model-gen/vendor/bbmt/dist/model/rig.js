/**
 * A bone (Blockbench group) hierarchy.
 *
 * Bones own a pivot and a parent; cubes attach to bones. This is the only structure that
 * makes a model animatable, so every rig is built by declaring parents explicitly and the
 * class refuses cycles and duplicate names instead of silently producing a broken outliner.
 */
export class Rig {
    bones = new Map();
    add(name, options) {
        if (this.bones.has(name))
            throw new Error(`rig: bone "${name}" already exists`);
        const parent = options.parent ?? null;
        // Checked before existence so a self-parent reports the clearer error.
        if (name === parent)
            throw new Error(`rig: bone "${name}" cannot parent itself`);
        if (parent !== null && !this.bones.has(parent)) {
            throw new Error(`rig: parent "${parent}" of bone "${name}" does not exist yet — declare parents first`);
        }
        const bone = {
            name,
            pivot: [...options.pivot],
            parent,
            rotation: options.rotation ? [...options.rotation] : [0, 0, 0],
            color: options.color ?? 0,
            visibility: options.visibility ?? true,
            scope: 0,
        };
        this.bones.set(name, bone);
        return bone;
    }
    /** Add only if absent; returns the existing bone otherwise. Handy for idempotent specs. */
    ensure(name, options) {
        return this.bones.get(name) ?? this.add(name, options);
    }
    get(name) {
        const bone = this.bones.get(name);
        if (!bone)
            throw new Error(`rig: unknown bone "${name}"`);
        return bone;
    }
    has(name) {
        return this.bones.has(name);
    }
    get size() {
        return this.bones.size;
    }
    get names() {
        return [...this.bones.keys()];
    }
    all() {
        return [...this.bones.values()];
    }
    childrenOf(name) {
        return this.all().filter((bone) => bone.parent === name);
    }
    root() {
        return this.all().find((bone) => bone.parent === null);
    }
    /** Bones ordered so every parent precedes its children (insertion order as tiebreak). */
    ordered() {
        const out = [];
        const seen = new Set();
        const visit = (bone) => {
            if (seen.has(bone.name))
                return;
            seen.add(bone.name);
            out.push(bone);
            for (const child of this.childrenOf(bone.name))
                visit(child);
        };
        for (const bone of this.all())
            if (bone.parent === null)
                visit(bone);
        for (const bone of this.all())
            visit(bone);
        return out;
    }
    /** Chain from the root down to `name`, inclusive. */
    ancestry(name) {
        const chain = [];
        let cursor = name;
        const guard = new Set();
        while (cursor) {
            if (guard.has(cursor))
                throw new Error(`rig: cycle detected at "${cursor}"`);
            guard.add(cursor);
            const bone = this.get(cursor);
            chain.unshift(bone);
            cursor = bone.parent;
        }
        return chain;
    }
    /** `name` plus every descendant, in parent-before-child order. */
    subtree(name) {
        const out = [];
        const walk = (boneName) => {
            out.push(this.get(boneName));
            for (const child of this.childrenOf(boneName))
                walk(child.name);
        };
        walk(name);
        return out;
    }
    /** Depth of a bone from the root (root = 0). */
    depth(name) {
        return this.ancestry(name).length - 1;
    }
}
//# sourceMappingURL=rig.js.map