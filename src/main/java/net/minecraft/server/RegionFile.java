package net.minecraft.server;

import com.destroystokyo.paper.exception.ServerInternalException;
import com.google.common.collect.Lists;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;

public class RegionFile implements AutoCloseable {

    // Spigot start
    // Minecraft is limited to 256 sections per chunk. So 1MB. This can easily be overriden.
    // So we extend this to use the REAL size when the count is maxed by seeking to that section and reading the length.
    private static final boolean ENABLE_EXTENDED_SAVE = Boolean.parseBoolean(System.getProperty("net.minecraft.server.RegionFile.enableExtendedSave", "true"));
    private final File file;
    // Spigot end
    private static final byte[] a = new byte[4096];
    private final RandomAccessFile b;
    private final int[] c = new int[1024];
    private final int[] d = new int[1024];
    private final List<Boolean> e;

    public RegionFile(File file) throws IOException {
        this.b = new RandomAccessFile(file, "rw");
        if (this.b.length() < 4096L) {
            this.b.write(RegionFile.a);
            this.b.write(RegionFile.a);
        }

        int i;

        if ((this.b.length() & 4095L) != 0L) {
            for (i = 0; (long) i < (this.b.length() & 4095L); ++i) {
                this.b.write(0);
            }
        }

        i = (int) this.b.length() / 4096;
        this.e = Lists.newArrayListWithCapacity(i);

        int j;

        for (j = 0; j < i; ++j) {
            this.e.add(true);
        }

        this.e.set(0, false);
        this.e.set(1, false);
        this.b.seek(0L);

        int k;

        for (j = 0; j < 1024; ++j) {
            k = this.b.readInt();
            this.c[j] = k;
            // Spigot start
            int length = k & 255;
            if (length == 255) {
                if ((k >> 8) <= this.e.size()) {
                     // We're maxed out, so we need to read the proper length from the section
                    this.b.seek((k >> 8) * 4096);
                    length = (this.b.readInt() + 4) / 4096 + 1;
                    this.b.seek(j * 4 + 4); // Go back to where we were
                }
            }
            if (k != 0 && (k >> 8) + (length) <= this.e.size()) {
                for (int l = 0; l < (length); ++l) {
                    // Spigot end
                    this.e.set((k >> 8) + l, false);
                }
            }
            // Spigot start
            else if (length > 0) {
                org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Invalid chunk: ({0}, {1}) Offset: {2} Length: {3} runs off end file. {4}", new Object[]{j % 32, (int) (j / 32), k >> 8, length, file});
            }
            // Spigot end
        }

        for (j = 0; j < 1024; ++j) {
            k = this.b.readInt();
            this.d[j] = k;
        }

        this.file = file; // Spigot
    }

    @Nullable
    public synchronized DataInputStream a(ChunkCoordIntPair chunkcoordintpair) {
        try {
            int i = this.getOffset(chunkcoordintpair);

            if (i == 0) {
                return null;
            } else {
                int j = i >> 8;
                int k = i & 255;
                // Spigot start
                if (k == 255) {
                    this.b.seek(j * 4096);
                    k = (this.b.readInt() + 4) / 4096 + 1;
                }
                // Spigot end

                if (j + k > this.e.size()) {
                    return null;
                } else {
                    this.b.seek((long) (j * 4096));
                    int l = this.b.readInt();

                    if (l > 4096 * k) {
                        org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Invalid chunk: ({0}) Offset: {1} Invalid Size: {2}>{3} {4}", new Object[]{chunkcoordintpair, j, l, k * 4096, this.file}); // Spigot
                        return null;
                    } else if (l <= 0) {
                        org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Invalid chunk: ({0}) Offset: {1} Invalid Size: {2} {3}", new Object[]{chunkcoordintpair, j, l, this.file}); // Spigot
                        return null;
                    } else {
                        byte b0 = this.b.readByte();
                        byte[] abyte;

                        if (b0 == 1) {
                            abyte = new byte[l - 1];
                            this.b.read(abyte);
                            return new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(abyte))));
                        } else if (b0 == 2) {
                            abyte = new byte[l - 1];
                            this.b.read(abyte);
                            return new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(abyte))));
                        } else {
                            return null;
                        }
                    }
                }
            }
        } catch (IOException ioexception) {
            ServerInternalException.reportInternalException(ioexception); // Paper
            return null;
        }
    }

    public boolean b(ChunkCoordIntPair chunkcoordintpair) {
        int i = this.getOffset(chunkcoordintpair);

        if (i == 0) {
            return false;
        } else {
            int j = i >> 8;
            int k = i & 255;

            if (j + k > this.e.size()) {
                return false;
            } else {
                try {
                    this.b.seek((long) (j * 4096));
                    int l = this.b.readInt();

                    return l > 4096 * k ? false : l > 0;
                } catch (IOException ioexception) {
                    return false;
                }
            }
        }
    }

    public DataOutputStream c(ChunkCoordIntPair chunkcoordintpair) {
        return new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(new RegionFile.ChunkBuffer(chunkcoordintpair))));
    }

    protected synchronized void a(ChunkCoordIntPair chunkcoordintpair, byte[] abyte, int i) {
        try {
            int j = this.getOffset(chunkcoordintpair);
            int k = j >> 8;
            int l = j & 255;
            // Spigot start
            if (l == 255) {
                this.b.seek(k * 4096);
                l = (this.b.readInt() + 4) / 4096 + 1;
            }
            // Spigot end
            int i1 = (i + 5) / 4096 + 1;

            if (i1 >= 256) {
                // Spigot start
                if (!ENABLE_EXTENDED_SAVE) throw new RuntimeException(String.format("Too big to save, %d > 1048576", i));
                org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING,"Large Chunk Detected: ({0}) Size: {1} {2}", new Object[]{chunkcoordintpair, i1, this.file});
                // Spigot end
            }

            if (k != 0 && l == i1) {
                this.a(k, abyte, i);
            } else {
                int j1;

                for (j1 = 0; j1 < l; ++j1) {
                    this.e.set(k + j1, true);
                }

                j1 = this.e.indexOf(true);
                int k1 = 0;
                int l1;

                if (j1 != -1) {
                    for (l1 = j1; l1 < this.e.size(); ++l1) {
                        if (k1 != 0) {
                            if ((Boolean) this.e.get(l1)) {
                                ++k1;
                            } else {
                                k1 = 0;
                            }
                        } else if ((Boolean) this.e.get(l1)) {
                            j1 = l1;
                            k1 = 1;
                        }

                        if (k1 >= i1) {
                            break;
                        }
                    }
                }

                if (k1 >= i1) {
                    k = j1;
                    this.a(chunkcoordintpair, j1 << 8 | (i1 > 255 ? 255 : i1)); // Spigot

                    for (l1 = 0; l1 < i1; ++l1) {
                        this.e.set(k + l1, false);
                    }

                    this.a(k, abyte, i);
                } else {
                    this.b.seek(this.b.length());
                    k = this.e.size();

                    for (l1 = 0; l1 < i1; ++l1) {
                        this.b.write(RegionFile.a);
                        this.e.add(false);
                    }

                    this.a(k, abyte, i);
                    this.a(chunkcoordintpair, k << 8 | (i1 > 255 ? 255 : i1)); // Spigot
                }
            }

            this.b(chunkcoordintpair, (int) (SystemUtils.getTimeMillis() / 1000L));
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
            ServerInternalException.reportInternalException(ioexception); // Paper
        }

    }

    private void a(int i, byte[] abyte, int j) throws IOException {
        this.b.seek((long) (i * 4096));
        this.b.writeInt(j + 1);
        this.b.writeByte(2);
        this.b.write(abyte, 0, j);
    }

    private int getOffset(ChunkCoordIntPair chunkcoordintpair) {
        return this.c[this.f(chunkcoordintpair)];
    }

    public boolean d(ChunkCoordIntPair chunkcoordintpair) {
        return this.getOffset(chunkcoordintpair) != 0;
    }

    private void a(ChunkCoordIntPair chunkcoordintpair, int i) throws IOException {
        int j = this.f(chunkcoordintpair);

        this.c[j] = i;
        this.b.seek((long) (j * 4));
        this.b.writeInt(i);
    }

    private int f(ChunkCoordIntPair chunkcoordintpair) {
        return chunkcoordintpair.j() + chunkcoordintpair.k() * 32;
    }

    private void b(ChunkCoordIntPair chunkcoordintpair, int i) throws IOException {
        int j = this.f(chunkcoordintpair);

        this.d[j] = i;
        this.b.seek((long) (4096 + j * 4));
        this.b.writeInt(i);
    }

    public void close() throws IOException {
        this.b.close();
    }

    class ChunkBuffer extends ByteArrayOutputStream {

        private final ChunkCoordIntPair b;

        public ChunkBuffer(ChunkCoordIntPair chunkcoordintpair) {
            super(8096);
            this.b = chunkcoordintpair;
        }

        public void close() {
            RegionFile.this.a(this.b, this.buf, this.count);
        }
    }
}
