package com.autospawn.ui;

import com.autospawn.AutoSpawnClient;
import com.autospawn.AutoSpawnConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Меню списку гравців: скруглена панель, плавна поява, список зі скролом,
 * анімовані перемикачі та індикатори "онлайн" у реальному часі.
 */
public class WatchListScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 288;
    private static final int ROW_H = 24;
    private static final int PAD = 12;

    private final Screen parent;

    private TextFieldWidget nameField;
    private TextFieldWidget commandField;
    private ModButton addBtn;
    private ModButton modeBtn;
    private ModButton doneBtn;
    private ToggleWidget enabledToggle;

    private int panelX;
    private int panelY;
    private int listTop;
    private int listBottom;

    private float openAnim;
    private float scroll;
    private float scrollTarget;
    private float[] rowHover = new float[0];
    private float removeHover;
    private int hoveredRow = -1;
    private boolean hoveredRemove;
    private boolean draggingScrollbar;

    private long lastFrameNanos = System.nanoTime();
    private float time;
    private int animOffset;

    public WatchListScreen(Screen parent) {
        super(Text.translatable("screen.autospawn.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- init

    @Override
    protected void init() {
        AutoSpawnConfig cfg = AutoSpawnConfig.get();

        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(8, (this.height - PANEL_H) / 2);
        listTop = panelY + 68;
        listBottom = panelY + 196;

        rowHover = new float[cfg.players.size()];

        enabledToggle = new ToggleWidget(panelX + PANEL_W - PAD - 38, panelY + 11, 38, 16,
                Text.translatable("screen.autospawn.toggle"), () -> AutoSpawnConfig.get().enabled,
                b -> {
                    cfg.enabled = !cfg.enabled;
                    cfg.save();
                });
        addDrawableChild(enabledToggle);

        nameField = new TextFieldWidget(this.textRenderer, panelX + PAD + 5, panelY + 46, 230, 12,
                Text.translatable("screen.autospawn.nick"));
        nameField.setMaxLength(32);
        nameField.setDrawsBackground(false);
        nameField.setPlaceholder(Text.translatable("screen.autospawn.nick_hint").styled(s -> s.withColor(Theme.TEXT_DIM & 0xFFFFFF)));
        addDrawableChild(nameField);

        addBtn = new ModButton(panelX + PANEL_W - PAD - 68, panelY + 40, 68, 20,
                Text.translatable("screen.autospawn.add"), true, b -> addCurrentName());
        addDrawableChild(addBtn);

        commandField = new TextFieldWidget(this.textRenderer, panelX + PAD + 5, panelY + 218, 140, 12,
                Text.translatable("screen.autospawn.command"));
        commandField.setMaxLength(64);
        commandField.setDrawsBackground(false);
        commandField.setText(cfg.command);
        commandField.setChangedListener(text -> cfg.command = text);
        addDrawableChild(commandField);

        modeBtn = new ModButton(panelX + 170, panelY + 212, PANEL_W - 170 - PAD, 20,
                modeText(cfg), false, b -> {
            cfg.triggerOnlyOnJoin = !cfg.triggerOnlyOnJoin;
            cfg.save();
            modeBtn.setMessage(modeText(cfg));
        });
        addDrawableChild(modeBtn);

        doneBtn = new ModButton(panelX + PAD, panelY + 252, PANEL_W - PAD * 2, 22,
                Text.translatable("gui.done"), false, b -> close());
        addDrawableChild(doneBtn);

        clampScroll();
    }

    private Text modeText(AutoSpawnConfig cfg) {
        return cfg.triggerOnlyOnJoin
                ? Text.translatable("screen.autospawn.mode_join")
                : Text.translatable("screen.autospawn.mode_any");
    }

    private void addCurrentName() {
        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        if (cfg.addPlayer(nameField.getText())) {
            nameField.setText("");
            rowHover = new float[cfg.players.size()];
            scrollTarget = maxScroll();
        }
    }

    private void click() {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    // -------------------------------------------------------------- рендер

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        updateAnimations(mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta); // фон + панель + віджети
        renderList(ctx, mouseX, mouseY);
    }

    private void updateAnimations(int mouseX, int mouseY) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000.0f);
        lastFrameNanos = now;
        time += dt;

        openAnim = Theme.approach(openAnim, 1.0f, 11.0f, dt);
        float eased = Theme.easeOutCubic(openAnim);
        animOffset = Math.round((1.0f - eased) * 16.0f);

        scroll = Theme.approach(scroll, scrollTarget, 18.0f, dt);

        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        if (rowHover.length != cfg.players.size()) {
            rowHover = new float[cfg.players.size()];
        }

        // що під курсором
        hoveredRow = -1;
        hoveredRemove = false;
        int listT = listTop + animOffset;
        int listB = listBottom + animOffset;
        if (mouseY >= listT && mouseY < listB && mouseX >= panelX + PAD && mouseX <= panelX + PANEL_W - PAD) {
            int index = (int) ((mouseY - listT + scroll) / ROW_H);
            if (index >= 0 && index < cfg.players.size()) {
                hoveredRow = index;
                hoveredRemove = mouseX >= removeX() && mouseX <= removeX() + 18;
            }
        }

        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = Theme.approach(rowHover[i], i == hoveredRow ? 1.0f : 0.0f, 15.0f, dt);
        }
        removeHover = Theme.approach(removeHover, hoveredRemove ? 1.0f : 0.0f, 15.0f, dt);

        // віджети їдуть разом із панеллю
        offset(enabledToggle, panelY + 11);
        offset(nameField, panelY + 46);
        offset(addBtn, panelY + 40);
        offset(commandField, panelY + 218);
        offset(modeBtn, panelY + 212);
        offset(doneBtn, panelY + 252);
    }

    private void offset(net.minecraft.client.gui.widget.ClickableWidget widget, int baseY) {
        if (widget != null) widget.setY(baseY + animOffset);
    }

    private int removeX() {
        return panelX + PANEL_W - PAD - 24 - (maxScroll() > 0 ? 6 : 0);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float eased = Theme.easeOutCubic(openAnim);
        ctx.fill(0, 0, this.width, this.height, Theme.withAlpha(0xB4000000, eased));

        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        int px = panelX;
        int py = panelY + animOffset;

        Theme.shadow(ctx, px, py, PANEL_W, PANEL_H, 8, 5, eased);
        Theme.panel(ctx, px, py, PANEL_W, PANEL_H, 8, Theme.withAlpha(Theme.PANEL, eased), Theme.withAlpha(Theme.PANEL_BORDER, eased));

        // шапка
        Theme.roundRect(ctx, px + 1, py + 1, PANEL_W - 2, 36, 7, Theme.withAlpha(Theme.HEADER, eased));
        ctx.fill(px + 1, py + 33, px + PANEL_W - 1, py + 37, Theme.withAlpha(Theme.HEADER, eased));
        ctx.fill(px + PAD, py + 37, px + PANEL_W - PAD, py + 38, Theme.withAlpha(Theme.DIVIDER, eased));

        // акцентна риска під заголовком, що "виїжджає"
        int titleW = this.textRenderer.getWidth(this.title);
        int barW = Math.round(titleW * eased);
        ctx.fill(px + PAD, py + 26, px + PAD + barW, py + 27, Theme.withAlpha(Theme.ACCENT, eased));

        ctx.drawTextWithShadow(this.textRenderer, this.title, px + PAD, py + 14, Theme.withAlpha(Theme.TEXT, eased));

        // статус праворуч від перемикача
        Text status = cfg.enabled
                ? Text.translatable("screen.autospawn.on")
                : Text.translatable("screen.autospawn.off");
        int statusColor = cfg.enabled ? Theme.GREEN : Theme.OFFLINE;
        int statusX = px + PANEL_W - PAD - 38 - 6 - this.textRenderer.getWidth(status);
        ctx.drawTextWithShadow(this.textRenderer, status, statusX, py + 15, Theme.withAlpha(statusColor, eased));

        // поле вводу ніка
        Theme.panel(ctx, px + PAD, py + 40, 230 + 10, 20, 4,
                Theme.withAlpha(Theme.FIELD, eased),
                Theme.withAlpha(nameField != null && nameField.isFocused() ? Theme.ACCENT_SOFT : Theme.FIELD_BORDER, eased));

        // підпис списку
        int watched = cfg.players.size();
        int online = countOnline(cfg);
        Text caption = Text.translatable("screen.autospawn.list_caption", watched, online);
        ctx.drawTextWithShadow(this.textRenderer, caption, px + PAD, py + 66 - 8, Theme.withAlpha(Theme.TEXT_DIM, eased));

        // підпис і поле команди
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("screen.autospawn.command_hint"),
                px + PAD, py + 202, Theme.withAlpha(Theme.TEXT_DIM, eased));
        Theme.panel(ctx, px + PAD, py + 212, 150, 20, 4,
                Theme.withAlpha(Theme.FIELD, eased),
                Theme.withAlpha(commandField != null && commandField.isFocused() ? Theme.ACCENT_SOFT : Theme.FIELD_BORDER, eased));

        // підказка про бінд
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("screen.autospawn.keybind_hint"),
                px + PAD, py + 240, Theme.withAlpha(0xFF6E6E7C, eased));
    }

    private int countOnline(AutoSpawnConfig cfg) {
        AutoSpawnClient client = AutoSpawnClient.instance();
        if (client == null) return 0;
        Set<String> online = client.onlineWatched();
        int n = 0;
        for (String p : cfg.players) {
            if (online.contains(p.toLowerCase(Locale.ROOT))) n++;
        }
        return n;
    }

    private void renderList(DrawContext ctx, int mouseX, int mouseY) {
        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        List<String> players = cfg.players;
        float eased = Theme.easeOutCubic(openAnim);

        int listT = listTop + animOffset;
        int listB = listBottom + animOffset;
        int rowX = panelX + PAD;
        int rowW = PANEL_W - PAD * 2 - (maxScroll() > 0 ? 6 : 0);

        if (players.isEmpty()) {
            Text empty = Text.translatable("screen.autospawn.empty");
            Text hint = Text.translatable("screen.autospawn.empty_hint");
            int cy = (listT + listB) / 2;
            ctx.drawCenteredTextWithShadow(this.textRenderer, empty, panelX + PANEL_W / 2, cy - 10,
                    Theme.withAlpha(Theme.TEXT_DIM, eased));
            ctx.drawCenteredTextWithShadow(this.textRenderer, hint, panelX + PANEL_W / 2, cy + 2,
                    Theme.withAlpha(0xFF63636E, eased));
            return;
        }

        Set<String> online = AutoSpawnClient.instance() == null ? Set.of() : AutoSpawnClient.instance().onlineWatched();

        ctx.enableScissor(panelX + 1, listT, panelX + PANEL_W - 1, listB);
        for (int i = 0; i < players.size(); i++) {
            int rowY = Math.round(listT - scroll + i * ROW_H);
            if (rowY + ROW_H < listT || rowY > listB) continue;

            // поява рядків із затримкою "сходинкою"
            float appear = Theme.clamp01((eased - i * 0.05f) / 0.45f);
            if (appear <= 0.0f) continue;
            float appearEase = Theme.easeOutCubic(appear);
            int slide = Math.round((1.0f - appearEase) * 14.0f);

            String name = players.get(i);
            boolean isOnline = online.contains(name.toLowerCase(Locale.ROOT));
            float hover = i < rowHover.length ? rowHover[i] : 0.0f;

            int bg = Theme.mix(Theme.ROW, Theme.ROW_HOVER, hover);
            Theme.roundRect(ctx, rowX + slide, rowY + 1, rowW, ROW_H - 3, 4, Theme.withAlpha(bg, appearEase));

            // акцентна смужка ліворуч у виділеного рядка
            if (hover > 0.01f) {
                Theme.roundRect(ctx, rowX + slide, rowY + 1, 2, ROW_H - 3, 1,
                        Theme.withAlpha(Theme.ACCENT, hover * appearEase));
            }

            // індикатор онлайну (пульсує)
            int dotColor;
            if (isOnline) {
                float pulse = 0.65f + 0.35f * MathHelper.sin(time * 3.2f);
                dotColor = Theme.withAlpha(Theme.GREEN, pulse * appearEase);
            } else {
                dotColor = Theme.withAlpha(Theme.OFFLINE, appearEase);
            }
            Theme.dot(ctx, rowX + slide + 12, rowY + ROW_H / 2 - 1, 3, dotColor);

            // нік
            int nameMax = rowW - 60;
            String shown = this.textRenderer.trimToWidth(name, nameMax);
            ctx.drawTextWithShadow(this.textRenderer, shown, rowX + slide + 22, rowY + 7,
                    Theme.withAlpha(isOnline ? Theme.TEXT : 0xFFC9C9D4, appearEase));

            // статус + кнопка видалення
            if (isOnline) {
                Text on = Text.translatable("screen.autospawn.row_online");
                int w = this.textRenderer.getWidth(on);
                ctx.drawTextWithShadow(this.textRenderer, on, removeX() - 8 - w, rowY + 7,
                        Theme.withAlpha(Theme.GREEN, 0.75f * appearEase));
            }

            boolean hoverX = i == hoveredRow && hoveredRemove;
            int xBtnColor = Theme.mix(0x00000000, 0x66F87171, hoverX ? removeHover : 0.0f);
            Theme.roundRect(ctx, removeX() + slide, rowY + 4, 18, ROW_H - 9, 3, Theme.withAlpha(xBtnColor, appearEase));
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("✕"),
                    removeX() + slide + 9, rowY + 8,
                    Theme.withAlpha(hoverX ? 0xFFFFFFFF : Theme.RED, appearEase));
        }
        ctx.disableScissor();

        // скролбар
        int max = maxScroll();
        if (max > 0) {
            int trackX = panelX + PANEL_W - PAD - 4;
            int trackH = listB - listT;
            Theme.roundRect(ctx, trackX, listT, 3, trackH, 1, Theme.withAlpha(0x1AFFFFFF, eased));
            int thumbH = Math.max(20, Math.round(trackH * (float) trackH / (players.size() * ROW_H)));
            int thumbY = listT + Math.round((trackH - thumbH) * (scroll / max));
            Theme.roundRect(ctx, trackX, thumbY, 3, thumbH, 1, Theme.withAlpha(Theme.ACCENT, 0.85f * eased));
        }
    }

    // --------------------------------------------------------------- ввід

    private int maxScroll() {
        int contentH = AutoSpawnConfig.get().players.size() * ROW_H;
        return Math.max(0, contentH - (listBottom - listTop));
    }

    private void clampScroll() {
        scrollTarget = MathHelper.clamp(scrollTarget, 0.0f, maxScroll());
        scroll = MathHelper.clamp(scroll, 0.0f, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (maxScroll() > 0) {
            scrollTarget = MathHelper.clamp(scrollTarget - (float) vertical * ROW_H, 0.0f, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredRow >= 0) {
            AutoSpawnConfig cfg = AutoSpawnConfig.get();
            if (hoveredRow < cfg.players.size()) {
                String name = cfg.players.get(hoveredRow);
                if (hoveredRemove) {
                    cfg.removePlayer(name);
                    rowHover = new float[cfg.players.size()];
                    clampScroll();
                } else {
                    nameField.setText(name);
                    setFocused(nameField);
                    nameField.setFocused(true);
                }
                click();
                return true;
            }
        }

        int max = maxScroll();
        if (button == 0 && max > 0) {
            int trackX = panelX + PANEL_W - PAD - 4;
            if (mouseX >= trackX - 2 && mouseX <= trackX + 5
                    && mouseY >= listTop + animOffset && mouseY <= listBottom + animOffset) {
                draggingScrollbar = true;
                dragScrollbar(mouseY);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void dragScrollbar(double mouseY) {
        int listT = listTop + animOffset;
        int trackH = listBottom - listTop;
        float t = (float) ((mouseY - listT) / trackH);
        scrollTarget = MathHelper.clamp(t * maxScroll(), 0.0f, maxScroll());
        scroll = scrollTarget;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && nameField.isFocused()) {
            addCurrentName();
            click();
            return true;
        }
        // повторне натискання біндa закриває меню, якщо не набираємо текст
        if (!nameField.isFocused() && !commandField.isFocused()
                && AutoSpawnClient.openMenuKey() != null
                && AutoSpawnClient.openMenuKey().matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        AutoSpawnConfig.get().save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
