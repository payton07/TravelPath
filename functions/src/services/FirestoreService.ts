import * as admin from 'firebase-admin';
import { Itinerary } from '../models';
import { Logger } from '../utils/Logger';

/**
 * Gère les interactions avec Firestore :
 * 1. Cache des itinéraires générés (TTL 24h)
 * 2. Sauvegarde explicite des itinéraires favoris
 */
export class FirestoreService {
    private readonly db = admin.firestore();
    private readonly log = new Logger('FirestoreService');

    /**
     * Récupère les itinéraires mis en cache pour une clé donnée (ville + critères).
     */
    async getCachedItineraries(cacheKey: string): Promise<Itinerary[] | null> {
        try {
            const doc = await this.db.collection('journey_cache').doc(cacheKey).get();
            if (!doc.exists) return null;

            const data = doc.data();
            if (!data || Date.now() > data.expiry) {
                this.log.info(`Cache expiré ou inexistant pour : ${cacheKey}`);
                return null;
            }

            this.log.info(`Hit cache pour : ${cacheKey}`);
            return data.itineraries as Itinerary[];
        } catch (err) {
            this.log.error('Erreur lecture cache Firestore', err);
            return null;
        }
    }

    /**
     * Met en cache une liste d'itinéraires pour 24 heures.
     */
    async setCachedItineraries(cacheKey: string, itineraries: Itinerary[]): Promise<void> {
        try {
            const expiry = Date.now() + 24 * 60 * 60 * 1000; // 24h
            await this.db.collection('journey_cache').doc(cacheKey).set({
                itineraries,
                expiry,
                createdAt: admin.firestore.FieldValue.serverTimestamp()
            });
            this.log.info(`Cache mis à jour pour : ${cacheKey}`);
        } catch (err) {
            this.log.error('Erreur écriture cache Firestore', err);
        }
    }

    /**
     * Sauvegarde un itinéraire dans la collection personnelle de l'utilisateur.
     */
    async saveUserItinerary(userId: string, itinerary: Itinerary): Promise<string> {
        try {
            const docRef = await this.db.collection('users').doc(userId).collection('saved_itineraries').add({
                ...itinerary,
                savedAt: admin.firestore.FieldValue.serverTimestamp()
            });
            this.log.info(`Itinéraire sauvegardé pour l'utilisateur ${userId} : ${docRef.id}`);
            return docRef.id;
        } catch (err) {
            this.log.error('Erreur sauvegarde itinéraire utilisateur', err);
            throw new Error('Impossible de sauvegarder l\'itinéraire.');
        }
    }
}
