package com.example.travelpath.data.remote;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Convertit les Map brutes retournées par Firebase Functions
 * en entités {@link Itinerary} typées.
 *
 * Responsabilité unique : mapping — aucune logique réseau, aucun état.
 * Stateless → peut être instancié une seule fois et réutilisé.
 *
 * La Gson est injectée pour faciliter les tests unitaires.
 */
public final class RemoteMapper {

    private final Gson gson;

    public RemoteMapper(Gson gson) {
        this.gson = gson;
    }

    /**
     * Transforme la liste de Maps brutes Firebase en liste d'entités Itinerary.
     *
     * @param rawList       Liste de Maps retournée par la Cloud Function
     * @param criteria      Critères utilisés (pour destinationCity)
     * @return              Liste d'Itinerary prête à être stockée dans Room
     */
    public List<Itinerary> mapItineraries(
            List<Map<String, Object>> rawList,
            SearchCriteria criteria) {

        List<Itinerary> result = new ArrayList<>();
        if (rawList == null) return result;

        for (Map<String, Object> map : rawList) {
            Itinerary it = mapSingle(map, criteria);
            if (it != null) result.add(it);
        }
        return result;
    }

    private Itinerary mapSingle(Map<String, Object> map, SearchCriteria criteria) {
        if (map == null) return null;

        Itinerary it = new Itinerary();
        it.setName(            getString(map, "name"));
        it.setDestinationCity( criteria.getDestinationCity());
        it.setDescription(     getString(map, "description"));
        it.setCost(            getDouble(map, "cost"));
        it.setDuration(        getString(map, "duration"));
        it.setEffort(          getString(map, "effort"));
        it.setWeather(         getString(map, "weather"));
        it.setSteps(           getString(map, "steps"));
        it.setRouteType(       getString(map, "routeType"));
        it.setImageUrl(        getString(map, "imageUrl"));
        it.setEncodedPolyline( getString(map, "encodedPolyline"));
        it.setCachedAt(        System.currentTimeMillis());
        it.setSaved(           false);

        // Sérialisation JSON des objets complexes pour Room
        Object fullSteps = map.get("fullSteps");
        if (fullSteps != null) {
            it.setFullStepsJson(gson.toJson(fullSteps));
        }

        Object coords = map.get("poiCoordinates");
        if (coords != null) {
            it.setPoiCoordinatesJson(gson.toJson(coords));
        }

        return it;
    }

    // ── Helpers de lecture sûre des Maps ─────────────────────────────────────

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : null;
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }
}
