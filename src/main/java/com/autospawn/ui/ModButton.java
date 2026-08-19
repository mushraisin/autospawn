package com.autospawn.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Кнопка в стилі мода: скруглена, з плавним підсвічуванням при наведенні
 * та коротким "натисканням" (кнопка трохи просідає).
 */
public class ModButton extends ButtonWidget {

    private final boolean primary;
    private float hover;
    private float press;
    private long lastFrame = System.nanoTime();

    public ModButton(int x, int y, int width, int height, Text message, boolean primary, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.primary = primary;
    }

    @Override
    public void onPress() {
        this.press = 1.0f;
        super.onPress();
    }

    @Override
    protected void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000.0f);
        lastFrame = now;

        boolean hovered = this.isHovered() || this.isFocused();
        hover = Theme.approach(hover, hovered && this.active ? 1.0f : 0.0f, 14.0f, dt);
        press = Theme.approach(press, 0.0f, 9.0f, dt);

        int x = getX();
        int y = getY() + Math.round(press * 1.0f);
        int w = getWidth();
        int h = getHeight();

        int fill;
        int border;
        int textColor;
        if (primary) {
            fill = Theme.mix(Theme.ACCENT, Theme.ACCENT_HOVER, hover);
            border = Theme.mix(0x40000000, 0x66FFFFFF, hover);
            textColor = Theme.TEXT;
        } else {
            fill = Theme.mix(Theme.BTN, Theme.BTN_HOVER, hover);
            border = Theme.mix(Theme.PANEL_BORDER, Theme.ACCENT_SOFT, hover);
            textColor = Theme.mix(Theme.TEXT, Theme.ACCENT, hover * 0.85f);
        }

        if (!this.active) {
            fill = 0xFF1C1C24;
            border = Theme.PANEL_BORDER;
            textColor = Theme.OFFLINE;
        }

        Theme.panel(ctx, x, y, w, h, 4, fill, border);

        // тонка смужка-акцент знизу при наведенні
        if (hover > 0.01f && !primary) {
            int barW = Math.round((w - 10) * hover);
            ctx.fill(x + (w - barW) / 2, y + h - 2, x + (w - barW) / 2 + barW, y + h - 1,
                    Theme.withAlpha(Theme.ACCENT, hover));
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ctx.drawCenteredTextWithShadow(client.textRenderer, getMessage(),
                x + w / 2, y + (h - 8) / 2, textColor);
    }
}
