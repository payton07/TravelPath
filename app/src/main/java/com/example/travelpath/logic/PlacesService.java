package com.example.travelpath.logic;

import android.content.Context;
import com.example.travelpath.data.models.SearchCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service chargé de récupérer des POIs réels via Google Places API.
 * Pour cette version, il simule la récupération dynamique basée sur la destination.
 */
public class PlacesService {

    private final Context context;

    public PlacesService(Context context) {
        this.context = context;
    }

    /**
     * Récupère une liste de POIs correspondant aux critères.
     * Dans une version finale, ceci ferait des appels à PlacesClient.searchByText().
     */
    public List<PointOfInterest> fetchPOIs(SearchCriteria criteria) {
        // Simulation d'un délai réseau
        try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); }

        List<PointOfInterest> allPois = PointOfInterest.getMockedPOIs();
        List<PointOfInterest> results = new ArrayList<>();

        String city = criteria.getDestinationCity().toLowerCase();

        // Filtrage par ville (Paris ou Londres dans nos mocks)
        for (PointOfInterest poi : allPois) {
            boolean cityMatch = (city.contains("paris") && poi.getId().startsWith("PAR")) ||
                               (city.contains("london") && poi.getId().startsWith("LON")) ||
                               (city.contains("londres") && poi.getId().startsWith("LON"));
            
            if (cityMatch) {
                results.add(poi);
            }
        }

        // Si la ville n'est ni Paris ni Londres, on génère quelques POIs "génériques" pour la démo
        if (results.isEmpty()) {
            results.add(new PointOfInterest("GEN_01", "Central Park of " + criteria.getDestinationCity(), 
                    PointOfInterest.CAT_NATURE, 0.0, 2.0, 1, 
                    java.util.Arrays.asList(PointOfInterest.WEATHER_ANY), 4.5, 2,
                    0.0, 0.0, "08:00", "20:00", PointOfInterest.SLOT_AFTERNOON));
            results.add(new PointOfInterest("GEN_02", "Local Museum", 
                    PointOfInterest.CAT_CULTURE, 12.0, 2.5, 1, 
                    java.util.Arrays.asList(PointOfInterest.WEATHER_ANY), 4.2, 3,
                    0.0, 0.0, "09:00", "18:00", PointOfInterest.SLOT_MORNING));
            results.add(new PointOfInterest("GEN_03", "Downtown Restaurant", 
                    PointOfInterest.CAT_FOOD, 25.0, 1.5, 1, 
                    java.util.Arrays.asList(PointOfInterest.WEATHER_ANY), 4.6, 4,
                    0.0, 0.0, "12:00", "23:00", PointOfInterest.SLOT_EVENING));
        }

        return results;
    }
}
