package cn.tohsaka.factory.terraingraph;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class ByePregenExpressionTransformerTest implements Opcodes {
    private static final String DENSITY = "net/minecraft/world/level/levelgen/DensityFunction";
    private static final String UNARY = "(L" + DENSITY + ";)L" + DENSITY + ";";
    private static final String HANDLER = "modifyExpressionValue$example$byepregen$captureWrappedFinalDensity";

    @Test
    void verifiedExpressionCaptureIsRetainedAndNotWrappedTwice() throws Exception {
        ClassNode node = target();
        MethodNode constructor = node.methods.get(0);
        AbstractInsnNode[] before = constructor.instructions.toArray();
        assertEquals("active: ByePregen 1.1.2.4", FinalDensityTransformer.attach(node, "1.1.2.4"));
        assertEquals(before.length + 1, constructor.instructions.size());
        for (AbstractInsnNode instruction : before) assertTrue(constructor.instructions.contains(instruction));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, constructor);
        assertEquals("active: ByePregen 1.1.2.4", FinalDensityTransformer.attach(node, "1.1.2.4"));
        assertEquals(before.length + 1, constructor.instructions.size());
    }

    @Test
    void unknownVersionOrMixinOriginKeepsOriginalConversion() {
        assertUnchanged(target(), null);
        assertUnchanged(target(), "1.1.2.5");
        ClassNode node = target();
        node.methods.get(1).visibleAnnotations.get(0).values.set(1, "other.mod.CaptureMixin");
        assertUnchanged(node, "1.1.2.4");
    }

    @Test
    void changedExpressionStackOrMethodBodyEntryKeepsOriginalConversion() {
        ClassNode stack = target();
        stack.methods.get(0).instructions.set(stack.methods.get(0).instructions.get(8), new InsnNode(NOP));
        assertUnchanged(stack, "1.1.2.4");
        ClassNode method = target();
        method.methods.get(1).access = ACC_PUBLIC;
        assertUnchanged(method, "1.1.2.4");
    }

    private static void assertUnchanged(ClassNode node, String version) {
        AbstractInsnNode[] before = node.methods.get(0).instructions.toArray();
        assertTrue(FinalDensityTransformer.attach(node, version).startsWith("skipped:"));
        assertArrayEquals(before, node.methods.get(0).instructions.toArray());
    }

    private static ClassNode target() {
        ClassNode node = VanillaFinalDensityTransformerTest.vanilla(false);
        node.version = V21;
        MethodNode constructor = node.methods.get(0);
        InsnList capture = new InsnList();
        capture.add(new VarInsnNode(ALOAD, 0));
        capture.add(new InsnNode(SWAP));
        capture.add(new MethodInsnNode(INVOKESPECIAL, node.name, HANDLER, UNARY, false));
        constructor.instructions.insert(constructor.instructions.get(6), capture);
        constructor.maxStack = 2;
        MethodNode handler = new MethodNode(ACC_PRIVATE, HANDLER, UNARY, null, null);
        handler.instructions.add(new VarInsnNode(ALOAD, 1));
        handler.instructions.add(new InsnNode(ARETURN));
        handler.maxLocals = 2;
        handler.maxStack = 1;
        AnnotationNode origin = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        origin.values = new ArrayList<>(List.of("mixin", "com.moepus.byepregen.mixin.dfc.DensityNoiseChunkMixin"));
        handler.visibleAnnotations = List.of(origin);
        node.methods.add(handler);
        return node;
    }
}
