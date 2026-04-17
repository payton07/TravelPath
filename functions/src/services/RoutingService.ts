/**
 * Calculs géographiques utilisés par la stratégie de génération.
 *
 * Séparé en service distinct pour être mockable en tests
 * sans dépendre d'une API externe.
 */
export class RoutingService {

    private static readonly EARTH_RADIUS_KM  = 6371;
    private static readonly WALKING_SPEED_KMH = 4.5;

    /**
     * Distance orthodromique (Haversine) entre deux coordonnées GPS.
     * @returns Distance en kilomètres.
     */
    calculateDistance(
        lat1: number, lon1: number,
        lat2: number, lon2: number,
    ): number {
        const toRad = (deg: number) => (deg * Math.PI) / 180;

        const dLat = toRad(lat2 - lat1);
        const dLon = toRad(lon2 - lon1);

        const a =
            Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLon / 2) ** 2;

        return RoutingService.EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Estime le temps de trajet à pied pour une distance donnée.
     * @returns Durée en heures.
     */
    estimateTravelTimeHours(distanceKm: number): number {
        return distanceKm / RoutingService.WALKING_SPEED_KMH;
    }
}