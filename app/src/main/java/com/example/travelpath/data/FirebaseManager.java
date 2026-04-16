package com.example.travelpath.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager centralisé pour les services Firebase.
 * Permet d'accéder aux instances Auth, Firestore, Storage et Functions de manière cohérente.
 */
public class FirebaseManager {
    private static FirebaseManager instance;
    
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final FirebaseFunctions functions;

    private FirebaseManager() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        functions = FirebaseFunctions.getInstance();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }
    
    public FirebaseFunctions getFunctions() {
        return functions;
    }

    /**
     * Appelle la Cloud Function pour générer 3 itinéraires basés sur les critères.
     */
    public Single<List<Itinerary>> generateJourneys(SearchCriteria criteria) {
        return Single.create(emitter -> {
            Map<String, Object> data = new HashMap<>();
            data.put("destinationCity", criteria.getDestinationCity());
            data.put("destinationPlaceId", criteria.getDestinationPlaceId());
            data.put("mandatoryPois", criteria.getMandatoryPois());
            data.put("budgetMin", criteria.getBudgetMin());
            data.put("budgetMax", criteria.getBudgetMax());
            data.put("durationMinHours", criteria.getDurationMinHours());
            data.put("durationMaxHours", criteria.getDurationMaxHours());
            data.put("interests", criteria.getInterests());
            data.put("effortLevel", criteria.getEffortLevel());
            data.put("weatherPreferences", criteria.getWeatherPreferences());

            functions.getHttpsCallable("generateJourneys")
                    .call(data)
                    .addOnSuccessListener(result -> {
                        Map<String, Object> res = (Map<String, Object>) result.getData();
                        if (res != null && "success".equals(res.get("status"))) {
                            List<Map<String, Object>> list = (List<Map<String, Object>>) res.get("data");
                            List<Itinerary> itineraries = new ArrayList<>();
                            if (list != null) {
                                for (Map<String, Object> map : list) {
                                    Itinerary it = new Itinerary();
                                    it.setName((String) map.get("name"));
                                    it.setDestinationCity(criteria.getDestinationCity());
                                    it.setDescription((String) map.get("description"));
                                    it.setCost(((Number) map.get("cost")).doubleValue());
                                    it.setDuration((String) map.get("duration"));
                                    it.setEffort((String) map.get("effort"));
                                    it.setWeather((String) map.get("weather"));
                                    it.setSteps((String) map.get("steps"));
                                    it.setRouteType((String) map.get("routeType"));
                                    
                                    // Conversion des coordonnées GPS en JSON pour Room
                                    Object coords = map.get("poiCoordinates");
                                    if (coords != null) {
                                        it.setPoiCoordinatesJson(new com.google.gson.Gson().toJson(coords));
                                    }
                                    
                                    itineraries.add(it);
                                }
                            }
                            emitter.onSuccess(itineraries);
                        } else {
                            emitter.onError(new Exception("Erreur serveur"));
                        }
                    })
                    .addOnFailureListener(emitter::onError);
        });
    }

    /**
     * Appelle la Cloud Function pour générer le PDF de l'itinéraire.
     */
    public Single<String> generatePDF(Itinerary itinerary) {
        return Single.create(emitter -> {
            Map<String, Object> data = new HashMap<>();
            data.put("name", itinerary.getName());
            data.put("description", itinerary.getDescription());
            data.put("cost", itinerary.getCost());
            data.put("duration", itinerary.getDuration());
            data.put("steps", itinerary.getSteps());

            functions.getHttpsCallable("generatePDF")
                    .call(data)
                    .addOnSuccessListener(result -> {
                        Map<String, Object> res = (Map<String, Object>) result.getData();
                        if (res != null && "success".equals(res.get("status"))) {
                            emitter.onSuccess((String) res.get("url"));
                        } else {
                            emitter.onError(new Exception("Erreur inattendue du serveur"));
                        }
                    })
                    .addOnFailureListener(emitter::onError);
        });
    }

    /**
     * @return true si un utilisateur est actuellement connecté.
     */
    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    /**
     * @return l'ID de l'utilisateur actuel ou null.
     */
    public String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
}
