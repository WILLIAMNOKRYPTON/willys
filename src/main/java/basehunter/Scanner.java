package basehunter;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Scanner {
    public record SusChunk(ChunkPos pos, int score) {}

    public static class AfkInfo {
        String name;
        double x, y, z;
        float yaw, pitch;
        long lastMoveTick;
        boolean isAfk;
        public String name() { return name; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public boolean isAfk() { return isAfk; }
    }

    public static final Map<ChunkPos, SusChunk> chunks = new ConcurrentHashMap<>();
    public static final Set<BlockPos> spawners = ConcurrentHashMap.newKeySet();
    public static final Map<UUID, AfkInfo> afk = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Boolean> spawnerCache = new HashMap<>();
    private static long tickCount = 0;

    public static int susThreshold = 8;

    public static List<SusChunk> susChunks() {
        List<SusChunk> list = new ArrayList<>(chunks.values());
        list.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return list;
    }

    public static void tick(MinecraftClient mc) {
        tickCount++;
        if (BaseHunterClient.scanAfk) scanAfk(mc);
        if (tickCount % 40 == 0) {
            scanChunks(mc);
        }
    }

    private static int weight(Block b) {
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return 1;
        if (b == Blocks.ENDER_CHEST) return 2;
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return 1;
        if (b == Blocks.HOPPER) return 2;
        if (b == Blocks.DISPENSER || b == Blocks.DROPPER) return 1;
        if (b == Blocks.BEACON) return 5;
        if (b.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.SHULKER_BOXES)) return 3;
        if (b.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.BEDS)) return 1;
        return 0;
    }

    private static void scanChunks(MinecraftClient mc) {
        World world = mc.world;
        if (world == null || mc.player == null) return;
        int radius = mc.options.getViewDistance().getValue();
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        chunks.clear();
        spawners.clear();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                int score = 0;
                boolean trialChamber = false;
                List<BlockPos> foundSpawners = new ArrayList<>();

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    Block b = be.getCachedState().getBlock();
                    // Trial chamber blocks: skip the whole chunk
                    if (b == Blocks.TRIAL_SPAWNER || b == Blocks.VAULT) {
                        trialChamber = true;
                        break;
                    }
                    if (b == Blocks.SPAWNER) {
                        foundSpawners.add(be.getPos());
                        continue;
                    }
                    score += weight(b);
                }
                if (trialChamber) continue;

                if (BaseHunterClient.scanSpawners) {
                    for (BlockPos sp : foundSpawners) {
                        if (!isMineshaftSpawner(world, sp)) spawners.add(sp);
                    }
                }

                if (BaseHunterClient.scanChunks && score >= susThreshold) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    chunks.put(cp, new SusChunk(cp, score));
                }
            }
        }
    }

    /** Mineshaft cave-spider spawners sit in cobwebs next to rails/planks. Ignore those. */
    private static boolean isMineshaftSpawner(World world, BlockPos pos) {
        return spawnerCache.computeIfAbsent(pos.toImmutable(), p -> {
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int dx = -6; dx <= 6; dx++)
                for (int dy = -3; dy <= 3; dy++)
                    for (int dz = -6; dz <= 6; dz++) {
                        m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                        Block b = world.getBlockState(m).getBlock();
                        if (b == Blocks.COBWEB || b == Blocks.RAIL) return true;
                    }
            return false;
        });
    }

    private static void scanAfk(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        long threshold = BaseHunterClient.afkSeconds * 20L;
        Set<UUID> seen = new HashSet<>();

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue; // never highlight yourself
            UUID id = p.getUuid();
            seen.add(id);
            AfkInfo info = afk.computeIfAbsent(id, k -> {
                AfkInfo a = new AfkInfo();
                a.lastMoveTick = tickCount;
                a.x = p.getX(); a.y = p.getY(); a.z = p.getZ();
                a.yaw = p.getYaw(); a.pitch = p.getPitch();
                return a;
            });
            info.name = p.getName().getString();

            boolean moved = Math.abs(p.getX() - info.x) > 0.05
                    || Math.abs(p.getY() - info.y) > 0.05
                    || Math.abs(p.getZ() - info.z) > 0.05
                    || Math.abs(p.getYaw() - info.yaw) > 1.0f
                    || Math.abs(p.getPitch() - info.pitch) > 1.0f;
            if (moved) {
                info.lastMoveTick = tickCount;
                info.x = p.getX(); info.y = p.getY(); info.z = p.getZ();
                info.yaw = p.getYaw(); info.pitch = p.getPitch();
            }
            info.isAfk = (tickCount - info.lastMoveTick) >= threshold;
            p.setGlowing(info.isAfk); // highlight only AFK players
        }
        afk.keySet().removeIf(id -> !seen.contains(id));
    }

    public static void clearGlow() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player) p.setGlowing(false);
            }
        }
        chunks.clear();
        spawners.clear();
        afk.clear();
    }
}
