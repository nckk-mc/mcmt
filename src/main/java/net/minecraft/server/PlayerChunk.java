package net.minecraft.server;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class PlayerChunk {

    public static final Either<IChunkAccess, PlayerChunk.Failure> UNLOADED_CHUNK_ACCESS = Either.right(PlayerChunk.Failure.b);
    public static final CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> UNLOADED_CHUNK_ACCESS_FUTURE = CompletableFuture.completedFuture(PlayerChunk.UNLOADED_CHUNK_ACCESS);
    public static final Either<Chunk, PlayerChunk.Failure> UNLOADED_CHUNK = Either.right(PlayerChunk.Failure.b);
    private static final CompletableFuture<Either<Chunk, PlayerChunk.Failure>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(PlayerChunk.UNLOADED_CHUNK);
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.a();
    private static final PlayerChunk.State[] CHUNK_STATES = PlayerChunk.State.values();
    private final AtomicReferenceArray<CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>>> statusFutures;
    private volatile CompletableFuture<Either<Chunk, PlayerChunk.Failure>> fullChunkFuture;
    private volatile CompletableFuture<Either<Chunk, PlayerChunk.Failure>> tickingFuture;
    private volatile CompletableFuture<Either<Chunk, PlayerChunk.Failure>> entityTickingFuture;
    private CompletableFuture<IChunkAccess> chunkSave;
    public int oldTicketLevel;
    private int ticketLevel;
    private int n;
    final ChunkCoordIntPair location; // Paper - private -> package
    private final short[] dirtyBlocks;
    private int dirtyCount;
    private int r;
    private int s;
    private int t;
    private int u;
    private final LightEngine lightEngine;
    private final PlayerChunk.c w;
    public final PlayerChunk.d players;
    private boolean hasBeenLoaded;

    public PlayerChunk(ChunkCoordIntPair chunkcoordintpair, int i, LightEngine lightengine, PlayerChunk.c playerchunk_c, PlayerChunk.d playerchunk_d) {
        this.statusFutures = new AtomicReferenceArray(PlayerChunk.CHUNK_STATUSES.size());
        this.fullChunkFuture = PlayerChunk.UNLOADED_CHUNK_FUTURE;
        this.tickingFuture = PlayerChunk.UNLOADED_CHUNK_FUTURE;
        this.entityTickingFuture = PlayerChunk.UNLOADED_CHUNK_FUTURE;
        this.chunkSave = CompletableFuture.completedFuture(null); // CraftBukkit - decompile error
        this.dirtyBlocks = new short[64];
        this.location = chunkcoordintpair;
        this.lightEngine = lightengine;
        this.w = playerchunk_c;
        this.players = playerchunk_d;
        this.oldTicketLevel = PlayerChunkMap.GOLDEN_TICKET + 1;
        this.ticketLevel = this.oldTicketLevel;
        this.n = this.oldTicketLevel;
        this.a(i);
    }

    // CraftBukkit start
    public Chunk getFullChunk() {
        CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> statusFuture = this.getStatusFutureUnchecked(ChunkStatus.FULL);
        Either<IChunkAccess, PlayerChunk.Failure> either = (Either<IChunkAccess, PlayerChunk.Failure>) statusFuture.getNow(null);
        return either == null ? null : (Chunk) either.left().orElse(null);
    }
    // CraftBukkit end

    public CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> getStatusFutureUnchecked(ChunkStatus chunkstatus) {
        CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = (CompletableFuture) this.statusFutures.get(chunkstatus.c());

        return completablefuture == null ? PlayerChunk.UNLOADED_CHUNK_ACCESS_FUTURE : completablefuture;
    }

    public CompletableFuture<Either<Chunk, PlayerChunk.Failure>> a() {
        return this.tickingFuture;
    }

    public CompletableFuture<Either<Chunk, PlayerChunk.Failure>> b() {
        return this.entityTickingFuture;
    }

    @Nullable
    public Chunk getChunk() {
        CompletableFuture<Either<Chunk, PlayerChunk.Failure>> completablefuture = this.a();
        Either<Chunk, PlayerChunk.Failure> either = (Either) completablefuture.getNow(null); // CraftBukkit - decompile error

        return either == null ? null : (Chunk) either.left().orElse(null); // CraftBukkit - decompile error
    }

    public CompletableFuture<IChunkAccess> getChunkSave() {
        return this.chunkSave;
    }

    public void a(int i, int j, int k) {
        Chunk chunk = this.getChunk();

        if (chunk != null) {
            this.r |= 1 << (j >> 4);
            if (this.dirtyCount < 64) {
                short short0 = (short) (i << 12 | k << 8 | j);

                for (int l = 0; l < this.dirtyCount; ++l) {
                    if (this.dirtyBlocks[l] == short0) {
                        return;
                    }
                }

                this.dirtyBlocks[this.dirtyCount++] = short0;
            }

        }
    }

    public void a(EnumSkyBlock enumskyblock, int i) {
        Chunk chunk = this.getChunk();

        if (chunk != null) {
            chunk.setNeedsSaving(true);
            if (enumskyblock == EnumSkyBlock.SKY) {
                this.u |= 1 << i - -1;
            } else {
                this.t |= 1 << i - -1;
            }

        }
    }

    public void a(Chunk chunk) {
        if (this.dirtyCount != 0 || this.u != 0 || this.t != 0) {
            World world = chunk.getWorld();

            if (this.dirtyCount == 64) {
                this.s = -1;
            }

            int i;
            int j;

            if (this.u != 0 || this.t != 0) {
                this.a(new PacketPlayOutLightUpdate(chunk.getPos(), this.lightEngine, this.u & ~this.s, this.t & ~this.s), true);
                i = this.u & this.s;
                j = this.t & this.s;
                if (i != 0 || j != 0) {
                    this.a(new PacketPlayOutLightUpdate(chunk.getPos(), this.lightEngine, i, j), false);
                }

                this.u = 0;
                this.t = 0;
                this.s &= ~(this.u & this.t);
            }

            int k;

            if (this.dirtyCount == 1) {
                i = (this.dirtyBlocks[0] >> 12 & 15) + this.location.x * 16;
                j = this.dirtyBlocks[0] & 255;
                k = (this.dirtyBlocks[0] >> 8 & 15) + this.location.z * 16;
                BlockPosition blockposition = new BlockPosition(i, j, k);

                this.a(new PacketPlayOutBlockChange(world, blockposition), false);
                if (world.getType(blockposition).getBlock().isTileEntity()) {
                    this.a(world, blockposition);
                }
            } else if (this.dirtyCount == 64) {
                this.a(new PacketPlayOutMapChunk(chunk, this.r), false);
            } else if (this.dirtyCount != 0) {
                this.a(new PacketPlayOutMultiBlockChange(this.dirtyCount, this.dirtyBlocks, chunk), false);

                for (i = 0; i < this.dirtyCount; ++i) {
                    j = (this.dirtyBlocks[i] >> 12 & 15) + this.location.x * 16;
                    k = this.dirtyBlocks[i] & 255;
                    int l = (this.dirtyBlocks[i] >> 8 & 15) + this.location.z * 16;
                    BlockPosition blockposition1 = new BlockPosition(j, k, l);

                    if (world.getType(blockposition1).getBlock().isTileEntity()) {
                        this.a(world, blockposition1);
                    }
                }
            }

            this.dirtyCount = 0;
            this.r = 0;
        }
    }

    private void a(World world, BlockPosition blockposition) {
        TileEntity tileentity = world.getTileEntity(blockposition);

        if (tileentity != null) {
            PacketPlayOutTileEntityData packetplayouttileentitydata = tileentity.getUpdatePacket();

            if (packetplayouttileentitydata != null) {
                this.a(packetplayouttileentitydata, false);
            }
        }

    }

    private void a(Packet<?> packet, boolean flag) {
        this.players.a(this.location, flag).forEach((entityplayer) -> {
            entityplayer.playerConnection.sendPacket(packet);
        });
    }

    public CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> a(ChunkStatus chunkstatus, PlayerChunkMap playerchunkmap) {
        int i = chunkstatus.c();
        CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = (CompletableFuture) this.statusFutures.get(i);

        if (completablefuture != null) {
            Either<IChunkAccess, PlayerChunk.Failure> either = (Either) completablefuture.getNow(null); // CraftBukkit - decompile error

            if (either == null || either.left().isPresent()) {
                return completablefuture;
            }
        }

        if (getChunkStatus(this.ticketLevel).b(chunkstatus)) {
            CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture1 = playerchunkmap.a(this, chunkstatus);

            this.a(completablefuture1);
            this.statusFutures.set(i, completablefuture1);
            return completablefuture1;
        } else {
            return completablefuture == null ? PlayerChunk.UNLOADED_CHUNK_ACCESS_FUTURE : completablefuture;
        }
    }

    private void a(CompletableFuture<? extends Either<? extends IChunkAccess, PlayerChunk.Failure>> completablefuture) {
        this.chunkSave = this.chunkSave.thenCombine(completablefuture, (ichunkaccess, either) -> {
            return (IChunkAccess) either.map((ichunkaccess1) -> {
                return ichunkaccess1;
            }, (playerchunk_failure) -> {
                return ichunkaccess;
            });
        });
    }

    public ChunkCoordIntPair h() {
        return this.location;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    public int j() {
        return this.n;
    }

    private void d(int i) {
        this.n = i;
    }

    public void a(int i) {
        this.ticketLevel = i;
    }

    protected void a(PlayerChunkMap playerchunkmap) {
        ChunkStatus chunkstatus = getChunkStatus(this.oldTicketLevel);
        ChunkStatus chunkstatus1 = getChunkStatus(this.ticketLevel);
        boolean flag = this.oldTicketLevel <= PlayerChunkMap.GOLDEN_TICKET;
        boolean flag1 = this.ticketLevel <= PlayerChunkMap.GOLDEN_TICKET;
        PlayerChunk.State playerchunk_state = getChunkState(this.oldTicketLevel);
        PlayerChunk.State playerchunk_state1 = getChunkState(this.ticketLevel);
        // CraftBukkit start
        // ChunkUnloadEvent: Called before the chunk is unloaded: isChunkLoaded is still true and chunk can still be modified by plugins.
        if (playerchunk_state.isAtLeast(PlayerChunk.State.BORDER) && !playerchunk_state1.isAtLeast(PlayerChunk.State.BORDER)) {
            this.getStatusFutureUnchecked(ChunkStatus.FULL).thenAccept((either) -> {
                either.ifLeft((chunkAccess) -> {
                    Chunk chunk = (Chunk) chunkAccess;
                    // Minecraft will apply the chunks tick lists to the world once the chunk got loaded, and then store the tick
                    // lists again inside the chunk once the chunk becomes inaccessible and set the chunk's needsSaving flag.
                    // These actions may however happen deferred, so we manually set the needsSaving flag already here.
                    chunk.setNeedsSaving(true);
                    chunk.unloadCallback();
                });
            });
        }
        // CraftBukkit end
        CompletableFuture completablefuture;

        if (flag) {
            Either<IChunkAccess, PlayerChunk.Failure> either = Either.right(new PlayerChunk.Failure() {
                public String toString() {
                    return "Unloaded ticket level " + PlayerChunk.this.location.toString();
                }
            });

            for (int i = flag1 ? chunkstatus1.c() + 1 : 0; i <= chunkstatus.c(); ++i) {
                completablefuture = (CompletableFuture) this.statusFutures.get(i);
                if (completablefuture != null) {
                    completablefuture.complete(either);
                } else {
                    this.statusFutures.set(i, CompletableFuture.completedFuture(either));
                }
            }
        }

        boolean flag2 = playerchunk_state.isAtLeast(PlayerChunk.State.BORDER);
        boolean flag3 = playerchunk_state1.isAtLeast(PlayerChunk.State.BORDER);

        this.hasBeenLoaded |= flag3;
        if (!flag2 && flag3) {
            this.fullChunkFuture = playerchunkmap.b(this);
            this.a(this.fullChunkFuture);
        }

        if (flag2 && !flag3) {
            completablefuture = this.fullChunkFuture;
            this.fullChunkFuture = PlayerChunk.UNLOADED_CHUNK_FUTURE;
            this.a(((CompletableFuture<Either<Chunk, PlayerChunk.Failure>>) completablefuture).thenApply((either1) -> { // CraftBukkit - decompile error
                playerchunkmap.getClass();
                return either1.ifLeft(playerchunkmap::a);
            }));
        }

        boolean flag4 = playerchunk_state.isAtLeast(PlayerChunk.State.TICKING);
        boolean flag5 = playerchunk_state1.isAtLeast(PlayerChunk.State.TICKING);

        if (!flag4 && flag5) {
            this.tickingFuture = playerchunkmap.a(this);
            this.a(this.tickingFuture);
        }

        if (flag4 && !flag5) {
            this.tickingFuture.complete(PlayerChunk.UNLOADED_CHUNK);
            this.tickingFuture = PlayerChunk.UNLOADED_CHUNK_FUTURE;
        }

        boolean flag6 = playerchunk_state.isAtLeast(PlayerChunk.State.ENTITY_TICKING);
        boolean flag7 = playerchunk_state1.isAtLeast(PlayerChunk.State.ENTITY_TICKING);

        if (!flag6 && flag7) {
            if (this.entityTickingFuture != PlayerChunk.UNLOADED_CHUNK_FUTURE) {
                throw new IllegalStateException();
            }

            this.entityTickingFuture = playerchunkmap.b(this.location);
            this.a(this.entityTickingFuture);
        }

        if (flag6 && !flag7) {
            this.entityTickingFuture.complete(PlayerChunk.UNLOADED_CHUNK);
            this.entityTickingFuture = PlayerChunk.UNLOADED_CHUNK_FUTURE;
        }

        this.w.a(this.location, this::j, this.ticketLevel, this::d);
        this.oldTicketLevel = this.ticketLevel;
        // CraftBukkit start
        // ChunkLoadEvent: Called after the chunk is loaded: isChunkLoaded returns true and chunk is ready to be modified by plugins.
        if (!playerchunk_state.isAtLeast(PlayerChunk.State.BORDER) && playerchunk_state1.isAtLeast(PlayerChunk.State.BORDER)) {
            this.getStatusFutureUnchecked(ChunkStatus.FULL).thenAccept((either) -> {
                either.ifLeft((chunkAccess) -> {
                    Chunk chunk = (Chunk) chunkAccess;
                    chunk.loadCallback();

                    // nckk
                    this.getFullChunk().getWorld().getPartitionManager().addChunk(this);
                });
            });
        }
        // CraftBukkit end
    }

    public static ChunkStatus getChunkStatus(int i) {
        return i < 33 ? ChunkStatus.FULL : ChunkStatus.a(i - 33);
    }

    public static PlayerChunk.State getChunkState(int i) {
        return PlayerChunk.CHUNK_STATES[MathHelper.clamp(33 - i + 1, 0, PlayerChunk.CHUNK_STATES.length - 1)];
    }

    public boolean hasBeenLoaded() {
        return this.hasBeenLoaded;
    }

    public void l() {
        this.hasBeenLoaded = getChunkState(this.ticketLevel).isAtLeast(PlayerChunk.State.BORDER);
    }

    public void a(ProtoChunkExtension protochunkextension) {
        for (int i = 0; i < this.statusFutures.length(); ++i) {
            CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = (CompletableFuture) this.statusFutures.get(i);

            if (completablefuture != null) {
                Optional<IChunkAccess> optional = ((Either) completablefuture.getNow(PlayerChunk.UNLOADED_CHUNK_ACCESS)).left();

                if (optional.isPresent() && optional.get() instanceof ProtoChunk) {
                    this.statusFutures.set(i, CompletableFuture.completedFuture(Either.left(protochunkextension)));
                }
            }
        }

        this.a(CompletableFuture.completedFuture(Either.left(protochunkextension.u())));
    }

    public interface d {

        Stream<EntityPlayer> a(ChunkCoordIntPair chunkcoordintpair, boolean flag);
    }

    public interface c {

        void a(ChunkCoordIntPair chunkcoordintpair, IntSupplier intsupplier, int i, IntConsumer intconsumer);
    }

    public interface Failure {

        PlayerChunk.Failure b = new PlayerChunk.Failure() {
            public String toString() {
                return "UNLOADED";
            }
        };
    }

    public static enum State {

        INACCESSIBLE, BORDER, TICKING, ENTITY_TICKING;

        private State() {}

        public boolean isAtLeast(PlayerChunk.State playerchunk_state) {
            return this.ordinal() >= playerchunk_state.ordinal();
        }
    }
}
