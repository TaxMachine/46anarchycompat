package dev.taxmachine.anarchycompat.mixin.zephyr;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * V1 video/audio fallback for ZephyrUI (experimental runtime only,
 * {@code required: false}): mute, never crash.
 *
 * <p>{@code VideoAudioPlayer} drives OpenAL directly ({@code alGenSources},
 * {@code alGenBuffers}, queue/unqueue, deletes). The creates are stubbed to
 * {@code -1}, the existing {@code source != -1} guards then silence every
 * playback path, and the {@code cleanup} deletes are skipped (no real ids
 * can exist). Lifecycle probes log the transitions.
 *
 * <p>GL30 half: {@code FrameBuffer} allocates {@code glGenFramebuffers} in a
 * field initialiser and {@code VideoResourceDecoder} uploads frames via GL
 * textures on an FFmpeg thread. {@link #probeGLVideoPath} checks (by name,
 * no linkage) that {@code GL30} and the framebuffer class resolve under
 * lwjgl3ify; a dedicated FBO redirect is V2 (call sites: {@code prepare},
 * {@code bind/unbind}, {@code delete}). Decoder-native (javacv) absence is
 * a known V1 boundary, documented not patched.
 */
@Mixin(targets = "fr.paladium.zephyrui.lib.video.VideoAudioPlayer", remap = false)
public abstract class MixinZephyrVideoFallback {

    private static boolean loggedGen;
    private static boolean loggedPlay;

    @Redirect(method = "initAL",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alGenSources()I"),
            remap = false)
    private static int anarchycompat$stubGenSources() {
        onStubbedCreate();
        return -1;
    }

    @Redirect(method = "initAL",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alGenBuffers()I"),
            remap = false)
    private static int anarchycompat$stubGenBuffers() {
        onStubbedCreate();
        return -1;
    }

    @Redirect(method = "cleanup",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alDeleteSources(I)V"),
            remap = false)
    private static void anarchycompat$skipDeleteSource(int source) {
        System.out.println("[AnarchyCompat] VideoAudioPlayer cleanup: no AL source to delete (stubbed)");
    }

    @Redirect(method = "cleanup",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alDeleteBuffers(I)V"),
            remap = false)
    private static void anarchycompat$skipDeleteBuffer(int buffer) {
    }

    @Inject(method = "play()V", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$onPlay(CallbackInfo ci) {
        if (!loggedPlay) {
            loggedPlay = true;
            System.out.println("[AnarchyCompat] VideoAudioPlayer.play muted (V1 audio stub)");
        }
    }

    @Inject(method = "cleanup()V", at = @At("HEAD"), require = 0, remap = false)
    private void anarchycompat$onCleanup(CallbackInfo ci) {
        System.out.println("[AnarchyCompat] VideoAudioPlayer.cleanup (stubbed, nothing to free)");
    }

    private static void onStubbedCreate() {
        if (!loggedGen) {
            loggedGen = true;
            System.out.println("[AnarchyCompat] VideoAudioPlayer AL creates stubbed to -1 (muted)");
            probeGLVideoPath();
        }
    }

    private static void probeGLVideoPath() {
        boolean gl30;
        boolean framebuffer;
        try {
            Class.forName("org.lwjgl.opengl.GL30");
            gl30 = true;
        } catch (Throwable t) {
            gl30 = false;
        }
        try {
            Class.forName("fr.paladium.zephyrui.lib.opengl.framebuffer.FrameBuffer");
            framebuffer = true;
        } catch (Throwable t) {
            framebuffer = false;
        }
        System.out.println("[AnarchyCompat] GL30 video probe: GL30=" + gl30
                + " FrameBuffer=" + framebuffer);
    }
}
