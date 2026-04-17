/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  AppConfig — Source unique de vérité pour la configuration serveur     ║
 * ║  Toutes les constantes et variables d'env passent par ici.             ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Avantages :
 *  - Un seul endroit à modifier pour changer une constante
 *  - Variables d'env validées au démarrage (fail-fast)
 *  - Facilite les mocks en tests (pas de process.env éparpillés)
 */

// ─── Variables d'environnement ────────────────────────────────────────────────

export const Env = {
    MAPS_API_KEY:  process.env.MAPS_API_KEY  ?? '',
    NODE_ENV:      process.env.NODE_ENV      ?? 'development',
    FIREBASE_REGION: process.env.FIREBASE_REGION ?? 'us-central1',
} as const;

// ─── Google Places ────────────────────────────────────────────────────────────

export const PlacesConfig = {
    BASE_URL:              'https://maps.googleapis.com/maps/api/place/textsearch/json',
    MAX_RESULTS_PER_INTEREST: 8,

    /** Coût moyen estimé (€) par price_level Google (0–4). */
    COST_BY_PRICE_LEVEL: {
        0: 0,
        1: 10,
        2: 25,
        3: 50,
        4: 80,
    } as Record<number, number>,

    DEFAULTS: {
        COST:            10,
        RATING:          4.0,
        DURATION_HOURS:  2,
        EFFORT_SCORE:    1,
        COMFORT_LEVEL:   2,
    },
} as const;

// ─── Génération d'itinéraires ─────────────────────────────────────────────────

export const JourneyConfig = {
    /** Nb minimal de POIs filtrés avant d'activer le fallback de pool. */
    MIN_FILTERED_POOL_SIZE:  3,

    /** Pénalité multiplicative si une catégorie est déjà présente dans le draft. */
    DIVERSITY_SCORE_PENALTY: 0.55,

    /** Poids du bonus de proximité géographique dans le score. */
    PROXIMITY_WEIGHT:        5,

    /** Bonus de score pour un POI dans le bon créneau horaire. */
    SLOT_MATCH_BONUS:        10,
} as const;

// ─── PDF ──────────────────────────────────────────────────────────────────────

export const PdfConfig = {
    /** Durée de validité de l'URL signée Storage (ms). */
    SIGNED_URL_TTL_MS: 2 * 60 * 60 * 1000, // 2 heures

    STORAGE_FOLDER: 'pdfs',

    COLORS: {
        EMERALD:    '#10B981',
        SLATE_950:  '#020617',
        SLATE_900:  '#0F172A',
        SLATE_500:  '#64748B',
        SLATE_400:  '#94A3B8',
    },

    FONTS: {
        TITLE_SIZE:    24,
        HEADING_SIZE:  20,
        SECTION_SIZE:  16,
        BODY_SIZE:     14,
        SMALL_SIZE:    12,
        CAPTION_SIZE:  10,
    },
} as const;