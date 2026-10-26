package com.example.hiall;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HiAllScreen extends Screen {
    private static final int SIDEBAR_W = 104;
    private static final int BG = 0xF00E0E14;
    private static final int SIDEBAR = 0xFF12121A;
    private static final int CARD = 0xFF16161F;
    private static final int CARD_EDGE = 0xFF262633;
    private static final int FIELD = 0xFF0D0D14;
    private static final int ACCENT = 0xFFFF2E7E;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUB = 0xFF8C8C9E;
    private static final int OFF = 0xFF3A3A48;
    private static final int GREEN = 0xFF3DDC84;

    private interface Click { void run(int mx, int my); }
    private record Hit(int x, int y, int w, int h, Click click) {}

    private final List<Hit> hits = new ArrayList<>();
    private int tab = 0; // 0 messaging, 1 blacklist, 2 settings
    private String focus = ""; // "", "text", "bl"
    private String blBuf = "";
    private int blScroll = 0;
    private boolean draggingDelay = false;
    private int sliderX = 0, sliderW = 1;
    private int wx, wy, ww, wh, cx, cw;

    public HiAllScreen() {
        super(Text.literal("HiAll"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- drawing helpers ----------

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void round(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + 1, color);
        c.fill(x, y + 1, x + w, y + h - 1, color);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    private void card(DrawContext c, int x, int y, int w, int h, boolean active) {
        if (active) {
            round(c, x - 2, y - 2, w + 4, h + 4, 0x25FF2E7E);
            round(c, x - 1, y - 1, w + 2, h + 2, ACCENT);
        } else {
            round(c, x - 1, y - 1, w + 2, h + 2, CARD_EDGE);
        }
        round(c, x, y, w, h, CARD);
    }

    private void toggle(DrawContext c, int x, int y, boolean on) {
        round(c, x, y, 26, 12, on ? ACCENT : OFF);
        round(c, on ? x + 16 : x + 2, y + 2, 8, 8, 0xFFFFFFFF);
    }

    private void button(DrawContext c, int mx, int my, int x, int y, int w, int h,
                        String label, boolean selected, Click click) {
        boolean hov = in(mx, my, x, y, w, h);
        if (selected) {
            round(c, x, y, w, h, ACCENT);
        } else {
            round(c, x - 1, y - 1, w + 2, h + 2, hov ? ACCENT : CARD_EDGE);
            round(c, x, y, w, h, FIELD);
        }
        int tw = textRenderer.getWidth(label);
        c.drawText(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2 + 1,
                selected ? 0xFFFFFFFF : (hov ? 0xFFD0D0DC : SUB), false);
        hits.add(new Hit(x, y, w, h, click));
    }

    private String fit(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private String tail(String s, int maxW) {
        while (s.length() > 1 && textRenderer.getWidth(s) > maxW) s = s.substring(1);
        return s;
    }

    private void drawField(DrawContext c, int x, int y, int w, int h, String id, String value, String placeholder) {
        boolean f = focus.equals(id);
        round(c, x - 1, y - 1, w + 2, h + 2, f ? ACCENT : CARD_EDGE);
        round(c, x, y, w, h, FIELD);
        int ty = y + (h - 8) / 2 + 1;
        if (value.isEmpty() && !f) {
            c.drawText(textRenderer, fit(placeholder, w - 12), x + 6, ty, SUB, false);
        } else {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            c.drawText(textRenderer, tail(value + (f && blink ? "_" : ""), w - 12), x + 6, ty, TEXT, false);
        }
        hits.add(new Hit(x, y, w, h, (px, py) -> focus = id));
    }

    // ---------- main render ----------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        ctx.fill(0, 0, width, height, 0x90000000);

        ww = Math.min(450, width - 16);
        wh = Math.min(262, height - 16);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;

        round(ctx, wx - 2, wy - 2, ww + 4, wh + 4, 0x25FF2E7E);
        round(ctx, wx - 1, wy - 1, ww + 2, wh + 2, 0x80FF2E7E);
        round(ctx, wx, wy, ww, wh, BG);

        drawSidebar(ctx, mouseX, mouseY);
        drawContent(ctx, mouseX, mouseY);
    }

    private void drawSidebar(DrawContext ctx, int mx, int my) {
        int sw = SIDEBAR_W;
        round(ctx, wx + 6, wy + 6, sw, wh - 12, SIDEBAR);

        round(ctx, wx + 14, wy + 14, 22, 22, ACCENT);
        ctx.drawText(textRenderer, "H", wx + 22, wy + 21, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, Text.literal("HiAll").formatted(Formatting.BOLD), wx + 42, wy + 15, TEXT, false);
        ctx.drawText(textRenderer, "v1.0.0", wx + 42, wy + 26, SUB, false);
        ctx.fill(wx + 14, wy + 44, wx + 6 + sw - 8, wy + 45, CARD_EDGE);
        ctx.drawText(textRenderer, "MENU", wx + 14, wy + 52, SUB, false);

        String[] names = {"Messaging", "Blacklist", "Settings"};
        for (int i = 0; i < 3; i++) {
            int x = wx + 12, y = wy + 66 + i * 26, w = sw - 12, h = 22;
            boolean sel = tab == i;
            boolean hov = in(mx, my, x, y, w, h);
            if (sel) {
                round(ctx, x - 1, y - 1, w + 2, h + 2, ACCENT);
                round(ctx, x, y, w, h, 0xFF26121D);
            } else if (hov) {
                round(ctx, x, y, w, h, 0xFF1B1B26);
            }
            ctx.drawText(textRenderer, names[i], x + 8, y + 7, sel ? ACCENT : (hov ? 0xFFD0D0DC : SUB), false);
            if (i == 1 && !HiAllClient.blacklist.isEmpty()) {
                String n = String.valueOf(HiAllClient.blacklist.size());
                int bw = textRenderer.getWidth(n) + 8;
                round(ctx, x + w - bw - 5, y + 4, bw, 14, 0xFF2A2A38);
                ctx.drawText(textRenderer, n, x + w - bw - 1, y + 7, TEXT, false);
            }
            final int idx = i;
            hits.add(new Hit(x, y, w, h, (px, py) -> { tab = idx; focus = ""; }));
        }

        int fx = wx + 12, fy = wy + wh - 46, fw = sw - 12;
        round(ctx, fx, fy, fw, 34, CARD);
        ctx.fill(fx + 8, fy + 9, fx + 14, fy + 15, HiAllClient.running ? GREEN : OFF);
        ctx.drawText(textRenderer, HiAllClient.running ? "Running" : "Paused", fx + 19, fy + 8, TEXT, false);
        ctx.drawText(textRenderer, "messaged: " + HiAllClient.done.size(), fx + 8, fy + 20, SUB, false);
    }

    private void drawContent(DrawContext ctx, int mx, int my) {
        cx = wx + 6 + SIDEBAR_W + 8;
        cw = wx + ww - 8 - cx;

        round(ctx, cx, wy + 8, cw, 24, CARD);
        String[] titles = {"Messaging", "Blacklist", "Settings"};
        String info = tab == 0 ? (HiAllClient.running ? "running" : "paused")
                : tab == 1 ? HiAllClient.blacklist.size() + " blacklisted" : "preferences";
        Text title = Text.literal(titles[tab]).formatted(Formatting.BOLD);
        ctx.drawText(textRenderer, title, cx + 10, wy + 16, TEXT, false);
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, "\u00b7 " + info, cx + 10 + tw + 6, wy + 16, SUB, false);
        String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        ctx.drawText(textRenderer, clock, cx + cw - 10 - textRenderer.getWidth(clock), wy + 16, ACCENT, false);

        int y0 = wy + 40;
        if (tab == 0) drawMessaging(ctx, mx, my, y0);
        else if (tab == 1) drawBlacklist(ctx, mx, my, y0);
        else drawSettings(ctx, mx, my, y0);
    }

    // ---------- tabs ----------

    private void drawMessaging(DrawContext ctx, int mx, int my, int y0) {
        // Card A: on/off
        int h1 = 36;
        card(ctx, cx, y0, cw, h1, HiAllClient.running);
        ctx.drawText(textRenderer, "Auto Messenger", cx + 10, y0 + 8, TEXT, false);
        ctx.drawText(textRenderer,
                fit("Random players, one at a time \u00b7 key " + HiAllClient.toggleKeyName(), cw - 60),
                cx + 10, y0 + 20, SUB, false);
        toggle(ctx, cx + cw - 36, y0 + 12, HiAllClient.running);
        hits.add(new Hit(cx, y0, cw, h1, (px, py) -> HiAllClient.setRunning(!HiAllClient.running)));

        // Card B: message text
        int y1 = y0 + h1 + 8, h2 = 58;
        card(ctx, cx, y1, cw, h2, false);
        ctx.drawText(textRenderer, "Message", cx + 10, y1 + 8, TEXT, false);
        int labelW = textRenderer.getWidth("Message");
        ctx.drawText(textRenderer, fit("Preview: " + HiAllClient.preview(), cw - labelW - 30),
                cx + 18 + labelW, y1 + 8, SUB, false);
        drawField(ctx, cx + 10, y1 + 24, cw - 20, 24, "text", HiAllClient.text,
                "type your message... {player} = their name");

        // Card C: delay slider
        int y2 = y1 + h2 + 8, h3 = 48;
        card(ctx, cx, y2, cw, h3, false);
        ctx.drawText(textRenderer, "Delay between messages", cx + 10, y2 + 8, TEXT, false);
        String v = String.format(Locale.ROOT, "%.1fs", HiAllClient.delaySeconds);
        ctx.drawText(textRenderer, v, cx + cw - 10 - textRenderer.getWidth(v), y2 + 8, ACCENT, false);

        sliderX = cx + 10;
        sliderW = cw - 20;
        double frac = Math.max(0, Math.min(1, (HiAllClient.delaySeconds - 0.5) / 29.5));
        int fw = (int) (frac * sliderW);
        round(ctx, sliderX, y2 + 32, sliderW, 4, OFF);
        round(ctx, sliderX, y2 + 32, Math.max(2, fw), 4, ACCENT);
        round(ctx, sliderX + fw - 3, y2 + 28, 6, 12, 0xFFFFFFFF);
        hits.add(new Hit(cx + 6, y2 + 22, cw - 12, 22, (px, py) -> {
            draggingDelay = true;
            setDelayFromMouse(px);
        }));
    }

    private void setDelayFromMouse(int mx) {
        double frac = Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
        double v = 0.5 + frac * 29.5;
        v = Math.round(v * 2) / 2.0;
        HiAllClient.delaySeconds = Math.max(0.5, Math.min(30, v));
    }

    private void drawBlacklist(DrawContext ctx, int mx, int my, int y0) {
        int h1 = 34;
        card(ctx, cx, y0, cw, h1, false);
        int bw = 44;
        drawField(ctx, cx + 8, y0 + 7, cw - 16 - bw - 6, 20, "bl", blBuf, "usernames to never message...");
        button(ctx, mx, my, cx + cw - 8 - bw, y0 + 7, bw, 20, "Add", true, (px, py) -> addFromField());

        int ly = y0 + h1 + 8;
        int avail = wy + wh - 8 - ly;
        int rows = Math.max(1, (avail + 6) / 26);
        List<String> items = new ArrayList<>(HiAllClient.blacklist);
        int totalRows = (items.size() + 1) / 2;
        int maxScroll = Math.max(0, totalRows - rows);
        blScroll = Math.max(0, Math.min(blScroll, maxScroll));

        if (items.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No one blacklisted yet", cx + cw / 2, ly + 10, SUB);
            return;
        }

        int iw = (cw - 6) / 2;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 2; c++) {
                int idx = (r + blScroll) * 2 + c;
                if (idx >= items.size()) break;
                String name = items.get(idx);
                int x = cx + c * (iw + 6), y = ly + r * 26;
                card(ctx, x, y, iw, 20, false);
                ctx.drawText(textRenderer, fit(name, iw - 28), x + 8, y + 6, TEXT, false);
                boolean hov = in(mx, my, x + iw - 18, y + 2, 16, 16);
                ctx.drawText(textRenderer, "x", x + iw - 12, y + 6, hov ? ACCENT : SUB, false);
                hits.add(new Hit(x + iw - 18, y + 2, 16, 16, (px, py) -> {
                    HiAllClient.blacklist.remove(name);
                    HiAllClient.save();
                }));
            }
        }
    }

    private void addFromField() {
        if (!blBuf.isBlank()) HiAllClient.addBlacklist(blBuf);
        blBuf = "";
    }

    private void drawSettings(DrawContext ctx, int mx, int my, int y0) {
        // Command selector
        int h1 = 50;
        card(ctx, cx, y0, cw, h1, false);
        ctx.drawText(textRenderer, "Command", cx + 10, y0 + 8, TEXT, false);
        String cur = "Sends /" + HiAllClient.command + " <player> <text>";
        int labelW = textRenderer.getWidth("Command");
        ctx.drawText(textRenderer, fit(cur, cw - labelW - 30), cx + 18 + labelW, y0 + 8, SUB, false);
        String[] cmds = {"msg", "w", "tell", "whisper"};
        for (int i = 0; i < cmds.length; i++) {
            String name = cmds[i];
            button(ctx, mx, my, cx + 10 + i * 56, y0 + 24, 50, 18, name,
                    HiAllClient.command.equals(name),
                    (px, py) -> { HiAllClient.command = name; HiAllClient.save(); });
        }

        // Auto start
        int y1 = y0 + h1 + 8, h2 = 34;
        card(ctx, cx, y1, cw, h2, HiAllClient.autoStart);
        ctx.drawText(textRenderer, "Auto-start on join", cx + 10, y1 + 7, TEXT, false);
        ctx.drawText(textRenderer, fit("Start messaging as soon as you join a server", cw - 60),
                cx + 10, y1 + 19, SUB, false);
        toggle(ctx, cx + cw - 36, y1 + 11, HiAllClient.autoStart);
        hits.add(new Hit(cx, y1, cw, h2, (px, py) -> {
            HiAllClient.autoStart = !HiAllClient.autoStart;
            HiAllClient.save();
        }));

        // Reset list
        int y2 = y1 + h2 + 8;
        card(ctx, cx, y2, cw, h2, false);
        ctx.drawText(textRenderer, "Messaged list", cx + 10, y2 + 7, TEXT, false);
        ctx.drawText(textRenderer, HiAllClient.done.size() + " players messaged this session",
                cx + 10, y2 + 19, SUB, false);
        button(ctx, mx, my, cx + cw - 10 - 46, y2 + 8, 46, 18, "Reset", false,
                (px, py) -> HiAllClient.done.clear());
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            focus = "";
            int mx = (int) mouseX, my = (int) mouseY;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (in(mx, my, h.x(), h.y(), h.w(), h.h())) {
                    h.click().run(mx, my);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingDelay) {
            setDelayFromMouse((int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingDelay) {
            draggingDelay = false;
            HiAllClient.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 1) {
            blScroll = Math.max(0, blScroll - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void append(String s) {
        if (focus.equals("text")) {
            if (HiAllClient.text.length() + s.length() <= 150) HiAllClient.text += s;
        } else if (focus.equals("bl")) {
            if (blBuf.length() + s.length() <= 80) blBuf += s;
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focus.isEmpty() && chr >= 32 && chr != 127) {
            append(String.valueOf(chr));
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focus.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focus = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus.equals("bl")) addFromField();
                else focus = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus.equals("text")) {
                    String t = HiAllClient.text;
                    HiAllClient.text = hasControlDown() || t.isEmpty() ? "" : t.substring(0, t.length() - 1);
                } else {
                    blBuf = hasControlDown() || blBuf.isEmpty() ? "" : blBuf.substring(0, blBuf.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
                String clip = client.keyboard.getClipboard();
                if (clip != null) append(clip.replaceAll("[\\r\\n]+", " "));
                return true;
            }
            return true; // swallow other keys while typing
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        HiAllClient.save();
        super.removed();
    }
}
