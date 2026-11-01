/** A stable UUID (8-4-4-4-12 hex) derived from `seed`. */
export declare function uuidFor(seed: string): string;
/**
 * A namespaced id allocator. Two callers asking for the same key get the same uuid, and
 * the key is namespaced so a bone called "head" never collides with a cube called "head".
 */
export declare class UuidSpace {
    private readonly namespace;
    private readonly cache;
    constructor(namespace: string);
    get(key: string): string;
}
//# sourceMappingURL=uuid.d.ts.map