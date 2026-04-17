import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";
import * as dotenv from "dotenv";
import PDFDocument = require("pdfkit");
import { v4 as uuidv4 } from "uuid";

// Charger les variables d'environnement (.env)
dotenv.config();

import { JourneyService } from "./services/JourneyService";

admin.initializeApp();

// Configuration régionale
const region = "us-central1";
const journeyService = new JourneyService();

export const generateJourneys = functions.region(region).https.onCall(async (data, context) => {
    functions.logger.info("generateJourneys appelé avec :", data);
    
    try {
        const itineraries = await journeyService.generate(data);
        return {
            status: "success",
            data: itineraries
        };
    } catch (error) {
        functions.logger.error("Erreur génération :", error);
        throw new functions.https.HttpsError('internal', 'Erreur lors de la génération des parcours');
    }
});

/**
 * Génération du PDF pour un itinéraire
 */
export const generatePDF = functions.region(region).https.onCall(async (data, context) => {
    functions.logger.info("generatePDF appelé avec :", data);
    
    // Le client Android va nous envoyer l'objet Itinerary entier 
    const { name, description, cost, duration, steps } = data;

    if (!name || !steps) {
        throw new functions.https.HttpsError('invalid-argument', 'Les informations du parcours (nom, étapes) sont requises');
    }

    try {
        // 1. Initialiser le seau (Bucket) Storage
        const bucket = admin.storage().bucket();
        const filename = `pdfs/travelpath_${uuidv4()}.pdf`;
        const file = bucket.file(filename);
        
        functions.logger.info(`Début génération PDF: ${filename} pour le seau: ${bucket.name}`);

        // 2. Créer le flux d'écriture vers Firebase Storage
        const writeStream = file.createWriteStream({
            metadata: {
                contentType: 'application/pdf',
            },
            resumable: false
        });

        writeStream.on('error', (err) => {
            functions.logger.error("Erreur de flux d'écriture Storage:", err);
        });

        // 3. Créer le document PDFKit
        const doc = new PDFDocument({ margin: 50 });
        doc.pipe(writeStream);

        // --- DESSIN DU PDF ---
        
        // Titre & En-tête
        doc.fillColor('#10B981') // Emerald 500
           .fontSize(24)
           .text('TravelPath Journey', { align: 'center' })
           .moveDown(1);

        doc.fillColor('#020617') // Slate 950
           .fontSize(20)
           .text(name, { align: 'center', underline: true })
           .moveDown(0.5);

        // Description
        doc.fontSize(12)
           .fillColor('#64748B')
           .text(description, { align: 'center' })
           .moveDown(1.5);

        // Résumé (Coût et Durée)
        doc.fillColor('#0F172A')
           .fontSize(14)
           .text(`Durée estimée : ${duration} | Coût moyen : ${cost} €`)
           .moveDown(2);

        // Étapes (Liste des POIs)
        doc.fontSize(16)
           .fillColor('#10B981')
           .text('Votre Parcours Étape par Étape', { underline: true })
           .moveDown(1);

        doc.fillColor('#0F172A').fontSize(12);
        
        // Les étapes nous sont envoyées comme une simple chaîne (ex: "Louvre → Tour Eiffel")
        const stepList = steps.split(' → ');
        stepList.forEach((step: string, index: number) => {
            doc.text(`${index + 1}. ${step}`);
            doc.moveDown(0.5);
        });

        // Footer
        doc.moveDown(3)
           .fontSize(10)
           .fillColor('#94A3B8')
           .text('Généré automatiquement par l\'application TravelPath', { align: 'center' });

        // Finaliser le document
        doc.end();

        // 4. Attendre que l'upload sur Firebase Storage soit terminé
        await new Promise((resolve, reject) => {
            writeStream.on('finish', resolve);
            writeStream.on('error', reject);
        });

        // 5. Générer une URL signée
        // Note: getSignedUrl nécessite souvent le rôle 'Service Account Token Creator'
        try {
            const [signedUrl] = await file.getSignedUrl({
                action: 'read',
                expires: Date.now() + 2 * 60 * 60 * 1000, // 2 heures
            });
            functions.logger.info("URL signée générée", { url: signedUrl });
            return { status: "success", url: signedUrl };
        } catch (signError: any) {
            functions.logger.error("Erreur getSignedUrl, tentative alternative...", signError);
            // Alternative: URL publique si le bucket le permet ou URL Firebase standard
            const publicUrl = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(filename)}?alt=media`;
            return { status: "success", url: publicUrl };
        }

    } catch (error) {
        functions.logger.error("Erreur lors de la génération du PDF", error);
        throw new functions.https.HttpsError('internal', 'Impossible de générer le fichier PDF.');
    }
});
