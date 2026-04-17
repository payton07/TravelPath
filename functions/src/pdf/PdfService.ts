import * as admin             from 'firebase-admin';
import { v4 as uuidv4 }      from 'uuid';
import { Itinerary }          from '../models';
import { PdfConfig }          from '../config/AppConfig';
import { PdfBuilder }         from './PdfBuilder';
import { Logger }             from '../utils/Logger';

export interface PdfResult {
    url:      string;
    filename: string;
}

/**
 * Orchestre la génération PDF et son upload vers Firebase Storage.
 *
 * Responsabilités :
 *   1. Déléguer le rendu à PdfBuilder (Buffer)
 *   2. Uploader le Buffer vers Storage
 *   3. Retourner une URL signée (ou publique en fallback)
 *
 * Séparé de PdfBuilder : ce service connaît Firebase, PdfBuilder ne le connaît pas.
 */
export class PdfService {

    private readonly log     = new Logger('PdfService');
    private readonly builder = new PdfBuilder();

    async generate(itinerary: Itinerary): Promise<PdfResult> {
        const filename = `${PdfConfig.STORAGE_FOLDER}/travelpath_${uuidv4()}.pdf`;

        this.log.info(`Génération PDF : ${filename}`);

        const buffer = await this.builder.buildBuffer(itinerary);
        await this.uploadToStorage(buffer, filename);

        const url = await this.resolveDownloadUrl(filename);

        this.log.info(`PDF prêt : ${url}`);
        return { url, filename };
    }

    // =========================================================================
    // Storage
    // =========================================================================

    private async uploadToStorage(buffer: Buffer, filename: string): Promise<void> {
        const file = admin.storage().bucket().file(filename);

        await file.save(buffer, {
            metadata:  { contentType: 'application/pdf' },
            resumable: false,
        });

        this.log.info(`Upload terminé : ${filename}`);
    }

    private async resolveDownloadUrl(filename: string): Promise<string> {
        const file = admin.storage().bucket().file(filename);

        try {
            const [signedUrl] = await file.getSignedUrl({
                action:  'read',
                expires: Date.now() + PdfConfig.SIGNED_URL_TTL_MS,
            });
            return signedUrl;
        } catch (err) {
            // Fallback : URL publique si le compte de service n'a pas le rôle
            // "Service Account Token Creator" (fréquent en dev local)
            this.log.warn('getSignedUrl indisponible — URL publique utilisée en fallback.', err);
            const bucket = admin.storage().bucket().name;
            return `https://firebasestorage.googleapis.com/v0/b/${bucket}/o/${encodeURIComponent(filename)}?alt=media`;
        }
    }
}