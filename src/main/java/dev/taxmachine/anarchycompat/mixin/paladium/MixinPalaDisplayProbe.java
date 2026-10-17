package dev.taxmachine.anarchycompat.mixin.paladium;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Log-only probe for LWJGL2 {@code Display} lifecycle calls made by PalaMod
 * (experimental runtime only, {@code required: false}).
 *
 * <p>Covers {@code IMixinMinecraft.setNewIcon} ({@code setIcon}) and
 * {@code PProxyClient.initialize} ({@code setTitle}) without touching
 * either caller: injecting into {@code Display} itself observes every path
 * while leaving behaviour to lwjgl3ify. Never cancellable, throttled to the
 * first calls so the log stays readable. Promote to {@code @Redirect} only
 * if the matrix shows icon/title creation failing under GLFW.
 */
@Mixin(value = Display.class, remap = false)
public abstract class MixinPalaDisplayProbe {

    private static int titles;
    private static int icons;

    @Inject(method = "setTitle(Ljava/lang/String;)V", at = @At("HEAD"),
            require = 0, remap = false)
    private static void anarchycompat$onSetTitle(String title, CallbackInfo ci) {
        if (titles++ < 2) {
            System.out.println("[AnarchyCompat] Display.setTitle -> " + title + " (lwjgl3ify)");
        }
    }

    @Inject(method = "setIcon([Ljava/nio/ByteBuffer;)I", at = @At("HEAD"),
            require = 0, remap = false)
    private static void anarchycompat$onSetIcon(ByteBuffer[] icons, CallbackInfo ci) {
        if (MixinPalaDisplayProbe.icons++ == 0) {
            System.out.println("[AnarchyCompat] Display.setIcon[" + icons.length
                    + "] intercepted (lwjgl3ify)");
        }
    }
}
