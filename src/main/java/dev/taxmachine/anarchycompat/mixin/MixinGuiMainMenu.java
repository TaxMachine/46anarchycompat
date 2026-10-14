package dev.taxmachine.anarchycompat.mixin;

import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Example shim mixin. Proves the Mixin pipeline (refmap + reobf) works end
 * to end: replace with real per-mod shims from the lwjgl3ify test matrix
 * (e.g. redirecting a closed mod's {@code Display} access or replacing a
 * {@code URLClassLoader} cast that breaks on Java 17+).
 */
@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu {

    @Inject(method = "initGui", at = @At("HEAD"), remap = false)
    private void anarchycompat$onInitGui(CallbackInfo ci) {
        // Shim entry point confirmed working if this logs on the main menu.
        System.out.println("[AnarchyCompat] example mixin injected into GuiMainMenu.initGui");
    }
}
