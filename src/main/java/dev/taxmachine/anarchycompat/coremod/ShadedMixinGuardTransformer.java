package dev.taxmachine.anarchycompat.coremod;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Guard + targeted neutralisation transformer (runs first via
 * {@code TweakOrder: 0} + first-sorted coremod position).
 *
 * <ol>
 *   <li><b>Detection only</b> for the legacy shaded Mixin service
 *   ({@code PalaMixins}/{@code PalaJobs} ship Mixin 0.6-era copies): logs a
 *   SEVERE naming it and passes bytes through.</li>
 *   <li><b>PalaMod LoadingPlugin neutralisation (P0-3, best-effort).</b>
 *   {@code fr.paladium.palamod.asm.LoadingPlugin#getModContainerClass}
 *   calls {@code MixinBootstrap.init()} + three
 *   {@code Mixins.addConfiguration(...)}. Stock FML + UniMixins already
 *   load every {@code mixins.*.json} from the jar manifest, so the
 *   explicit init is redundant — but the plugin class usually defines
 *   before this transformer installs, so this is a safety net, not the
 *   primary fix (see below).</li>
 *   <li><b>PalaMod MixinLoader neutralisation (P0-4, the real fix).</b>
 *   {@code fr.paladium.palamod.mixins.forge.MixinLoader#beforeConstructingMods}
 *   grabs {@code MixinTransformer} reflectively out of Mixin's
 *   {@code Proxy} and calls {@code selectConfigs/prepareConfigs} to force
 *   {@code mixins.palamod-after.json} late. Under RFB the transformer was
 *   defined system-side while the handler runs launch-side, so every call
 *   dies with {@code IllegalAccessError} in {@code Loader.loadMods}.
 *   The mixin class loads late (Mixin prep, after this transformer
 *   installs), so its handler body is replaced with an empty return. The
 *   palamod-after mixins don't apply on the experimental path (menu
 *   falls back); the correct upstream fix is GTNHMixins'
 *   {@code ILateMixinLoader} instead of the reflection hack.</li>
 * </ol>
 */
public class ShadedMixinGuardTransformer implements IClassTransformer {

    private static final String OLD_SERVICE =
            "org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper";

    private static final String PALAMOD_LOADING_PLUGIN =
            "fr.paladium.palamod.asm.LoadingPlugin";

    private static final String PALAMOD_MIXIN_LOADER =
            "fr.paladium.palamod.mixins.forge.MixinLoader";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (OLD_SERVICE.equals(transformedName)) {
            FMLLog.severe("[AnarchyCompat] Legacy shaded MixinServiceLaunchWrapper"
                    + " is being defined (loader=%s). On Java 9+ its URLClassLoader cast"
                    + " will throw ClassCastException — UniMixins must win the tweaker"
                    + " race. See CompatCorePlugin log above for the offending jars.", name);
            return bytes;
        }
        if (PALAMOD_LOADING_PLUGIN.equals(transformedName)) {
            try {
                byte[] rewritten = replaceMethodBody(bytes,
                        "getModContainerClass", "()Ljava/lang/String;", true);
                FMLLog.info("[AnarchyCompat] Neutralized %s#getModContainerClass"
                        + " (redundant MixinBootstrap.init, harmful under RFB).",
                        PALAMOD_LOADING_PLUGIN);
                return rewritten;
            } catch (Exception e) {
                FMLLog.warning("[AnarchyCompat] Failed to neutralize %s: %s"
                        + " — continuing unpatched.", PALAMOD_LOADING_PLUGIN, e);
                return bytes;
            }
        }
        if (PALAMOD_MIXIN_LOADER.equals(transformedName)) {
            try {
                byte[] rewritten = replaceMethodBody(bytes,
                        "beforeConstructingMods",
                        "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                        false);
                FMLLog.warning("[AnarchyCompat] Neutralized %s#beforeConstructingMods"
                        + " (reflective MixinTransformer grab crashes under RFB;"
                        + " palamod-after mixins skipped on experimental).",
                        PALAMOD_MIXIN_LOADER);
                return rewritten;
            } catch (Exception e) {
                FMLLog.warning("[AnarchyCompat] Failed to neutralize %s: %s"
                        + " — continuing unpatched.", PALAMOD_MIXIN_LOADER, e);
                return bytes;
            }
        }
        return bytes;
    }

    /**
     * Replaces one method body: {@code return null} for reference types,
     * empty {@code return} for void. Annotations are preserved; all code
     * events of the original body are dropped.
     */
    static byte[] replaceMethodBody(byte[] bytes, final String method,
                                    final String desc, final boolean returnsRef) {
        return replaceMethodBody(bytes, method, desc,
                returnsRef ? new int[] {Opcodes.ACONST_NULL, Opcodes.ARETURN}
                           : new int[] {Opcodes.RETURN});
    }

    private static byte[] replaceMethodBody(byte[] bytes, final String method,
                                            final String desc, final int[] prefix) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String mname, String mdesc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mname, mdesc, signature, exceptions);
                if (method.equals(mname) && desc.equals(mdesc)) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            for (int opcode : prefix) {
                                mv.visitInsn(opcode);
                            }
                        }

                        @Override
                        public void visitEnd() {
                            mv.visitEnd();
                        }
                        // Every other code event of the original body is
                        // swallowed (dead init() calls fully removed, not
                        // just skipped over). Annotations/attrs still forward
                        // via the methods below that we do NOT override;
                        // visitMaxs MUST reach the writer (COMPUTE_MAXS).

                        @Override
                        public void visitInsn(int opcode) {
                        }

                        @Override
                        public void visitIntInsn(int opcode, int operand) {
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner,
                                                   String name, String desc) {
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner,
                                                    String name, String desc,
                                                    boolean itf) {
                        }

                        @Override
                        public void visitJumpInsn(int opcode, Label label) {
                        }

                        @Override
                        public void visitLabel(Label label) {
                        }

                        @Override
                        public void visitLdcInsn(Object cst) {
                        }

                        @Override
                        public void visitIincInsn(int var, int increment) {
                        }

                        @Override
                        public void visitTableSwitchInsn(int min, int max,
                                                         Label dflt, Label... labels) {
                        }

                        @Override
                        public void visitLookupSwitchInsn(Label dflt, int[] keys,
                                                          Label[] labels) {
                        }

                        @Override
                        public void visitMultiANewArrayInsn(String desc, int dims) {
                        }

                        @Override
                        public void visitTryCatchBlock(Label start, Label end,
                                                       Label handler, String type) {
                        }

                        @Override
                        public void visitLocalVariable(String name, String desc,
                                                       String signature, Label start,
                                                       Label end, int index) {
                        }

                        @Override
                        public void visitLineNumber(int line, Label start) {
                        }

                        @Override
                        public void visitFrame(int type, int nLocal, Object[] local,
                                               int nStack, Object[] stack) {
                        }
                    };
                }
                return mv;
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
