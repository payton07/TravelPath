package com.example.travelpath.data.remote;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Single;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source de données distante — encapsule tous les appels aux Firebase Cloud Functions.
 *
 * Responsabilité : appeler le réseau et retourner des types métier.
 * Le mapping Map→Entity est délégué à {@link RemoteMapper}.
 * La logique de cache est dans {@link com.example.travelpath.data.repository.TravelRepository}.
 *
 * Toutes les méthodes retournent des {@link Single} RxJava3 — pas de callbacks.
 */
public final class FirebaseDataSource {

    private final FirebaseFunctions functions;
    private final RemoteMapper      mapper;

    public FirebaseDataSource(FirebaseFunctions functions, Gson gson) {
        this.functions = functions;
        this.mapper    = new RemoteMapper(gson);
    }

    // =========================================================================
    // Cloud Functions
    // =========================================================================

    /**
     * Appelle generateJourneys() et retourne 3 itinéraires typés.
     */
    public Single<List<Itinerary>> generateJourneys(SearchCriteria criteria) {
        return Single.create(emitter -> {
            Map<String, Object> payload = buildCriteriaPayload(criteria);

            functions.getHttpsCallable("generateJourneys")
                    .call(payload)
                    .addOnSuccessListener(result -> {
                        try {
                            Map<String, Object> res = castMap(result.getData());
                            if (!"success".equals(res.get("status"))) {
                                emitter.onError(new Exception("Erreur serveur : statut inattendu"));
                                return;
                            }
                            List<Map<String, Object>> list = castList(res.get("data"));
                            emitter.onSuccess(mapper.mapItineraries(list, criteria));
                        } catch (Exception e) {
                            emitter.onError(new Exception("Erreur de mapping serveur", e));
                        }
                    })
                    .addOnFailureListener(e ->
                        emitter.onError(new Exception("Erreur réseau generateJourneys : " + e.getMessage(), e))
                    );
        });
    }

    /**
     * Appelle generatePDF() et retourne l'URL de téléchargement.
     */
    public Single<String> generatePdf(Itinerary itinerary) {
        return Single.create(emitter -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name",        itinerary.getName());
            payload.put("description", itinerary.getDescription());
            payload.put("cost",        itinerary.getCost());
            payload.put("duration",    itinerary.getDuration());
            payload.put("steps",       itinerary.getSteps());

            functions.getHttpsCallable("generatePDF")
                    .call(payload)
                    .addOnSuccessListener(result -> {
                        try {
                            Map<String, Object> res = castMap(result.getData());
                            if (!"success".equals(res.get("status"))) {
                                emitter.onError(new Exception("Erreur serveur PDF"));
                                return;
                            }
                            Object url = res.get("url");
                            if (url instanceof String) {
                                emitter.onSuccess((String) url);
                            } else {
                                emitter.onError(new Exception("URL PDF invalide"));
                            }
                        } catch (Exception e) {
                            emitter.onError(new Exception("Erreur de mapping PDF", e));
                        }
                    })
                    .addOnFailureListener(e ->
                        emitter.onError(new Exception("Erreur réseau generatePDF", e))
                    );
        });
    }

    /**
     * Appelle rateItinerary() — like ou délike.
     */
    public Single<Void> rateItinerary(String itineraryId, boolean liked) {
        return Single.create(emitter -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("itineraryId", itineraryId);
            payload.put("liked",       liked);

            functions.getHttpsCallable("rateItinerary")
                    .call(payload)
                    .addOnSuccessListener(r -> emitter.onSuccess(null))
                    .addOnFailureListener(e ->
                        emitter.onError(new Exception("Erreur rateItinerary", e))
                    );
        });
    }

    /**
     * Appelle saveUserItinerary() — sauvegarde cloud explicite.
     */
    public Single<String> saveItineraryCloud(Itinerary itinerary) {
        return Single.create(emitter -> {
            Map<String, Object> payload = buildItineraryPayload(itinerary);

            functions.getHttpsCallable("saveUserItinerary")
                    .call(payload)
                    .addOnSuccessListener(result -> {
                        try {
                            Map<String, Object> res = castMap(result.getData());
                            Object docId = res.get("id");
                            emitter.onSuccess(docId instanceof String ? (String) docId : "");
                        } catch (Exception e) {
                            emitter.onError(new Exception("Erreur mapping saveItinerary", e));
                        }
                    })
                    .addOnFailureListener(e ->
                        emitter.onError(new Exception("Erreur réseau saveItinerary", e))
                    );
        });
    }

    /**
     * Appelle shareItinerary() et retourne l'URL de partage.
     */
    public Single<String> shareItinerary(Itinerary itinerary) {
        return Single.create(emitter -> {
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("itinerary", buildItineraryPayload(itinerary));

            functions.getHttpsCallable("shareItinerary")
                    .call(wrapper)
                    .addOnSuccessListener(result -> {
                        try {
                            Map<String, Object> res = castMap(result.getData());
                            Object url = res.get("url");
                            emitter.onSuccess(url instanceof String ? (String) url : "");
                        } catch (Exception e) {
                            emitter.onError(new Exception("Erreur mapping shareItinerary", e));
                        }
                    })
                    .addOnFailureListener(e ->
                        emitter.onError(new Exception("Erreur réseau shareItinerary", e))
                    );
        });
    }

    // =========================================================================
    // Helpers de construction des payloads
    // =========================================================================

    private Map<String, Object> buildCriteriaPayload(SearchCriteria c) {
        Map<String, Object> m = new HashMap<>();
        m.put("destinationCity",    c.getDestinationCity());
        m.put("destinationPlaceId", c.getDestinationPlaceId());
        m.put("mandatoryPois",      c.getMandatoryPois());
        m.put("excludeIds",         c.getExcludeIds());
        m.put("budgetMin",          c.getBudgetMin());
        m.put("budgetMax",          c.getBudgetMax());
        m.put("durationMinHours",   c.getDurationMinHours());
        m.put("durationMaxHours",   c.getDurationMaxHours());
        m.put("interests",          c.getInterests());
        m.put("effortLevel",        c.getEffortLevel());
        m.put("weatherPreferences", c.getWeatherPreferences());
        return m;
    }

    private Map<String, Object> buildItineraryPayload(Itinerary it) {
        Map<String, Object> m = new HashMap<>();
        m.put("name",        it.getName());
        m.put("description", it.getDescription());
        m.put("cost",        it.getCost());
        m.put("duration",    it.getDuration());
        m.put("effort",      it.getEffort());
        m.put("weather",     it.getWeather());
        m.put("steps",       it.getSteps());
        m.put("routeType",   it.getRouteType());
        return m;
    }

    // ── Casts sûrs ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        throw new ClassCastException("Réponse Firebase inattendue : " + (o != null ? o.getClass() : "null"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object o) {
        if (o instanceof List) return (List<Map<String, Object>>) o;
        return java.util.Collections.emptyList();
    }
}
