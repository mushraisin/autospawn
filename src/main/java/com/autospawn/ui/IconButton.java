package com.autospawn.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Кнопка-іконка з посиланням. При наведенні підсвічується, підіймається
 * і за нею розходиться акцентне коло; після кліку — коротка хвиля.
 */
public class IconButton extends ButtonWidget {

    private static final int ICON = 20;

    private final Identifier texture;
    private float hover;
    private float pop;
    private float time;
    private long lastFrame = System.nanoTime();

    public IconButton(int x, int y, Identifier texture, String url, Text tooltip, Screen parent) {
        super(x, y, ICON, ICON, tooltip, b -> openLink(parent, url), DEFAULT_NARRATION_SUPPLIER);
        this.texture = texture;
        this.setTooltip(Tooltip.of(tooltip));
    }

    private static void openLink(Screen parent, String url) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmLinkScreen(open -> {
            if (open) {
                net.minecraft.util.Util.getOperatingSystem().open(url);
            }
            client.setScreen(parent);
        }, url, true));
    }

    @Override
    public void onPress() {
        this.pop = 1.0f;
        super.onPress();
    }

    @Override
    protected void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000.0f);
        lastFrame = now;
        time += dt;

        boolean active = this.isHovered() || this.isFocused();
        hover = Theme.approach(hover, active ? 1.0f : 0.0f, 13.0f, dt);
        pop = Theme.approach(pop, 0.0f, 5.0f, dt);

        int cx = getX() + ICON / 2;
        int cy = getY() + ICON / 2;

        // коло, що розходиться під іконкою
        if (hover > 0.01f) {
            int radius = Math.round(6 + 6 * Theme.easeOutCubic(hover));
            Theme.dot(ctx, cx, cy, radius, Theme.withAlpha(Theme.ACCENT, 0.16f * hover));
            Theme.dot(ctx, cx, cy, radius - 2, Theme.withAlpha(Theme.ACCENT, 0.12f * hover));
        }

        // хвиля після кліку
        if (pop > 0.01f) {
            int radius = Math.round(8 + 8 * (1.0f - pop));
            Theme.dot(ctx, cx, cy, radius, Theme.withAlpha(Theme.ACCENT, 0.35f * pop));
        }

        // сама іконка: підіймається й ледь погойдується під курсором
        float bob = hover * MathHelper.sin(time * 4.6f) * 0.6f;
        int iconY = getY() + Math.round(-2.0f * Theme.easeOutCubic(hover) + bob - pop * 1.5f);
        int tint = Theme.mix(0xFF9DA0AD, 0xFFFFFFFF, hover);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                ((tint >> 16) & 0xFF) / 255.0f,
                ((tint >> 8) & 0xFF) / 255.0f,
                (tint & 0xFF) / 255.0f,
                ((tint >>> 24) & 0xFF) / 255.0f);
        ctx.drawTexture(texture, getX(), iconY, 0.0f, 0.0f, ICON, ICON, ICON, ICON);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
