package basehunter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class BaseHunterClient implements ClientModInitializer {
    public static boolean enabled = false;
    public static boolean scanChunks = true;
    public static boolean scanSpawners = true;
    public static boolean scanAfk = true;
    public static int afkSeconds = 30;

    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("basehunter", "main"));
        // Both keys can be rebound in Options > Controls > Key Binds > Base Hunter
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.basehunter.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, cat));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.basehunter.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (!enabled) Scanner.clearGlow();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Base Hunter: " + (enabled ? "ON" : "OFF")), true);
                }
            }
            while (menuKey.wasPressed()) {
                mc.setScreen(new HunterScreen());
            }
            if (enabled && mc.world != null && mc.player != null) {
                Scanner.tick(mc);
            }
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            if (!enabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int x = 6, y = 6;
            ctx.fill(x - 3, y - 3, x + 150, y + 44, 0x99000000);
            ctx.drawTextWithShadow(mc.textRenderer, "Base Hunter", x, y, 0xFF55FFAA);
            ctx.drawTextWithShadow(mc.textRenderer, "Sus chunks: " + Scanner.susChunks().size(), x, y + 11, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.textRenderer, "Spawners: " + Scanner.spawners.size(), x, y + 22, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.textRenderer, "AFK players: " + Scanner.afk.values().stream().filter(a -> a.isAfk).count(), x, y + 33, 0xFFFFAA00);
        });
    }
}
