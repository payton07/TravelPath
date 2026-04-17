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
    weatherCompatibility: string[];
    effortScore:          number;
    comfortLevel:         number;
}

// ─── SearchCriteria ───────────────────────────────────────────────────────────
export interface SearchCriteria {
    destinationCity:     string;
    destinationPlaceId?: string;
    mandatoryPois:       string[];
    budgetMin:           number;
    budgetMax:           number;
    durationMinHours:    number;
    durationMaxHours:    number;
    interests:           string[];
    effortLevel:         string;
    weatherPreferences:  string[];
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
}

// ─── Barrel ───────────────────────────────────────────────────────────────────
// Tout est dans ce fichier unique — pas de confusion d'imports entre models