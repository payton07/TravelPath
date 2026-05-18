package com.example.travelpath.ui.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import androidx.core.content.res.ResourcesCompat;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.PointOfInterest;
import java.util.List;

/**
 * Generates a 1080×1920 story card bitmap (9:16) for social sharing.
 *
 * Layout (fixed vertical positions):
 *   Tier badge    — y  70
 *   City header   — y 195
 *   Route title   — y 290–660 (StaticLayout, max 4 lines)
 *   Divider       — y 710
 *   Stats row     — y 730–930 (cost · duration · effort)
 *   "LES ÉTAPES"  — y 1000
 *   POI timeline  — y 1040–1590 (up to 3 stops)
 *   Footer        — y 1760–1900
 */
public final class StoryCardGenerator {

    private static final int W  = 1080;
    private static final int H  = 1920;
    private static final int MX = 80;  // horizontal margin

    // Pastel stop colors (same cycle as timeline)
    private static final int[] DOT_COLORS = { 0xFFF4E5BB, 0xFFC8DCE8, 0xFFF4D4C4 };

    private StoryCardGenerator() {}

    public static Bitmap generate(Context ctx, Itinerary it, List<PointOfInterest> pois) {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        String tier   = it.getRouteType();
        boolean dark  = "COMFORT".equalsIgnoreCase(tier);
        int ink       = dark ? Color.WHITE       : 0xFF1B1A17;
        int inkSoft   = dark ? 0xAAFFFFFF        : 0xFF6E6A62;
        int statBg    = dark ? 0x33FFFFFF        : 0x22000000;

        Typeface serif = loadFont(ctx, R.font.fraunces, Typeface.SERIF);
        Typeface sans  = loadFont(ctx, R.font.geist,    Typeface.SANS_SERIF);

        drawBackground(canvas, tier);
        drawBadge(canvas, tier, sans, dark);
        drawCityHeader(canvas, ctx, it, sans, inkSoft);
        drawTitle(canvas, it.getName(), serif, ink);
        drawDivider(canvas, 710, dark);
        drawStats(canvas, it, serif, sans, ink, inkSoft, statBg);
        drawSectionLabel(canvas, ctx.getString(R.string.story_card_steps_label), sans, inkSoft, 1000);
        if (pois != null && !pois.isEmpty()) {
            drawTimeline(canvas, pois, serif, sans, ink, dark);
        }
        drawFooter(canvas, ctx, serif, sans, ink, inkSoft);

        return bmp;
    }

    // ── Background ────────────────────────────────────────────────────────────

    private static void drawBackground(Canvas canvas, String tier) {
        int[] colors = gradientColors(tier);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, H, colors[0], colors[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, p);
    }

    private static int[] gradientColors(String tier) {
        if ("COMFORT".equalsIgnoreCase(tier))  return new int[]{ 0xFF3D3354, 0xFF5C4A7A };
        if ("BALANCED".equalsIgnoreCase(tier)) return new int[]{ 0xFFC8DCE8, 0xFFCFE3D2 };
        /* ECONOMY */                           return new int[]{ 0xFFF4E5BB, 0xFFF4D4C4 };
    }

    // ── Tier badge ────────────────────────────────────────────────────────────

    private static void drawBadge(Canvas canvas, String tier, Typeface sans, boolean dark) {
        String label;
        int bgColor, txtColor;

        if ("COMFORT".equalsIgnoreCase(tier)) {
            label = "CONFORT"; bgColor = 0xFFF4E5BB; txtColor = 0xFF3D3354;
        } else if ("BALANCED".equalsIgnoreCase(tier)) {
            label = "ÉQUILIBRÉ"; bgColor = 0xFFC8DCE8; txtColor = 0xFF1B1A17;
        } else {
            label = "ÉCONOMIQUE"; bgColor = 0xFFF4D4C4; txtColor = 0xFF1B1A17;
        }

        Paint p = aa();
        p.setTypeface(Typeface.create(sans, Typeface.BOLD));
        p.setTextSize(28);
        p.setLetterSpacing(0.08f);

        float textW  = p.measureText(label);
        float pillW  = textW + 52;
        float pillH  = 52;
        float top    = 70;

        p.setColor(bgColor);
        canvas.drawRoundRect(new RectF(MX, top, MX + pillW, top + pillH), 26, 26, p);

        p.setColor(txtColor);
        canvas.drawText(label, MX + 26, top + pillH - 14, p);
    }

    // ── City header ───────────────────────────────────────────────────────────

    private static void drawCityHeader(Canvas canvas, Context ctx, Itinerary it, Typeface sans, int inkSoft) {
        String city = it.getDestinationCity();
        String header = city != null && !city.isEmpty()
                ? ctx.getString(R.string.story_card_city_prefix) + city.toUpperCase()
                : ctx.getString(R.string.story_card_my_route);

        Paint p = aa();
        p.setTypeface(sans);
        p.setTextSize(34);
        p.setColor(inkSoft);
        canvas.drawText(header, MX, 195, p);
    }

    // ── Title (multi-line) ────────────────────────────────────────────────────

    private static void drawTitle(Canvas canvas, String title, Typeface serif, int ink) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Typeface.create(serif, Typeface.BOLD));
        tp.setTextSize(88);
        tp.setColor(ink);

        int availW = W - MX * 2;
        StaticLayout layout = StaticLayout.Builder
                .obtain(title != null ? title : "", 0, title != null ? title.length() : 0, tp, availW)
                .setMaxLines(4)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(8, 1f)
                .build();

        canvas.save();
        canvas.translate(MX, 290);
        layout.draw(canvas);
        canvas.restore();
    }

    // ── Stats row ─────────────────────────────────────────────────────────────

    private static void drawStats(Canvas canvas, Itinerary it,
                                   Typeface serif, Typeface sans,
                                   int ink, int inkSoft, int cellBg) {
        int gap   = 12;
        int cellW = (W - 2 * MX - 2 * gap) / 3;
        int cellH = 200;
        int top   = 730;

        String[] values = {
            String.format("~%.0f€", it.getCost()),
            it.getDuration() != null ? it.getDuration() : "—",
            it.getEffort()   != null ? it.getEffort()   : "—"
        };
        String[] labels = { "COÛT", "DURÉE", "EFFORT" };

        Paint p = aa();
        for (int i = 0; i < 3; i++) {
            float left  = MX + i * (cellW + gap);
            float right = left + cellW;
            float bot   = top + cellH;
            float cx    = left + cellW / 2f;

            // Cell bg
            p.setColor(cellBg);
            canvas.drawRoundRect(new RectF(left, top, right, bot), 24, 24, p);

            // Value
            p.setColor(ink);
            p.setTypeface(Typeface.create(serif, Typeface.BOLD));
            p.setTextSize(52);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(values[i], cx, top + 118, p);

            // Label
            p.setColor(inkSoft);
            p.setTypeface(sans);
            p.setTextSize(22);
            p.setLetterSpacing(0.08f);
            canvas.drawText(labels[i], cx, top + 168, p);
        }
        // Reset align
        p.setTextAlign(Paint.Align.LEFT);
    }

    // ── Section label ─────────────────────────────────────────────────────────

    private static void drawSectionLabel(Canvas canvas, String text, Typeface sans, int color, int y) {
        Paint p = aa();
        p.setTypeface(sans);
        p.setTextSize(26);
        p.setColor(color);
        p.setLetterSpacing(0.10f);
        canvas.drawText(text, MX, y, p);
    }

    // ── POI timeline ──────────────────────────────────────────────────────────

    private static void drawTimeline(Canvas canvas, List<PointOfInterest> pois,
                                      Typeface serif, Typeface sans,
                                      int ink, boolean dark) {
        int max     = Math.min(3, pois.size());
        int dotR    = 40;
        int dotCX   = MX + dotR;
        int spacing = 185;
        int startY  = 1040;

        Paint p = aa();
        for (int i = 0; i < max; i++) {
            int dotCY = startY + dotR + i * spacing;

            // Connector line to next stop
            if (i < max - 1) {
                p.reset(); p.setAntiAlias(true);
                p.setColor(dark ? 0x33FFFFFF : 0x22000000);
                p.setStrokeWidth(3);
                canvas.drawLine(dotCX, dotCY + dotR, dotCX, dotCY + spacing - dotR, p);
            }

            // Dot
            p.reset(); p.setAntiAlias(true);
            p.setColor(DOT_COLORS[i % DOT_COLORS.length]);
            canvas.drawCircle(dotCX, dotCY, dotR, p);

            // Step number
            p.setColor(0xFF1B1A17);
            p.setTypeface(Typeface.create(serif, Typeface.BOLD));
            p.setTextSize(38);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(i + 1), dotCX, dotCY + 14, p);

            // POI name
            String name = pois.get(i).getName();
            if (name == null) name = "—";

            p.reset(); p.setAntiAlias(true);
            p.setColor(ink);
            p.setTypeface(sans);
            p.setTextSize(42);
            p.setTextAlign(Paint.Align.LEFT);
            float nameX = dotCX + dotR + 24;
            float maxW  = W - MX - nameX;
            name = ellipsize(name, p, maxW);
            canvas.drawText(name, nameX, dotCY + 15, p);
        }
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private static void drawFooter(Canvas canvas, Context ctx, Typeface serif, Typeface sans,
                                    int ink, int inkSoft) {
        drawDivider(canvas, 1760, ink == Color.WHITE);

        Paint p = aa();
        p.setTypeface(Typeface.create(serif, Typeface.BOLD));
        p.setTextSize(50);
        p.setColor(ink);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("TravelPath", W / 2f, 1840, p);

        p.setTypeface(sans);
        p.setTextSize(28);
        p.setColor(inkSoft);
        canvas.drawText(ctx.getString(R.string.story_card_footer), W / 2f, 1895, p);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void drawDivider(Canvas canvas, int y, boolean dark) {
        Paint p = aa();
        p.setColor(dark ? 0x33FFFFFF : 0x22000000);
        p.setStrokeWidth(2);
        canvas.drawLine(MX, y, W - MX, y, p);
    }

    private static String ellipsize(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) return text;
        while (text.length() > 1 && paint.measureText(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    private static Typeface loadFont(Context ctx, int resId, Typeface fallback) {
        try {
            Typeface tf = ResourcesCompat.getFont(ctx, resId);
            return tf != null ? tf : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Paint aa() {
        Paint p = new Paint();
        p.setAntiAlias(true);
        return p;
    }
}
