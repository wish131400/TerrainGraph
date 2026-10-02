package cn.tohsaka.factory.terraingraph;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class FinalDensityTransformerTest implements Opcodes {
    private static final String DENSITY = "net/minecraft/world/level/levelgen/DensityFunction";
    private static final String VISITOR = "L" + DENSITY + "$Visitor;";
    private static final String MAP = "(" + VISITOR + ")L" + DENSITY + ";";
    private static final String HANDLER = "(L" + DENSITY + ";" + VISITOR + ")L" + DENSITY + ";";

    @Test
    void mergedHandlerIsFoundWithoutDependingOnGeneratedPrefix() throws Exception {
        ClassNode target = target("redirect$changed123$byepregen$captureWrappedFinalDensity", "m_207456_");
        MethodNode handler = target.methods.get(1);
        AbstractInsnNode[] original = handler.instructions.toArray();
        assertEquals("active: ByePregen 1.1.2.4", FinalDensityTransformer.attach(target, "1.1.2.4"));
        assertEquals(original.length + 1, handler.instructions.size());
        assertEquals("cn/tohsaka/factory/terraingraph/FinalDensityHooks",
                assertInstanceOf(MethodInsnNode.class, original[2].getPrevious()).owner);
        for (AbstractInsnNode instruction : original) assertTrue(handler.instructions.contains(instruction));
        new Analyzer<>(new BasicVerifier()).analyze(target.name, handler);
        assertEquals("active: ByePregen 1.1.2.4", FinalDensityTransformer.attach(target, "1.1.2.4"));
        assertEquals(original.length + 1, handler.instructions.size());
    }

    @Test
    void developmentNamesAndInvisibleOriginAnnotationsAreSupported() {
        ClassNode target = target("byepregen$captureWrappedFinalDensity", "mapAll");
        MethodNode handler = target.methods.get(1);
        handler.invisibleAnnotations = handler.visibleAnnotations;
        handler.visibleAnnotations = null;
        assertEquals("active: ByePregen 1.1.2.4", FinalDensityTransformer.attach(target, "1.1.2.4"));
    }

    @Test
    void absentAndUnknownByePregenVersionsLeaveBytecodeUntouched() {
        ClassNode target = target("byepregen$captureWrappedFinalDensity", "mapAll");
        assertUnchanged(target, null, "no supported");
        assertUnchanged(target, "1.1.2.0", "unsupported");
        assertUnchanged(target, "1.1.2.5", "unsupported");
    }

    @Test
    void matchingNameFromAnotherMixinIsNotEligible() {
        ClassNode target = target("byepregen$captureWrappedFinalDensity", "mapAll");
        target.methods.get(1).visibleAnnotations.get(0).values.set(1, "another.mod.NoiseChunkMixin");
        assertUnchanged(target, "1.1.2.4", "not uniquely identified");
    }

    @Test
    void changedHandlerBodyAndDescriptorAreNotEligible() {
        ClassNode changedBody = target("byepregen$captureWrappedFinalDensity", "mapAll");
        changedBody.methods.get(1).instructions.insert(new InsnNode(NOP));
        assertUnchanged(changedBody, "1.1.2.4", "body changed");
        ClassNode changedDescriptor = target("byepregen$captureWrappedFinalDensity", "mapAll");
        changedDescriptor.methods.get(1).desc = "()V";
        assertUnchanged(changedDescriptor, "1.1.2.4", "not uniquely identified");
    }

    @Test
    void ambiguousOrDisconnectedConstructorCallIsNotEligible() {
        ClassNode duplicate = target("byepregen$captureWrappedFinalDensity", "mapAll");
        duplicate.methods.add(target("byepregen$captureWrappedFinalDensity", "mapAll").methods.get(0));
        assertUnchanged(duplicate, "1.1.2.4", "constructor call changed");
        ClassNode disconnected = target("byepregen$captureWrappedFinalDensity", "mapAll");
        disconnected.methods.get(0).name = "unrelatedMethod";
        assertUnchanged(disconnected, "1.1.2.4", "constructor call changed");
    }

    private static void assertUnchanged(ClassNode target, String version, String reason) {
        AbstractInsnNode[] before = target.methods.get(1).instructions.toArray();
        assertTrue(FinalDensityTransformer.attach(target, version).contains(reason));
        assertArrayEquals(before, target.methods.get(1).instructions.toArray());
    }

    private static ClassNode target(String handlerName, String mappingName) {
        ClassNode node = new ClassNode();
        node.version = V17;
        node.name = "net/minecraft/world/level/levelgen/NoiseChunk";
        node.superName = "java/lang/Object";
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new InsnNode(ACONST_NULL));
        constructor.instructions.add(new InsnNode(ACONST_NULL));
        constructor.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, handlerName, HANDLER, false));
        constructor.instructions.add(new InsnNode(POP));
        constructor.instructions.add(new InsnNode(RETURN));
        constructor.maxLocals = 1;
        constructor.maxStack = 3;
        node.methods.add(constructor);
        MethodNode handler = new MethodNode(ACC_PRIVATE, handlerName, HANDLER, null, null);
        AnnotationNode origin = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        origin.values = new java.util.ArrayList<>(List.of("mixin", "com.moepus.byepregen.mixin.dfc.DensityNoiseChunkMixin"));
        handler.visibleAnnotations = List.of(origin);
        handler.instructions.add(new VarInsnNode(ALOAD, 1));
        handler.instructions.add(new VarInsnNode(ALOAD, 2));
        handler.instructions.add(new MethodInsnNode(INVOKEINTERFACE, DENSITY, mappingName, MAP, true));
        handler.instructions.add(new VarInsnNode(ASTORE, 3));
        handler.instructions.add(new VarInsnNode(ALOAD, 3));
        handler.instructions.add(new InsnNode(ARETURN));
        handler.maxLocals = 4;
        handler.maxStack = 2;
        node.methods.add(handler);
        return node;
    }
}
