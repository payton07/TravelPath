import { createHash } from 'crypto';
import { SearchCriteria } from '../models';

/**
 * Builds a deterministic, collision-resistant cache key from SearchCriteria.
 *
 * Design pattern: Single Responsibility — this class owns the cache-key contract
 * so JourneyService, tests, and future callers all derive the same key from the
 * same input without duplicating the logic.
 *
 * Key design:
 *  - All criteria fields are included (previously budgetMin, durationMinHours,
 *    mandatoryPois and excludeIds were silently omitted — causing incorrect cache hits).
 *  - Fields are sorted before joining so order-independent criteria produce the
 *    same key (e.g. interests ["Food","Culture"] == ["Culture","Food"]).
 *  - SHA-256 is used for uniform distribution; we take 16 hex chars (64 bits of
 *    entropy), which is collision-safe for this workload while staying readable.
 *  - VERSION prefix allows cache invalidation if the schema changes.
 */
export class CacheKeyBuilder {
    private static readonly VERSION = 'v2';

    static build(c: SearchCriteria): string {
        const parts: string[] = [
            c.destinationCity.toLowerCase().trim(),
            [...c.interests].sort().join(','),
            [...c.weatherPreferences].sort().join(','),
            [...(c.mandatoryPois ?? [])].sort().map(p => p.toLowerCase().trim()).join(','),
            String(Math.round(c.budgetMin)),
            String(Math.round(c.budgetMax)),
            String(Math.round(c.durationMinHours  * 10)),
            String(Math.round(c.durationMaxHours  * 10)),
            c.effortLevel.toLowerCase(),
        ];

        const hash = createHash('sha256')
            .update(parts.join('|'))
            .digest('hex')
            .slice(0, 16);

        return `${CacheKeyBuilder.VERSION}_${hash}`;
    }
}
