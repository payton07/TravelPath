package com.example.travelpath.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;
import com.example.travelpath.data.entities.Itinerary;
import io.reactivex.rxjava3.core.Single;
import java.util.HashMap;
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
