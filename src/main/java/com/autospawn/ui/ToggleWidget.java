package com.autospawn.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * Перемикач-"пігулка" з анімованим повзунком.
 */
public class ToggleWidget extends ButtonWidget {

    private final BooleanSupplier state;
    private float progress;
    private float hover;
    private long lastFrame = System.nanoTime();

    public ToggleWidget(int x, int y, int width, int height, Text narration, BooleanSupplier state, PressAction onPress) {
        super(x, y, width, height, narration, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.state = state;
        this.progress = state.getAsBoolean() ? 1.0f : 0.0f;
    }

    @Override
    protected void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000.0f);
        lastFrame = now;

        progress = Theme.approach(progress, state.getAsBoolean() ? 1.0f : 0.0f, 16.0f, dt);
        hover = Theme.approach(hover, this.isHovered() || this.isFocused() ? 1.0f : 0.0f, 14.0f, dt);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int r = h / 2;

        int track = Theme.mix(0xFF2A2A36, Theme.GREEN, progress);
        track = Theme.mix(track, 0xFFFFFFFF, hover * 0.12f);

        Theme.roundRect(ctx, x, y, w, h, r, track);
        Theme.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Theme.withAlpha(0xFF000000, 0.18f * (1.0f - progress)));

        int knobR = r - 2;
        int travel = w - 2 * r;
        int knobX = x + r + Math.round(travel * progress);
        Theme.dot(ctx, knobX, y + h / 2, knobR, 0xFFF6F6FA);
    }
}
