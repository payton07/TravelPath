package com.example.travelpath.logic;

public class RoutingService {

    /**
     * Calcule la distance entre deux points en km (Formule de Haversine).
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double longitude2) {
        double R = 6371; // Rayon de la terre en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(longitude2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Estime le temps de trajet en heures basé sur une vitesse de marche moyenne (4.5 km/h).
     */
    public double estimateTravelTimeHours(double distanceKm) {
        return distanceKm / 4.5;
    }
}
