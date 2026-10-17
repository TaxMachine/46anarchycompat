package com.mitchej123.hodgepodge.mixins.early.minecraft.packets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Name-shadow of Hodgepodge's S35 "byte array" TE-packet mixin
 * (experimental runtime only).
 *
 * <p>Hodgepodge replaces {@code S35PacketUpdateTileEntity}'s NBT
 * serialization with GTNH's byte-array format on BOTH the write and the read
 * path ({@code @Redirect} on {@code readPacketData}/{@code writePacketData}'s
 * {@code readNBTTagCompoundFromBuffer}/{@code writeNBTTagCompoundToBuffer}
 * calls). That format only matches a GTNH server that ships the same patch.
 *
 * <p>The Paladium server does NOT run Hodgepodge (verified against its Server
 * List Ping mod list: it runs the Paladium stack, no modern perf mods), so it
 * sends vanilla-format S35. The client then finishes reading the vanilla NBT
 * with the GTNH reader, leaves the remainder unread and the netty decoder
 * aborts with:
 * {@code Packet was larger than I expected, found 164 bytes extra whilst
 * reading packet 53} — "packet 53" being {@code 0x35}, i.e. S35.
 *
 * <p>This class carries the same fully-qualified name in our jar, which the
 * Mixin service scans first, so its bytes shadow Hodgepodge's and the client
 * keeps VANILLA S35 serialization — matching the server. The GTNH format only
 * exists to make TE packets cheaper between GTNH peers; it is not needed here.
 *
 * <p>Audited siblings for the same hazard: {@code MixinS0E..._ItemThrower} is
 * guarded by {@code readableBytes() >= 5} (vanilla-compatible), and
 * {@code MixinS1DPacketEntityEffect_LongDuration} / {@code MixinS0A...} /
 * {@code MixinS02...} only touch constructors or values, not the wire — so no
 * other stubs are shipped. If a future "packet larger than expected" names a
 * different packet id, shadow the corresponding GTNH-format mixin the same way.
 *
 * <p>Do not rename or relocate: the shadow works by exact name match only.
 */
@Mixin(targets = "net.minecraft.network.play.server.S35PacketUpdateTileEntity", remap = false)
public class MixinS35PacketUpdateTileEntity_ByteArray {

    @Unique
    private static void anarchycompat$shadowStub() {
    }
}
