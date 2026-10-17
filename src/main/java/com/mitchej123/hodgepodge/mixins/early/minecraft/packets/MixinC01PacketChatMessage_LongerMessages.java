package com.mitchej123.hodgepodge.mixins.early.minecraft.packets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Name-shadow of Hodgepodge's LongerMessages mixin (experimental runtime only).
 *
 * <p>Hodgepodge 2.7.216 ships {@code mixins.hodgepodge.early.json} as a
 * "wildcard" config (no explicit mixin list; the GTNH Mixin fork synthesizes it
 * from every {@code @Mixin} class in the declared package). Its
 * {@code minecraft.packets.MixinC01PacketChatMessage_LongerMessages} performs a
 * {@code @ModifyConstant} into
 * {@code C01PacketChatMessage#func_148837_a(Lnet/minecraft/network/PacketBuffer;)V}.
 * PalaMod's Helios {@code @Overwrite}s that exact method (permission-based chat
 * length), so the patched constant no longer exists; Hodgepodge's injection
 * then fails at APPLY time with {@code InvalidInjectionException} and Mixin
 * refuses to define {@code C01PacketChatMessage} at all
 * ({@code ClassNotFoundException: Exception caught while transforming class ...}).
 *
 * <p>That failure cascades: {@code EnumConnectionState.<clinit>} references
 * every packet class, so it dies once with
 * {@code NoClassDefFoundError: C01PacketChatMessage} and is then permanently
 * broken — every connect attempt afterwards dies instantly with
 * {@code Internal exception: Could not initialize class
 * net.minecraft.network.EnumConnectionState}.
 *
 * <p>This class carries the exact same fully-qualified name and lives in our
 * jar, which the Mixin service scans before hodgepodge's — so its bytes shadow
 * the original and no conflicting injection is applied. The patch is redundant
 * on this stack anyway: Helios' overwrite already removes the 100-char limit
 * by reading the length the server sent.
 *
 * <p>The {@code @Mixin} annotation is kept so the wildcard package scan still
 * resolves this entry to a valid, empty mixin; the {@code @Unique} member
 * exists only so this is never an "empty" mixin. Do not rename this class or
 * move it out of this package — the shadow only works by exact name match.
 */
@Mixin(targets = "net.minecraft.network.play.client.C01PacketChatMessage", remap = false)
public class MixinC01PacketChatMessage_LongerMessages {

    @Unique
    private static void anarchycompat$shadowStub() {
    }
}
