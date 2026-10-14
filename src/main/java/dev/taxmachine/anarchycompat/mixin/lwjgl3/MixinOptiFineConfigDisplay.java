package dev.taxmachine.anarchycompat.mixin.lwjgl3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P0-2 stub — OptiFine Display-lifecycle redirect (experimental runtime only,
 * config {@code anarchycompat-lwjgl3.json} with {@code required: false}).
 *
 * <p>OptiFine's {@code Config.checkDisplaySettings()} drives the LWJGL2
 * lifecycle directly ({@code Display.destroy()} → {@code setDisplayMode()}
 * → {@code Display.create(PixelFormat…)}), and
 * {@code Config.checkDisplayMode()} calls {@code setDisplayMode()} /
 * {@code setFullscreen(true)} / {@code Display.update()}. Under lwjgl3ify
 * these must resolve to the {@code org.lwjglx} shim surface (GLFW window
 * hints for {@code PixelFormat.withDepthBits/withSamples}).
 *
 * <p>This stub only intercepts and logs so the boot matrix can prove
 * whether lwjgl3ify already covers these call sites. Promote to a real
 * redirect ({@code @Redirect} on the {@code Display.create} overloads,
 * mapping {@code PixelFormat} to window hints) the moment the matrix goes
 * red. {@code targets = "Config"} (default package) plus
 * {@code remap = false} keep this compilable without OptiFine on the
 * compile classpath; {@code mods/} only needs it for a full reference
 * check.
 */
@Mixin(targets = "Config", remap = false)
public abstract class MixinOptiFineConfigDisplay {

    @Inject(method = "checkDisplaySettings", at = @At("HEAD"), remap = false)
    private static void anarchycompat$onCheckDisplaySettings(CallbackInfo ci) {
        System.out.println("[AnarchyCompat] OptiFine Config.checkDisplaySettings"
                + " intercepted — Display destroy/create(PixelFormat) path (lwjgl3ify)");
    }

    @Inject(method = "checkDisplayMode", at = @At("HEAD"), remap = false)
    private static void anarchycompat$onCheckDisplayMode(CallbackInfo ci) {
        System.out.println("[AnarchyCompat] OptiFine Config.checkDisplayMode"
                + " intercepted — setDisplayMode/setFullscreen/update path (lwjgl3ify)");
    }
}
