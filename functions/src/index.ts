/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  TravelPath — Point d'entrée Firebase Functions                        ║
 * ║                                                                        ║
 * ║  Fonctions exposées :                                                  ║
 * ║    • generateJourneys    — génère 3 itinéraires (avec cache)           ║
 * ║    • generatePDF         — génère et stocke un PDF                     ║
 * ║    • saveUserItinerary   — sauvegarde un itinéraire en favori Cloud    ║
 * ║    • rateItinerary       — like/dislike un itinéraire                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

import * as functions from 'firebase-functions/v1';
import * as admin     from 'firebase-admin';
import * as dotenv    from 'dotenv';
import { v4 as uuidv4 } from 'uuid';

// Charger .env
dotenv.config();

import { Env }                        from './config/AppConfig';
import { JourneyService }             from './services/JourneyService';
import { PdfService }                 from './pdf/PdfService';
import { FirestoreService }           from './services/FirestoreService';
import { SearchCriteriaValidator }    from './validators/SearchCriteriaValidator';
import { Itinerary }                  from './models';

// ─── Initialisation Firebase ──────────────────────────────────────────────────

admin.initializeApp();

// ─── Singletons ───────────────────────────────────────────────────────────────

const journeyService    = new JourneyService();
const pdfService        = new PdfService();
const firestoreService  = new FirestoreService();
const criteriaValidator = new SearchCriteriaValidator();

// ─── Région ───────────────────────────────────────────────────────────────────

const fn = functions.region(Env.FIREBASE_REGION);

// =============================================================================
// generateJourneys
// =============================================================================

export const generateJourneys = fn.https.onCall(async (data, _context) => {
    functions.logger.info('generateJourneys — payload reçu :', data);

    const { valid, errors } = criteriaValidator.validate(data);
    if (!valid) {
        throw new functions.https.HttpsError('invalid-argument', `Données invalides : ${errors.join(' | ')}`);
    }

    const criteria = criteriaValidator.normalize(data);

    try {
        const itineraries = await journeyService.generate(criteria);
        return { status: 'success', data: itineraries };
    } catch (err) {
        functions.logger.error('Erreur génération itinéraires :', err);
        throw new functions.https.HttpsError('internal', 'Erreur lors de la génération des parcours.');
    }
});

// =============================================================================
// generatePDF
// =============================================================================

export const generatePDF = fn.https.onCall(async (data, _context) => {
    functions.logger.info('generatePDF — payload reçu :', { name: data?.name });

    if (!data?.name || !data?.steps) {
        throw new functions.https.HttpsError('invalid-argument', 'Les champs "name" et "steps" sont requis.');
    }

    try {
        const itinerary = data as Itinerary;
        const result    = await pdfService.generate(itinerary);
        return { status: 'success', url: result.url };
    } catch (err) {
        functions.logger.error('Erreur génération PDF :', err);
        throw new functions.https.HttpsError('internal', 'Impossible de générer le fichier PDF.');
    }
});

// =============================================================================
// saveUserItinerary (NOUVEAU - Tâche 3)
// =============================================================================

export const saveUserItinerary = fn.https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
        throw new functions.https.HttpsError('unauthenticated', 'Vous devez être connecté pour sauvegarder un itinéraire.');
    }

    try {
        const itinerary = data as Itinerary;
        const docId = await firestoreService.saveUserItinerary(uid, itinerary);
        return { status: 'success', id: docId };
    } catch (err) {
        throw new functions.https.HttpsError('internal', 'Erreur lors de la sauvegarde.');
    }
});

// =============================================================================
// rateItinerary (NOUVEAU - Tâche 4)
// =============================================================================

export const rateItinerary = fn.https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    const { itineraryId, liked } = data;

    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Authentification requise.');
    if (!itineraryId) throw new functions.https.HttpsError('invalid-argument', 'ID d\'itinéraire requis.');

    try {
        const db = admin.firestore();
        await db.collection('reactions').doc(`${uid}_${itineraryId}`).set({
            userId: uid,
            itineraryId,
            liked,
            timestamp: admin.firestore.FieldValue.serverTimestamp()
        });
        return { status: 'success' };
    } catch (err) {
        throw new functions.https.HttpsError('internal', 'Erreur lors de la notation.');
    }
});

// =============================================================================
// shareItinerary (NOUVEAU - Tâche 10)
// =============================================================================

export const shareItinerary = fn.https.onCall(async (data, _context) => {
    const { itinerary } = data;
    functions.logger.info('shareItinerary — Début du traitement', { name: itinerary?.name });

    if (!itinerary || !itinerary.name) {
        throw new functions.https.HttpsError('invalid-argument', 'Itinéraire invalide (nom manquant).');
    }

    try {
        const db = admin.firestore();
        const shareId = uuidv4().slice(0, 8);
        
        const docData = {
            name: itinerary.name,
            description: itinerary.description || "",
            cost: itinerary.cost || 0,
            duration: itinerary.duration || "",
            steps: itinerary.steps || "",
            imageUrl: itinerary.imageUrl || "",
            sharedAt: Date.now(), // Utilisation d'un timestamp simple pour éviter les soucis FieldValue
            expiresAt: Date.now() + (30 * 24 * 60 * 60 * 1000)
        };

        functions.logger.info(`Tentative d'écriture Firestore: shared_itineraries/${shareId}`);
        await db.collection('shared_itineraries').doc(shareId).set(docData);
        functions.logger.info('Écriture Firestore réussie');

        const shareUrl = `https://travelpath-e8f03.web.app/share/${shareId}`;
        return { status: 'success', shareId, url: shareUrl };
    } catch (err: any) {
        functions.logger.error('Erreur partage itinéraire détail:', { error: err.message, stack: err.stack });
        throw new functions.https.HttpsError('internal', `Erreur technique : ${err.message}`);
    }
});
