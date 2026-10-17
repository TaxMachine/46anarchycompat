package dev.taxmachine.anarchycompat.rfb;

import java.util.jar.Manifest;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;

/**
 * Two compat repairs for PalaMod's {@code mixins.palaforge.json} handler on
 * {@code cpw.mods.fml.common.Loader} under RFB + Mixin 0.8.7
 * (experimental runtime only). Both are behaviour-neutral.
 *
 * <ol>
 *   <li><b>Widen {@code MixinTransformer} to public.</b> Mixin 0.8.x merges
 *   PalaMod's mixin method body into a synthetic handler on {@code Loader},
 *   which contains {@code (MixinTransformer) Proxy.transformer} — a
 *   checkcast against a <em>package-private</em> class. Under RFB the Mixin
 *   classes live in the system loader while {@code Loader} lives in the
 *   launch loader, so the check fails with {@code IllegalAccessError}.
 *   The class and the instance are both the system-side
 *   {@code MixinTransformer}, so widening access makes the checkcast pass.</li>
 *
 *   <li><b>Provide the legacy {@code selectConfigs}/{@code prepareConfigs}
 *   API on {@code MixinTransformer}.</b> The same handler reflectively calls
 *   {@code MixinTransformer.class.getDeclaredMethod("selectConfigs",
 *   MixinEnvironment.class)} (and {@code prepareConfigs}) to force-select
 *   {@code mixins.palamod-after.json} late. Mixin 0.7/early-0.8 exposed those
 *   on {@code MixinTransformer}; 0.8.7 moved the logic to
 *   {@code MixinProcessor} (private). The correct 0.8.x call sequence is
 *   exactly what GTNHMixins' {@code Reflection.invokeSelectConfigs/
 *   invokePrepareConfigs} does: {@code processor.selectConfigs(env)} and
 *   {@code processor.prepareConfigs(env, extensions)}. This transformer
 *   injects public one-arg bridge methods with those bodies (and widens the
 *   two {@code MixinProcessor} methods so the bridges can link), which makes
 *   the handler's reflective lookup succeed and do the real work.</li>
 * </ol>
 */
public class PublicMixinTransformer implements RfbClassTransformer {

    public static final String MIXIN_TRANSFORMER_DOT =
            "org.spongepowered.asm.mixin.transformer.MixinTransformer";
    public static final String MIXIN_TRANSFORMER_SLASH =
            "org/spongepowered/asm/mixin/transformer/MixinTransformer";
    public static final String MIXIN_PROCESSOR_DOT =
            "org.spongepowered.asm.mixin.transformer.MixinProcessor";
    public static final String MIXIN_PROCESSOR_SLASH =
            "org/spongepowered/asm/mixin/transformer/MixinProcessor";

    private static final String MIXIN_ENV_SLASH = "org/spongepowered/asm/mixin/MixinEnvironment";
    private static final String PROCESSOR_DESC =
            "Lorg/spongepowered/asm/mixin/transformer/MixinProcessor;";
    private static final String EXTENSIONS_DESC =
            "Lorg/spongepowered/asm/mixin/transformer/ext/Extensions;";

    /** Signature the handler looks up (parameters only matter for the lookup). */
    private static final String ENV_V_DESC = "(L" + MIXIN_ENV_SLASH + ";)V";
    /** Real {@code MixinProcessor.prepareConfigs} signature. */
    private static final String PROCESSOR_PREPARE_DESC = "(L" + MIXIN_ENV_SLASH + ";"
            + EXTENSIONS_DESC + ")I";
    /** Real {@code MixinProcessor.selectConfigs} signature. */
    private static final String PROCESSOR_SELECT_DESC = "(L" + MIXIN_ENV_SLASH + ";)V";

    /** {@code ACC_PUBLIC} — inlined so access rules are explicit. */
    private static final int ACC_PUBLIC = 0x0001;

    @Override
    public String id() {
        return "anarchycompat-mixin-visibility";
    }

    @Override
    public boolean shouldTransformClass(ExtensibleClassLoader classLoader,
            RfbClassTransformer.Context context, Manifest manifest, String className,
            ClassNodeHandle classNode) {
        if (!classNode.isPresent()) {
            return false;
        }
        return MIXIN_TRANSFORMER_DOT.equals(className)
                || MIXIN_TRANSFORMER_SLASH.equals(className)
                || MIXIN_PROCESSOR_DOT.equals(className)
                || MIXIN_PROCESSOR_SLASH.equals(className);
    }

    @Override
    public boolean transformClassIfNeeded(ExtensibleClassLoader classLoader,
            RfbClassTransformer.Context context, Manifest manifest, String name,
            ClassNodeHandle classNodeHandle) {
        final ClassNode node = classNodeHandle.getNode();
        if (node == null) {
            return false;
        }
        if (MIXIN_TRANSFORMER_DOT.equals(name) || MIXIN_TRANSFORMER_SLASH.equals(name)) {
            return transformMixinTransformer(node);
        }
        if (MIXIN_PROCESSOR_DOT.equals(name) || MIXIN_PROCESSOR_SLASH.equals(name)) {
            return widenProcessorApi(node);
        }
        return false;
    }

    /** (1) public class + (2) legacy bridge methods. */
    private static boolean transformMixinTransformer(ClassNode node) {
        boolean changed = false;

        if ((node.access & ACC_PUBLIC) == 0) {
            node.access |= ACC_PUBLIC;
            changed = true;
            System.out.println("[AnarchyCompat] Widened " + MIXIN_TRANSFORMER_DOT
                    + " to public (RFB system-loader access check for PalaMod's"
                    + " Loader handler checkcast).");
        }

        if (!hasMethod(node, "selectConfigs", ENV_V_DESC)) {
            node.methods.add(buildSelectConfigs());
            changed = true;
            System.out.println("[AnarchyCompat] Injected " + MIXIN_TRANSFORMER_DOT
                    + "#selectConfigs(MixinEnvironment) -> MixinProcessor"
                    + " (PalaMod's legacy late-config call).");
        }
        if (!hasMethod(node, "prepareConfigs", ENV_V_DESC)) {
            node.methods.add(buildPrepareConfigs());
            changed = true;
            System.out.println("[AnarchyCompat] Injected " + MIXIN_TRANSFORMER_DOT
                    + "#prepareConfigs(MixinEnvironment) -> MixinProcessor");
        }
        return changed;
    }

    /** Widens {@code MixinProcessor.selectConfigs/prepareConfigs} so the bridges link. */
    private static boolean widenProcessorApi(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("selectConfigs".equals(method.name) && PROCESSOR_SELECT_DESC.equals(method.desc)) {
                if ((method.access & ACC_PUBLIC) == 0) {
                    method.access = widenToPublic(method.access);
                    changed = true;
                }
            } else if ("prepareConfigs".equals(method.name)
                    && PROCESSOR_PREPARE_DESC.equals(method.desc)) {
                if ((method.access & ACC_PUBLIC) == 0) {
                    method.access = widenToPublic(method.access);
                    changed = true;
                }
            }
        }
        if (changed) {
            System.out.println("[AnarchyCompat] Widened " + MIXIN_PROCESSOR_DOT
                    + " config-selection methods to public (bridge linkage).");
        }
        return changed;
    }

    private static int widenToPublic(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | ACC_PUBLIC;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code public void selectConfigs(MixinEnvironment env) { this.processor.selectConfigs(env); }}
     */
    private static MethodNode buildSelectConfigs() {
        final MethodNode method = new MethodNode(ACC_PUBLIC, "selectConfigs", ENV_V_DESC,
                null, null);
        final InsnList code = method.instructions;
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, MIXIN_TRANSFORMER_SLASH,
                "processor", PROCESSOR_DESC));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MIXIN_PROCESSOR_SLASH,
                "selectConfigs", PROCESSOR_SELECT_DESC, false));
        code.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        return method;
    }

    /**
     * {@code public void prepareConfigs(MixinEnvironment env) {
     *     this.processor.prepareConfigs(env, this.extensions); }}
     */
    private static MethodNode buildPrepareConfigs() {
        final MethodNode method = new MethodNode(ACC_PUBLIC, "prepareConfigs", ENV_V_DESC,
                null, null);
        final InsnList code = method.instructions;
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, MIXIN_TRANSFORMER_SLASH,
                "processor", PROCESSOR_DESC));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, MIXIN_TRANSFORMER_SLASH,
                "extensions", EXTENSIONS_DESC));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MIXIN_PROCESSOR_SLASH,
                "prepareConfigs", PROCESSOR_PREPARE_DESC, false));
        code.add(new InsnNode(Opcodes.POP));
        code.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 4;
        method.maxLocals = 2;
        return method;
    }
}
