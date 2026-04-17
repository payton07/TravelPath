import { RouteMode } from './RouteMode';

export interface Itinerary {
    id?: string;
    name: string;
    description: string;
    cost: number;
    duration: string;
    effort: string;
    weather: string;
    steps: string;
    poiCoordinates?: {lat: number, lng: number}[];
    imageUrl?: string;
    routeType: RouteMode;
}
