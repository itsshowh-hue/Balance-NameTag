package com.example.balancenametag;

import com.example.balancenametag.config.BalanceConfig;
import com.example.balancenametag.net.BalanceSyncPayload;
import com.example.balancenametag.provider.BalanceManager;
import com.example.balancenametag.provider.ScoreboardBalanceProvider;
import com.example.balancenametag.provider.ServerPayloadBalanceProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class BalanceNametagClient implements ClientModInitializer {
    public static final String MOD_ID = "balancenametag";
    public static final Logger LOGGER = LoggerFactory.getLogger("Balance Nametag");

    private static volatile BalanceConfig config = new BalanceConfig();
    private static BalanceManager manager = new BalanceManager(List.of());
    private static KeyBinding toggleKey;

    public static BalanceConfig config() {
        return config;
    }

    public static BalanceManager manager() {
        return manager;
    }

    @Override
    public void onInitializeClient() {
        config = BalanceConfig.load(LOGGER);

        ServerPayloadBalanceProvider payloadProvider = new ServerPayloadBalanceProvider();
        // Provider order = priority. First provider that has a value wins.
        manager = new BalanceManager(List.of(payloadProvider, new ScoreboardBalanceProvider()));

        PayloadTypeRegistry.playS2C().register(BalanceSyncPayload.ID, BalanceSyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BalanceSyncPayload.ID,
                (payload, context) -> payloadProvider.apply(payload));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> manager.clear());

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.balancenametag.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                "category.balancenametag"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                toggle(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("balancenametag")
                        .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                            toggle(ctx.getSource().getClient());
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            config = BalanceConfig.load(LOGGER);
                            manager.clear();
                            ctx.getSource().sendFeedback(Text.literal("Balance Nametag: config reloaded."));
                            return 1;
                        }))));

        LOGGER.info("Balance Nametag initialised (enabled={})", config.enabled);
    }

    private static void toggle(MinecraftClient client) {
        BalanceConfig cfg = config;
        cfg.enabled = !cfg.enabled;
        cfg.save(LOGGER);
        manager.clear();
        if (client != null && client.player != null) {
            client.player.sendMessage(
                    Text.literal("Balance display: " + (cfg.enabled ? "ON" : "OFF")), true);
        }
    }
}
