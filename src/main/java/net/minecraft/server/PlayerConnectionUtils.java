package net.minecraft.server;

import co.aikar.timings.MinecraftTimings; // Paper
import co.aikar.timings.Timing; // Paper

public class PlayerConnectionUtils {

    public static <T extends PacketListener> void ensureMainThread(Packet<T> packet, T t0, WorldServer worldserver) throws CancelledPacketHandleException {
        ensureMainThread(packet, t0, (IAsyncTaskHandler) worldserver.getMinecraftServer());
    }

    public static <T extends PacketListener> void ensureMainThread(Packet<T> packet, T t0, IAsyncTaskHandler<?> iasynctaskhandler) throws CancelledPacketHandleException {
        if (!iasynctaskhandler.isMainThread()) {
            Timing timing = MinecraftTimings.getPacketTiming(packet); // Paper
            iasynctaskhandler.execute(() -> {
                if (MinecraftServer.getServer().hasStopped() || (t0 instanceof PlayerConnection && ((PlayerConnection) t0).processedDisconnect)) return; // CraftBukkit, MC-142590
                try (Timing ignored = timing.startTiming()) { // Paper
                packet.a(t0);
                } // Paper - timings
            });
            throw CancelledPacketHandleException.INSTANCE;
        }
    }
}
