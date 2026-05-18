/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  TravelPath — Point d'entrée Firebase Functions                        ║
 * ║                                                                        ║
 * ║  Fonctions exposées :                                                  ║
 * ║    • generateJourneys       — génère 3 itinéraires (avec cache)        ║
 * ║    • generatePDF            — génère et stocke un PDF                  ║
 * ║    • saveUserItinerary      — sauvegarde un itinéraire en favori Cloud ║
 * ║    • rateItinerary          — like/dislike un itinéraire               ║
 * ║    • shareItinerary         — crée un lien de partage public           ║
 * ║    • onNotificationCreated  — pousse une notif FCM pour likes/comments ║
 * ║    • onMessageCreated       — pousse une notif FCM pour les messages   ║
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
admin.firestore().settings({ ignoreUndefinedProperties: true });

// ─── Lazy Getters ─────────────────────────────────────────────────────────────

let journeyService:    JourneyService;
let pdfService:        PdfService;
let firestoreService:  FirestoreService;
let criteriaValidator: SearchCriteriaValidator;

const getJourneyService = () => journeyService || (journeyService = JourneyService.create());
const getPdfService     = () => pdfService     || (pdfService     = new PdfService());
const getFirestore      = () => firestoreService || (firestoreService = new FirestoreService());
const getValidator      = () => criteriaValidator || (criteriaValidator = new SearchCriteriaValidator());

// ─── Région ───────────────────────────────────────────────────────────────────

const fn = functions.region(Env.FIREBASE_REGION);

// =============================================================================
// generateJourneys
// =============================================================================

export const generateJourneys = fn.https.onCall(async (data, _context) => {
    functions.logger.info('generateJourneys — payload reçu :', data);

    try {
        const validator = getValidator();
        const { valid, errors } = validator.validate(data);
        if (!valid) {
            throw new functions.https.HttpsError('invalid-argument', `Données invalides : ${errors.join(' | ')}`);
        }

        const criteria = validator.normalize(data);
        const itineraries = await getJourneyService().generate(criteria);
        return { status: 'success', data: itineraries };
    } catch (err: any) {
        functions.logger.error('CRASH generateJourneys :', {
            message: err.message,
            stack: err.stack
        });
        // Si c'est déjà une HttpsError, on la relance telle quelle
        if (err instanceof functions.https.HttpsError) throw err;
        throw new functions.https.HttpsError('internal', `Erreur serveur : ${err.message}`);
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
        const result    = await getPdfService().generate(itinerary);
        return { status: 'success', url: result.url };
    } catch (err: any) {
        functions.logger.error('Erreur génération PDF :', err);
        throw new functions.https.HttpsError('internal', `Erreur PDF : ${err.message}`);
    }
});

// =============================================================================
// saveUserItinerary
// =============================================================================

export const saveUserItinerary = fn.https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
        throw new functions.https.HttpsError('unauthenticated', 'Vous devez être connecté.');
    }

    try {
        const itinerary = data as Itinerary;
        const docId = await getFirestore().saveUserItinerary(uid, itinerary);
        return { status: 'success', id: docId };
    } catch (err: any) {
        throw new functions.https.HttpsError('internal', `Erreur sauvegarde : ${err.message}`);
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
    } catch (err: any) {
        functions.logger.error('Erreur rateItinerary :', err);
        throw new functions.https.HttpsError('internal', `Erreur notation : ${err.message}`);
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
            sharedAt: Date.now(),
            expiresAt: Date.now() + (30 * 24 * 60 * 60 * 1000)
        };

        await db.collection('shared_itineraries').doc(shareId).set(docData);
        const shareUrl = `https://travelpath-e8f03.web.app/share/${shareId}`;
        return { status: 'success', shareId, url: shareUrl };
    } catch (err: any) {
        functions.logger.error('Erreur partage itinéraire :', err);
        throw new functions.https.HttpsError('internal', `Erreur partage : ${err.message}`);
    }
});

// =============================================================================
// Helpers FCM
// =============================================================================

async function getFcmToken(uid: string): Promise<string | null> {
    const snap = await admin.firestore().collection('users').doc(uid).get();
    return snap.exists ? (snap.data()?.fcmToken ?? null) : null;
}

async function sendPush(token: string, notification: { title: string; body: string }, data: Record<string, string>): Promise<void> {
    try {
        await admin.messaging().send({ token, notification, data });
    } catch (err: any) {
        functions.logger.warn(`FCM send échoué pour token ${token.slice(0, 8)}… :`, err.message);
    }
}

// =============================================================================
// onNotificationCreated — push FCM pour likes, commentaires, follows
// =============================================================================

export const onNotificationCreated = fn.firestore
    .document('notifications/{notifId}')
    .onCreate(async (snap) => {
        const data = snap.data();
        if (!data) return;

        const { userId, type, fromUserName, photoId } = data;
        if (!userId || !type) return;

        const token = await getFcmToken(userId);
        if (!token) return;

        let title = 'Traveling';
        let body  = '';
        const sender = fromUserName || 'Quelqu\'un';

        switch (type) {
            case 'NEW_LIKE':
                title = '❤️ Nouveau like';
                body  = `${sender} a aimé votre photo.`;
                break;
            case 'NEW_COMMENT':
                title = '💬 Nouveau commentaire';
                body  = `${sender} a commenté votre photo.`;
                break;
            case 'NEW_FOLLOWER':
                title = '👤 Nouvel abonné';
                body  = `${sender} vous suit maintenant.`;
                break;
            case 'NEW_GROUP_PHOTO':
                title = '📸 Nouvelle photo dans le groupe';
                body  = `${sender} a partagé une photo.`;
                break;
            default:
                title = 'Traveling';
                body  = `Nouvelle activité de ${sender}.`;
        }

        const fcmData: Record<string, string> = {
            type:    type === 'NEW_FOLLOWER' ? 'follow' : type.includes('LIKE') ? 'like' : 'comment',
            photoId: photoId ?? '',
        };

        await sendPush(token, { title, body }, fcmData);
        functions.logger.info(`Notif FCM envoyée → ${userId} (${type})`);
    });

// =============================================================================
// onMessageCreated — push FCM pour les nouveaux messages
// =============================================================================

export const onMessageCreated = fn.firestore
    .document('conversations/{convId}/messages/{msgId}')
    .onCreate(async (snap, context) => {
        const msg = snap.data();
        if (!msg) return;

        const { senderId, senderName, text, messageType } = msg;
        const convId = context.params.convId as string;
        if (!senderId || !convId) return;

        // Récupérer la conversation pour obtenir les participants
        const convSnap = await admin.firestore()
            .collection('conversations')
            .doc(convId)
            .get();
        if (!convSnap.exists) return;

        const conv    = convSnap.data()!;
        const convType: string  = conv.type ?? 'direct';
        const convTitle: string = conv.title ?? '';
        const participants: string[] = conv.participantIds ?? [];

        const body = messageType === 'shared_photo'
            ? `${senderName || 'Quelqu\'un'} a partagé une photo.`
            : (text && text.length > 0 ? text : '📷');

        const chatTitle = convType === 'group' ? (convTitle || 'Groupe') : (senderName || 'Message');

        const truncatedBody = body.length > 100 ? body.slice(0, 97) + '…' : body;
        const notifData: Record<string, string> = {
            type:           'message',
            conversationId: convId,
            chatType:       convType,
            chatTitle:      chatTitle,
        };

        // Envoyer à tous les participants sauf l'expéditeur
        const sends = participants
            .filter(uid => uid !== senderId)
            .map(async uid => {
                const token = await getFcmToken(uid);
                if (token) await sendPush(token, { title: chatTitle, body: truncatedBody }, notifData);
            });

        await Promise.allSettled(sends);
        functions.logger.info(`Message FCM envoyé (conv: ${convId}, ${sends.length} destinataire(s))`);
    });
