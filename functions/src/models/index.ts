// ─── RouteMode ────────────────────────────────────────────────────────────────
export enum RouteMode {
    ECONOMY  = 'ECONOMY',
    BALANCED = 'BALANCED',
    COMFORT  = 'COMFORT',
}

// ─── TimeSlot ─────────────────────────────────────────────────────────────────
export type TimeSlot = 'morning' | 'afternoon' | 'evening';

// ─── PointOfInterest ──────────────────────────────────────────────────────────
export interface PointOfInterest {
    id:                   string;
    name:                 string;
    category:             string;
    latitude:             number;
    longitude:            number;
    baseCost:             number;
    rating:               number;
    averageDurationHours: number;
    preferredTimeSlot:    TimeSlot;
    weatherCompatibility: string[]; // ex: ['SUN', 'RAIN', 'ANY']
    effortScore:          number;
    comfortLevel:         number;
    photoUrls?:           string[]; // URLs complètes vers Places Photo API
    openingHours?: {
        isOpenNow:   boolean;
        weekdayText: string[];
    };
    crowdLevel?:          'LOW' | 'MEDIUM' | 'HIGH';
    address?:             string;
}

// ─── SearchCriteria ───────────────────────────────────────────────────────────
export interface SearchCriteria {
    destinationCity:     string;
    destinationPlaceId?: string;
    mandatoryPois:       string[]; // Liste de noms ou IDs à inclure absolument
    budgetMin:           number;
    budgetMax:           number;
    durationMinHours:    number;
    durationMaxHours:    number;
    interests:           string[];
    effortLevel:         string;
    weatherPreferences:  string[];
    excludeIds?:         string[]; // Pour la regénération (Tâche 4)
}

// ─── Itinerary ────────────────────────────────────────────────────────────────
export interface Itinerary {
    id?:             string;
    name:            string;
    description:     string;
    cost:            number;
    duration:        string;
    effort:          string;
    weather:         string;
    steps:           string;
    poiCoordinates?: { lat: number; lng: number }[];
    imageUrl?:       string;
    routeType:       RouteMode;
    fullSteps?:      PointOfInterest[]; // Détails riches pour l'UI Android
    encodedPolyline?: string;           // Pour le tracé sur la carte (Sprint 3)
}

// ─── Barrel ───────────────────────────────────────────────────────────────────
// Tout est dans ce fichier unique — pas de confusion d'imports entre models
