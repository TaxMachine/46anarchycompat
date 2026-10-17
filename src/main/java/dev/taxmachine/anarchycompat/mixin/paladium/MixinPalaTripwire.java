package dev.taxmachine.anarchycompat.mixin.paladium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralises PalaMod's loader tripwires (experimental runtime only,
 * config {@code anarchycompat-paladium.json} with {@code required: false}).
 *
 * <p>{@code PProxyClient.initialize} deliberately crashes exotic runtimes:
 * when the game dir lacks {@code "paladium"} it calls
 * {@code Unsafe.getByte(0L)} (two sites: the data-dir check and the
 * {@code WINE}-env check), and {@code PlayerUtil.fix} does the same via
 * {@code scala.concurrent.util.Unsafe}. Under lwjgl3ify/GraalVM 21 the dir
 * legitimately differs, so both are no-ops here. Vanilla init
 * ({@code Display.setTitle}, keybinds, handlers) is untouched.
 *
 * <p>{@code targets} strings + {@code remap = false} keep this compilable
 * without PalaMod on the classpath; {@code require = 0} degrades to a
 * silent skip on version drift instead of a boot crash.
 */
@Mixin(targets = {
        "fr.paladium.palamod.client.PProxyClient",
        "fr.paladium.palamod.util.PlayerUtil"
}, remap = false)
public abstract class MixinPalaTripwire {

    private static boolean logged;

    @Redirect(method = "initialize",
            at = @At(value = "INVOKE", target = "Lsun/misc/Unsafe;getByte(J)B"),
            require = 0, remap = false)
    private static byte anarchycompat$noTripwire(sun.misc.Unsafe unsafe, long address) {
        logOnce("PProxyClient.initialize tripwire skipped (dir/env check, not a crash)");
        return 0;
    }

    @Inject(method = "fix", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void anarchycompat$cancelFix(CallbackInfo ci) {
        logOnce("PlayerUtil.fix tripwire cancelled");
        ci.cancel();
    }

    private static void logOnce(String msg) {
        if (!logged) {
            logged = true;
            System.out.println("[AnarchyCompat] " + msg);
        }
    }
}
