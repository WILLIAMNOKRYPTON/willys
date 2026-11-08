package basehunter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HunterScreen extends Screen {
    private static final int[] AFK_OPTIONS = {15, 30, 60, 120, 300};

    private ButtonWidget masterBtn, chunkBtn, spawnerBtn, afkBtn, timeBtn;

    public HunterScreen() {
        super(Text.literal("Base Hunter"));
    }

    @Override
    protected void init() {
        int w = 150, h = 20, x = 20, y = 40, gap = 24;

        masterBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            BaseHunterClient.enabled = !BaseHunterClient.enabled;
            if (!BaseHunterClient.enabled) Scanner.clearGlow();
            refresh();
        }).dimensions(x, y, w, h).build());

        chunkBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            BaseHunterClient.scanChunks = !BaseHunterClient.scanChunks;
            refresh();
        }).dimensions(x, y + gap, w, h).build());

        spawnerBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            BaseHunterClient.scanSpawners = !BaseHunterClient.scanSpawners;
            refresh();
        }).dimensions(x, y + gap * 2, w, h).build());

        afkBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            BaseHunterClient.scanAfk = !BaseHunterClient.scanAfk;
            if (!BaseHunterClient.scanAfk) Scanner.clearGlow();
            refresh();
        }).dimensions(x, y + gap * 3, w, h).build());

        timeBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            int idx = 0;
            for (int i = 0; i < AFK_OPTIONS.length; i++) {
                if (AFK_OPTIONS[i] == BaseHunterClient.afkSeconds) idx = i;
            }
            BaseHunterClient.afkSeconds = AFK_OPTIONS[(idx + 1) % AFK_OPTIONS.length];
            refresh();
        }).dimensions(x, y + gap * 4, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x, y + gap * 5 + 6, w, h).build());

        refresh();
    }

    private static String onOff(boolean v) {
        return v ? "§aON" : "§cOFF";
    }

    private void refresh() {
        masterBtn.setMessage(Text.literal("Base Hunter: " + onOff(BaseHunterClient.enabled)));
        chunkBtn.setMessage(Text.literal("Sus chunks: " + onOff(BaseHunterClient.scanChunks)));
        spawnerBtn.setMessage(Text.literal("Spawners: " + onOff(BaseHunterClient.scanSpawners)));
        afkBtn.setMessage(Text.literal("AFK players: " + onOff(BaseHunterClient.scanAfk)));
        timeBtn.setMessage(Text.literal("AFK after: " + BaseHunterClient.afkSeconds + "s"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, "§lBase Hunter", width / 2, 12, 0xFF55FFAA);

        int px = 190, py = 40, pw = width - px - 20, ph = height - py - 20;
        ctx.fill(px, py, px + pw, py + ph, 0xAA000000);

        MinecraftClient mc = MinecraftClient.getInstance();
        int ty = py + 6;
        int tx = px + 8;

        ctx.drawTextWithShadow(textRenderer, "Suspicious chunks", tx, ty, 0xFFFFD166);
        ty += 11;
        int shown = 0;
        for (Scanner.SusChunk c : Scanner.susChunks()) {
            if (shown++ >= 5) break;
            ctx.drawTextWithShadow(textRenderer,
                    "  x " + (c.pos().getStartX() + 8) + "  z " + (c.pos().getStartZ() + 8) + "   score " + c.score(),
                    tx, ty, 0xFFFFFFFF);
            ty += 10;
        }
        if (shown == 0) { ctx.drawTextWithShadow(textRenderer, "  none yet", tx, ty, 0xFF888888); ty += 10; }

        ty += 6;
        ctx.drawTextWithShadow(textRenderer, "Spawners (no mineshaft / trial chamber)", tx, ty, 0xFFFF6B6B);
        ty += 11;
        List<BlockPos> sp = new ArrayList<>(Scanner.spawners);
        if (mc.player != null) {
            sp.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(mc.player.getBlockPos())));
        }
        shown = 0;
        for (BlockPos p : sp) {
            if (shown++ >= 5) break;
            ctx.drawTextWithShadow(textRenderer,
                    "  " + p.getX() + " " + p.getY() + " " + p.getZ(), tx, ty, 0xFFFFFFFF);
            ty += 10;
        }
        if (shown == 0) { ctx.drawTextWithShadow(textRenderer, "  none yet", tx, ty, 0xFF888888); ty += 10; }

        ty += 6;
        ctx.drawTextWithShadow(textRenderer, "AFK players", tx, ty, 0xFFFFAA00);
        ty += 11;
        shown = 0;
        for (Scanner.AfkInfo a : Scanner.afk.values()) {
            if (!a.isAfk()) continue;
            if (shown++ >= 6) break;
            ctx.drawTextWithShadow(textRenderer,
                    "  " + a.name() + "  " + (int) a.x() + " " + (int) a.y() + " " + (int) a.z(),
                    tx, ty, 0xFFFFFFFF);
            ty += 10;
        }
        if (shown == 0) { ctx.drawTextWithShadow(textRenderer, "  none yet", tx, ty, 0xFF888888); }

        ctx.drawCenteredTextWithShadow(textRenderer,
                "Press B to toggle, N for this menu. Rebind in Controls.", width / 2, height - 14, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
