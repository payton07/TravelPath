export interface PointOfInterest {
    id: string;
    name: string;
    category: string;
    latitude: number;
    longitude: number;
    baseCost: number;
    rating: number;
    averageDurationHours: number;
    preferredTimeSlot: 'morning' | 'afternoon' | 'evening';
    weatherCompatibility: string[];
    effortScore: number;
    comfortLevel: number;
}
