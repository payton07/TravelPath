import PDFDocument          from 'pdfkit';
import { PassThrough }      from 'stream';
import * as https           from 'https';
import * as http            from 'http';
import { Itinerary, PointOfInterest, RouteMode } from '../models';
import { Env, PdfConfig }   from '../config/AppConfig';
import { Logger }           from '../utils/Logger';

const P = PdfConfig.PALETTE;

const STOP_COLORS = [P.BUTTER, P.SKY, P.BLUSH, P.MINT, P.LILAC] as const;

const SLOT_LABELS: Record<string, string> = {
    morning:   'Matin',
    afternoon: 'Après-midi',
    evening:   'Soirée',
};

/**
 * Construit un document PDF "Bright & Airy" pour un itinéraire donné.
 *
 * Structure :
 *   1. Page couverture — palette pastel selon le tier, stats grid 4 cellules
 *   2. Une page par POI — photo plein cadre (aucun texte dessus), badge
 *      numéroté sur le seam photo/contenu, infos structurées en dessous
 *   3. Page carte — snapshot Google Maps Static API (polyline + marqueurs)
 *
 * Retourne un Buffer ; le stockage Firebase est géré dans PdfService.
 */
export class PdfBuilder {

    private readonly log = new Logger('PdfBuilder');

    async buildBuffer(itinerary: Itinerary): Promise<Buffer> {
        const pois         = itinerary.fullSteps ?? [];
        const photoBuffers = await this.prefetchPhotos(pois);
        const mapBuffer    = await this.fetchMapSnapshot(itinerary);

        return new Promise<Buffer>((resolve, reject) => {
            const doc    = new PDFDocument({ margin: 0, bufferPages: true, size: 'A4' });
            const chunks: Buffer[] = [];
            const stream = new PassThrough();

            stream.on('data',  (chunk: Buffer) => chunks.push(chunk));
            stream.on('end',   () => resolve(Buffer.concat(chunks)));
            stream.on('error', (err: Error) => {
                this.log.error('Erreur flux PDF :', err);
                reject(err);
            });

            doc.pipe(stream);
            this.render(doc, itinerary, photoBuffers, mapBuffer);
            this.addPageNumbers(doc);
            doc.end();
        });
    }

    // =========================================================================
    // Rendu principal
    // =========================================================================

    private render(
        doc: PDFKit.PDFDocument,
        it: Itinerary,
        photos: (Buffer | null)[],
        mapBuffer: Buffer | null,
    ): void {
        this.renderCoverPage(doc, it);

        (it.fullSteps ?? []).forEach((poi, i) => {
            doc.addPage({ margin: 0 });
            this.renderPoiPage(doc, poi, i, photos[i] ?? null);
        });

        if (mapBuffer) {
            doc.addPage({ margin: 0 });
            this.renderMapPage(doc, mapBuffer, it);
        }
    }

    // =========================================================================
    // Page de couverture
    // =========================================================================

    private renderCoverPage(doc: PDFKit.PDFDocument, it: Itinerary): void {
        const W = doc.page.width;
        const H = doc.page.height;

        // Fond pastel selon le tier
        doc.rect(0, 0, W, H).fill(this.tierBgColor(it.routeType));

        // Bande supérieure tomate
        doc.rect(0, 0, W, 6).fill(P.ACCENT);

        // Label marque
        doc.fillColor(P.INK_SOFT)
           .fontSize(9)
           .font('Helvetica')
           .text('TRAVELPATH', 50, 50, { characterSpacing: 4, lineBreak: false });

        // Badge tier (pill tomate, coin droit)
        const badgeLabel = it.routeType ?? 'PARCOURS';
        const badgeW     = 100;
        const badgeX     = W - 50 - badgeW;
        doc.roundedRect(badgeX, 42, badgeW, 24, 12).fill(P.ACCENT);
        doc.fillColor('#FFFFFF')
           .fontSize(9)
           .font('Helvetica-Bold')
           .text(badgeLabel, badgeX, 49, { width: badgeW, align: 'center', characterSpacing: 1, lineBreak: false });

        // Titre du parcours
        doc.fillColor(P.INK)
           .fontSize(34)
           .font('Helvetica-Bold')
           .text(it.name, 50, 106, { width: W - 100, lineGap: 4 });

        const nameH = doc.heightOfString(it.name, { width: W - 100 });

        // Description
        const descY = 106 + nameH + 14;
        doc.fillColor(P.INK_SOFT)
           .fontSize(13)
           .font('Helvetica')
           .text(it.description ?? '', 50, descY, { width: W - 100, lineGap: 3 });

        const descH = doc.heightOfString(it.description ?? '', { width: W - 100 });

        // Séparateur
        const divY = descY + descH + 30;
        doc.moveTo(50, divY).lineTo(W - 50, divY)
           .strokeColor(P.LINE).lineWidth(1).stroke();

        // Grille des stats
        this.renderStatsGrid(doc, it, 50, divY + 22, W - 100);

        // Nombre d'étapes
        const stepCount = (it.fullSteps ?? []).length;
        if (stepCount > 0) {
            const stepsY = divY + 22 + 78 + 20;
            doc.circle(56, stepsY + 5, 3).fill(P.ACCENT);
            doc.fillColor(P.INK_SOFT)
               .fontSize(11)
               .font('Helvetica')
               .text(
                   `${stepCount} étape${stepCount > 1 ? 's' : ''} au programme`,
                   66, stepsY,
                   { lineBreak: false },
               );
        }

        // Bande pied de page
        doc.rect(0, H - 60, W, 60).fill(P.INK);

        doc.fillColor('#FFFFFF')
           .fontSize(12)
           .font('Helvetica-Bold')
           .text('TravelPath', 50, H - 44, { lineBreak: false });

        doc.fillColor('#9E9A94')
           .fontSize(9)
           .font('Helvetica')
           .text('Itinéraire personnalisé', 50, H - 26, { lineBreak: false });

        const dateStr = new Date().toLocaleDateString('fr-FR', {
            day: 'numeric', month: 'long', year: 'numeric',
        });
        doc.fillColor('#9E9A94')
           .fontSize(9)
           .font('Helvetica')
           .text(dateStr, 0, H - 35, { width: W - 50, align: 'right', lineBreak: false });
    }

    private renderStatsGrid(
        doc: PDFKit.PDFDocument,
        it: Itinerary,
        x: number,
        y: number,
        totalW: number,
    ): void {
        const cells = [
            { label: 'COÛT',   value: `~${it.cost} €`,  color: P.BUTTER },
            { label: 'DURÉE',  value: it.duration,       color: P.SKY    },
            { label: 'EFFORT', value: it.effort,         color: P.BLUSH  },
            { label: 'MÉTÉO',  value: it.weather ?? '—', color: P.MINT   },
        ];

        const gap   = 10;
        const cellW = (totalW - gap * (cells.length - 1)) / cells.length;
        const cellH = 78;

        cells.forEach((cell, i) => {
            const cx = x + i * (cellW + gap);

            doc.roundedRect(cx, y, cellW, cellH, 12).fill(cell.color);

            doc.fillColor(P.INK)
               .fontSize(18)
               .font('Helvetica-Bold')
               .text(cell.value, cx, y + 18, { width: cellW, align: 'center', lineBreak: false });

            doc.fillColor(P.INK_SOFT)
               .fontSize(8)
               .font('Helvetica')
               .text(cell.label, cx, y + 48, {
                   width:            cellW,
                   align:            'center',
                   characterSpacing: 1.5,
                   lineBreak:        false,
               });
        });
    }

    // =========================================================================
    // Page d'un POI
    // =========================================================================

    private renderPoiPage(
        doc: PDFKit.PDFDocument,
        poi: PointOfInterest,
        index: number,
        photo: Buffer | null,
    ): void {
        const W         = doc.page.width;
        const H         = doc.page.height;
        const stopColor = STOP_COLORS[index % STOP_COLORS.length];
        const PHOTO_H   = 340;
        const BADGE_R   = 28;
        const BADGE_CX  = 60;
        const BADGE_CY  = PHOTO_H;

        // Fond crème
        doc.rect(0, 0, W, H).fill(P.BG);

        // Photo plein cadre — aucun texte, aucun overlay
        if (photo) {
            try {
                doc.image(photo, 0, 0, { cover: [W, PHOTO_H] });
            } catch {
                this.log.warn(`Photo embedding échoué pour ${poi.name}`);
                doc.rect(0, 0, W, PHOTO_H).fill(stopColor);
            }
        } else {
            doc.rect(0, 0, W, PHOTO_H).fill(stopColor);
        }

        // Badge numéroté — centré sur la jonction photo / contenu
        doc.circle(BADGE_CX, BADGE_CY, BADGE_R).fill(stopColor);
        doc.fillColor(P.INK)
           .fontSize(16)
           .font('Helvetica-Bold')
           .text(String(index + 1), BADGE_CX - BADGE_R, BADGE_CY - 9, {
               width:     BADGE_R * 2,
               align:     'center',
               lineBreak: false,
           });

        // Zone contenu — démarre sous le badge
        let curY = BADGE_CY + BADGE_R + 10;

        // Nom du POI + rating
        doc.fillColor(P.INK)
           .fontSize(22)
           .font('Helvetica-Bold')
           .text(poi.name, 50, curY, { width: W - 120 });

        if (poi.rating) {
            doc.fillColor(P.ACCENT)
               .fontSize(12)
               .font('Helvetica-Bold')
               .text(`★ ${poi.rating.toFixed(1)}`, W - 110, curY, {
                   width:     60,
                   align:     'right',
                   lineBreak: false,
               });
        }
        curY += doc.heightOfString(poi.name, { width: W - 120 }) + 6;

        // Catégorie
        const cat = (poi.category ?? '').toUpperCase();
        if (cat) {
            doc.fillColor(P.INK_SOFT)
               .fontSize(9)
               .font('Helvetica')
               .text(cat, 50, curY, { characterSpacing: 1.5, lineBreak: false });
            curY += 20;
        }

        curY = this.renderDivider(doc, curY, 50, W - 50);

        // Adresse
        if (poi.address) {
            curY = this.renderInfoRow(doc, poi.address, curY, W);
            curY = this.renderDivider(doc, curY, 50, W - 50);
        }

        // Statut ouverture + horaires
        if (poi.openingHours) {
            const open      = poi.openingHours.isOpenNow;
            const dotColor  = open ? '#22C55E' : '#EF4444';
            const statusTxt = open ? '● Ouvert actuellement' : '● Fermé actuellement';

            doc.fillColor(dotColor)
               .fontSize(11)
               .font('Helvetica-Bold')
               .text(statusTxt, 50, curY, { lineBreak: false });
            curY += 20;

            const weekdays = poi.openingHours.weekdayText?.slice(0, 7) ?? [];
            if (weekdays.length) {
                doc.fillColor(P.INK_SOFT).fontSize(9).font('Helvetica');
                for (const line of weekdays) {
                    if (curY >= H - 120) break;
                    doc.text(line, 66, curY, { lineBreak: false });
                    curY += 13;
                }
                curY += 6;
            }
            curY = this.renderDivider(doc, curY, 50, W - 50);
        }

        // Durée estimée
        if (poi.averageDurationHours && curY < H - 120) {
            curY = this.renderInfoRow(
                doc, `Durée estimée : ${poi.averageDurationHours}h`, curY, W,
            );
        }

        // Créneau recommandé
        if (poi.preferredTimeSlot && curY < H - 120) {
            const slotLabel = SLOT_LABELS[poi.preferredTimeSlot] ?? poi.preferredTimeSlot;
            curY = this.renderInfoRow(
                doc, `Créneau recommandé : ${slotLabel}`, curY, W,
            );
        }

        // Badge affluence
        if (poi.crowdLevel && curY < H - 100) {
            curY += 8;
            this.renderCrowdBadge(doc, poi.crowdLevel, 50, curY);
        }

        this.renderFooterStrip(doc, W, H);
    }

    // =========================================================================
    // Page carte
    // =========================================================================

    private renderMapPage(
        doc: PDFKit.PDFDocument,
        mapBuffer: Buffer,
        it: Itinerary,
    ): void {
        const W = doc.page.width;
        const H = doc.page.height;

        doc.rect(0, 0, W, H).fill(P.BG);
        doc.rect(0, 0, W, 6).fill(P.ACCENT);

        doc.fillColor(P.INK)
           .fontSize(22)
           .font('Helvetica-Bold')
           .text('Carte du parcours', 50, 36);

        doc.fillColor(P.INK_SOFT)
           .fontSize(11)
           .font('Helvetica')
           .text(it.name, 50, 64);

        const mapY = 98;
        const mapW = W - 100;
        const mapH = H - mapY - 120; // réduit pour laisser place à la légende

        try {
            doc.image(mapBuffer, 50, mapY, { fit: [mapW, mapH] });
        } catch (e) {
            this.log.warn('Carte statique non embarquée', e);
            doc.roundedRect(50, mapY, mapW, mapH, 12).fillColor(P.LINE).fill();
        }

        // Bordure arrondie autour de la carte
        doc.roundedRect(50, mapY, mapW, mapH, 12)
           .strokeColor(P.LINE)
           .lineWidth(1)
           .stroke();

        // Légende des étapes
        this.renderMapLegend(doc, it.fullSteps ?? [], 50, mapY + mapH + 16, mapW);

        this.renderFooterStrip(doc, W, H);
    }

    // =========================================================================
    // Pagination
    // =========================================================================

    private addPageNumbers(doc: PDFKit.PDFDocument): void {
        const { count } = doc.bufferedPageRange();
        for (let i = 0; i < count; i++) {
            doc.switchToPage(i);
            const W = doc.page.width;
            const H = doc.page.height;
            doc.fillColor(P.INK_SOFT)
               .fontSize(8)
               .font('Helvetica')
               .text(`${i + 1} / ${count}`, W - 90, H - 26, {
                   width:     50,
                   align:     'right',
                   lineBreak: false,
               });
        }
    }

    // =========================================================================
    // Composants partagés
    // =========================================================================

    /** Ligne d'info avec puce tomate. Retourne le prochain Y. */
    private renderInfoRow(
        doc: PDFKit.PDFDocument,
        text: string,
        y: number,
        W: number,
    ): number {
        doc.circle(56, y + 5, 3).fill(P.ACCENT);
        doc.fillColor(P.INK_SOFT)
           .fontSize(10)
           .font('Helvetica')
           .text(text, 70, y, { width: W - 120, lineBreak: false });
        return y + 20;
    }

    /** Divider horizontal. Retourne le prochain Y. */
    private renderDivider(
        doc: PDFKit.PDFDocument,
        y: number,
        x1: number,
        x2: number,
    ): number {
        const divY = y + 8;
        doc.moveTo(x1, divY).lineTo(x2, divY)
           .strokeColor(P.LINE).lineWidth(0.5).stroke();
        return divY + 14;
    }

    /** Pill colorée d'affluence + libellé. */
    private renderCrowdBadge(
        doc: PDFKit.PDFDocument,
        crowdLevel: string,
        x: number,
        y: number,
    ): void {
        const level = crowdLevel.toUpperCase();
        const colorMap: Record<string, string> = {
            LOW:    P.MINT,
            MEDIUM: P.BUTTER,
            HIGH:   P.BLUSH,
        };
        const labelMap: Record<string, string> = {
            LOW:    'FAIBLE',
            MEDIUM: 'MOYEN',
            HIGH:   'ELEVE',
        };
        const bgColor   = colorMap[level] ?? P.LINE;
        const badgeText = labelMap[level] ?? level;

        const badgeW = 72;
        const badgeH = 22;
        doc.roundedRect(x, y, badgeW, badgeH, 11).fill(bgColor);
        doc.fillColor(P.INK)
           .fontSize(8)
           .font('Helvetica-Bold')
           .text(badgeText, x, y + 7, {
               width:            badgeW,
               align:            'center',
               characterSpacing: 1,
               lineBreak:        false,
           });
        doc.fillColor(P.INK_SOFT)
           .fontSize(10)
           .font('Helvetica')
           .text(`Affluence ${badgeText.toLowerCase()}`, x + badgeW + 10, y + 6, {
               lineBreak: false,
           });
    }

    /** Légende de la page carte : puce colorée + nom pour chaque étape. */
    private renderMapLegend(
        doc: PDFKit.PDFDocument,
        pois: PointOfInterest[],
        x: number,
        y: number,
        totalW: number,
    ): void {
        if (!pois.length) return;

        const cols = 3;
        const colW = totalW / cols;
        const rowH = 18;

        pois.slice(0, 6).forEach((poi, i) => {
            const col   = i % cols;
            const row   = Math.floor(i / cols);
            const lx    = x + col * colW;
            const ly    = y + row * rowH;
            const color = STOP_COLORS[i % STOP_COLORS.length];

            doc.circle(lx + 7, ly + 7, 6).fill(color);
            doc.fillColor(P.INK_SOFT)
               .fontSize(9)
               .font('Helvetica')
               .text(`${i + 1}. ${poi.name}`, lx + 18, ly + 2, {
                   width:     colW - 22,
                   lineBreak: false,
               });
        });
    }

    private renderFooterStrip(doc: PDFKit.PDFDocument, W: number, H: number): void {
        doc.rect(0, H - 40, W, 40).fill(P.LINE);
        doc.fillColor(P.INK_SOFT)
           .fontSize(8)
           .font('Helvetica')
           .text('TravelPath — Votre itinéraire personnalisé', 50, H - 26, { lineBreak: false });
    }

    // =========================================================================
    // Fetch images
    // =========================================================================

    private async prefetchPhotos(pois: PointOfInterest[]): Promise<(Buffer | null)[]> {
        return Promise.all(pois.map(async poi => {
            const url = poi.photoUrls?.[0];
            if (!url) return null;
            try {
                return await this.fetchImageBuffer(url);
            } catch {
                this.log.warn(`Photo introuvable pour ${poi.name}`);
                return null;
            }
        }));
    }

    private async fetchMapSnapshot(it: Itinerary): Promise<Buffer | null> {
        if (!Env.MAPS_API_KEY) return null;
        const pois = it.fullSteps ?? [];
        if (!pois.length) return null;

        const markers = pois
            .map((p, i) =>
                `markers=color:0xE8654A%7Clabel:${i + 1}%7C${p.latitude},${p.longitude}`)
            .join('&');

        const pathParam = it.encodedPolyline
            ? `&path=color:0xE8654Aff%7Cenc:${encodeURIComponent(it.encodedPolyline)}`
            : '';

        const url = `https://maps.googleapis.com/maps/api/staticmap`
            + `?size=640x400&scale=2&maptype=roadmap&${markers}${pathParam}`
            + `&key=${Env.MAPS_API_KEY}`;

        return this.fetchImageBuffer(url).catch(err => {
            this.log.warn('Snapshot carte échoué :', err);
            return null;
        });
    }

    private fetchImageBuffer(url: string, redirectsLeft = 3): Promise<Buffer> {
        return new Promise<Buffer>((resolve, reject) => {
            const get = url.startsWith('https://') ? https.get : http.get;
            get(url, res => {
                if ((res.statusCode === 301 || res.statusCode === 302) && redirectsLeft > 0) {
                    const location = res.headers.location;
                    if (location) {
                        resolve(this.fetchImageBuffer(location, redirectsLeft - 1));
                    } else {
                        reject(new Error(`Redirect sans Location pour ${url}`));
                    }
                    return;
                }
                if (res.statusCode !== 200) {
                    reject(new Error(`HTTP ${res.statusCode} pour ${url}`));
                    return;
                }
                const chunks: Buffer[] = [];
                res.on('data', (chunk: Buffer) => chunks.push(chunk));
                res.on('end',  () => resolve(Buffer.concat(chunks)));
                res.on('error', reject);
            }).on('error', reject);
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private tierBgColor(tier: RouteMode | undefined): string {
        switch (tier) {
            case RouteMode.COMFORT:  return P.LILAC;
            case RouteMode.BALANCED: return P.SKY;
            default:                 return P.MINT;
        }
    }
}
