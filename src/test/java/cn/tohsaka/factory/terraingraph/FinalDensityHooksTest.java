package cn.tohsaka.factory.terraingraph;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalDensityHooksTest {
    private CommentedConfig config;
    private final DensityFunction.Visitor visitor = density -> density;

    @BeforeEach
    void loadConfig() {
        config = CommentedConfig.inMemory();
        TerrainGraphConfig.SPEC.setConfig(config);
    }

    @AfterEach
    void unloadConfig() {
        TerrainGraphConfig.SPEC.setConfig(null);
    }

    @Test
    void existingConfigurationEnablesSeparateSecondTraversalByDefault() {
        config.set("enabled", true);
        assertInstanceOf(MemoizingVisitor.class, FinalDensityHooks.wrap(visitor));
        assertNotSame(FinalDensityHooks.wrap(visitor), FinalDensityHooks.wrap(visitor));
    }

    @Test
    void disablingSecondTraversalKeepsFirstTraversalEnabled() {
        config.set("reuseFinalDensity", false);
        assertSame(visitor, FinalDensityHooks.wrap(visitor));
        assertTrue(TerrainGraphConfig.enabled());
    }

    @Test
    void masterSwitchAndDiagnosticOriginalPathDisableSecondTraversal() {
        assertSame(visitor, TerrainGraphConfig.original(() -> FinalDensityHooks.wrap(visitor)));
        assertInstanceOf(MemoizingVisitor.class, FinalDensityHooks.wrap(visitor));
        TerrainGraphConfig.SPEC.getValues().<ForgeConfigSpec.BooleanValue>get("enabled").set(false);
        assertSame(visitor, FinalDensityHooks.wrap(visitor));
    }

    @Test
    void specialVisitorsArePreservedAndCountedWhenMetricsAreEnabled() {
        config.set("collectMetrics", true);
        DensityFunction.Visitor special = new MarkedVisitor();
        long skipped = GraphMetrics.FINAL_SKIPPED_VISITORS.sum();
        long traversals = GraphMetrics.FINAL_TRAVERSALS.sum();
        assertSame(special, FinalDensityHooks.wrap(special));
        assertEquals(skipped + 1, GraphMetrics.FINAL_SKIPPED_VISITORS.sum());
        assertInstanceOf(MemoizingVisitor.class, FinalDensityHooks.wrap(visitor));
        assertEquals(traversals + 1, GraphMetrics.FINAL_TRAVERSALS.sum());
    }

    private interface Marker {}

    private static class MarkedVisitor implements DensityFunction.Visitor, Marker {
        @Override public DensityFunction apply(DensityFunction density) { return density; }
    }
}
