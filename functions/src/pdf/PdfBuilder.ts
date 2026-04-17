import PDFDocument             from 'pdfkit';
import { PassThrough }         from 'stream';
import { Itinerary }           from '../models';
import { PdfConfig }           from '../config/AppConfig';
import { Logger }              from '../utils/Logger';

/**
 * Construit un document PDF pour un itinéraire donné.
 *
 * Responsabilité unique : mise en page et rendu PDF.
 * Le stockage (Firebase Storage) est géré dans PdfService.
 *
 * Retourne un Buffer pour être agnostique du transport (Storage, email, tests…).
 */
export class PdfBuilder {

    private readonly log = new Logger('PdfBuilder');
    private readonly C   = PdfConfig.COLORS;
    private readonly F   = PdfConfig.FONTS;

    async buildBuffer(itinerary: Itinerary): Promise<Buffer> {
        return new Promise((resolve, reject) => {
            const doc    = new PDFDocument({ margin: 50, bufferPages: true });
            const chunks: Buffer[] = [];
            const stream = new PassThrough();

            stream.on('data', chunk => chunks.push(Buffer.from(chunk)));
            stream.on('end',  ()    => resolve(Buffer.concat(chunks)));
            stream.on('error', err  => {
                this.log.error('Erreur flux PDF :', err);
                reject(err);
            });

            doc.pipe(stream);
            this.render(doc, itinerary);
            doc.end();
        });
    }

    // =========================================================================
    // Rendu
    // =========================================================================

    private render(doc: PDFKit.PDFDocument, it: Itinerary): void {
        this.renderHeader(doc, it);
        this.renderSummary(doc, it);
        this.renderSteps(doc, it);
        this.renderFooter(doc);
    }

    private renderHeader(doc: PDFKit.PDFDocument, it: Itinerary): void {
        doc.fillColor(this.C.EMERALD)
           .fontSize(this.F.TITLE_SIZE)
           .text('TravelPath Journey', { align: 'center' })
           .moveDown(1);

        doc.fillColor(this.C.SLATE_950)
           .fontSize(this.F.HEADING_SIZE)
           .text(it.name, { align: 'center', underline: true })
           .moveDown(0.5);

        doc.fillColor(this.C.SLATE_500)
           .fontSize(this.F.SMALL_SIZE)
           .text(it.description, { align: 'center' })
           .moveDown(1.5);
    }

    private renderSummary(doc: PDFKit.PDFDocument, it: Itinerary): void {
        doc.fillColor(this.C.SLATE_900)
           .fontSize(this.F.BODY_SIZE)
           .text(`Durée estimée : ${it.duration}   |   Coût moyen : ${it.cost} €   |   Effort : ${it.effort}`)
           .moveDown(2);
    }

    private renderSteps(doc: PDFKit.PDFDocument, it: Itinerary): void {
        doc.fillColor(this.C.EMERALD)
           .fontSize(this.F.SECTION_SIZE)
           .text('Votre Parcours Étape par Étape', { underline: true })
           .moveDown(1);

        const steps = it.steps.split(' → ');

        doc.fillColor(this.C.SLATE_900).fontSize(this.F.SMALL_SIZE);

        steps.forEach((step, index) => {
            doc.text(`${index + 1}. ${step}`).moveDown(0.5);
        });
    }

    private renderFooter(doc: PDFKit.PDFDocument): void {
        doc.moveDown(3)
           .fillColor(this.C.SLATE_400)
           .fontSize(this.F.CAPTION_SIZE)
           .text("Généré automatiquement par l'application TravelPath", { align: 'center' });
    }
}