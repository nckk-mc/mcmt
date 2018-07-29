package net.minecraft.server;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import com.destroystokyo.paper.PaperConfig; // Paper

import org.apache.logging.log4j.LogManager;

public abstract class RegionFileCache implements AutoCloseable {

    public final Long2ObjectLinkedOpenHashMap<RegionFile> cache = new Long2ObjectLinkedOpenHashMap();
    private final File a;
    // Paper start
    private final File templateWorld;
    private final File actualWorld;
    private boolean useAltWorld;
    // Paper end


    protected RegionFileCache(File file) {
        this.a = file;
        // Paper end

        this.actualWorld = file;
        if (com.destroystokyo.paper.PaperConfig.useVersionedWorld) {
            this.useAltWorld = true;
            String name = file.getName();
            File container = file.getParentFile().getParentFile();
            if (name.equals("DIM-1") || name.equals("DIM1")) {
                container = container.getParentFile();
            }
            this.templateWorld = new File(container, name);
            File region = new File(file, "region");
            if (!region.exists()) {
                region.mkdirs();
            }
        } else {
            this.useAltWorld = false;
            this.templateWorld = file;
        }
        // Paper start
    }

    private RegionFile a(ChunkCoordIntPair chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit
        long i = ChunkCoordIntPair.pair(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ());
        RegionFile regionfile = (RegionFile) this.cache.getAndMoveToFirst(i);

        if (regionfile != null) {
            return regionfile;
        } else {
            if (this.cache.size() >= PaperConfig.regionFileCacheSize) { // Paper - configurable
                ((RegionFile) this.cache.removeLast()).close();
            }

            if (!this.a.exists()) {
                this.a.mkdirs();
            }

            copyIfNeeded(chunkcoordintpair.x, chunkcoordintpair.z); // Paper
            File file = new File(this.a, "r." + chunkcoordintpair.getRegionX() + "." + chunkcoordintpair.getRegionZ() + ".mca");
            if (existingOnly && !file.exists()) return null; // CraftBukkit
            RegionFile regionfile1 = new RegionFile(file);

            this.cache.putAndMoveToFirst(i, regionfile1);
            return regionfile1;
        }
    }

    public static File getRegionFileName(File file, int i, int j) {
        File file1 = new File(file, "region");
        return new File(file1, "r." + (i >> 5) + "." + (j >> 5) + ".mca");
    }
    public synchronized boolean hasRegionFile(File file, int i, int j) {
        return cache.containsKey(ChunkCoordIntPair.pair(i, j));
    }
    // Paper End

    @Nullable
    public NBTTagCompound read(ChunkCoordIntPair chunkcoordintpair) throws IOException {
        RegionFile regionfile = this.a(chunkcoordintpair, false); // CraftBukkit
        DataInputStream datainputstream = regionfile.a(chunkcoordintpair);
        Throwable throwable = null;

        NBTTagCompound nbttagcompound;

        try {
            if (datainputstream != null) {
                nbttagcompound = NBTCompressedStreamTools.a(datainputstream);
                return nbttagcompound;
            }

            nbttagcompound = null;
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (datainputstream != null) {
                if (throwable != null) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    datainputstream.close();
                }
            }

        }

        return nbttagcompound;
    }

    protected void write(ChunkCoordIntPair chunkcoordintpair, NBTTagCompound nbttagcompound) throws IOException {
        int attempts = 0; Exception laste = null; while (attempts++ < 5) { try { // Paper
        RegionFile regionfile = this.a(chunkcoordintpair, false); // CraftBukkit
        DataOutputStream dataoutputstream = regionfile.c(chunkcoordintpair);
        Throwable throwable = null;

        try {
            NBTCompressedStreamTools.a(nbttagcompound, (DataOutput) dataoutputstream);
        } catch (Throwable throwable1) {
            throwable = throwable1;
            throw throwable1;
        } finally {
            if (dataoutputstream != null) {
                if (throwable != null) {
                    try {
                        dataoutputstream.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                } else {
                    dataoutputstream.close();
                }
            }

        }

            // Paper start
            return;
        } catch (Exception ex)  {
            laste = ex;
        }
        }

        if (laste != null) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(laste);
            MinecraftServer.LOGGER.error("Failed to save chunk", laste);
        }
        // Paper end
    }

    public void close() throws IOException {
        ObjectIterator objectiterator = this.cache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            regionfile.close();
        }

    }

    // CraftBukkit start
    public boolean chunkExists(ChunkCoordIntPair pos) throws IOException {
        copyIfNeeded(pos.x, pos.z); // Paper
        RegionFile regionfile = a(pos, true);

        return regionfile != null ? regionfile.d(pos) : false;
    }
    // CraftBukkit end

    private void copyIfNeeded(int x, int z) {
        if (!useAltWorld) {
            return;
        }
        synchronized (RegionFileCache.class) {
            if (hasRegionFile(this.actualWorld, x, z)) {
                return;
            }
            File actual = RegionFileCache.getRegionFileName(this.actualWorld, x, z);
            File template = RegionFileCache.getRegionFileName(this.templateWorld, x, z);
            if (!actual.exists() && template.exists()) {
                try {
                    net.minecraft.server.MinecraftServer.LOGGER.info("Copying" + template + " to " + actual);
                    java.nio.file.Files.copy(template.toPath(), actual.toPath(), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e1) {
                    LogManager.getLogger().error("Error copying " + template + " to " + actual, e1);
                    MinecraftServer.getServer().safeShutdown(false);
                    com.destroystokyo.paper.util.SneakyThrow.sneaky(e1);
                }
            }
        }
    }
}
