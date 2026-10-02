package cn.tohsaka.factory.terraingraph;

import cn.tohsaka.factory.terraingraph.mixin.NoiseGeneratorAccess;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;

/** Explicit opt-in diagnostic; never runs automatically during normal world generation. */
final class GraphVerifier {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final DensityFunctions.BeardifierOrMarker NO_STRUCTURES = new DensityFunctions.BeardifierOrMarker() {
        @Override public double compute(net.minecraft.world.level.levelgen.DensityFunction.FunctionContext context) { return 0; }
        @Override public double minValue() { return 0; }
        @Override public double maxValue() { return 0; }
    };
    private static final int[][] POINTS = {
            {0, 0}, {15, -16}, {-1, 16}, {16, 17}, {-4096, 1024}, {30000, 30000},
            {40000, 40000}, {50000, 50000}, {60000, 60000}, {-30000, -30000},
            {12345, -67890}, {29999000, -29999000}
    };

    private GraphVerifier() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("terraingraph").requires(source -> source.hasPermission(2))
                .then(Commands.literal("verify").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!TerrainGraphConfig.enabled() || !RUNNING.compareAndSet(false, true)) {
                        source.sendFailure(Component.literal("TerrainGraph verification requires enabled optimization and no active verification."));
                        return 0;
                    }
                    List<WorldQuery> worlds = new ArrayList<>();
                    for (var level : source.getServer().getAllLevels()) {
                        ChunkGenerator generator = level.getChunkSource().getGenerator();
                        if (generator instanceof NoiseBasedChunkGenerator) {
                            worlds.add(new WorldQuery(level.dimension().location().toString(), (NoiseBasedChunkGenerator) generator,
                                    level.getChunkSource().randomState(),
                                    LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()),
                                    level.registryAccess().registryOrThrow(Registries.BIOME), level.structureManager()));
                        }
                    }
                    source.sendSuccess(() -> Component.literal("TerrainGraph verification started for " + worlds.size() + " noise-generated dimensions."), false);
                    CompletableFuture.runAsync(() -> verify(worlds), Util.backgroundExecutor());
                    return 1;
                })));
    }

    private static void verify(List<WorldQuery> worlds) {
        JsonObject report = new JsonObject();
        JsonArray dimensions = new JsonArray();
        report.add("dimensions", dimensions);
        int columns = 0;
        int heights = 0;
        int chunks = 0;
        long blocks = 0;
        try {
            for (WorldQuery world : worlds) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (int[] point : POINTS) {
                    int x = point[0];
                    int z = point[1];
                    for (Heightmap.Types type : Heightmap.Types.values()) {
                        int original = TerrainGraphConfig.original(() -> world.generator.getBaseHeight(x, z, type, world.height, world.random));
                        int optimized = world.generator.getBaseHeight(x, z, type, world.height, world.random);
                        if (original != optimized) {
                            throw new IllegalStateException(world.dimension + " height mismatch at " + x + "," + z + " " + type + ": " + original + " != " + optimized);
                        }
                        heights++;
                        digest.update((x + "," + z + "," + type + "," + original + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    NoiseColumn original = TerrainGraphConfig.original(() -> world.generator.getBaseColumn(x, z, world.height, world.random));
                    NoiseColumn optimized = world.generator.getBaseColumn(x, z, world.height, world.random);
                    for (int y = world.height.getMinBuildHeight(); y < world.height.getMaxBuildHeight(); y++) {
                        if (original.getBlock(y) != optimized.getBlock(y)) {
                            throw new IllegalStateException(world.dimension + " column mismatch at " + x + "," + y + "," + z
                                    + ": " + original.getBlock(y) + " != " + optimized.getBlock(y));
                        }
                        digest.update((y + ":" + original.getBlock(y) + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    columns++;
                    // Custom generators can require thread context established by their own pipeline.
                    if (world.generator.getClass() != NoiseBasedChunkGenerator.class) continue;
                    ProtoChunk originalChunk = TerrainGraphConfig.original(() -> fill(world, x, z));
                    ProtoChunk optimizedChunk = fill(world, x, z);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    ChunkPos chunkPos = originalChunk.getPos();
                    for (int y = world.height.getMinBuildHeight(); y < world.height.getMaxBuildHeight(); y++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            for (int localX = 0; localX < 16; localX++) {
                                pos.set(chunkPos.getMinBlockX() + localX, y, chunkPos.getMinBlockZ() + localZ);
                                if (originalChunk.getBlockState(pos) != optimizedChunk.getBlockState(pos)) {
                                    throw new IllegalStateException(world.dimension + " noise fill mismatch at " + pos);
                                }
                                blocks++;
                            }
                        }
                    }
                    for (int section = 0; section < originalChunk.getSectionsCount(); section++) {
                        for (int qy = 0; qy < 4; qy++) {
                            for (int qz = 0; qz < 4; qz++) {
                                for (int qx = 0; qx < 4; qx++) {
                                    if (!originalChunk.getSection(section).getNoiseBiome(qx, qy, qz)
                                            .equals(optimizedChunk.getSection(section).getNoiseBiome(qx, qy, qz))) {
                                        throw new IllegalStateException(world.dimension + " noise biome mismatch in " + chunkPos);
                                    }
                                }
                            }
                        }
                    }
                    chunks++;
                }
                JsonObject dimension = new JsonObject();
                dimension.addProperty("dimension", world.dimension);
                dimension.addProperty("points", POINTS.length);
                dimension.addProperty("heightTypes", Heightmap.Types.values().length);
                dimension.addProperty("queryGenerator", world.generator.getClass().getName());
                boolean fillCompared = world.generator.getClass() == NoiseBasedChunkGenerator.class;
                dimension.addProperty("noiseFillCompared", fillCompared);
                dimension.addProperty("noiseBiomesCompared", fillCompared);
                if (!fillCompared) dimension.addProperty("noiseFillSkipped", "Custom generator requires its own pipeline context");
                dimension.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
                dimensions.add(dimension);
                LOGGER.info("TerrainGraph verified {}: {} full columns, {} noise chunks and {} height queries", world.dimension,
                        POINTS.length, fillCompared ? POINTS.length : 0, POINTS.length * Heightmap.Types.values().length);
            }
            report.addProperty("status", "passed");
            LOGGER.info("TerrainGraph verification PASSED: {} columns, {} heights, {} noise chunks, {} blocks", columns, heights, chunks, blocks);
        } catch (Exception error) {
            report.addProperty("status", "failed");
            report.addProperty("error", error.toString());
            LOGGER.error("TerrainGraph verification FAILED", error);
        } finally {
            report.addProperty("columnsCompared", columns);
            report.addProperty("heightsCompared", heights);
            report.addProperty("noiseChunksCompared", chunks);
            report.addProperty("noiseBlocksCompared", blocks);
            String output = System.getProperty("terraingraph.verify.report");
            if (output != null) {
                try {
                    Files.writeString(Path.of(output), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
                } catch (Exception error) {
                    LOGGER.error("Could not save TerrainGraph verification report", error);
                }
            }
            RUNNING.set(false);
        }
    }

    private static ProtoChunk fill(WorldQuery world, int x, int z) {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)),
                UpgradeData.EMPTY, world.height, world.biomes, null);
        NoiseGeneratorAccess access = (NoiseGeneratorAccess) world.generator;
        chunk.getOrCreateNoiseChunk(ignored -> NoiseChunk.forChunk(chunk, world.random, NO_STRUCTURES,
                world.generator.generatorSettings().value(), access.terraingraph$fluidPicker().get(), Blender.empty()));
        access.terraingraph$createBiomes(Blender.empty(), world.random, world.structures, chunk);
        NoiseSettings settings = world.generator.generatorSettings().value().noiseSettings().clampToHeightAccessor(world.height);
        int minCellY = Math.floorDiv(settings.minY(), settings.getCellHeight());
        int count = Math.floorDiv(settings.height(), settings.getCellHeight());
        if (count > 0) access.terraingraph$fill(Blender.empty(), world.structures, world.random, chunk, minCellY, count);
        return chunk;
    }

    private record WorldQuery(String dimension, NoiseBasedChunkGenerator generator, RandomState random,
                              LevelHeightAccessor height, Registry<Biome> biomes, StructureManager structures) {}
}
