package cn.tohsaka.factory.terraingraph;

import net.minecraft.world.level.levelgen.DensityFunction;

public final class FinalDensityHooks {
    private FinalDensityHooks() {}

    public static DensityFunction.Visitor wrap(DensityFunction.Visitor visitor) {
        if (!TerrainGraphConfig.finalDensityEnabled()) return visitor;
        DensityFunction.Visitor wrapped = MemoizingVisitor.wrap(visitor);
        if (TerrainGraphConfig.metrics()) {
            if (wrapped instanceof MemoizingVisitor) GraphMetrics.FINAL_TRAVERSALS.increment();
            else GraphMetrics.FINAL_SKIPPED_VISITORS.increment();
        }
        return wrapped;
    }
}
