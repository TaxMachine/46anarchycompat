package dev.taxmachine.anarchycompat;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = AnarchyCompat.MODID, version = "@VERSION@", acceptableRemoteVersions = "*")
public class AnarchyCompat {
    public static final String MODID = "anarchycompat";

    private static Logger logger;

    // NOTE: no reflective flag forcing here, by design. An earlier revision
    // forced FalseTweaks module flags at construct time and that
    // desynchronised the module system (forced value disagreeing with the
    // config file half-initialises modules) which blanked all world
    // rendering. The config FILE is the single source of truth (see
    // matrix); the only requirement is that the file is loaded before any
    // consumer reads it, which holds because config loads at first access.

    /**
     * P0-5 — FalseTweaks stitcher ownership (experimental path). lwjgl3ify
     * owns texture stitching (STB, native to the LWJGL3 runtime, proven by
     * every green matrix boot). Ship {@code config/falsetweaks.cfg} with
     * {@code 00_modules.textureOptimizations=false}: FalseTweaks' own
     * compat check reads the flag at construct time, and the file value is
     * loaded at first access before that. Keep
     * {@code stbiTextureStitching=true} in lwjgl3ify.cfg. Do NOT force
     * module flags reflectively (see NOTE above). Sibling modules
     * ({@code voxelizer}, {@code itemRenderLists}) stay config-driven,
     * default ON.
     */

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("[AnarchyCompat] shims active, version @VERSION@");
    }

    public static Logger logger() {
        return logger;
    }
}
