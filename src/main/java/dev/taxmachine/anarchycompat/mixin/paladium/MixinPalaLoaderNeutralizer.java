package dev.taxmachine.anarchycompat.mixin.paladium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin-side neutralisation of PalaMod's reflective late-mixin hack
 * (experimental runtime only, {@code required: false}).
 *
 * <p>PalaMod's {@code MixinLoader#beforeConstructingMods} grabs Mixin's
 * {@code Proxy.transformer} reflectively and re-runs
 * {@code selectConfigs/prepareConfigs} to force
 * {@code mixins.palamod-after.json} late; under RFB the transformer was
 * defined on another loader, so every call dies with
 * {@code IllegalAccessError} in {@code Loader.loadMods} (the
 * {@code mixins.palaforge.json:forge.MixinLoader} injector in the crash
 * report). The {@code @Overwrite} below turns the handler into an empty
 * return (the after-config is currently empty), while
 * {@link PalaLateMixinLoader} re-registers that config through
 * {@code ILateMixinLoader} — the upstream fix.
 *
 * <p>Kept separate from {@link PalaLateMixinLoader}: the late-loader
 * entry and the {@code @Mixin} target must not share a class. If this
 * Mixin-on-Mixin still loses the prep race,
 * {@code ShadedMixinGuardTransformer} strips the synthetic
 * {@code Loader#handler$...$beforeConstructingMods} as backup
 * ({@code Loader} IS visible to {@code IClassTransformer}, unlike the
 * Mixin class itself).
 */
@Mixin(targets = "fr.paladium.palamod.mixins.forge.MixinLoader", remap = false, priority = 1100)
public abstract class MixinPalaLoaderNeutralizer {

    /**
     * Neutralises the reflective transformer grab; late registration is
     * handled by {@link PalaLateMixinLoader} instead.
     *
     * @author AnarchyCompat
     * @reason Reflective Proxy.transformer grab throws IllegalAccessError
     *         under RFB; palamod-after registers via ILateMixinLoader now
     */
    @Overwrite(remap = false)
    private void beforeConstructingMods(CallbackInfo ci) {
    }
}
