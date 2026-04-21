import axios, { AxiosInstance } from 'axios';
import { PointOfInterest } from '../models';
import { Env, PlacesConfig } from '../config/AppConfig';
import { OsrmService } from './OsrmService';
import { Logger } from '../utils/Logger';

/**
 * Calculs géographiques et routage utilisés par la stratégie de génération.
 */
export class RoutingService {
    private static readonly EARTH_RADIUS_KM = 6371;
    private readonly http: AxiosInstance;
    private readonly log: Logger;
    private readonly osrm: OsrmService;

    constructor(
        log: Logger = new Logger('RoutingService'),
        httpClient: AxiosInstance = axios.create({ timeout: 5000 })
    ) {
        this.log = log;
        this.http = httpClient;
        this.osrm = new OsrmService();
    }

    /**
     * Distance orthodromique (Haversine) entre deux coordonnées GPS.
     * @returns Distance en kilomètres.
     */
    calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
        const toRad = (deg: number) => (deg * Math.PI) / 180;
        const dLat = toRad(lat2 - lat1);
        const dLon = toRad(lon2 - lon1);
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLon / 2) ** 2;
        return RoutingService.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Estime le temps de trajet à pied pour une distance donnée.
     * Tente d'utiliser OSRM pour plus de précision.
     */
    async getWalkingMetrics(lat1: number, lon1: number, lat2: number, lon2: number): Promise<{ distanceKm: number, durationHours: number }> {
        return this.osrm.getRoute(lat1, lon1, lat2, lon2);
    }

    /**
     * Estime le temps de trajet à pied pour une distance donnée (simplifié).
     * @returns Durée en heures.
     */
    estimateTravelTimeHours(distanceKm: number): number {
        return distanceKm / 4.5;
    }

    /**
     * Récupère la polyline réelle via Google Directions API.
     */
    async getRoutePolyline(pois: PointOfInterest[]): Promise<string | undefined> {
        if (pois.length < 2 || !Env.MAPS_API_KEY) return undefined;

        try {
            const origin = `${pois[0].latitude},${pois[0].longitude}`;
            const destination = `${pois[pois.length - 1].latitude},${pois[pois.length - 1].longitude}`;
            
            // Les étapes intermédiaires (waypoints)
            const waypoints = pois.slice(1, -1)
                .map(p => `via:${p.latitude},${p.longitude}`)
                .join('|');

            const response = await this.http.get(PlacesConfig.DIRECTIONS_URL, {
                params: {
                    origin,
                    destination,
                    waypoints,
                    mode: 'walking',
                    key: Env.MAPS_API_KEY
                }
            });

            if (response.data.status === 'OK' && response.data.routes?.length > 0) {
                this.log.info(`Polyline récupérée avec succès (${pois.length} étapes)`);
                return response.data.routes[0].overview_polyline.points;
            } else {
                this.log.warn(`Directions API Status: ${response.data.status}`);
                return undefined;
            }
        } catch (err) {
            this.log.error('Erreur lors de l\'appel Directions API', err);
            return undefined;
        }
    }
}
