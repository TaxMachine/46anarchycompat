package dev.taxmachine.anarchycompat.mixin.zephyr;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Log-only probe for ZephyrUI input queues (experimental runtime only,
 * config {@code anarchycompat-zephyr.json} with {@code required: false}).
 *
 * <p>Raw LWJGL queues are consumed in {@code OverlayBridge.handleMouseInput}
 * / {@code handleKeyboardInput} ({@code Mouse.getEventButton/DWheel},
 * {@code Keyboard.getEventKey/Character}); {@code UI.onKeyPressed} /
 * {@code onMouseScroll} (incl. the dev-zoom {@code isKeyDown(56/42)} path)
 * and {@code Node.onUpdate} observe the post-dispatch side, including the
 * {@code Mouse.isGrabbed} drag check and ctrl-scroll speedup. All handlers
 * are HEAD-only, never cancellable, throttled to one line per tag change or
 * 5 s. Promote to {@code @Redirect} toward lwjgl3ify queues if the matrix
 * shows stuck keys or dead scroll under GLFW.
 */
@Mixin(targets = {
        "fr.paladium.zephyrui.internal.mod.bridge.overlay.client.OverlayBridge",
        "fr.paladium.zephyrui.lib.ui.core.UI",
        "fr.paladium.zephyrui.lib.ui.node.Node"
}, remap = false)
public abstract class MixinZephyrInputProbe {

    private static long lastLog;
    private static String lastTag = "";

    @Inject(method = "handleMouseInput()Z", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$probeMouse(CallbackInfo ci) {
        probe("mouse", "button=" + Mouse.getEventButton()
                + " state=" + Mouse.getEventButtonState()
                + " dwheel=" + Mouse.getEventDWheel()
                + " pos=" + Mouse.getX() + "," + Mouse.getY());
    }

    @Inject(method = "handleKeyboardInput()Z", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$probeKeys(CallbackInfo ci) {
        probe("keys", "key=" + Keyboard.getEventKey()
                + " char=" + (int) Keyboard.getEventCharacter()
                + " state=" + Keyboard.getEventKeyState());
    }

    @Inject(method = "onKeyPressed(CI)Z", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$probeKeyTyped(char c, int keyCode, CallbackInfo ci) {
        probe("ui-key", "c=" + (int) c + " key=" + keyCode);
    }

    @Inject(method = "onMouseScroll(I)Z", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$probeScroll(int value, CallbackInfo ci) {
        probe("ui-scroll", "value=" + value);
    }

    @Inject(method = "onUpdate()V", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$probeTick(CallbackInfo ci) {
        probe("tick", "ui/node update (grabbed=" + Mouse.isCreated() + ")");
    }

    private static void probe(String tag, String msg) {
        long now = System.currentTimeMillis();
        if (!tag.equals(lastTag) || now - lastLog > 5000L) {
            lastTag = tag;
            lastLog = now;
            System.out.println("[AnarchyCompat] Zephyr input " + tag + ": " + msg);
        }
    }
}
