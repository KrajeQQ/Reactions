package me.tochuuu.reactions.network;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClientConfig;
import me.tochuuu.reactions.client.RemoteEyeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ReactionsNetworking {
    private static final Identifier EYE_CONFIG_C2S = Identifier.fromNamespaceAndPath(Reactions.MOD_ID, "eye_config_c2s");
    private static final Identifier EYE_CONFIG_S2C = Identifier.fromNamespaceAndPath(Reactions.MOD_ID, "eye_config_s2c");
    private static final int UPDATE = 0;
    private static final int REMOVE = 1;
    private static final int CLIENT_SYNC_RETRY_TICKS = 20 * 30;
    private static final int SERVER_SYNC_RETRY_TICKS = 20 * 30;
    private static final Map<Integer, RemoteEyeConfig> CLIENT_CONFIGS = new HashMap<>();
    private static final Map<UUID, RemoteEyeConfig> CLIENT_CONFIGS_BY_UUID = new HashMap<>();
    private static final Map<UUID, RemoteEyeConfig> SERVER_CONFIGS = new HashMap<>();
    private static final Map<UUID, Integer> SERVER_PENDING_SYNC = new HashMap<>();
    private static int clientSyncTicksRemaining;
    private static int clientSyncCooldown;
    private static boolean initialized;

    private ReactionsNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.c2s(), EYE_CONFIG_C2S, (buf, context) -> {
            RemoteEyeConfig config = readUpdate(buf);
            context.queue(() -> {
                Player player = context.getPlayer();
                if (player instanceof ServerPlayer serverPlayer) {
                    RemoteEyeConfig serverConfig = withPlayerIdentity(config, serverPlayer.getUUID(), serverPlayer.getId());
                    SERVER_CONFIGS.put(serverPlayer.getUUID(), serverConfig);
                    sendKnownConfigs(serverPlayer);
                    sendUpdateToReceivers(serverPlayer, serverConfig);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), EYE_CONFIG_S2C, (buf, context) -> {
            int action = buf.readUnsignedByte();
            if (action == UPDATE) {
                RemoteEyeConfig config = readUpdateBody(buf);
                context.queue(() -> applyRemoteConfig(config));
            } else if (action == REMOVE) {
                UUID playerId = buf.readUUID();
                context.queue(() -> removeRemoteConfig(playerId));
            }
        });

        PlayerEvent.PLAYER_JOIN.register(player -> SERVER_PENDING_SYNC.put(player.getUUID(), SERVER_SYNC_RETRY_TICKS));
        PlayerEvent.PLAYER_QUIT.register(player -> {
            SERVER_CONFIGS.remove(player.getUUID());
            SERVER_PENDING_SYNC.remove(player.getUUID());
            sendRemoveToReceivers(player);
        });

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            CLIENT_CONFIGS.clear();
            CLIENT_CONFIGS_BY_UUID.clear();
            requestLocalConfigSync();
        });
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            CLIENT_CONFIGS.clear();
            CLIENT_CONFIGS_BY_UUID.clear();
            clientSyncTicksRemaining = 0;
            clientSyncCooldown = 0;
        });

        ClientTickEvent.CLIENT_POST.register(client -> retryClientSync());
        TickEvent.SERVER_POST.register(server -> retryServerSync(server));
    }

    public static RemoteEyeConfig remoteConfig(int entityId) {
        RemoteEyeConfig config = CLIENT_CONFIGS.get(entityId);
        if (config != null) {
            return config;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        for (Player player : minecraft.level.players()) {
            if (player.getId() == entityId) {
                config = CLIENT_CONFIGS_BY_UUID.get(player.getUUID());
                if (config != null) {
                    RemoteEyeConfig resolved = withPlayerIdentity(config, player.getUUID(), entityId);
                    CLIENT_CONFIGS.put(entityId, resolved);
                    return resolved;
                }
            }
        }
        return null;
    }

    public static boolean hasRemoteConfig(int entityId) {
        return remoteConfig(entityId) != null;
    }

    public static void applyRemoteConfig(RemoteEyeConfig config) {
        CLIENT_CONFIGS.put(config.entityId(), config);
        if (config.playerId() != null) {
            CLIENT_CONFIGS_BY_UUID.put(config.playerId(), config);
        }
    }

    public static boolean canSyncWithServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && NetworkManager.canServerReceive(EYE_CONFIG_C2S);
    }

    public static void sendLocalConfigToServer() {
        requestLocalConfigSync();
    }

    private static void requestLocalConfigSync() {
        clientSyncTicksRemaining = CLIENT_SYNC_RETRY_TICKS;
        clientSyncCooldown = 0;
        trySendLocalConfigToServer();
    }

    private static void retryClientSync() {
        if (clientSyncTicksRemaining <= 0) {
            return;
        }

        clientSyncTicksRemaining--;
        if (clientSyncCooldown > 0) {
            clientSyncCooldown--;
            return;
        }

        if (trySendLocalConfigToServer()) {
            clientSyncTicksRemaining = 0;
        } else {
            clientSyncCooldown = 10;
        }
    }

    private static boolean trySendLocalConfigToServer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !NetworkManager.canServerReceive(EYE_CONFIG_C2S)) {
            return false;
        }

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), minecraft.level.registryAccess());
        writeUpdate(buf, localConfig(minecraft.player.getUUID(), minecraft.player.getId()));
        NetworkManager.sendToServer(EYE_CONFIG_C2S, buf);
        return true;
    }

    private static void retryServerSync(net.minecraft.server.MinecraftServer server) {
        if (SERVER_PENDING_SYNC.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = SERVER_PENDING_SYNC.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayers().stream()
                .filter(candidate -> candidate.getUUID().equals(entry.getKey()))
                .findFirst()
                .orElse(null);
            if (player == null || entry.getValue() <= 0) {
                iterator.remove();
                continue;
            }

            entry.setValue(entry.getValue() - 10);
            if (NetworkManager.canPlayerReceive(player, EYE_CONFIG_S2C)) {
                SERVER_CONFIGS.values().forEach(config -> sendUpdate(player, config));
                iterator.remove();
            }
        }
    }

    private static void sendUpdateToReceivers(ServerPlayer source, RemoteEyeConfig config) {
        source.level().getServer().getPlayerList().getPlayers().stream()
            .filter(player -> NetworkManager.canPlayerReceive(player, EYE_CONFIG_S2C))
            .forEach(player -> sendUpdate(player, config));
    }

    private static void sendKnownConfigs(ServerPlayer player) {
        if (!NetworkManager.canPlayerReceive(player, EYE_CONFIG_S2C)) {
            return;
        }
        SERVER_CONFIGS.values().forEach(config -> sendUpdate(player, config));
    }

    private static void sendRemoveToReceivers(ServerPlayer source) {
        source.level().getServer().getPlayerList().getPlayers().stream()
            .filter(player -> NetworkManager.canPlayerReceive(player, EYE_CONFIG_S2C))
            .forEach(player -> sendRemove(player, source.getUUID()));
    }

    private static void sendUpdate(ServerPlayer player, RemoteEyeConfig config) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        writeUpdate(buf, config);
        NetworkManager.sendToPlayer(player, EYE_CONFIG_S2C, buf);
    }

    private static void sendRemove(ServerPlayer player, UUID playerId) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        buf.writeByte(REMOVE);
        buf.writeUUID(playerId);
        NetworkManager.sendToPlayer(player, EYE_CONFIG_S2C, buf);
    }

    private static void writeUpdate(RegistryFriendlyByteBuf buf, RemoteEyeConfig config) {
        buf.writeByte(UPDATE);
        buf.writeUUID(config.playerId());
        buf.writeVarInt(config.entityId());
        buf.writeByte(config.leftEyeX());
        buf.writeByte(config.leftEyeY());
        buf.writeByte(config.rightEyeX());
        buf.writeByte(config.rightEyeY());
        buf.writeByte(config.eyelidColorX());
        buf.writeByte(config.eyelidColorY());
        buf.writeByte(config.eyeWidth());
        buf.writeByte(config.eyeHeight());
    }

    private static RemoteEyeConfig readUpdate(RegistryFriendlyByteBuf buf) {
        int action = buf.readUnsignedByte();
        if (action != UPDATE) {
            throw new IllegalArgumentException("Expected eye config update packet");
        }
        return readUpdateBody(buf);
    }

    private static RemoteEyeConfig readUpdateBody(RegistryFriendlyByteBuf buf) {
        return new RemoteEyeConfig(
            buf.readUUID(),
            buf.readVarInt(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte(),
            buf.readUnsignedByte()
        );
    }

    private static RemoteEyeConfig localConfig(UUID playerId, int entityId) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        return new RemoteEyeConfig(
            playerId,
            entityId,
            config.leftEyeX,
            config.leftEyeY,
            config.rightEyeX,
            config.rightEyeY,
            config.eyelidColorX,
            config.eyelidColorY,
            config.eyeWidth,
            config.eyeHeight
        );
    }

    private static RemoteEyeConfig withPlayerIdentity(RemoteEyeConfig config, UUID playerId, int entityId) {
        return new RemoteEyeConfig(
            playerId,
            entityId,
            config.leftEyeX(),
            config.leftEyeY(),
            config.rightEyeX(),
            config.rightEyeY(),
            config.eyelidColorX(),
            config.eyelidColorY(),
            config.eyeWidth(),
            config.eyeHeight()
        );
    }

    private static void removeRemoteConfig(UUID playerId) {
        CLIENT_CONFIGS_BY_UUID.remove(playerId);
        CLIENT_CONFIGS.entrySet().removeIf(entry -> playerId.equals(entry.getValue().playerId()));
    }
}
