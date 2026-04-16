export interface SearchCriteria {
    destinationCity: string;
    destinationPlaceId?: string;
    mandatoryPois: string[];
    budgetMin: number;
    budgetMax: number;
    durationMinHours: number;
    durationMaxHours: number;
    interests: string[];
    effortLevel: string;
    weatherPreferences: string[];
}
