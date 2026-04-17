/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  TravelPath — Point d'entrée Firebase Functions                        ║
 * ║                                                                        ║
 * ║  Fonctions exposées :                                                  ║
 * ║    • generateJourneys  — génère 3 itinéraires depuis des critères      ║
 * ║    • generatePDF       — génère et stocke un PDF pour un itinéraire    ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

import * as functions from 'firebase-functions/v1';
import * as admin     from 'firebase-admin';
import * as dotenv    from 'dotenv';

// Charger .env avant toute instanciation de service
dotenv.config();

import { Env }                        from './config/AppConfig';
import { JourneyService }             from './services/JourneyService';
import { PdfService }                 from './pdf/PdfService';
import { SearchCriteriaValidator }    from './validators/SearchCriteriaValidator';
import { Itinerary }                  from './models';

// ─── Initialisation Firebase ──────────────────────────────────────────────────

admin.initializeApp();

// ─── Singletons (instanciés une seule fois à cold-start) ─────────────────────

const journeyService  = new JourneyService();
const pdfService      = new PdfService();
const criteriaValidator = new SearchCriteriaValidator();

// ─── Région ───────────────────────────────────────────────────────────────────

const fn = functions.region(Env.FIREBASE_REGION);

// =============================================================================
// generateJourneys
// =============================================================================

/**
 * Génère jusqu'à 3 itinéraires (ECONOMY / BALANCED / COMFORT).
 *
 * Payload attendu : SearchCriteria
 * Réponse        : { status: 'success', data: Itinerary[] }
 */
export const generateJourneys = fn.https.onCall(async (data, _context) => {
    functions.logger.info('generateJourneys — payload reçu :', data);

    // 1. Validation
    const { valid, errors } = criteriaValidator.validate(data);
    if (!valid) {
        throw new functions.https.HttpsError(
            'invalid-argument',
            `Données invalides : ${errors.join(' | ')}`,
        );
    }

    // 2. Normalisation (valeurs par défaut pour les champs optionnels)
    const criteria = criteriaValidator.normalize(data);

    // 3. Génération
    try {
        const itineraries = await journeyService.generate(criteria);
        return { status: 'success', data: itineraries };
    } catch (err) {
        functions.logger.error('Erreur génération itinéraires :', err);
        throw new functions.https.HttpsError(
            'internal',
            'Erreur lors de la génération des parcours.',
        );
    }
});

// =============================================================================
// generatePDF
// =============================================================================

/**
 * Génère un PDF pour un itinéraire et retourne une URL de téléchargement.
 *
 * Payload attendu : Itinerary (champs name, description, cost, duration, steps requis)
 * Réponse        : { status: 'success', url: string }
 */
export const generatePDF = fn.https.onCall(async (data, _context) => {
    functions.logger.info('generatePDF — payload reçu :', { name: data?.name });

    // Validation minimale des champs obligatoires
    if (!data?.name || !data?.steps) {
        throw new functions.https.HttpsError(
            'invalid-argument',
            'Les champs "name" et "steps" sont requis.',
        );
    }

    try {
        const itinerary = data as Itinerary;
        const result    = await pdfService.generate(itinerary);

        return { status: 'success', url: result.url };
    } catch (err) {
        functions.logger.error('Erreur génération PDF :', err);
        throw new functions.https.HttpsError(
            'internal',
            'Impossible de générer le fichier PDF.',
        );
    }
});