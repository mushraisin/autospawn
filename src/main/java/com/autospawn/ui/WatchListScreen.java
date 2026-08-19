package com.autospawn.ui;

import com.autospawn.AutoSpawnClient;
import com.autospawn.AutoSpawnConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Меню списку гравців: скруглена панель, плавна поява, список зі скролом,
 * анімовані перемикачі та індикатори "онлайн" у реальному часі.
 *
 * Уся розкладка обчислюється від розміру панелі, а панель — від розміру вікна,
 * тож меню лишається цілим на будь-якому масштабі GUI та роздільності екрана.
 */
public class WatchListScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int PAD = 12;
    private static final int HEADER_H = 38;
    private static final int FIELD_H = 20;
    private static final int DONE_H = 22;
    private static final int ICON_H = 20;

    private static final String GITHUB_URL = "https://github.com/mushraisin";
    private static final String TELEGRAM_URL = "https://t.me/mushbarry";
    private static final Identifier GITHUB_ICON = new Identifier("autospawn", "textures/gui/github.png");
    private static final Identifier TELEGRAM_ICON = new Identifier("autospawn", "textures/gui/telegram.png");

    private final Screen parent;

    private TextFieldWidget nameField;
    private TextFieldWidget commandField;
    private ModButton addBtn;
    private ModButton modeBtn;
    private ModButton doneBtn;
    private ToggleWidget enabledToggle;
    private IconButton githubBtn;
    private IconButton telegramBtn;

    // розкладка
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int inputY;
    private int captionY;
    private int listTop;
    private int listBottom;
    private int cmdLabelY;
    private int cmdRowY;
    private int cmdW;
    private int hintY;
    private int doneY;
    private int creditY;
    private boolean showHint;

    // анімації та стан
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

        panelW = MathHelper.clamp(this.width - 40, 220, 360);
        panelH = MathHelper.clamp(this.height - 40, 200, 326);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int contentW = panelW - PAD * 2;
        int contentX = panelX + PAD;

        // верх: поле вводу + "Додати"
        inputY = panelY + HEADER_H + 8;
        int addW = MathHelper.clamp(this.textRenderer.getWidth(Text.translatable("screen.autospawn.add")) + 18, 46, 80);
        int nameBoxW = contentW - addW - 8;
        captionY = inputY + FIELD_H + 7;

        // низ: підпис автора з іконками, "Готово", підказка, рядок команди
        creditY = panelY + panelH - PAD - ICON_H;
        doneY = creditY - 8 - DONE_H;
        Text hint = Text.translatable("screen.autospawn.keybind_hint");
        Text hintShort = Text.translatable("screen.autospawn.keybind_hint_short");
        if (this.textRenderer.getWidth(hint) > contentW) {
            hint = hintShort;
        }
        showHint = this.textRenderer.getWidth(hint) <= contentW;
        hintY = doneY - 13;
        cmdRowY = (showHint ? hintY : doneY) - 6 - FIELD_H;
        cmdLabelY = cmdRowY - 11;

        cmdW = MathHelper.clamp((contentW - 8) * 44 / 100, 70, 160);
        int modeW = contentW - cmdW - 8;

        listTop = captionY + 12;
        listBottom = Math.max(listTop + ROW_H, cmdLabelY - 8);

        rowHover = new float[cfg.players.size()];

        enabledToggle = new ToggleWidget(panelX + panelW - PAD - 38, panelY + 11, 38, 16,
                Text.translatable("screen.autospawn.toggle"), () -> AutoSpawnConfig.get().enabled,
                b -> {
                    cfg.enabled = !cfg.enabled;
                    cfg.save();
                });
        addDrawableChild(enabledToggle);

        nameField = new TextFieldWidget(this.textRenderer, contentX + 5, inputY + 6, nameBoxW - 10, 12,
                Text.translatable("screen.autospawn.nick"));
        nameField.setMaxLength(32);
        nameField.setDrawsBackground(false);
        nameField.setPlaceholder(placeholder(nameBoxW - 10));
        addDrawableChild(nameField);

        addBtn = new ModButton(contentX + nameBoxW + 8, inputY, addW, FIELD_H,
                Text.translatable("screen.autospawn.add"), true, b -> addCurrentName());
        addDrawableChild(addBtn);

        commandField = new TextFieldWidget(this.textRenderer, contentX + 5, cmdRowY + 6, cmdW - 10, 12,
                Text.translatable("screen.autospawn.command"));
        commandField.setMaxLength(64);
        commandField.setDrawsBackground(false);
        commandField.setText(cfg.command);
        commandField.setChangedListener(text -> cfg.command = text);
        addDrawableChild(commandField);

        modeBtn = new ModButton(contentX + cmdW + 8, cmdRowY, modeW, FIELD_H,
                modeText(cfg, modeW), false, b -> {
            cfg.triggerOnlyOnJoin = !cfg.triggerOnlyOnJoin;
            cfg.save();
            modeBtn.setMessage(modeText(cfg, modeBtn.getWidth()));
        });
        addDrawableChild(modeBtn);

        doneBtn = new ModButton(contentX, doneY, contentW, DONE_H,
                Text.translatable("gui.done"), false, b -> close());
        addDrawableChild(doneBtn);

        int iconRight = contentX + contentW - ICON_H;
        telegramBtn = new IconButton(iconRight, creditY, TELEGRAM_ICON, TELEGRAM_URL,
                Text.literal("t.me/mushbarry"), this);
        addDrawableChild(telegramBtn);
        githubBtn = new IconButton(iconRight - ICON_H - 4, creditY, GITHUB_ICON, GITHUB_URL,
                Text.literal("github.com/mushraisin"), this);
        addDrawableChild(githubBtn);

        clampScroll();
    }

    /** Плейсхолдер, який гарантовано вміщується в поле. */
    private Text placeholder(int maxW) {
        Text full = Text.translatable("screen.autospawn.nick_hint");
        Text shortText = Text.translatable("screen.autospawn.nick");
        Text chosen = this.textRenderer.getWidth(full) <= maxW ? full : shortText;
        return chosen.copy().styled(s -> s.withColor(Theme.TEXT_DIM & 0xFFFFFF));
    }

    /** Довга назва режиму, якщо вміщується, інакше коротка. */
    private Text modeText(AutoSpawnConfig cfg, int buttonW) {
        Text full = Text.translatable(cfg.triggerOnlyOnJoin
                ? "screen.autospawn.mode_join" : "screen.autospawn.mode_any");
        if (this.textRenderer.getWidth(full) <= buttonW - 10) {
            return full;
        }
        return Text.translatable(cfg.triggerOnlyOnJoin
                ? "screen.autospawn.mode_join_short" : "screen.autospawn.mode_any_short");
    }

    private Text hintText(int maxW) {
        Text full = Text.translatable("screen.autospawn.keybind_hint");
        if (this.textRenderer.getWidth(full) <= maxW) return full;
        return Text.translatable("screen.autospawn.keybind_hint_short");
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
        drawPanel(ctx);                            // фон і панель
        super.render(ctx, mouseX, mouseY, delta);  // віджети
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

        hoveredRow = -1;
        hoveredRemove = false;
        int listT = listTop + animOffset;
        int listB = listBottom + animOffset;
        if (mouseY >= listT && mouseY < listB && mouseX >= panelX + PAD && mouseX <= panelX + panelW - PAD) {
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
        offset(nameField, inputY + 6);
        offset(addBtn, inputY);
        offset(commandField, cmdRowY + 6);
        offset(modeBtn, cmdRowY);
        offset(doneBtn, doneY);
        offset(githubBtn, creditY);
        offset(telegramBtn, creditY);
    }

    private void offset(ClickableWidget widget, int baseY) {
        if (widget != null) widget.setY(baseY + animOffset);
    }

    private int removeX() {
        return panelX + panelW - PAD - 22 - (maxScroll() > 0 ? 7 : 0);
    }

    private void drawPanel(DrawContext ctx) {
        float eased = Theme.easeOutCubic(openAnim);
        ctx.fill(0, 0, this.width, this.height, Theme.withAlpha(0xB4000000, eased));

        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        int px = panelX;
        int py = panelY + animOffset;
        int contentX = px + PAD;
        int contentW = panelW - PAD * 2;

        Theme.shadow(ctx, px, py, panelW, panelH, 8, 5, eased);
        Theme.panel(ctx, px, py, panelW, panelH, 8, Theme.withAlpha(Theme.PANEL, eased), Theme.withAlpha(Theme.PANEL_BORDER, eased));

        // шапка
        Theme.roundRect(ctx, px + 1, py + 1, panelW - 2, HEADER_H - 4, 7, Theme.withAlpha(Theme.HEADER, eased));
        ctx.fill(px + 1, py + HEADER_H - 8, px + panelW - 1, py + HEADER_H - 1, Theme.withAlpha(Theme.HEADER, eased));
        ctx.fill(contentX, py + HEADER_H - 1, px + panelW - PAD, py + HEADER_H, Theme.withAlpha(Theme.DIVIDER, eased));

        int titleW = this.textRenderer.getWidth(this.title);
        ctx.fill(contentX, py + 26, contentX + Math.round(titleW * eased), py + 27, Theme.withAlpha(Theme.ACCENT, eased));
        ctx.drawTextWithShadow(this.textRenderer, this.title, contentX, py + 14, Theme.withAlpha(Theme.TEXT, eased));

        // статус — лише якщо не налазить на заголовок
        Text status = Text.translatable(cfg.enabled ? "screen.autospawn.on" : "screen.autospawn.off");
        int statusW = this.textRenderer.getWidth(status);
        int statusX = px + panelW - PAD - 38 - 6 - statusW;
        if (statusX > contentX + titleW + 8) {
            ctx.drawTextWithShadow(this.textRenderer, status, statusX, py + 15,
                    Theme.withAlpha(cfg.enabled ? Theme.GREEN : Theme.OFFLINE, eased));
        }

        // поле вводу ніка
        int addW = addBtn == null ? 60 : addBtn.getWidth();
        Theme.panel(ctx, contentX, py + (inputY - panelY), contentW - addW - 8, FIELD_H, 4,
                Theme.withAlpha(Theme.FIELD, eased),
                Theme.withAlpha(nameField != null && nameField.isFocused() ? Theme.ACCENT_SOFT : Theme.FIELD_BORDER, eased));

        // підпис списку
        Text caption = Text.translatable("screen.autospawn.list_caption", cfg.players.size(), countOnline(cfg));
        ctx.drawTextWithShadow(this.textRenderer, caption, contentX, py + (captionY - panelY),
                Theme.withAlpha(Theme.TEXT_DIM, eased));

        // рядок команди
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable("screen.autospawn.command_hint"),
                contentX, py + (cmdLabelY - panelY), Theme.withAlpha(Theme.TEXT_DIM, eased));
        Theme.panel(ctx, contentX, py + (cmdRowY - panelY), cmdW, FIELD_H, 4,
                Theme.withAlpha(Theme.FIELD, eased),
                Theme.withAlpha(commandField != null && commandField.isFocused() ? Theme.ACCENT_SOFT : Theme.FIELD_BORDER, eased));

        // підказка про бінд
        if (showHint) {
            ctx.drawTextWithShadow(this.textRenderer, hintText(contentW), contentX, py + (hintY - panelY),
                    Theme.withAlpha(0xFF6E6E7C, eased));
        }

        // підпис автора
        int creditRowY = py + (creditY - panelY);
        ctx.fill(contentX, creditRowY - 6, px + panelW - PAD, creditRowY - 5, Theme.withAlpha(Theme.DIVIDER, eased));
        Text by = Text.translatable("screen.autospawn.credits");
        if (this.textRenderer.getWidth(by) < contentW - ICON_H * 2 - 12) {
            ctx.drawTextWithShadow(this.textRenderer, by, contentX, creditRowY + 6,
                    Theme.withAlpha(Theme.TEXT_DIM, eased));
        }
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
        int rowW = panelW - PAD * 2 - (maxScroll() > 0 ? 7 : 0);

        if (players.isEmpty()) {
            int cy = (listT + listB) / 2;
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.autospawn.empty"),
                    panelX + panelW / 2, cy - 10, Theme.withAlpha(Theme.TEXT_DIM, eased));
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.autospawn.empty_hint"),
                    panelX + panelW / 2, cy + 2, Theme.withAlpha(0xFF63636E, eased));
            return;
        }

        Set<String> online = AutoSpawnClient.instance() == null
                ? Set.<String>of() : AutoSpawnClient.instance().onlineWatched();

        ctx.enableScissor(panelX + 1, listT, panelX + panelW - 1, listB);
        for (int i = 0; i < players.size(); i++) {
            int rowY = Math.round(listT - scroll + i * ROW_H);
            if (rowY + ROW_H < listT || rowY > listB) continue;

            float appear = Theme.clamp01((eased - i * 0.05f) / 0.45f);
            if (appear <= 0.0f) continue;
            float appearEase = Theme.easeOutCubic(appear);
            int slide = Math.round((1.0f - appearEase) * 14.0f);

            String name = players.get(i);
            boolean isOnline = online.contains(name.toLowerCase(Locale.ROOT));
            float hover = i < rowHover.length ? rowHover[i] : 0.0f;

            Theme.roundRect(ctx, rowX + slide, rowY + 1, rowW, ROW_H - 3, 4,
                    Theme.withAlpha(Theme.mix(Theme.ROW, Theme.ROW_HOVER, hover), appearEase));

            if (hover > 0.01f) {
                Theme.roundRect(ctx, rowX + slide, rowY + 1, 2, ROW_H - 3, 1,
                        Theme.withAlpha(Theme.ACCENT, hover * appearEase));
            }

            int dotColor = isOnline
                    ? Theme.withAlpha(Theme.GREEN, (0.65f + 0.35f * MathHelper.sin(time * 3.2f)) * appearEase)
                    : Theme.withAlpha(Theme.OFFLINE, appearEase);
            Theme.dot(ctx, rowX + slide + 11, rowY + ROW_H / 2 - 1, 3, dotColor);

            // статус праворуч (малюємо першим, щоб знати, скільки лишилось під нік)
            int rightLimit = removeX() - 6;
            if (isOnline) {
                Text on = Text.translatable("screen.autospawn.row_online");
                int w = this.textRenderer.getWidth(on);
                if (rightLimit - w > rowX + 60) {
                    ctx.drawTextWithShadow(this.textRenderer, on, rightLimit - w + slide, rowY + 7,
                            Theme.withAlpha(Theme.GREEN, 0.75f * appearEase));
                    rightLimit -= w + 6;
                }
            }

            int nameX = rowX + slide + 20;
            String shown = this.textRenderer.trimToWidth(name, Math.max(16, rightLimit - nameX));
            ctx.drawTextWithShadow(this.textRenderer, shown, nameX, rowY + 7,
                    Theme.withAlpha(isOnline ? Theme.TEXT : 0xFFC9C9D4, appearEase));

            boolean hoverX = i == hoveredRow && hoveredRemove;
            Theme.roundRect(ctx, removeX() + slide, rowY + 4, 18, ROW_H - 9, 3,
                    Theme.withAlpha(Theme.mix(0x00000000, 0x66F87171, hoverX ? removeHover : 0.0f), appearEase));
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("✕"),
                    removeX() + slide + 9, rowY + 8,
                    Theme.withAlpha(hoverX ? 0xFFFFFFFF : Theme.RED, appearEase));
        }
        ctx.disableScissor();

        int max = maxScroll();
        if (max > 0) {
            int trackX = panelX + panelW - PAD - 4;
            int trackH = listB - listT;
            Theme.roundRect(ctx, trackX, listT, 3, trackH, 1, Theme.withAlpha(0x1AFFFFFF, eased));
            int thumbH = MathHelper.clamp(Math.round(trackH * (float) trackH / (players.size() * ROW_H)), 16, trackH);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll() > 0) {
            scrollTarget = MathHelper.clamp(scrollTarget - (float) amount * ROW_H, 0.0f, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
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

        if (button == 0 && maxScroll() > 0) {
            int trackX = panelX + panelW - PAD - 4;
            if (mouseX >= trackX - 3 && mouseX <= trackX + 6
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
        int trackH = Math.max(1, listBottom - listTop);
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
