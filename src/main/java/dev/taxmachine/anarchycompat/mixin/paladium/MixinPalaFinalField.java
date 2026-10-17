package dev.taxmachine.anarchycompat.mixin.paladium;

import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes PalaMod's {@code Field.modifiers} hack version-tolerant
 * (experimental runtime only, {@code required: false}).
 *
 * <p>Three sites clear {@code FINAL} via
 * {@code Field.class.getDeclaredField("modifiers")}:
 * {@code Back2Future.setFinalField}, {@code TalkLikePirateEvent.onPlayerChat}
 * (final chat message) and {@code PotionExtender.extendPotionArray} (static
 * final potion array). The redirect below keeps the call on Java 8 (where
 * the hack works) and skips it on Java 9+ (where it is neutered or throws),
 * leaving the preceding {@code setAccessible(true)} in place — sufficient
 * for instance finals. Static-final array extension stays a logged no-op on
 * modern Java (needs {@code Unsafe.staticFieldBase}; V2 scope).
 */
@Mixin(targets = {
        "fr.paladium.palamod.modules.back2future.Back2Future",
        "fr.paladium.palamod.modules.luckyblock.monthly.modules.july.common.luckyevents.TalkLikePirateEvent",
        "fr.paladium.palamod.modules.paladium.common.potion.PotionExtender"
}, remap = false)
public abstract class MixinPalaFinalField {

    private static boolean logged;

    @Redirect(method = "*",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/reflect/Field;setInt(Ljava/lang/Object;I)V"),
            require = 0, remap = false)
    private static void anarchycompat$modifiersHack(Field modifiers, Object field, int value) {
        if (isJava8()) {
            try {
                modifiers.setInt(field, value);
                return;
            } catch (Exception ignored) {
                return;
            }
        }
        if (!logged) {
            logged = true;
            System.out.println("[AnarchyCompat] Field.modifiers hack skipped on Java "
                    + System.getProperty("java.specification.version")
                    + " (setAccessible only; static-final stays read-only)");
        }
    }

    private static boolean isJava8() {
        return System.getProperty("java.specification.version", "1.8").startsWith("1.");
    }
}
