package net.minecraft.server;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class ChunkProviderServer extends IChunkProvider {

    private static final int b = (int) Math.pow(17.0D, 2.0D);
    private static final List<ChunkStatus> c = ChunkStatus.a(); static final List<ChunkStatus> getPossibleChunkStatuses() { return ChunkProviderServer.c; } // Paper - OBFHELPER
    private final ChunkMapDistance chunkMapDistance;
    public final ChunkGenerator<?> chunkGenerator;
    private final WorldServer world;
    private final Thread serverThread;
    private final LightEngineThreaded lightEngine;
    private final ChunkProviderServer.a serverThreadQueue;
    public final PlayerChunkMap playerChunkMap;
    private final WorldPersistentData worldPersistentData;
    private long lastTickTime;
    public boolean allowMonsters = true;
    public boolean allowAnimals = true;
    private final int cacheSize = 4;
    private final Map<Long, IChunkAccess> chunkMap = new HashMap<>();
    private final Map<Long, ChunkStatus> statusMap = new HashMap<>();
    private final long[] cachePos = new long[cacheSize];
    private final ChunkStatus[] cacheStatus = new ChunkStatus[cacheSize];
    private final IChunkAccess[] cacheChunk = new IChunkAccess[cacheSize];

    public ChunkProviderServer(WorldServer worldserver, File file, DataFixer datafixer, DefinedStructureManager definedstructuremanager, Executor executor, ChunkGenerator<?> chunkgenerator, int i, int j, WorldLoadListener worldloadlistener, Supplier<WorldPersistentData> supplier) {
        this.world = worldserver;
        this.serverThreadQueue = new ChunkProviderServer.a(worldserver);
        this.chunkGenerator = chunkgenerator;
        this.serverThread = Thread.currentThread();
        File file1 = worldserver.getWorldProvider().getDimensionManager().a(file);
        File file2 = new File(file1, "data");

        file2.mkdirs();
        this.worldPersistentData = new WorldPersistentData(file2, datafixer);
        this.playerChunkMap = new PlayerChunkMap(worldserver, file, datafixer, definedstructuremanager, executor, this.serverThreadQueue, this, this.getChunkGenerator(), worldloadlistener, supplier, i, j);
        this.lightEngine = this.playerChunkMap.a();
        this.chunkMapDistance = this.playerChunkMap.e();
        this.clearCache();
    }

    @Override
    public LightEngineThreaded getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private PlayerChunk getChunk(long i) {
        return this.playerChunkMap.getVisibleChunk(i);
    }

    public int b() {
        return this.playerChunkMap.c();
    }

    @Nullable
    @Override
    public IChunkAccess getChunkAt(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        long k = ChunkCoordIntPair.pair(i, j);

        IChunkAccess ichunkaccess;

        if (this.statusMap.get(k) == chunkstatus) {
            ichunkaccess = this.chunkMap.get(k);
            if (ichunkaccess != null) return ichunkaccess;
        }

//        for (int l = 0; l < cacheSize; ++l) {
//            if (k == this.cachePos[l] && chunkstatus == this.cacheStatus[l]) {
//                ichunkaccess = this.cacheChunk[l];
//                if (ichunkaccess != null) { // CraftBukkit - the chunk can become accessible in the meantime TODO for non-null chunks it might also make sense to check that the chunk's state hasn't changed in the meantime
//                    return ichunkaccess;
//                }
//            }
//        }

        Supplier<IChunkAccess> chunkSupplier = () -> {
            world.timings.syncChunkLoadTimer.startTiming(); // Spigot
            CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = this.getChunkFutureMainThread(i, j, chunkstatus, flag);
            this.serverThreadQueue.awaitTasks(completablefuture::isDone);
            world.timings.syncChunkLoadTimer.stopTiming(); // Spigot

            return (IChunkAccess) ((Either) completablefuture.join()).map((ichunkaccess1) -> {
                this.statusMap.merge(k, chunkstatus, (oldValue, newValue) -> chunkstatus);
                this.chunkMap.merge(k, (IChunkAccess)ichunkaccess1, (oldValue, newValue) -> newValue);
                return ichunkaccess1;
            }, (playerchunk_failure) -> {
                if (flag) {
                    throw new IllegalStateException("Chunk not there when requested: " + playerchunk_failure);
                } else {
                    return null;
                }
            });
        };

        if (Thread.currentThread() != this.serverThread) {
            ichunkaccess = CompletableFuture.supplyAsync(chunkSupplier, this.serverThreadQueue).join();
        } else {
            ichunkaccess = chunkSupplier.get();
        }

//        for (int i1 = cacheSize-1; i1 > 0; --i1) {
//            this.cachePos[i1] = this.cachePos[i1 - 1];
//            this.cacheStatus[i1] = this.cacheStatus[i1 - 1];
//            this.cacheChunk[i1] = this.cacheChunk[i1 - 1];
//        }
//
//        this.cachePos[0] = k;
//        this.cacheStatus[0] = chunkstatus;
//        this.cacheChunk[0] = ichunkaccess;
        return ichunkaccess;
    }

    private void clearCache() {
        Arrays.fill(this.cachePos, ChunkCoordIntPair.a);
        Arrays.fill(this.cacheStatus, (Object) null);
        Arrays.fill(this.cacheChunk, (Object) null);
    }

    private CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> getChunkFutureMainThread(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(i, j);
        long k = chunkcoordintpair.pair();
        int l = 33 + ChunkStatus.a(chunkstatus);
        PlayerChunk playerchunk = this.getChunk(k);

        // CraftBukkit start - don't add new ticket for currently unloading chunk
        boolean currentlyUnloading = false;
        if (playerchunk != null) {
            PlayerChunk.State oldChunkState = PlayerChunk.getChunkState(playerchunk.oldTicketLevel);
            PlayerChunk.State currentChunkState = PlayerChunk.getChunkState(playerchunk.getTicketLevel());
            currentlyUnloading = (oldChunkState.isAtLeast(PlayerChunk.State.BORDER) && !currentChunkState.isAtLeast(PlayerChunk.State.BORDER));
        }
        if (flag && !currentlyUnloading) {
            // CraftBukkit end
            this.chunkMapDistance.a(TicketType.UNKNOWN, chunkcoordintpair, l, chunkcoordintpair);
            if (this.a(playerchunk, l)) {
                GameProfilerFiller gameprofilerfiller = this.world.getMethodProfiler();

                gameprofilerfiller.enter("chunkLoad");
                this.tickDistanceManager();
                playerchunk = this.getChunk(k);
                gameprofilerfiller.exit();
                if (this.a(playerchunk, l)) {
                    throw new IllegalStateException("No chunk holder after ticket has been added");
                }
            }
        }

        return this.a(playerchunk, l) ? PlayerChunk.UNLOADED_CHUNK_ACCESS_FUTURE : playerchunk.a(chunkstatus, this.playerChunkMap);
    }

    private boolean a(@Nullable PlayerChunk playerchunk, int i) {
        return playerchunk == null || playerchunk.oldTicketLevel > i; // CraftBukkit using oldTicketLevel for isLoaded checks
    }

    public boolean isLoaded(int i, int j) {
        PlayerChunk playerchunk = this.getChunk((new ChunkCoordIntPair(i, j)).pair());
        int k = 33 + ChunkStatus.a(ChunkStatus.FULL);

        return !this.a(playerchunk, k);
    }

    @Override
    public IBlockAccess b(int i, int j) {
        long k = ChunkCoordIntPair.pair(i, j);
        PlayerChunk playerchunk = this.getChunk(k);

        if (playerchunk == null) {
            return null;
        } else {
            int l = ChunkProviderServer.c.size() - 1;

            while (true) {
                ChunkStatus chunkstatus = (ChunkStatus) ChunkProviderServer.c.get(l);
                Optional<IChunkAccess> optional = ((Either) playerchunk.getStatusFutureUnchecked(chunkstatus).getNow(PlayerChunk.UNLOADED_CHUNK_ACCESS)).left();

                if (optional.isPresent()) {
                    return (IBlockAccess) optional.get();
                }

                if (chunkstatus == ChunkStatus.LIGHT.e()) {
                    return null;
                }

                --l;
            }
        }
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    public boolean runTasks() {
        return this.serverThreadQueue.executeNext();
    }

    private boolean tickDistanceManager() {
        boolean flag = this.chunkMapDistance.a(this.playerChunkMap);
        boolean flag1 = this.playerChunkMap.b();

        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCache();
            return true;
        }
    }

    @Override
    public boolean a(Entity entity) {
        long i = ChunkCoordIntPair.pair(MathHelper.floor(entity.locX) >> 4, MathHelper.floor(entity.locZ) >> 4);

        return this.a(i, PlayerChunk::b);
    }

    @Override
    public boolean a(ChunkCoordIntPair chunkcoordintpair) {
        return this.a(chunkcoordintpair.pair(), PlayerChunk::b);
    }

    @Override
    public boolean a(BlockPosition blockposition) {
        long i = ChunkCoordIntPair.pair(blockposition.getX() >> 4, blockposition.getZ() >> 4);

        return this.a(i, PlayerChunk::a);
    }

    private boolean a(long i, Function<PlayerChunk, CompletableFuture<Either<Chunk, PlayerChunk.Failure>>> function) {
        PlayerChunk playerchunk = this.getChunk(i);

        if (playerchunk == null) {
            return false;
        } else {
            Either<Chunk, PlayerChunk.Failure> either = (Either) ((CompletableFuture) function.apply(playerchunk)).getNow(PlayerChunk.UNLOADED_CHUNK);

            return either.left().isPresent();
        }
    }

    public void save(boolean flag) {
        this.tickDistanceManager();
        try (co.aikar.timings.Timing timed = world.timings.chunkSaveData.startTiming()) { // Paper - Timings
        this.playerChunkMap.save(flag);
        } // Paper - Timings
    }

    @Override
    public void close() throws IOException {
        this.save(true);
        this.lightEngine.close();
        this.playerChunkMap.close();
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        this.world.getMethodProfiler().enter("purge");
        this.chunkMapDistance.purgeTickets();
        this.tickDistanceManager();
        this.world.getMethodProfiler().exitEnter("unload");
        this.playerChunkMap.unloadChunks(() -> true);
        this.world.getMethodProfiler().exit();
        this.clearCache();
    }
    // CraftBukkit end

    public void tick(BooleanSupplier booleansupplier) {
        this.world.getMethodProfiler().enter("purge");
        this.world.timings.doChunkMap.startTiming(); // Spigot
        this.chunkMapDistance.purgeTickets();
        this.tickDistanceManager();
        this.world.timings.doChunkMap.stopTiming(); // Spigot
        this.world.getMethodProfiler().exitEnter("chunks");
        this.tickChunks();
        this.world.timings.doChunkUnload.startTiming(); // Spigot
        this.world.getMethodProfiler().exitEnter("unload");
        this.playerChunkMap.unloadChunks(booleansupplier);
        this.world.timings.doChunkUnload.stopTiming(); // Spigot
        this.world.getMethodProfiler().exit();
        this.clearCache();
    }

    public void tickPartition(int index, PartitionManager partitionManager) {
        partitionManager.tickPartition(index, world, playerChunkMap, allowAnimals, allowMonsters);
    }

    private void tickChunks() {
        long i = this.world.getTime();
        long j = i - this.lastTickTime;

        this.lastTickTime = i;
        WorldData worlddata = this.world.getWorldData();
        boolean flag = worlddata.getType() == WorldType.DEBUG_ALL_BLOCK_STATES;
        boolean flag1 = this.world.getGameRules().getBoolean("doMobSpawning") && !world.getPlayers().isEmpty(); // CraftBukkit

        if (!flag) {
//            this.world.getMethodProfiler().enter("pollingChunks");
//            int k = this.world.getGameRules().c("randomTickSpeed");
//            BlockPosition blockposition = this.world.getSpawn();
//            boolean flag2 = world.ticksPerAnimalSpawns != 0L && worlddata.getTime() % world.ticksPerAnimalSpawns == 0L; // CraftBukkit // PAIL: TODO monster ticks
//
//            this.world.getMethodProfiler().enter("naturalSpawnCount");
//            int l = this.chunkMapDistance.b();
//            EnumCreatureType[] aenumcreaturetype = EnumCreatureType.values();
//            Object2IntMap<EnumCreatureType> object2intmap = this.world.l();
//
//            this.world.getMethodProfiler().exit();
//            ObjectBidirectionalIterator objectbidirectionaliterator = this.playerChunkMap.f();
//
//            while (objectbidirectionaliterator.hasNext()) {
//                Entry<PlayerChunk> entry = (Entry) objectbidirectionaliterator.next();
//                PlayerChunk playerchunk = (PlayerChunk) entry.getValue();
//                Optional<Chunk> optional = ((Either) playerchunk.b().getNow(PlayerChunk.UNLOADED_CHUNK)).left();
//
//                if (optional.isPresent()) {
//                    Chunk chunk = (Chunk) optional.get();
//
//                    this.world.getMethodProfiler().enter("broadcast");
//                    playerchunk.a(chunk);
//                    this.world.getMethodProfiler().exit();
//                    ChunkCoordIntPair chunkcoordintpair = playerchunk.h();
//
//                    if (!this.playerChunkMap.isOutsideOfRange(chunkcoordintpair, false)) { // Spigot
//                        chunk.b(chunk.q() + j);
//                        if (flag1 && (this.allowMonsters || this.allowAnimals) && this.world.getWorldBorder().isInBounds(chunk.getPos()) && !this.playerChunkMap.isOutsideOfRange(chunkcoordintpair, true)) { // Spigot
//                            this.world.getMethodProfiler().enter("spawner");
//                            this.world.timings.mobSpawn.startTiming(); // Spigot
//                            EnumCreatureType[] aenumcreaturetype1 = aenumcreaturetype;
//                            int i1 = aenumcreaturetype.length;
//
//                            for (int j1 = 0; j1 < i1; ++j1) {
//                                EnumCreatureType enumcreaturetype = aenumcreaturetype1[j1];
//
//                                // CraftBukkit start - Use per-world spawn limits
//                                int limit = enumcreaturetype.b();
//                                switch (enumcreaturetype) {
//                                    case MONSTER:
//                                        limit = world.getWorld().getMonsterSpawnLimit();
//                                        break;
//                                    case CREATURE:
//                                        limit = world.getWorld().getAnimalSpawnLimit();
//                                        break;
//                                    case WATER_CREATURE:
//                                        limit = world.getWorld().getWaterAnimalSpawnLimit();
//                                        break;
//                                    case AMBIENT:
//                                        limit = world.getWorld().getAmbientSpawnLimit();
//                                        break;
//                                }
//
//                                if (limit == 0) {
//                                    continue;
//                                }
//                                // CraftBukkit end
//
//                                if (enumcreaturetype != EnumCreatureType.MISC && (!enumcreaturetype.c() || this.allowAnimals) && (enumcreaturetype.c() || this.allowMonsters) && (!enumcreaturetype.d() || flag2)) {
//                                    int k1 = limit * l / ChunkProviderServer.b; // CraftBukkit - use per-world limits
//
//                                    if (object2intmap.getInt(enumcreaturetype) <= k1) {
//                                        SpawnerCreature.a(enumcreaturetype, (World) this.world, chunk, blockposition);
//                                    }
//                                }
//                            }
//
//                            this.world.timings.mobSpawn.stopTiming(); // Spigot
//                            this.world.getMethodProfiler().exit();
//                        }
//
//                        this.world.timings.chunkTicks.startTiming(); // Spigot
//                        this.world.a(chunk, k);
//                        this.world.timings.chunkTicks.stopTiming(); // Spigot
//                    }
//                }
//            }

            this.world.getMethodProfiler().enter("customSpawners");
            if (flag1) {
                this.chunkGenerator.doMobSpawning(this.world, this.allowMonsters, this.allowAnimals);
            }

            this.world.getMethodProfiler().exit();
//            this.world.getMethodProfiler().exit();
        }

        this.playerChunkMap.g();
    }

    @Override
    public String getName() {
        return "ServerChunkCache: " + this.g();
    }

    @Override
    public ChunkGenerator<?> getChunkGenerator() {
        return this.chunkGenerator;
    }

    public int g() {
        return this.playerChunkMap.d();
    }

    public void flagDirty(BlockPosition blockposition) {
        int i = blockposition.getX() >> 4;
        int j = blockposition.getZ() >> 4;
        PlayerChunk playerchunk = this.getChunk(ChunkCoordIntPair.pair(i, j));

        if (playerchunk != null) {
            playerchunk.a(blockposition.getX() & 15, blockposition.getY(), blockposition.getZ() & 15);
        }

    }

    @Override
    public void a(EnumSkyBlock enumskyblock, SectionPosition sectionposition) {
        this.serverThreadQueue.execute(() -> {
            PlayerChunk playerchunk = this.getChunk(sectionposition.u().pair());

            if (playerchunk != null) {
                playerchunk.a(enumskyblock, sectionposition.b());
            }

        });
    }

    public <T> void addTicket(TicketType<T> tickettype, ChunkCoordIntPair chunkcoordintpair, int i, T t0) {
        this.chunkMapDistance.addTicket(tickettype, chunkcoordintpair, i, t0);
    }

    public <T> void removeTicket(TicketType<T> tickettype, ChunkCoordIntPair chunkcoordintpair, int i, T t0) {
        this.chunkMapDistance.removeTicket(tickettype, chunkcoordintpair, i, t0);
    }

    @Override
    public void a(ChunkCoordIntPair chunkcoordintpair, boolean flag) {
        this.chunkMapDistance.a(chunkcoordintpair, flag);
    }

    public void movePlayer(EntityPlayer entityplayer) {
        this.playerChunkMap.movePlayer(entityplayer);
    }

    public void removeEntity(Entity entity) {
        this.playerChunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.playerChunkMap.addEntity(entity);
    }

    public void broadcastIncludingSelf(Entity entity, Packet<?> packet) {
        this.playerChunkMap.broadcastIncludingSelf(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.playerChunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int i, int j) {
        this.playerChunkMap.setViewDistance(i, j);
    }

    @Override
    public void a(boolean flag, boolean flag1) {
        this.allowMonsters = flag;
        this.allowAnimals = flag1;
    }

    public WorldPersistentData getWorldPersistentData() {
        return this.worldPersistentData;
    }

    public VillagePlace i() {
        return this.playerChunkMap.h();
    }

    final class a extends IAsyncTaskHandler<Runnable> {

        private a(World world) {
            super("Chunk source main thread executor for " + IRegistry.DIMENSION_TYPE.getKey(world.getWorldProvider().getDimensionManager()));
        }

        @Override
        protected Runnable postToMainThread(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean canExecute(Runnable runnable) {
            return true;
        }

        @Override
        protected boolean isNotMainThread() {
            return true;
        }

        @Override
        protected Thread getThread() {
            return ChunkProviderServer.this.serverThread;
        }

        @Override
        protected boolean executeNext() {
            if (ChunkProviderServer.this.tickDistanceManager()) {
                return true;
            } else {
                ChunkProviderServer.this.lightEngine.queueUpdate();
                return super.executeNext();
            }
        }
    }
}
