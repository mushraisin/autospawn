package com.autospawn.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Єдина палітра, скруглені прямокутники та згладжування анімацій.
 * Усе малюється звичайними заливками — жодних текстур, тож вигляд однаковий на будь-якому ресурспаку.
 */
public final class Theme {

    private Theme() {
    }

    public static final int PANEL = 0xF213131A;
    public static final int PANEL_BORDER = 0x2EFFFFFF;
    public static final int HEADER = 0xFF1B1B25;
    public static final int DIVIDER = 0x1FFFFFFF;

    public static final int FIELD = 0xFF0D0D12;
    public static final int FIELD_BORDER = 0x2BFFFFFF;

    public static final int ROW = 0x12FFFFFF;
    public static final int ROW_HOVER = 0x2BFFFFFF;

    public static final int BTN = 0xFF262632;
    public static final int BTN_HOVER = 0xFF35354A;

    public static final int ACCENT = 0xFFFFB238;
    public static final int ACCENT_HOVER = 0xFFFFC766;
    public static final int ACCENT_SOFT = 0x59FFB238;
    public static final int ACCENT_TEXT = 0xFF17110A;

    public static final int TEXT = 0xFFF3F3F7;
    public static final int TEXT_DIM = 0xFF9A9AA8;
    public static final int GREEN = 0xFF4ADE80;
    public static final int RED = 0xFFF87171;
    public static final int OFFLINE = 0xFF4A4A58;

    // ------------------------------------------------------------- анімації

    public static float easeOutCubic(float t) {
        float c = 1.0f - clamp01(t);
        return 1.0f - c * c * c;
    }

    public static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    /** Плавне наближення значення до цілі, незалежне від FPS. */
    public static float approach(float current, float target, float speed, float dt) {
        return current + (target - current) * clamp01(speed * dt);
    }

    public static int withAlpha(int color, float factor) {
        int a = (int) (((color >>> 24) & 0xFF) * clamp01(factor));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return (int) (from + (to - from) * t) & 0xFF;
    }

    // -------------------------------------------------------------- фігури

    /** Скруглений прямокутник: середина одним філом, кутові рядки — по одному. */
    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** Скруглений прямокутник із рамкою в 1px. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int radius, int fill, int border) {
        roundRect(ctx, x, y, w, h, radius, border);
        roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
    }

    private static int cornerInset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
    }

    /** Кружечок-індикатор. */
    public static void dot(DrawContext ctx, int cx, int cy, int radius, int color) {
        roundRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** М'яка тінь під панеллю. */
    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int radius, int layers, float alpha) {
        for (int i = layers; i >= 1; i--) {
            int a = (int) (16 * alpha * (1.0f - (float) i / (layers + 1)));
            if (a <= 0) continue;
            roundRect(ctx, x - i, y - i + 2, w + i * 2, h + i * 2, radius + i, a << 24);
        }
    }
}
