package dev.taxmachine.anarchycompat.coremod;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Coremod entry point. Registered via the {@code FMLCorePlugin} manifest
 * attribute.
 *
 * <p>P0-1 — shaded legacy-Mixin guard. The pack ships Mixin 0.6-era copies
 * shaded inside mod jars ({@code PalaMixins}, {@code PalaJobs},
 * {@code fspatches}); their
 * {@code MixinServiceLaunchWrapper} does an unconditional
 * {@code (URLClassLoader) Launch.class.getClassLoader()} cast that throws
 * {@code ClassCastException} on Java 9+ (experimental lwjgl3ify + Java 21
 * runtime). {@link #injectData} detects those copies early and fails with
 * an actionable message instead of a cryptic CCE. On the classic Java 8
 * path the old service works, so this only warns there.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class CompatCorePlugin implements IFMLLoadingPlugin {

    /** Class-file path of the legacy Mixin service inside mod jars. */
    static final String OLD_MIXIN_SERVICE =
            "org/spongepowered/asm/service/mojang/MixinServiceLaunchWrapper.class";

    /** Configs re-registered programmatically (PalaMod LoadingPlugin pattern). */
    static final String[] EARLY_MIXIN_CONFIGS = {
        "anarchycompat-paladium.json",
        "anarchycompat-zephyr.json",
    };


    private static final String OLD_MIXIN_CAST_MARKER = "java/net/URLClassLoader";

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "dev.taxmachine.anarchycompat.coremod.ShadedMixinGuardTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        pinTCCLToLaunchLoader();
        registerEarlyMixinConfigs();
        boolean modernJava = isModernJava();
        List<String> offenders = findShadedMixinServices();
        ClassLoader cl = CompatCorePlugin.class.getClassLoader();
        URL visible = cl.getResource(OLD_MIXIN_SERVICE);
        boolean visibleIsOld = visible != null && isOldMixinService(visible);

        if (!offenders.isEmpty()) {
            FMLLog.warning("[AnarchyCompat] Shaded legacy Mixin service copies found in: %s."
                    + " UniMixins must provide the Mixin service — these copies must lose"
                    + " the tweaker race (TweakOrder) and never be embedded in our jar.",
                    offenders);
        }
        if (visible != null) {
            FMLLog.info("[AnarchyCompat] Visible MixinServiceLaunchWrapper: %s (legacy-cast=%s, java=%s)",
                    visible, visibleIsOld, System.getProperty("java.version"));
        }
        if (modernJava && visibleIsOld) {
            // Fail fast is opt-in: UniMixins usually wins the tweaker race
            // even when an old shaded copy is visible as a bare resource, so
            // only abort when explicitly asked (matrix triage). Otherwise the
            // transformer below keeps watch and the boot log names the jars.
            // UniMixins' own service copy is exempt: it hosts Mixin by design
            // (verified winning the race when its jar sorts first — see
            // README "boot matrix"), even if it still carries the legacy
            // cast string in unreachable code.
            boolean visibleIsHost = visible != null
                    && visible.toString().toLowerCase().contains("unimixins");
            if (visibleIsHost) {
                FMLLog.info("[AnarchyCompat] Mixin service hosted by %s —"
                        + " shaded legacy copies %s lose the race by design.",
                        visible, offenders.isEmpty() ? "(none other)" : offenders);
                return;
            }
            if (Boolean.getBoolean("anarchycompat.strictMixinGuard")) {
                throw new RuntimeException(
                        "[AnarchyCompat] Legacy shaded Mixin service will crash on Java 9+ "
                        + "(URLClassLoader cast). Visible copy: " + visible
                        + (offenders.isEmpty() ? "" : "; shaded copies in: " + offenders)
                        + ". The experimental runtime requires UniMixins to host the Mixin"
                        + " service — update load order (TweakOrder) so UniMixins wins, and"
                        + " never embed a Mixin jar in AnarchyCompat.");
            }
            FMLLog.severe("[AnarchyCompat] Legacy shaded Mixin service visible at %s"
                    + " — continuing because UniMixins normally wins the tweaker race"
                    + " (set -Danarchycompat.strictMixinGuard=true to abort instead).",
                    visible);
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    /**
     * P0-3 — pin the thread context classloader to the launch loader during
     * coremod discovery. RFB isolates mods from the system classloader, so
     * at this point TCCL is the SYSTEM loader, while vanilla FML assumed a
     * single loader for everything. Half the pack's coremods call
     * {@code MixinBootstrap.init()} from here (PalaMod, PalaMixins, Helios,
     * Paladium-Common, PalaKeybind, Propsy), which resolves Mixin classes
     * via TCCL: with the system TCCL, {@code MixinTransformer} gets defined
     * system-side and every later launch-side use dies with
     * {@code IllegalAccessError} (different unnamed modules). Pinning TCCL
     * restores vanilla semantics. CompatCorePlugin instantiates second
     * (right after UniMixins' AllCore), so the pin covers all pack coremods.
     */
    static void pinTCCLToLaunchLoader() {
        try {
            ClassLoader launch = CompatCorePlugin.class.getClassLoader();
            Thread current = Thread.currentThread();
            ClassLoader before = current.getContextClassLoader();
            if (before != launch) {
                current.setContextClassLoader(launch);
                FMLLog.info("[AnarchyCompat] Pinned TCCL to %s for coremod discovery"
                        + " (was %s) — Mixin service initializes launch-side.",
                        launch, before);
            }
        } catch (Exception e) {
            FMLLog.warning("[AnarchyCompat] Could not pin TCCL: %s", e);
        }
    }

    /**
     * P1 — programmatic config registration (FS-Patches MixinLoader +
     * PalaMod LoadingPlugin pattern). The manifest {@code MixinConfigs} +
     * {@code TweakClass} entry is the primary route; this re-registers the
     * two phase-sensitive configs reflectively (no hard Mixin linkage, so
     * no new failure mode where Mixin is absent) so they are queued before
     * PalaMod's PREINIT injection into {@code Loader} prepares. Runs after
     * the TCCL pin so {@code MixinBootstrap.init()} resolves launch-side.
     */
    static void registerEarlyMixinConfigs() {
        try {
            Class.forName("org.spongepowered.asm.launch.MixinBootstrap")
                    .getMethod("init").invoke(null);
            java.lang.reflect.Method add = Class
                    .forName("org.spongepowered.asm.mixin.Mixins")
                    .getMethod("addConfiguration", String.class);
            for (String cfg : EARLY_MIXIN_CONFIGS) {
                try {
                    add.invoke(null, cfg);
                    FMLLog.info("[AnarchyCompat] Registered Mixin config %s"
                            + " programmatically (pre-race backup).", cfg);
                } catch (Exception e) {
                    FMLLog.warning("[AnarchyCompat] Could not register Mixin"
                            + " config %s: %s — manifest entry remains.", cfg, e);
                }
            }
        } catch (Throwable t) {
            FMLLog.warning("[AnarchyCompat] Programmatic Mixin registration"
                    + " skipped (%s) — manifest MixinConfigs remains primary.", t);
        }
    }

    /** True on Java 9+ (spec "1.8" = Java 8, "9"/"11"/"17"/"21" = modern). */
    static boolean isModernJava() {
        String spec = System.getProperty("java.specification.version", "1.8");
        if (spec.startsWith("1.")) {
            return false;
        }
        try {
            return Integer.parseInt(spec) >= 9;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Lists every jar visible to our classloader that embeds the legacy
     * Mixin service class. Works on the Java 8 LaunchClassLoader
     * (URLClassLoader) and falls back to launchwrapper sources otherwise.
     */
    static List<String> findShadedMixinServices() {
        List<String> hits = new ArrayList<String>();
        List<URL> sources = new ArrayList<URL>();
        ClassLoader cl = CompatCorePlugin.class.getClassLoader();
        if (cl instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) cl).getURLs()) {
                sources.add(url);
            }
        } else {
            // Java 9+: AppClassLoader has no getURLs. Ask launchwrapper.
            try {
                Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch");
                Object loader = launch.getField("classLoader").get(null);
                Object srcs = loader.getClass().getMethod("getSources").invoke(loader);
                @SuppressWarnings("unchecked")
                List<URL> list = (List<URL>) srcs;
                sources.addAll(list);
            } catch (Exception e) {
                FMLLog.warning("[AnarchyCompat] Could not enumerate classloader sources: %s", e);
            }
        }
        for (URL url : sources) {
            if (!"file".equals(url.getProtocol()) || !url.getPath().endsWith(".jar")) {
                continue;
            }
            try {
                ZipFile zip = new ZipFile(url.getPath());
                try {
                    ZipEntry entry = zip.getEntry(OLD_MIXIN_SERVICE);
                    if (entry != null) {
                        hits.add(jarName(url.getPath()));
                    }
                } finally {
                    zip.close();
                }
            } catch (Exception ignored) {
                // Unreadable jar — not our problem here.
            }
        }
        return hits;
    }

    /** True if the class bytes still contain the legacy URLClassLoader cast. */
    static boolean isOldMixinService(URL resource) {
        try {
            InputStream in = resource.openStream();
            try {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                // Constant-pool entries are plain ASCII in the class file.
                String ascii = new String(buf.toByteArray(), "ISO-8859-1");
                return ascii.contains(OLD_MIXIN_CAST_MARKER);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            FMLLog.warning("[AnarchyCompat] Could not inspect Mixin service copy %s: %s", resource, e);
            return false;
        }
    }

    private static String jarName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        // Strip Forge's trailing "!..." or version noise? Keep it simple.
        return name;
    }
}
