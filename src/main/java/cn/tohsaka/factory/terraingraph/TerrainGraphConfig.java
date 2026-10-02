package cn.tohsaka.factory.terraingraph;

import java.util.function.Supplier;
import net.minecraftforge.common.ForgeConfigSpec;

public final class TerrainGraphConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue FINAL_DENSITY;
    private static final ForgeConfigSpec.BooleanValue METRICS;
    private static final ForgeConfigSpec.IntValue MAX_NODES;
    private static final boolean FORCE_DISABLED = Boolean.getBoolean("terraingraph.disabled");
    private static final ThreadLocal<Boolean> ORIGINAL_PATH = new ThreadLocal<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.comment("Reuse density graph conversions within one NoiseChunk initialization.",
                "Restart the server after changing this setting.").define("enabled", true);
        FINAL_DENSITY = builder.comment("Also reuse the second final-density conversion through vanilla or supported ByePregen 1.1.2.4.",
                "Unknown redirects or changed injection points keep their original conversion.",
                "Set false to retain only the first-pass optimization. Restart after changing.")
                .define("reuseFinalDensity", true);
        MAX_NODES = builder.comment("Maximum remembered source nodes per traversal.",
                "When full, additional nodes use the original conversion path.")
                .defineInRange("maxNodesPerTraversal", 8192, 64, 65536);
        METRICS = builder.comment("Count conversion cache hits for /terraingraph stats.",
                "Leave disabled for normal use to avoid counter overhead.")
                .define("collectMetrics", false);
        SPEC = builder.build();
    }

    private TerrainGraphConfig() {}

    public static boolean enabled() {
        return !FORCE_DISABLED && !Boolean.TRUE.equals(ORIGINAL_PATH.get()) && ENABLED.get();
    }

    public static int maxNodes() {
        return MAX_NODES.get();
    }

    public static boolean finalDensityEnabled() {
        return enabled() && FINAL_DENSITY.get();
    }

    public static boolean metrics() {
        return METRICS.get();
    }

    static <T> T original(Supplier<T> query) {
        Boolean previous = ORIGINAL_PATH.get();
        ORIGINAL_PATH.set(true);
        try {
            return query.get();
        } finally {
            if (previous == null) ORIGINAL_PATH.remove();
            else ORIGINAL_PATH.set(previous);
        }
    }
}
