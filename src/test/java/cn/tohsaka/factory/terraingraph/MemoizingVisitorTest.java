package cn.tohsaka.factory.terraingraph;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoizingVisitorTest {
    @BeforeAll
    static void loadConfig() {
        TestConfigs.load(CommentedConfig.inMemory());
    }

    @AfterAll
    static void unloadConfig() {
        TestConfigs.unload();
    }

    @Test
    void plainVisitorRemainsMemoizedAndDelegatesBothMethods() {
        PlainVisitor original = new PlainVisitor();
        DensityFunction.Visitor wrapped = MemoizingVisitor.wrap(original);
        assertInstanceOf(MemoizingVisitor.class, wrapped);
        assertNull(wrapped.apply(null));
        assertNull(wrapped.visitNoise(null));
        assertEquals(1, original.densityCalls);
        assertEquals(1, original.noiseCalls);
    }

    @Test
    void anAlreadyWrappedVisitorIsNotWrappedAgain() {
        DensityFunction.Visitor wrapped = MemoizingVisitor.wrap(new PlainVisitor());
        assertSame(wrapped, MemoizingVisitor.wrap(wrapped));
    }

    @Test
    void extraInterfacePreservesBlendingDecisionAndVisitorIdentity() {
        BlendingVisitor original = new BlendingVisitor();
        DensityFunction.Visitor wrapped = MemoizingVisitor.wrap(original);
        assertSame(original, wrapped);
        assertTrue(assertInstanceOf(BlendingAware.class, wrapped).blendingEnabled());
    }

    @Test
    void inheritedMarkerInterfaceAlsoPreservesBlendingDecision() {
        InheritedBlendingVisitor original = new InheritedBlendingVisitor();
        DensityFunction.Visitor wrapped = MemoizingVisitor.wrap(original);
        assertSame(original, wrapped);
        assertTrue(assertInstanceOf(BlendingAware.class, wrapped).blendingEnabled());
    }

    @Test
    void visitorSubinterfaceRetainsItsAdditionalContract() {
        ExtendedVisitor original = new ExtendedVisitor() {
            @Override public DensityFunction apply(DensityFunction density) { return density; }
            @Override public boolean blendingEnabled() { return true; }
        };
        DensityFunction.Visitor wrapped = MemoizingVisitor.wrap(original);
        assertSame(original, wrapped);
        assertTrue(assertInstanceOf(ExtendedVisitor.class, wrapped).blendingEnabled());
    }

    private interface BlendingAware {
        boolean blendingEnabled();
    }

    private interface ExtendedVisitor extends DensityFunction.Visitor, BlendingAware {}

    private static class PlainVisitor implements DensityFunction.Visitor {
        private int densityCalls;
        private int noiseCalls;

        @Override
        public DensityFunction apply(DensityFunction density) {
            densityCalls++;
            return density;
        }

        @Override
        public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
            noiseCalls++;
            return noise;
        }
    }

    private static class BlendingVisitor extends PlainVisitor implements BlendingAware {
        @Override public boolean blendingEnabled() { return true; }
    }

    private static final class InheritedBlendingVisitor extends BlendingVisitor {}
}
