package cn.tohsaka.factory.terraingraph;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

final class FinalDensityTransformer {
    private static final String DENSITY = "net/minecraft/world/level/levelgen/DensityFunction";
    private static final String FUNCTIONS = "net/minecraft/world/level/levelgen/DensityFunctions";
    private static final String VISITOR = "L" + DENSITY + "$Visitor;";
    private static final String MAP_DESCRIPTOR = "(" + VISITOR + ")L" + DENSITY + ";";
    private static final String HANDLER_DESCRIPTOR = "(L" + DENSITY + ";" + VISITOR + ")L" + DENSITY + ";";
    private static final String DENSITY_DESCRIPTOR = "(L" + DENSITY + ";)L" + DENSITY + ";";
    private static final String ORIGIN = "com.moepus.byepregen.mixin.dfc.DensityNoiseChunkMixin";
    private static final String HANDLER = "byepregen$captureWrappedFinalDensity";
    private static final String HOOK = "cn/tohsaka/factory/terraingraph/FinalDensityHooks";

    private FinalDensityTransformer() {}

    static String attach(ClassNode node, String version) {
        List<MethodInsnNode> directCalls = new ArrayList<>();
        boolean redirectedCall = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (isMap(call)) directCalls.add(call);
                if (call.owner.equals(node.name) && call.desc.equals(HANDLER_DESCRIPTOR)) redirectedCall = true;
            }
        }
        if (!directCalls.isEmpty()) {
            if (directCalls.size() != 1 || redirectedCall) return "skipped: ambiguous final-density constructor calls";
            return attachVanilla(node, directCalls.get(0));
        }
        if (version == null) return "skipped: no supported final-density mapping";
        if (!"1.1.2.4".equals(version)) return "skipped: unsupported ByePregen " + version;

        List<MethodNode> handlers = node.methods.stream()
                .filter(method -> method.name.equals(HANDLER) || method.name.endsWith("$" + HANDLER))
                .filter(method -> HANDLER_DESCRIPTOR.equals(method.desc))
                .filter(method -> (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == Opcodes.ACC_PRIVATE)
                .filter(FinalDensityTransformer::hasExpectedOrigin).toList();
        if (handlers.size() != 1) return "skipped: final-density handler not uniquely identified";
        MethodNode handler = handlers.get(0);

        List<MethodInsnNode> callers = new ArrayList<>();
        MethodNode constructor = null;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)
                        && call.name.equals(handler.name) && call.desc.equals(handler.desc)) {
                    callers.add(call);
                    if (method.name.equals("<init>")) constructor = method;
                }
            }
        }
        if (callers.size() != 1 || constructor == null) return "skipped: final-density constructor call changed";

        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction.getOpcode() >= 0) instructions.add(instruction);
        }
        if (instructions.stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call
                && isHook(call))) return "active: ByePregen " + version;

        // Only the verified redirect's initial source.mapAll(visitor) is eligible.
        // This retains ByePregen's verification root and all subsequent binding hooks.
        if (instructions.size() < 4 || !loads(instructions.get(0), 1) || !loads(instructions.get(1), 2)
                || !(instructions.get(2) instanceof MethodInsnNode map) || !isMap(map)
                || !(instructions.get(3) instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE
                || instructions.stream().filter(instruction -> instruction instanceof MethodInsnNode call
                        && isMap(call)).count() != 1) {
            return "skipped: final-density handler body changed";
        }

        // The visitor is on top of the stack. The hook preserves its type and stack size.
        handler.instructions.insertBefore(map, hook());
        return "active: ByePregen " + version;
    }

    private static String attachVanilla(ClassNode node, MethodInsnNode map) {
        AbstractInsnNode visitor = previous(map);
        boolean attached = visitor instanceof MethodInsnNode call && isHook(call);
        if (attached) visitor = previous(visitor);
        AbstractInsnNode capture = previous(visitor);
        AbstractInsnNode source = previous(capture);
        if (!(visitor instanceof InvokeDynamicInsnNode lambda) || !isWrappingVisitor(node, lambda)
                || !loads(capture, 0) || !(source instanceof MethodInsnNode factory)
                || factory.getOpcode() != Opcodes.INVOKESTATIC || !factory.owner.equals(FUNCTIONS)
                || !(factory.name.equals("cacheAllInCell") || factory.name.equals("m_208387_"))
                || !factory.desc.equals(DENSITY_DESCRIPTOR)
                || !(next(map) instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) {
            return "skipped: vanilla final-density entry changed";
        }
        if (!attached) {
            for (MethodNode method : node.methods) {
                if (method.instructions.contains(map)) {
                    method.instructions.insertBefore(map, hook());
                    break;
                }
            }
        }
        return "active: vanilla";
    }

    private static boolean isWrappingVisitor(ClassNode node, InvokeDynamicInsnNode lambda) {
        if (!lambda.desc.equals("(L" + node.name + ";)" + VISITOR)
                || !lambda.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                || !(lambda.bsm.getName().equals("metafactory") || lambda.bsm.getName().equals("altMetafactory"))) {
            return false;
        }
        for (Object argument : lambda.bsmArgs) {
            if (argument instanceof Handle implementation && implementation.getOwner().equals(node.name)
                    && (implementation.getName().equals("wrap") || implementation.getName().equals("m_209213_"))
                    && implementation.getDesc().equals(DENSITY_DESCRIPTOR)
                    && (implementation.getTag() == Opcodes.H_INVOKEVIRTUAL
                        || implementation.getTag() == Opcodes.H_INVOKESPECIAL)) return true;
        }
        return false;
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static MethodInsnNode hook() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "wrap", "(" + VISITOR + ")" + VISITOR, false);
    }

    private static boolean isHook(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(HOOK) && call.name.equals("wrap")
                && call.desc.equals("(" + VISITOR + ")" + VISITOR);
    }

    private static boolean loads(AbstractInsnNode instruction, int variable) {
        return instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == variable;
    }

    private static boolean isMap(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEINTERFACE && call.itf && call.owner.equals(DENSITY)
                && (call.name.equals("mapAll") || call.name.equals("m_207456_")) && call.desc.equals(MAP_DESCRIPTOR);
    }

    private static boolean hasExpectedOrigin(MethodNode method) {
        return hasExpectedOrigin(method.visibleAnnotations) || hasExpectedOrigin(method.invisibleAnnotations);
    }

    private static boolean hasExpectedOrigin(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                    || annotation.values == null) continue;
            for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
                if ("mixin".equals(annotation.values.get(index)) && ORIGIN.equals(annotation.values.get(index + 1))) {
                    return true;
                }
            }
        }
        return false;
    }
}
