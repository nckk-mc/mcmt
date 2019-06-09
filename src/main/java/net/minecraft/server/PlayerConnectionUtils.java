package net.minecraft.server;

public class PlayerConnectionUtils {

    public static <T extends PacketListener> void ensureMainThread(Packet<T> packet, T t0, WorldServer worldserver) throws CancelledPacketHandleException {
        ensureMainThread(packet, t0, (IAsyncTaskHandler) worldserver.getMinecraftServer());
    }

    public static <T extends PacketListener> void ensureMainThread(Packet<T> packet, T t0, IAsyncTaskHandler<?> iasynctaskhandler) throws CancelledPacketHandleException {
        if (!iasynctaskhandler.isMainThread()) {
            iasynctaskhandler.execute(() -> {
                packet.a(t0);
            });
            throw CancelledPacketHandleException.INSTANCE;
        }
    }
}
