package dev.taxmachine.anarchycompat.mixin.paladium;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Normalises PalaVoiceChat PTT mouse codes for lwjgl3ify (experimental
 * runtime only, {@code required: false}).
 *
 * <p>{@code MicThread.isPTTKeyDown} maps negative key codes to
 * {@code Mouse.isButtonDown(keyCode + 100)}. Under GLFW-backed input,
 * button indices outside {@code 0..7} or queries before the window exists
 * misbehave, so out-of-range codes are clamped to "not pressed" (logged
 * once) and every query is guarded. Key codes keep vanilla semantics.
 * PTT key identity is resolved reflectively so this compiles without
 * PalaVoiceChat on the classpath.
 */
@Mixin(targets = "fr.paladium.palavoicechat.client.voip.micro.MicThread", remap = false)
public abstract class MixinVoiceMicNormalize {

    private static Field pttField;
    private static Method getKeyCode;
    private static boolean clamped;

    /**
     * Replaces the raw {@code keyCode + 100} mapping with a guarded one.
     *
     * @author AnarchyCompat
     * @reason Negative mouse codes and pre-window queries misbehave under
     *         lwjgl3ify; clamp to not-pressed instead of misfiring PTT
     */
    @Overwrite(remap = false)
    private boolean isPTTKeyDown() {
        int keyCode = readPTTKeyCode();
        if (keyCode == 0) {
            return false;
        }
        try {
            if (keyCode < 0) {
                int button = keyCode + 100;
                if (button < 0 || button > 7 || !Mouse.isCreated()) {
                    logClamp(keyCode);
                    return false;
                }
                return Mouse.isButtonDown(button);
            }
            return Keyboard.isKeyDown(keyCode);
        } catch (Throwable t) {
            return false;
        }
    }

    private static int readPTTKeyCode() {
        try {
            if (pttField == null) {
                Class<?> listener = Class.forName(
                        "fr.paladium.palavoicechat.client.listener.KeyBindHandlerListener");
                pttField = listener.getField("PTT");
                getKeyCode = pttField.getType().getMethod("func_151463_i");
            }
            Object ptt = pttField.get(null);
            return ((Integer) getKeyCode.invoke(ptt)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void logClamp(int keyCode) {
        if (!clamped) {
            clamped = true;
            System.out.println("[AnarchyCompat] MicThread PTT code " + keyCode
                    + " out of GLFW mouse range, treated as not pressed");
        }
    }
}
