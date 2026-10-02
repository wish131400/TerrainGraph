package cn.tohsaka.factory.terraingraph;

import java.util.concurrent.atomic.LongAdder;

public final class GraphMetrics {
    public static final LongAdder TRAVERSALS = new LongAdder();
    public static final LongAdder FINAL_TRAVERSALS = new LongAdder();
    public static final LongAdder FINAL_SKIPPED_VISITORS = new LongAdder();
    public static final LongAdder HITS = new LongAdder();
    public static final LongAdder MISSES = new LongAdder();

    private GraphMetrics() {}

    public static String snapshot() {
        return "traversals=" + TRAVERSALS.sum() + ", finalTraversals=" + FINAL_TRAVERSALS.sum()
                + ", finalSpecialVisitors=" + FINAL_SKIPPED_VISITORS.sum()
                + ", reused=" + HITS.sum() + ", converted=" + MISSES.sum();
    }
}
