package dev.taxmachine.anarchycompat.rfb;

import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;

/**
 * RFB plugin (experimental runtime only). Registered via
 * {@code META-INF/rfb-plugin/anarchycompat.properties} so it runs during
 * RFB bootstrap — long before the Mixin tweaker loads any Mixin classes.
 *
 * <p>The single job here is {@link PublicMixinTransformer}: widen
 * {@code org.spongepowered.asm.mixin.transformer.MixinTransformer} to
 * {@code public} so that PalaMod's merged handler on
 * {@code cpw.mods.fml.common.Loader} can pass its {@code checkcast}
 * without {@code IllegalAccessError}. See the transformer javadoc for
 * the full failure chain.
 */
public class AnarchyCompatRfbPlugin implements RfbPlugin {

    @Override
    public RfbClassTransformer[] makeTransformers() {
        return new RfbClassTransformer[] { new PublicMixinTransformer() };
    }
}
