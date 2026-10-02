package cn.tohsaka.factory.terraingraph;

import net.minecraft.world.level.levelgen.DensityFunction;

public final class MemoizingVisitor implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final IdentityMemo<DensityFunction, DensityFunction> converted;
    private final boolean metrics;

    public static DensityFunction.Visitor wrap(DensityFunction.Visitor visitor) {
        if (visitor instanceof MemoizingVisitor) return visitor;
        // Compiled density nodes can inspect visitor interfaces, such as C2ME's blending marker.
        for (Class<?> type = visitor.getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> contract : type.getInterfaces()) {
                if (contract != DensityFunction.Visitor.class) return visitor;
            }
        }
        return new MemoizingVisitor(visitor);
    }

    private MemoizingVisitor(DensityFunction.Visitor delegate) {
        this.delegate = delegate;
        this.converted = new IdentityMemo<>(TerrainGraphConfig.maxNodes());
        this.metrics = TerrainGraphConfig.metrics();
        if (metrics) GraphMetrics.TRAVERSALS.increment();
    }

    public DensityFunction find(DensityFunction source) {
        DensityFunction mapped = converted.get(source);
        if (metrics) {
            if (mapped == null) GraphMetrics.MISSES.increment();
            else GraphMetrics.HITS.increment();
        }
        return mapped;
    }

    public void remember(DensityFunction source, DensityFunction mapped) {
        converted.remember(source, mapped);
    }

    @Override
    public DensityFunction apply(DensityFunction mapped) {
        return delegate.apply(mapped);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return delegate.visitNoise(noise);
    }
}
