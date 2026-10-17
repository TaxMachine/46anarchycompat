package dev.taxmachine.anarchycompat.coremod;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.launchwrapper.IClassTransformer;
import java.util.ArrayList;
import java.util.List;
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
 *   <li><b>FML Loader handler strip (P0-4 complement, the deterministic
 *   fix).</b> The Mixin class above bypasses {@code IClassTransformer}
 *   entirely (defined via {@code MixinTransformer.defineClass}), so the
 *   branch above never fires — exactly what the boot log proved (guard
 *   logs, but no {@code Neutralized ... MixinLoader} line). The injected
 *   synthetic handler ({@code handler$zze000$beforeConstructingMods} in
 *   the crash report) lives on {@code cpw.mods.fml.common.Loader}, which
 *   IS visible here. Our transformer runs after Mixin's {@code Proxy} in
 *   the chain, so by the time {@code Loader} is defined the handler is
 *   already injected and its body is replaced with an empty return. Any
 *   {@code Loader} method containing {@code beforeConstructingMods} is
 *   PalaMod's (vanilla has none), so the match is name-based to survive
 *   handler-id rotation.</li>
 * </ol>
 */
public class ShadedMixinGuardTransformer implements IClassTransformer {

    /** Diagnostic: log the first few transform() calls to confirm the chain is live. */
    private static final java.util.concurrent.atomic.AtomicInteger CALLS =
            new java.util.concurrent.atomic.AtomicInteger();

    // Boot log proved transform() receives DOT-form names in this runtime
    // ("name=cpw.mods.fml.common.Loader transformed=cpw.mods.fml.common.Loader"),
    // so match dot-form constants against BOTH name and transformedName.
    private static final String OLD_SERVICE =
            "org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper";

    private static final String PALAMOD_LOADING_PLUGIN =
            "fr.paladium.palamod.asm.LoadingPlugin";

    private static final String PALAMOD_MIXIN_LOADER =
            "fr.paladium.palamod.mixins.forge.MixinLoader";

    private static final String FML_LOADER =
            "cpw.mods.fml.common.Loader";

    /** True if either the raw or transformed name equals the target. */
    private static boolean matches(String name, String transformedName, String target) {
        return target.equals(name) || target.equals(transformedName);
    }

    private static final String CALLBACK_INFO_DESC =
            "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int n = CALLS.incrementAndGet();
        // System.out, NOT FMLLog: transform() runs while FMLLog itself is
        // being defined, so FMLLog.info here is a circular NoClassDefFoundError.
        if (n <= 30) {
            System.out.println("[AnarchyCompat] transform() call #" + n
                    + ": name=" + name + " transformed=" + transformedName);
        }
        if (name != null && name.contains("Loader") && n <= 200) {
            System.out.println("[AnarchyCompat] transform() saw Loader-ish class: name="
                    + name + " transformed=" + transformedName);
        }
        if (matches(name, transformedName, OLD_SERVICE)) {
            System.out.println("[AnarchyCompat] Legacy shaded MixinServiceLaunchWrapper"
                    + " is being defined (loader=" + name + "). On Java 9+ its URLClassLoader"
                    + " cast will throw ClassCastException — UniMixins must win the tweaker"
                    + " race. See CompatCorePlugin log above for the offending jars.");
            return bytes;
        }
        if (matches(name, transformedName, PALAMOD_LOADING_PLUGIN)) {
            try {
                byte[] rewritten = replaceMethodBody(bytes,
                        "getModContainerClass", "()Ljava/lang/String;", true);
                System.out.println("[AnarchyCompat] Neutralized " + PALAMOD_LOADING_PLUGIN
                        + "#getModContainerClass (redundant MixinBootstrap.init, harmful under RFB).");
                return rewritten;
            } catch (Exception e) {
                System.out.println("[AnarchyCompat] Failed to neutralize " + PALAMOD_LOADING_PLUGIN
                        + ": " + e + " — continuing unpatched.");
                return bytes;
            }
        }
        if (matches(name, transformedName, PALAMOD_MIXIN_LOADER)) {
            System.out.println("[AnarchyCompat] Saw " + PALAMOD_MIXIN_LOADER
                    + " being defined (transformer chain).");
            try {
                byte[] rewritten = replaceMethodBody(bytes,
                        "beforeConstructingMods",
                        CALLBACK_INFO_DESC,
                        false);
                System.out.println("[AnarchyCompat] Neutralized " + PALAMOD_MIXIN_LOADER
                        + "#beforeConstructingMods (reflective MixinTransformer grab crashes"
                        + " under RFB; palamod-after mixins skipped on experimental).");
                return rewritten;
            } catch (Exception e) {
                System.out.println("[AnarchyCompat] Failed to neutralize " + PALAMOD_MIXIN_LOADER
                        + ": " + e + " — continuing unpatched.");
                return bytes;
            }
        }
        if (matches(name, transformedName, FML_LOADER)) {
            try {
                List<String> stripped = new ArrayList<String>();
                byte[] rewritten = stripLoaderHandlers(bytes, stripped);
                System.out.println("[AnarchyCompat] Saw " + FML_LOADER + " being defined;"
                        + " beforeConstructingMods handler present: "
                        + (stripped.isEmpty() ? "no" : "yes (" + stripped + ")"));
                if (!stripped.isEmpty()) {
                    System.out.println("[AnarchyCompat] Neutralized " + FML_LOADER + "#" + stripped
                            + " (PalaMod reflective MixinTransformer grab crashes under RFB;"
                            + " palamod-after is empty, safe to skip).");
                    return rewritten;
                }
                return bytes;
            } catch (Exception e) {
                System.out.println("[AnarchyCompat] Failed to inspect " + FML_LOADER + ": " + e
                        + " — continuing unpatched.");
                return bytes;
            }
        }
        return bytes;
    }

    /**
     * Replaces every {@code Loader} synthetic handler PalaMod's
     * {@code MixinLoader} injected (any {@code (CallbackInfo)V} method
     * whose name contains {@code beforeConstructingMods} — vanilla has
     * none) with an empty return. Matched names are appended to
     * {@code stripped} for the log line; callers return the original
     * bytes untouched when it stays empty.
     */
    static byte[] stripLoaderHandlers(byte[] bytes, final List<String> stripped) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String mname, String mdesc,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mname, mdesc, signature, exceptions);
                if (CALLBACK_INFO_DESC.equals(mdesc) && mname.contains("beforeConstructingMods")) {
                    stripped.add(mname);
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitInsn(Opcodes.RETURN);
                        }

                        @Override
                        public void visitEnd() {
                            mv.visitEnd();
                        }
                        // Same swallow-the-body contract as
                        // replaceMethodBody above: annotations/attrs still
                        // forward, visitMaxs still reaches the writer.

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
