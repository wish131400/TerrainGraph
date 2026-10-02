package cn.tohsaka.factory.terraingraph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityMemoTest {
    @Test
    void identityLookupDoesNotChangeEqualityOrCallRecursiveHash() {
        Object node = new Object() {
            @Override public int hashCode() { throw new AssertionError("Structural hash called"); }
            @Override public boolean equals(Object other) { throw new AssertionError("Structural equality called"); }
        };
        IdentityMemo<Object, Object> memo = new IdentityMemo<>(8);
        Object mapped = new Object();
        memo.remember(node, mapped);
        assertSame(mapped, memo.get(node));
        assertNull(memo.get(new Object()));
    }

    @Test
    void equalButDistinctSourcesAndTraversalsRemainIndependent() {
        Object first = new String("same");
        Object second = new String("same");
        IdentityMemo<Object, Object> left = new IdentityMemo<>(8);
        IdentityMemo<Object, Object> right = new IdentityMemo<>(8);
        Object a = new Object();
        Object b = new Object();
        left.remember(first, a);
        left.remember(second, b);
        assertSame(a, left.get(first));
        assertSame(b, left.get(second));
        assertNull(right.get(first));
    }

    @Test
    void fullCacheKeepsExistingResultsAndFallsBackForNewNodes() {
        IdentityMemo<Object, Object> memo = new IdentityMemo<>(1);
        Object first = new Object();
        Object second = new Object();
        Object mapped = new Object();
        memo.remember(first, mapped);
        memo.remember(second, new Object());
        assertEquals(1, memo.size());
        assertSame(mapped, memo.get(first));
        assertNull(memo.get(second));
    }
}
