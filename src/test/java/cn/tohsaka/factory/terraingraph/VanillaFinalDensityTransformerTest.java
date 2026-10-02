package cn.tohsaka.factory.terraingraph;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class VanillaFinalDensityTransformerTest implements Opcodes {
    private static final String NOISE_CHUNK = "net/minecraft/world/level/levelgen/NoiseChunk";
    private static final String DENSITY = "net/minecraft/world/level/levelgen/DensityFunction";
    private static final String VISITOR = "L" + DENSITY + "$Visitor;";
    private static final String UNARY = "(L" + DENSITY + ";)L" + DENSITY + ";";
    private static final String MAP = "(" + VISITOR + ")L" + DENSITY + ";";
    private static final String HOOK = "cn/tohsaka/factory/terraingraph/FinalDensityHooks";

    @Test
    void vanillaEntryWorksWithoutByePregenAndPreservesBytecodeStack() throws Exception {
        ClassNode node = vanilla(false);
        MethodNode constructor = node.methods.get(0);
        AbstractInsnNode[] original = constructor.instructions.toArray();
        assertEquals("active: vanilla", FinalDensityTransformer.attach(node, null));
        assertEquals(original.length + 1, constructor.instructions.size());
        for (AbstractInsnNode instruction : original) assertTrue(constructor.instructions.contains(instruction));
        assertEquals(1, hookCount(constructor));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, constructor);
        assertEquals("active: vanilla", FinalDensityTransformer.attach(node, null));
        assertEquals(1, hookCount(constructor));
    }

    @Test
    void srgNamesAndDebugLabelsAreRecognized() throws Exception {
        ClassNode node = vanilla(true);
        MethodNode constructor = node.methods.get(0);
        constructor.instructions.insertBefore(constructor.instructions.get(6), new LabelNode());
        assertEquals("active: vanilla", FinalDensityTransformer.attach(node, null));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, constructor);
    }

    @Test
    void unchangedVanillaEntryIsUsableWhenByePregenDfcIsOff() {
        assertEquals("active: vanilla", FinalDensityTransformer.attach(vanilla(false), "1.1.2.4"));
        assertEquals("active: vanilla", FinalDensityTransformer.attach(vanilla(false), "1.1.2.5"));
    }

    @Test
    void customVisitorOrSourceFactoryKeepsOriginalConversion() {
        ClassNode visitor = vanilla(false);
        InvokeDynamicInsnNode lambda = (InvokeDynamicInsnNode) visitor.methods.get(0).instructions.get(5);
        lambda.bsmArgs[1] = new Handle(H_INVOKEVIRTUAL, NOISE_CHUNK, "anotherModsVisitor", UNARY, false);
        assertUnchanged(visitor, "entry changed");
        ClassNode source = vanilla(false);
        ((MethodInsnNode) source.methods.get(0).instructions.get(3)).name = "anotherFactory";
        assertUnchanged(source, "entry changed");
    }

    @Test
    void additionalVisitorInjectionKeepsOriginalConversion() {
        ClassNode node = vanilla(false);
        MethodNode constructor = node.methods.get(0);
        constructor.instructions.insertBefore(constructor.instructions.get(6),
                new MethodInsnNode(INVOKESTATIC, "another/Mod", "visitor", "(" + VISITOR + ")" + VISITOR, false));
        assertUnchanged(node, "entry changed");
    }

    @Test
    void unrelatedMappingOutsideConstructorIsNotTouched() {
        ClassNode node = vanilla(false);
        node.methods.get(0).name = "cachedClimateSampler";
        assertUnchanged(node, "no supported");
    }

    @Test
    void ambiguousConstructorMappingsAreNotPartiallyChanged() {
        ClassNode node = vanilla(false);
        node.methods.add(vanilla(true).methods.get(0));
        assertUnchanged(node, "ambiguous");
        assertEquals(0, hookCount(node.methods.get(1)));
    }

    @Test
    void coexistingNativeAndRedirectedCallsAreNotDoubleWrapped() throws Exception {
        ClassNode node = vanilla(false);
        MethodNode constructor = node.methods.get(0);
        InsnList redirected = new InsnList();
        redirected.add(new VarInsnNode(ALOAD, 0));
        redirected.add(new VarInsnNode(ALOAD, 1));
        redirected.add(new InsnNode(ACONST_NULL));
        redirected.add(new MethodInsnNode(INVOKESPECIAL, NOISE_CHUNK, "redirect$other",
                "(L" + DENSITY + ";" + VISITOR + ")L" + DENSITY + ";", false));
        redirected.add(new InsnNode(POP));
        constructor.instructions.insertBefore(constructor.instructions.get(8), redirected);
        constructor.maxStack = 3;
        new Analyzer<>(new BasicVerifier()).analyze(node.name, constructor);
        assertUnchanged(node, "ambiguous");
    }

    private static void assertUnchanged(ClassNode node, String reason) {
        AbstractInsnNode[] before = node.methods.get(0).instructions.toArray();
        assertTrue(FinalDensityTransformer.attach(node, null).contains(reason));
        assertArrayEquals(before, node.methods.get(0).instructions.toArray());
    }

    private static long hookCount(MethodNode method) {
        long count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(HOOK)) count++;
        }
        return count;
    }

    static ClassNode vanilla(boolean srg) {
        ClassNode node = new ClassNode();
        node.version = V17;
        node.name = NOISE_CHUNK;
        node.superName = "java/lang/Object";
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(ACONST_NULL));
        constructor.instructions.add(new MethodInsnNode(INVOKESTATIC, DENSITY + "s",
                srg ? "m_208387_" : "cacheAllInCell", UNARY, false));
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        Handle bootstrap = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                        + "Ljava/lang/invoke/CallSite;", false);
        constructor.instructions.add(new InvokeDynamicInsnNode(srg ? "m_214017_" : "apply",
                "(L" + NOISE_CHUNK + ";)" + VISITOR, bootstrap, Type.getMethodType(UNARY),
                new Handle(H_INVOKEVIRTUAL, NOISE_CHUNK, srg ? "m_209213_" : "wrap", UNARY, false), Type.getMethodType(UNARY)));
        constructor.instructions.add(new MethodInsnNode(INVOKEINTERFACE, DENSITY, srg ? "m_207456_" : "mapAll", MAP, true));
        constructor.instructions.add(new VarInsnNode(ASTORE, 1));
        constructor.instructions.add(new InsnNode(RETURN));
        constructor.maxLocals = 2;
        constructor.maxStack = 2;
        node.methods.add(constructor);
        return node;
    }
}
