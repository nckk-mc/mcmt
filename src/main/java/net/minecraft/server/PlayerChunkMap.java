package net.minecraft.server;

import com.destroystokyo.paper.PaperWorldConfig; // Paper
import com.google.common.collect.ImmutableList;
import co.aikar.timings.Timing;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap; // Paper
import java.util.Iterator;
import java.util.List;
import java.util.Map; // Paper
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID; // Paper
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.entity.Player; // CraftBukkit

public class PlayerChunkMap extends IChunkLoader implements PlayerChunk.d {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final int GOLDEN_TICKET = 33 + ChunkStatus.b();
    public final Long2ObjectLinkedOpenHashMap<PlayerChunk> updatingChunks = new Long2ObjectLinkedOpenHashMap();
    public volatile Long2ObjectLinkedOpenHashMap<PlayerChunk> visibleChunks;
    private final Long2ObjectLinkedOpenHashMap<PlayerChunk> pendingUnload;
    private final LongSet loadedChunks;
    public final WorldServer world;
    private final LightEngineThreaded lightEngine;
    private final IAsyncTaskHandler<Runnable> executor;
    public final ChunkGenerator<?> chunkGenerator;
    private final Supplier<WorldPersistentData> m;
    private final VillagePlace n;
    public final LongSet unloadQueue;
    private boolean updatingChunksModified;
    private final ChunkTaskQueueSorter q;
    private final Mailbox<ChunkTaskQueueSorter.a<Runnable>> mailboxWorldGen;
    private final Mailbox<ChunkTaskQueueSorter.a<Runnable>> mailboxMain;
    public final WorldLoadListener worldLoadListener;
    private final PlayerChunkMap.a u;
    private final AtomicInteger v;
    private final DefinedStructureManager definedStructureManager;
    private final File x;
    private final PlayerMap playerMap;
    public final Int2ObjectMap<PlayerChunkMap.EntityTracker> trackedEntities;
    private final Queue<Runnable> A;
    private int viewDistance;
    private int entityDistance;

    public PlayerChunkMap(WorldServer worldserver, File file, DataFixer datafixer, DefinedStructureManager definedstructuremanager, Executor executor, IAsyncTaskHandler<Runnable> iasynctaskhandler, ILightAccess ilightaccess, ChunkGenerator<?> chunkgenerator, WorldLoadListener worldloadlistener, Supplier<WorldPersistentData> supplier, int i, int j) {
        super(new File(worldserver.getWorldProvider().getDimensionManager().a(file), "region"), datafixer);
        this.visibleChunks = this.updatingChunks.clone();
        this.pendingUnload = new Long2ObjectLinkedOpenHashMap();
        this.loadedChunks = new LongOpenHashSet();
        this.unloadQueue = new LongOpenHashSet();
        this.v = new AtomicInteger();
        this.playerMap = new PlayerMap();
        this.trackedEntities = new Int2ObjectOpenHashMap();
        this.A = new com.destroystokyo.paper.utils.CachedSizeConcurrentLinkedQueue<>(); // Paper
        this.definedStructureManager = definedstructuremanager;
        this.x = worldserver.getWorldProvider().getDimensionManager().a(file);
        this.world = worldserver;
        this.chunkGenerator = chunkgenerator;
        this.executor = iasynctaskhandler;
        ThreadedMailbox<Runnable> threadedmailbox = ThreadedMailbox.a(executor, "worldgen");
        ThreadedMailbox<Runnable> threadedmailbox1 = ThreadedMailbox.a((Executor) iasynctaskhandler, "main");

        this.worldLoadListener = worldloadlistener;
        ThreadedMailbox<Runnable> threadedmailbox2 = ThreadedMailbox.a(executor, "light");

        this.q = new ChunkTaskQueueSorter(ImmutableList.of(threadedmailbox, threadedmailbox1, threadedmailbox2), executor, Integer.MAX_VALUE);
        this.mailboxWorldGen = this.q.a(threadedmailbox, false);
        this.mailboxMain = this.q.a(threadedmailbox1, false);
        this.lightEngine = new LightEngineThreaded(ilightaccess, this, this.world.getWorldProvider().g(), threadedmailbox2, this.q.a(threadedmailbox2, false));
        this.u = new PlayerChunkMap.a(executor, iasynctaskhandler);
        this.m = supplier;
        this.n = new VillagePlace(new File(this.x, "poi"), datafixer);
        this.setViewDistance(i, j);
    }

    private static double a(ChunkCoordIntPair chunkcoordintpair, Entity entity) {
        double d0 = (double) (chunkcoordintpair.x * 16 + 8);
        double d1 = (double) (chunkcoordintpair.z * 16 + 8);
        double d2 = d0 - entity.locX;
        double d3 = d1 - entity.locZ;

        return d2 * d2 + d3 * d3;
    }

    private static int b(ChunkCoordIntPair chunkcoordintpair, EntityPlayer entityplayer, boolean flag) {
        int i;
        int j;

        if (flag) {
            SectionPosition sectionposition = entityplayer.M();

            i = sectionposition.a();
            j = sectionposition.c();
        } else {
            i = MathHelper.floor(entityplayer.locX / 16.0D);
            j = MathHelper.floor(entityplayer.locZ / 16.0D);
        }

        return a(chunkcoordintpair, i, j);
    }

    private static int a(ChunkCoordIntPair chunkcoordintpair, int i, int j) {
        int k = chunkcoordintpair.x - i;
        int l = chunkcoordintpair.z - j;

        return Math.max(Math.abs(k), Math.abs(l));
    }

    protected LightEngineThreaded a() {
        return this.lightEngine;
    }

    @Nullable
    protected PlayerChunk getUpdatingChunk(long i) {
        return (PlayerChunk) this.updatingChunks.get(i);
    }

    @Nullable
    protected PlayerChunk getVisibleChunk(long i) {
        return (PlayerChunk) this.visibleChunks.get(i);
    }

    protected IntSupplier c(long i) {
        return () -> {
            PlayerChunk playerchunk = this.getVisibleChunk(i);

            return playerchunk == null ? ChunkTaskQueue.a - 1 : Math.min(playerchunk.j(), ChunkTaskQueue.a - 1);
        };
    }

    private CompletableFuture<Either<List<IChunkAccess>, PlayerChunk.Failure>> a(ChunkCoordIntPair chunkcoordintpair, int i, IntFunction<ChunkStatus> intfunction) {
        List<CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>>> list = Lists.newArrayList();
        int j = chunkcoordintpair.x;
        int k = chunkcoordintpair.z;

        for (int l = -i; l <= i; ++l) {
            for (int i1 = -i; i1 <= i; ++i1) {
                int j1 = Math.max(Math.abs(i1), Math.abs(l));
                final ChunkCoordIntPair chunkcoordintpair1 = new ChunkCoordIntPair(j + i1, k + l);
                long k1 = chunkcoordintpair1.pair();
                PlayerChunk playerchunk = this.getUpdatingChunk(k1);

                if (playerchunk == null) {
                    return CompletableFuture.completedFuture(Either.right(new PlayerChunk.Failure() {
                        public String toString() {
                            return "Unloaded " + chunkcoordintpair1.toString();
                        }
                    }));
                }

                ChunkStatus chunkstatus = (ChunkStatus) intfunction.apply(j1);
                CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = playerchunk.a(chunkstatus, this);

                list.add(completablefuture);
            }
        }

        CompletableFuture<List<Either<IChunkAccess, PlayerChunk.Failure>>> completablefuture1 = SystemUtils.b(list);

        return completablefuture1.thenApply((list1) -> {
            List<IChunkAccess> list2 = Lists.newArrayList();
            // CraftBukkit start - decompile error
            int cnt = 0;

            for (Iterator iterator = list1.iterator(); iterator.hasNext(); ++cnt) {
                final int l1 = cnt;
                // CraftBukkit end
                final Either<IChunkAccess, PlayerChunk.Failure> either = (Either) iterator.next();
                Optional<IChunkAccess> optional = either.left();

                if (!optional.isPresent()) {
                    return Either.right(new PlayerChunk.Failure() {
                        public String toString() {
                            return "Unloaded " + new ChunkCoordIntPair(j + l1 % (i * 2 + 1), k + l1 / (i * 2 + 1)) + " " + ((PlayerChunk.Failure) either.right().get()).toString();
                        }
                    });
                }

                list2.add(optional.get());
            }

            return Either.left(list2);
        });
    }

    public CompletableFuture<Either<Chunk, PlayerChunk.Failure>> b(ChunkCoordIntPair chunkcoordintpair) {
        return this.a(chunkcoordintpair, 2, (i) -> {
            return ChunkStatus.FULL;
        }).thenApplyAsync((either) -> {
            return either.mapLeft((list) -> {
                return (Chunk) list.get(list.size() / 2);
            });
        }, this.executor);
    }

    @Nullable
    private PlayerChunk a(long i, int j, @Nullable PlayerChunk playerchunk, int k) {
        if (k > PlayerChunkMap.GOLDEN_TICKET && j > PlayerChunkMap.GOLDEN_TICKET) {
            return playerchunk;
        } else {
            if (playerchunk != null) {
                playerchunk.a(j);
            }

            if (playerchunk != null) {
                if (j > PlayerChunkMap.GOLDEN_TICKET) {
                    this.unloadQueue.add(i);
                } else {
                    this.unloadQueue.remove(i);
                }
            }

            if (j <= PlayerChunkMap.GOLDEN_TICKET && playerchunk == null) {
                playerchunk = (PlayerChunk) this.pendingUnload.remove(i);
                if (playerchunk != null) {
                    playerchunk.a(j);
                } else {
                    playerchunk = new PlayerChunk(new ChunkCoordIntPair(i), j, this.lightEngine, this.q, this);
                }

                this.updatingChunks.put(i, playerchunk);
                this.updatingChunksModified = true;
            }

            return playerchunk;
        }
    }

    @Override
    public void close() throws IOException {
        this.q.close();
        this.n.close();
        super.close();
    }

    protected void save(boolean flag) {
        if (flag) {
            List<PlayerChunk> list = (List) this.visibleChunks.values().stream().filter(PlayerChunk::hasBeenLoaded).peek(PlayerChunk::l).collect(Collectors.toList());
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream().map((playerchunk) -> {
                    CompletableFuture completablefuture;

                    do {
                        completablefuture = playerchunk.getChunkSave();
                        this.executor.awaitTasks(completablefuture::isDone);
                    } while (completablefuture != playerchunk.getChunkSave());

                    return (IChunkAccess) completablefuture.join();
                }).filter((ichunkaccess) -> {
                    return ichunkaccess instanceof ProtoChunkExtension || ichunkaccess instanceof Chunk;
                }).filter(this::saveChunk).forEach((ichunkaccess) -> {
                    mutableboolean.setTrue();
                });
            } while (mutableboolean.isTrue());

            this.b(() -> {
                return true;
            });
            PlayerChunkMap.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", this.x.getName());
        } else {
            this.visibleChunks.values().stream().filter(PlayerChunk::hasBeenLoaded).forEach((playerchunk) -> {
                IChunkAccess ichunkaccess = (IChunkAccess) playerchunk.getChunkSave().getNow(null); // CraftBukkit - decompile error

                if (ichunkaccess instanceof ProtoChunkExtension || ichunkaccess instanceof Chunk) {
                    this.saveChunk(ichunkaccess);
                    playerchunk.l();
                }

            });
        }

    }

    private static final double UNLOAD_QUEUE_RESIZE_FACTOR = 0.96; // Spigot

    protected void unloadChunks(BooleanSupplier booleansupplier) {
        GameProfilerFiller gameprofilerfiller = this.world.getMethodProfiler();

        gameprofilerfiller.enter("poi");
        this.n.a(booleansupplier);
        gameprofilerfiller.exitEnter("chunk_unload");
        if (!this.world.isSavingDisabled()) {
            this.b(booleansupplier);
        }

        gameprofilerfiller.exit();
    }

    private void b(BooleanSupplier booleansupplier) {
        LongIterator longiterator = this.unloadQueue.iterator();
        // Spigot start
        org.spigotmc.SlackActivityAccountant activityAccountant = this.world.getMinecraftServer().slackActivityAccountant;
        activityAccountant.startActivity(0.5);
            int targetSize = Math.min(this.unloadQueue.size() - 100,  (int) (this.unloadQueue.size() * UNLOAD_QUEUE_RESIZE_FACTOR)); // Paper - Make more aggressive
        // Spigot end
        while (longiterator.hasNext()) { // Spigot
            long j = longiterator.nextLong();
            longiterator.remove(); // Spigot
            PlayerChunk playerchunk = (PlayerChunk) this.updatingChunks.remove(j);

            if (playerchunk != null) {
                this.pendingUnload.put(j, playerchunk);
                this.updatingChunksModified = true;
                // Spigot start
                if (!booleansupplier.getAsBoolean() && this.unloadQueue.size() <= targetSize && activityAccountant.activityTimeIsExhausted()) {
                    break;
                }
                // Spigot end
                this.a(j, playerchunk);
            }
        }
        activityAccountant.endActivity(); // Spigot

        Runnable runnable;

        int queueTarget = Math.min(this.A.size() - 100, (int) (this.A.size() * UNLOAD_QUEUE_RESIZE_FACTOR)); // Paper - Target this queue as well

        while ((booleansupplier.getAsBoolean() || this.A.size() > queueTarget) && (runnable = (Runnable) this.A.poll()) != null) { // Paper - Target this queue as well
            runnable.run();
        }

    }

    private void a(long i, PlayerChunk playerchunk) {
        CompletableFuture<IChunkAccess> completablefuture = playerchunk.getChunkSave();
        Consumer<IChunkAccess> consumer = (ichunkaccess) -> { // CraftBukkit - decompile error
            CompletableFuture<IChunkAccess> completablefuture1 = playerchunk.getChunkSave();

            if (completablefuture1 != completablefuture) {
                this.a(i, playerchunk);
            } else {
                if (this.pendingUnload.remove(i, playerchunk) && ichunkaccess != null) {
                    if (ichunkaccess instanceof Chunk) {
                        ((Chunk) ichunkaccess).setLoaded(false);
                    }

                    this.saveChunk(ichunkaccess);
                    if (this.loadedChunks.remove(i) && ichunkaccess instanceof Chunk) {
                        Chunk chunk = (Chunk) ichunkaccess;

                        this.world.unloadChunk(chunk);
                    }

                    this.lightEngine.a(ichunkaccess.getPos());
                    this.lightEngine.queueUpdate();
                    this.worldLoadListener.a(ichunkaccess.getPos(), (ChunkStatus) null);
                }

            }
        };
        Queue queue = this.A;

        this.A.getClass();
        completablefuture.thenAcceptAsync(consumer, queue::add).whenComplete((ovoid, throwable) -> {
            if (throwable != null) {
                PlayerChunkMap.LOGGER.error("Failed to save chunk " + playerchunk.h(), throwable);
            }

        });
    }

    protected boolean b() {
        if (!this.updatingChunksModified) {
            return false;
        } else {
            this.visibleChunks = this.updatingChunks.clone();
            this.updatingChunksModified = false;
            return true;
        }
    }

    public CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> a(PlayerChunk playerchunk, ChunkStatus chunkstatus) {
        ChunkCoordIntPair chunkcoordintpair = playerchunk.h();

        if (chunkstatus == ChunkStatus.EMPTY) {
            return this.f(chunkcoordintpair);
        } else {
            CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = playerchunk.a(chunkstatus.e(), this);

            return completablefuture.thenComposeAsync((either) -> {
                Optional<IChunkAccess> optional = either.left();

                if (!optional.isPresent()) {
                    return CompletableFuture.completedFuture(either);
                } else {
                    if (chunkstatus == ChunkStatus.LIGHT) {
                        this.u.a(TicketType.LIGHT, chunkcoordintpair, 33 + ChunkStatus.a(ChunkStatus.FEATURES), chunkcoordintpair);
                    }

                    IChunkAccess ichunkaccess = (IChunkAccess) optional.get();

                    if (ichunkaccess.getChunkStatus().b(chunkstatus)) {
                        CompletableFuture completablefuture1;

                        if (chunkstatus == ChunkStatus.LIGHT) {
                            completablefuture1 = this.b(playerchunk, chunkstatus);
                        } else {
                            completablefuture1 = chunkstatus.a(this.world, this.definedStructureManager, this.lightEngine, (ichunkaccess1) -> {
                                return this.c(playerchunk);
                            }, ichunkaccess);
                        }

                        this.worldLoadListener.a(chunkcoordintpair, chunkstatus);
                        return completablefuture1;
                    } else {
                        return this.b(playerchunk, chunkstatus);
                    }
                }
            }, this.executor);
        }
    }

    private CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> f(ChunkCoordIntPair chunkcoordintpair) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NBTTagCompound nbttagcompound = this.readChunkData(chunkcoordintpair);

                if (nbttagcompound != null) {
                    boolean flag = nbttagcompound.hasKeyOfType("Level", 10) && nbttagcompound.getCompound("Level").hasKeyOfType("Status", 8);

                    if (flag) {
                        ProtoChunk protochunk = ChunkRegionLoader.loadChunk(this.world, this.definedStructureManager, this.n, chunkcoordintpair, nbttagcompound);

                        protochunk.setLastSaved(this.world.getTime());
                        return Either.left(protochunk);
                    }

                    PlayerChunkMap.LOGGER.error("Chunk file at {} is missing level data, skipping", chunkcoordintpair);
                }
            } catch (ReportedException reportedexception) {
                Throwable throwable = reportedexception.getCause();

                if (!(throwable instanceof IOException)) {
                    throw reportedexception;
                }

                PlayerChunkMap.LOGGER.error("Couldn't load chunk {}", chunkcoordintpair, throwable);
            } catch (Exception exception) {
                PlayerChunkMap.LOGGER.error("Couldn't load chunk {}", chunkcoordintpair, exception);
            }

            return Either.left(new ProtoChunk(chunkcoordintpair, ChunkConverter.a));
        }, this.executor);
    }

    private CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> b(PlayerChunk playerchunk, ChunkStatus chunkstatus) {
        ChunkCoordIntPair chunkcoordintpair = playerchunk.h();
        CompletableFuture<Either<List<IChunkAccess>, PlayerChunk.Failure>> completablefuture = this.a(chunkcoordintpair, chunkstatus.f(), (i) -> {
            return this.a(chunkstatus, i);
        });

        return completablefuture.thenComposeAsync((either) -> {
            return either.map((list) -> { // Paper - Shut up.
                try {
                    CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture1 = chunkstatus.a(this.world, this.chunkGenerator, this.definedStructureManager, this.lightEngine, (ichunkaccess) -> {
                        return this.c(playerchunk);
                    }, list);

                    this.worldLoadListener.a(chunkcoordintpair, chunkstatus);
                    return completablefuture1;
                } catch (Exception exception) {
                    CrashReport crashreport = CrashReport.a(exception, "Exception generating new chunk");
                    CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Chunk to be generated");

                    crashreportsystemdetails.a("Location", (Object) String.format("%d,%d", chunkcoordintpair.x, chunkcoordintpair.z));
                    crashreportsystemdetails.a("Position hash", (Object) ChunkCoordIntPair.pair(chunkcoordintpair.x, chunkcoordintpair.z));
                    crashreportsystemdetails.a("Generator", (Object) this.chunkGenerator);
                    throw new ReportedException(crashreport);
                }
            }, (playerchunk_failure) -> {
                this.c(chunkcoordintpair);
                return CompletableFuture.completedFuture(Either.right(playerchunk_failure));
            });
        }, (runnable) -> {
            this.mailboxWorldGen.a(ChunkTaskQueueSorter.a(playerchunk, runnable)); // CraftBukkit - decompile error
        });
    }

    protected void c(ChunkCoordIntPair chunkcoordintpair) {
        this.executor.a(SystemUtils.a(() -> {
            this.u.b(TicketType.LIGHT, chunkcoordintpair, 33 + ChunkStatus.a(ChunkStatus.FEATURES), chunkcoordintpair);
        }, () -> {
            return "release light ticket " + chunkcoordintpair;
        }));
    }

    private ChunkStatus a(ChunkStatus chunkstatus, int i) {
        ChunkStatus chunkstatus1;

        if (i == 0) {
            chunkstatus1 = chunkstatus.e();
        } else {
            chunkstatus1 = ChunkStatus.a(ChunkStatus.a(chunkstatus) + i);
        }

        return chunkstatus1;
    }

    private CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> c(PlayerChunk playerchunk) {
        CompletableFuture<Either<IChunkAccess, PlayerChunk.Failure>> completablefuture = playerchunk.getStatusFutureUnchecked(ChunkStatus.FULL.e());

        return completablefuture.thenApplyAsync((either) -> {
            ChunkStatus chunkstatus = PlayerChunk.getChunkStatus(playerchunk.getTicketLevel());

            return !chunkstatus.b(ChunkStatus.FULL) ? PlayerChunk.UNLOADED_CHUNK_ACCESS : either.mapLeft((ichunkaccess) -> {
                ChunkCoordIntPair chunkcoordintpair = playerchunk.h();
                Chunk chunk;

                if (ichunkaccess instanceof ProtoChunkExtension) {
                    chunk = ((ProtoChunkExtension) ichunkaccess).u();
                } else {
                    chunk = new Chunk(this.world, (ProtoChunk) ichunkaccess);
                    playerchunk.a(new ProtoChunkExtension(chunk));
                }

                chunk.a(() -> {
                    return PlayerChunk.getChunkState(playerchunk.getTicketLevel());
                });
                chunk.addEntities();
                if (this.loadedChunks.add(chunkcoordintpair.pair())) {
                    chunk.setLoaded(true);
                    this.world.a(chunk.getTileEntities().values());
                    List<Entity> list = null;
                    List<Entity>[] aentityslice = chunk.getEntitySlices(); // Spigot
                    int i = aentityslice.length;

                    for (int j = 0; j < i; ++j) {
                        List<Entity> entityslice = aentityslice[j]; // Spigot

                        // Paper start
                        PaperWorldConfig.DuplicateUUIDMode mode = world.paperConfig.duplicateUUIDMode;
                        if (mode == PaperWorldConfig.DuplicateUUIDMode.WARN || mode == PaperWorldConfig.DuplicateUUIDMode.DELETE || mode == PaperWorldConfig.DuplicateUUIDMode.SAFE_REGEN) {
                            Map<UUID, Entity> thisChunk = new HashMap<>();
                            for (Iterator<Entity> iterator = ((List<Entity>) entityslice).iterator(); iterator.hasNext(); ) {
                                Entity entity = iterator.next();
                                if (entity.dead || entity.valid) continue;
                                Entity other = ((WorldServer) world).getEntity(entity.uniqueID);
                                if (other == null || other.dead) {
                                    other = thisChunk.get(entity.uniqueID);
                                }

                                if (mode == PaperWorldConfig.DuplicateUUIDMode.SAFE_REGEN && other != null && !other.dead
                                        && java.util.Objects.equals(other.getSaveID(), entity.getSaveID())
                                        && entity.getBukkitEntity().getLocation().distance(other.getBukkitEntity().getLocation()) < world.paperConfig.duplicateUUIDDeleteRange
                                ) {
                                    if (World.DEBUG_ENTITIES) LOGGER.warn("[DUPE-UUID] Duplicate UUID found used by " + other + ", deleted entity " + entity + " because it was near the duplicate and likely an actual duplicate. See https://github.com/PaperMC/Paper/issues/1223 for discussion on what this is about.");
                                    entity.dead = true;
                                    iterator.remove();
                                    continue;
                                }
                                if (other != null && !other.dead) {
                                    switch (mode) {
                                        case SAFE_REGEN: {
                                            entity.setUUID(UUID.randomUUID());
                                            if (World.DEBUG_ENTITIES) LOGGER.warn("[DUPE-UUID] Duplicate UUID found used by " + other + ", regenerated UUID for " + entity + ". See https://github.com/PaperMC/Paper/issues/1223 for discussion on what this is about.");
                                            break;
                                        }
                                        case DELETE: {
                                            if (World.DEBUG_ENTITIES) LOGGER.warn("[DUPE-UUID] Duplicate UUID found used by " + other + ", deleted entity " + entity + ". See https://github.com/PaperMC/Paper/issues/1223 for discussion on what this is about.");
                                            entity.dead = true;
                                            iterator.remove();
                                            break;
                                        }
                                        default:
                                            if (World.DEBUG_ENTITIES) LOGGER.warn("[DUPE-UUID] Duplicate UUID found used by " + other + ", doing nothing to " + entity + ". See https://github.com/PaperMC/Paper/issues/1223 for discussion on what this is about.");
                                            break;
                                    }
                                }


                            if (!(entity instanceof EntityHuman) && (entity.dead || !this.world.addEntityChunk(entity))) { // Paper
                                if (list == null) {
                                    list = Lists.newArrayList(new Entity[]{entity});
                                } else {
                                    list.add(entity);
                                }
                            }
                        }
                        } // Paper
                    }

                    if (list != null) {
                        list.forEach(chunk::b);
                    }
                }

                return chunk;
            });
        }, (runnable) -> {
            Mailbox mailbox = this.mailboxMain;
            long i = playerchunk.h().pair();

            playerchunk.getClass();
            mailbox.a(ChunkTaskQueueSorter.a(runnable, i, playerchunk::getTicketLevel)); // CraftBukkit - decompile error
        });
    }

    public CompletableFuture<Either<Chunk, PlayerChunk.Failure>> a(PlayerChunk playerchunk) {
        ChunkCoordIntPair chunkcoordintpair = playerchunk.h();
        CompletableFuture<Either<List<IChunkAccess>, PlayerChunk.Failure>> completablefuture = this.a(chunkcoordintpair, 1, (i) -> {
            return ChunkStatus.FULL;
        });
        CompletableFuture<Either<Chunk, PlayerChunk.Failure>> completablefuture1 = completablefuture.thenApplyAsync((either) -> {
            return either.flatMap((list) -> {
                Chunk chunk = (Chunk) list.get(list.size() / 2);

                chunk.A();
                return Either.left(chunk);
            });
        }, (runnable) -> {
            this.mailboxMain.a(ChunkTaskQueueSorter.a(playerchunk, runnable)); // CraftBukkit - decompile error
        });

        completablefuture1.thenAcceptAsync((either) -> {
            either.mapLeft((chunk) -> {
                this.v.getAndIncrement();
                Packet<?>[] apacket = new Packet[2];

                this.a(chunkcoordintpair, false).forEach((entityplayer) -> {
                    this.a(entityplayer, apacket, chunk);
                });
                return Either.left(chunk);
            });
        }, (runnable) -> {
            this.mailboxMain.a(ChunkTaskQueueSorter.a(playerchunk, runnable)); // CraftBukkit - decompile error
        });
        return completablefuture1;
    }

    public CompletableFuture<Either<Chunk, PlayerChunk.Failure>> b(PlayerChunk playerchunk) {
        return playerchunk.a(ChunkStatus.FULL, this).thenApplyAsync((either) -> {
            return either.mapLeft((ichunkaccess) -> {
                Chunk chunk = (Chunk) ichunkaccess;

                chunk.B();
                return chunk;
            });
        }, (runnable) -> {
            this.mailboxMain.a(ChunkTaskQueueSorter.a(playerchunk, runnable)); // CraftBukkit - decompile error
        });
    }

    public int c() {
        return this.v.get();
    }

    public boolean saveChunk(IChunkAccess ichunkaccess) {
        this.n.a(ichunkaccess.getPos());
        if (!ichunkaccess.isNeedsSaving()) {
            return false;
        } else {
            try {
                this.world.checkSession();
            } catch (ExceptionWorldConflict exceptionworldconflict) {
                PlayerChunkMap.LOGGER.error("Couldn't save chunk; already in use by another instance of Minecraft?", exceptionworldconflict);
                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exceptionworldconflict); // Paper
                return false;
            }

            ichunkaccess.setLastSaved(this.world.getTime());
            ichunkaccess.setNeedsSaving(false);
            ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();

            try {
                ChunkStatus chunkstatus = ichunkaccess.getChunkStatus();
                NBTTagCompound nbttagcompound;

                if (chunkstatus.getType() != ChunkStatus.Type.LEVELCHUNK) {
                    nbttagcompound = this.readChunkData(chunkcoordintpair);
                    if (nbttagcompound != null && ChunkRegionLoader.a(nbttagcompound) == ChunkStatus.Type.LEVELCHUNK) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && ichunkaccess.h().values().stream().noneMatch(StructureStart::e)) {
                        return false;
                    }
                }

                nbttagcompound = ChunkRegionLoader.saveChunk(this.world, ichunkaccess);
                this.write(chunkcoordintpair, nbttagcompound);
                return true;
            } catch (Exception exception) {
                PlayerChunkMap.LOGGER.error("Failed to save chunk {},{}", chunkcoordintpair.x, chunkcoordintpair.z, exception);
                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exception); // Paper
                return false;
            }
        }
    }

    protected void setViewDistance(int i, int j) {
        int k = MathHelper.clamp(i + 1, 3, 33);
        int l;

        if (k != this.viewDistance) {
            l = this.viewDistance;
            this.viewDistance = k;
            this.u.b(this.viewDistance);
            ObjectIterator objectiterator = this.updatingChunks.values().iterator();

            while (objectiterator.hasNext()) {
                PlayerChunk playerchunk = (PlayerChunk) objectiterator.next();
                ChunkCoordIntPair chunkcoordintpair = playerchunk.h();
                Packet<?>[] apacket = new Packet[2];

                int finall = l; // CraftBukkit - decompile error
                this.a(chunkcoordintpair, false).forEach((entityplayer) -> {
                    int i1 = b(chunkcoordintpair, entityplayer, true);
                    boolean flag = i1 <= finall; // CraftBukkit - decompile error
                    boolean flag1 = i1 <= this.viewDistance;

                    this.sendChunk(entityplayer, chunkcoordintpair, apacket, flag, flag1);
                });
            }
        }

        l = MathHelper.clamp(j + 1, 1, 16);
        if (l != this.entityDistance) {
            this.entityDistance = l;
            this.u.setEntityDistance(this.entityDistance);
        }

    }

    protected void sendChunk(EntityPlayer entityplayer, ChunkCoordIntPair chunkcoordintpair, Packet<?>[] apacket, boolean flag, boolean flag1) {
        if (entityplayer.world == this.world) {
            if (flag1 && !flag) {
                PlayerChunk playerchunk = this.getVisibleChunk(chunkcoordintpair.pair());

                if (playerchunk != null) {
                    Chunk chunk = playerchunk.getChunk();

                    if (chunk != null) {
                        this.a(entityplayer, apacket, chunk);
                    }

                    PacketDebug.a(this.world, chunkcoordintpair);
                }
            }

            if (!flag1 && flag) {
                entityplayer.a(chunkcoordintpair);
            }

        }
    }

    public int d() {
        return this.visibleChunks.size();
    }

    protected PlayerChunkMap.a e() {
        return this.u;
    }

    protected ObjectBidirectionalIterator<Entry<PlayerChunk>> f() {
        return this.visibleChunks.long2ObjectEntrySet().fastIterator();
    }

    @Nullable
    private NBTTagCompound readChunkData(ChunkCoordIntPair chunkcoordintpair) throws IOException {
        NBTTagCompound nbttagcompound = this.read(chunkcoordintpair);

        return nbttagcompound == null ? null : this.getChunkData(this.world.getWorldProvider().getDimensionManager(), this.m, nbttagcompound, chunkcoordintpair, world); // CraftBukkit
    }

    // Spigot Start
    boolean isOutsideOfRange(ChunkCoordIntPair chunkcoordintpair, boolean reducedRange) {
        int chunkRange = world.spigotConfig.mobSpawnRange;
        chunkRange = (chunkRange > world.spigotConfig.viewDistance) ? (byte) world.spigotConfig.viewDistance : chunkRange;
        chunkRange = (chunkRange > 8) ? 8 : chunkRange;

        final int finalChunkRange = chunkRange; // Paper for lambda below
        //double blockRange = (reducedRange) ? Math.pow(chunkRange << 4, 2) : 16384.0D; // Paper - use from event
        // Spigot end

        return this.playerMap.a(chunkcoordintpair.pair()).noneMatch((entityplayer) -> {
            // Paper start -
            com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent event;
            double blockRange = 16384.0D;
            if (reducedRange) {
                event = new com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent(entityplayer.getBukkitEntity(), (byte) finalChunkRange);
                blockRange = (double) ((event.getSpawnRadius() << 4) * (event.getSpawnRadius() << 4));
                if (event.isCancelled()) return true;
            }

            return (!entityplayer.isSpectator() && a(chunkcoordintpair, (Entity) entityplayer) < blockRange); // Spigot
            // Paper end
        });
    }

    private boolean b(EntityPlayer entityplayer) {
        return entityplayer.isSpectator() && !this.world.getGameRules().getBoolean("spectatorsGenerateChunks");
    }

    void a(EntityPlayer entityplayer, boolean flag) {
        boolean flag1 = this.b(entityplayer);
        boolean flag2 = this.playerMap.c(entityplayer);
        int i = MathHelper.floor(entityplayer.locX) >> 4;
        int j = MathHelper.floor(entityplayer.locZ) >> 4;

        if (flag) {
            this.playerMap.a(ChunkCoordIntPair.pair(i, j), entityplayer, flag1);
            if (!flag1) {
                this.u.a(SectionPosition.a((Entity) entityplayer), entityplayer);
            }
        } else {
            SectionPosition sectionposition = entityplayer.M();

            this.playerMap.a(sectionposition.u().pair(), entityplayer);
            if (!flag1) {
                this.u.b(sectionposition, entityplayer);
            }
        }

        for (int k = i - this.viewDistance; k <= i + this.viewDistance; ++k) {
            for (int l = j - this.viewDistance; l <= j + this.viewDistance; ++l) {
                ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(k, l);

                this.sendChunk(entityplayer, chunkcoordintpair, new Packet[2], !flag && !flag2, flag && !flag1);
            }
        }

    }

    public void movePlayer(EntityPlayer entityplayer) {
        ObjectIterator objectiterator = this.trackedEntities.values().iterator();

        while (objectiterator.hasNext()) {
            PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();

            if (playerchunkmap_entitytracker.tracker == entityplayer) {
                playerchunkmap_entitytracker.track(this.world.getPlayers());
            } else {
                playerchunkmap_entitytracker.updatePlayer(entityplayer);
            }
        }

        int i = MathHelper.floor(entityplayer.locX) >> 4;
        int j = MathHelper.floor(entityplayer.locZ) >> 4;
        SectionPosition sectionposition = entityplayer.M();
        SectionPosition sectionposition1 = SectionPosition.a((Entity) entityplayer);
        long k = sectionposition.u().pair();
        long l = sectionposition1.u().pair();
        boolean flag = this.playerMap.c(entityplayer);
        boolean flag1 = this.b(entityplayer);
        boolean flag2 = sectionposition.v() != sectionposition1.v();

        if (flag2 || flag != flag1) {
            if (!flag && flag1 || flag2) {
                this.u.b(sectionposition, entityplayer);
            }

            if (flag && !flag1 || flag2) {
                this.u.a(sectionposition1, entityplayer);
            }

            if (!flag && flag1) {
                this.playerMap.a(entityplayer);
            }

            if (flag && !flag1) {
                this.playerMap.b(entityplayer);
            }

            if (k != l) {
                this.playerMap.a(k, l, entityplayer);
            }

            int i1 = sectionposition.a();
            int j1 = sectionposition.c();
            int k1;
            int l1;

            if (Math.abs(i1 - i) <= this.viewDistance * 2 && Math.abs(j1 - j) <= this.viewDistance * 2) {
                k1 = Math.min(i, i1) - this.viewDistance;
                l1 = Math.min(j, j1) - this.viewDistance;
                int i2 = Math.max(i, i1) + this.viewDistance;
                int j2 = Math.max(j, j1) + this.viewDistance;

                for (int k2 = k1; k2 <= i2; ++k2) {
                    for (int l2 = l1; l2 <= j2; ++l2) {
                        ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(k2, l2);
                        boolean flag3 = !flag && a(chunkcoordintpair, i1, j1) <= this.viewDistance;
                        boolean flag4 = !flag1 && a(chunkcoordintpair, i, j) <= this.viewDistance;

                        this.sendChunk(entityplayer, chunkcoordintpair, new Packet[2], flag3, flag4);
                    }
                }
            } else {
                ChunkCoordIntPair chunkcoordintpair1;
                boolean flag5;
                boolean flag6;

                for (k1 = i1 - this.viewDistance; k1 <= i1 + this.viewDistance; ++k1) {
                    for (l1 = j1 - this.viewDistance; l1 <= j1 + this.viewDistance; ++l1) {
                        chunkcoordintpair1 = new ChunkCoordIntPair(k1, l1);
                        flag5 = !flag;
                        flag6 = false;
                        this.sendChunk(entityplayer, chunkcoordintpair1, new Packet[2], flag5, false);
                    }
                }

                for (k1 = i - this.viewDistance; k1 <= i + this.viewDistance; ++k1) {
                    for (l1 = j - this.viewDistance; l1 <= j + this.viewDistance; ++l1) {
                        chunkcoordintpair1 = new ChunkCoordIntPair(k1, l1);
                        flag5 = false;
                        flag6 = !flag1;
                        this.sendChunk(entityplayer, chunkcoordintpair1, new Packet[2], false, flag6);
                    }
                }
            }

        }
    }

    @Override
    public Stream<EntityPlayer> a(ChunkCoordIntPair chunkcoordintpair, boolean flag) {
        return this.playerMap.a(chunkcoordintpair.pair()).filter((entityplayer) -> {
            int i = b(chunkcoordintpair, entityplayer, true);

            return i > this.viewDistance ? false : !flag || i == this.viewDistance;
        });
    }

    protected void addEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp( "entity track"); // Spigot
        if (!(entity instanceof EntityComplexPart)) {
            if (!(entity instanceof EntityLightning)) {
                EntityTypes<?> entitytypes = entity.getEntityType();
                int i = entitytypes.getChunkRange() * 16;
                i = org.spigotmc.TrackingRange.getEntityTrackingRange(entity, i); // Spigot
                int j = entitytypes.getUpdateInterval();

                if (this.trackedEntities.containsKey(entity.getId())) {
                    throw new IllegalStateException("Entity is already tracked!");
                } else {
                    PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = new PlayerChunkMap.EntityTracker(entity, i, j, entitytypes.isDeltaTracking());

                    entity.tracker = playerchunkmap_entitytracker; // Paper - Fast access to tracker
                    this.trackedEntities.put(entity.getId(), playerchunkmap_entitytracker);
                    playerchunkmap_entitytracker.track(this.world.getPlayers());
                    if (entity instanceof EntityPlayer) {
                        EntityPlayer entityplayer = (EntityPlayer) entity;

                        this.a(entityplayer, true);
                        ObjectIterator objectiterator = this.trackedEntities.values().iterator();

                        while (objectiterator.hasNext()) {
                            PlayerChunkMap.EntityTracker playerchunkmap_entitytracker1 = (PlayerChunkMap.EntityTracker) objectiterator.next();

                            if (playerchunkmap_entitytracker1.tracker != entityplayer) {
                                playerchunkmap_entitytracker1.updatePlayer(entityplayer);
                            }
                        }
                    }

                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        org.spigotmc.AsyncCatcher.catchOp( "entity untrack"); // Spigot
        if (entity instanceof EntityPlayer) {
            EntityPlayer entityplayer = (EntityPlayer) entity;

            this.a(entityplayer, false);
            ObjectIterator objectiterator = this.trackedEntities.values().iterator();

            while (objectiterator.hasNext()) {
                PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();

                playerchunkmap_entitytracker.clear(entityplayer);
            }
        }

        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker1 = (PlayerChunkMap.EntityTracker) this.trackedEntities.remove(entity.getId());

        if (playerchunkmap_entitytracker1 != null) {
            playerchunkmap_entitytracker1.a();
        }
        entity.tracker = null; // Paper - We're no longer tracked
    }

    protected void g() {
        List<EntityPlayer> list = Lists.newArrayList();
        List<EntityPlayer> list1 = this.world.getPlayers();

        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker;
        ObjectIterator objectiterator;
        world.timings.tracker1.startTiming(); // Paper

        for (objectiterator = this.trackedEntities.values().iterator(); objectiterator.hasNext(); playerchunkmap_entitytracker.trackerEntry.a()) {
            playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();
            SectionPosition sectionposition = playerchunkmap_entitytracker.e;
            SectionPosition sectionposition1 = SectionPosition.a(playerchunkmap_entitytracker.tracker);

            if (!Objects.equals(sectionposition, sectionposition1)) {
                playerchunkmap_entitytracker.track(list1);
                Entity entity = playerchunkmap_entitytracker.tracker;

                if (entity instanceof EntityPlayer) {
                    list.add((EntityPlayer) entity);
                }

                playerchunkmap_entitytracker.e = sectionposition1;
            }
        }
        world.timings.tracker1.stopTiming(); // Paper

        objectiterator = this.trackedEntities.values().iterator();

        world.timings.tracker2.startTiming(); // Paper
        while (objectiterator.hasNext()) {
            playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();
            playerchunkmap_entitytracker.track(list);
        }
        world.timings.tracker2.stopTiming(); // Paper

    }

    protected void broadcast(Entity entity, Packet<?> packet) {
        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) this.trackedEntities.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcast(packet);
        }

    }

    protected void broadcastIncludingSelf(Entity entity, Packet<?> packet) {
        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) this.trackedEntities.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcastIncludingSelf(packet);
        }

    }

    private void a(EntityPlayer entityplayer, Packet<?>[] apacket, Chunk chunk) {
        if (apacket[0] == null) {
            apacket[0] = new PacketPlayOutMapChunk(chunk, 65535);
            apacket[1] = new PacketPlayOutLightUpdate(chunk.getPos(), this.lightEngine);
        }

        entityplayer.a(chunk.getPos(), apacket[0], apacket[1]);
        PacketDebug.a(this.world, chunk.getPos());
        List<Entity> list = Lists.newArrayList();
        List<Entity> list1 = Lists.newArrayList();
        ObjectIterator objectiterator = this.trackedEntities.values().iterator();

        while (objectiterator.hasNext()) {
            PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();
            Entity entity = playerchunkmap_entitytracker.tracker;

            if (entity != entityplayer && entity.chunkX == chunk.getPos().x && entity.chunkZ == chunk.getPos().z) {
                playerchunkmap_entitytracker.updatePlayer(entityplayer);
                if (entity instanceof EntityInsentient && ((EntityInsentient) entity).getLeashHolder() != null) {
                    list.add(entity);
                }

                if (!entity.getPassengers().isEmpty()) {
                    list1.add(entity);
                }
            }
        }

        Iterator iterator;
        Entity entity1;

        if (!list.isEmpty()) {
            iterator = list.iterator();

            while (iterator.hasNext()) {
                entity1 = (Entity) iterator.next();
                entityplayer.playerConnection.sendPacket(new PacketPlayOutAttachEntity(entity1, ((EntityInsentient) entity1).getLeashHolder()));
            }
        }

        if (!list1.isEmpty()) {
            iterator = list1.iterator();

            while (iterator.hasNext()) {
                entity1 = (Entity) iterator.next();
                entityplayer.playerConnection.sendPacket(new PacketPlayOutMount(entity1));
            }
        }

    }

    protected VillagePlace h() {
        return this.n;
    }

    public CompletableFuture<Void> a(Chunk chunk) {
        return this.executor.e(() -> {
            chunk.a(this.world);
        });
    }

    public class EntityTracker {

        private final EntityTrackerEntry trackerEntry;
        private final Entity tracker;
        private final int trackingDistance;
        private SectionPosition e;
        // Paper start
        // Replace trackedPlayers Set with a Map. The value is true until the player receives
        // their first update (which is forced to have absolute coordinates), false afterward.
        public java.util.Map<EntityPlayer, Boolean> trackedPlayerMap = new java.util.HashMap<>();
        public Set<EntityPlayer> trackedPlayers = trackedPlayerMap.keySet();

        public EntityTracker(Entity entity, int i, int j, boolean flag) {
            this.trackerEntry = new EntityTrackerEntry(PlayerChunkMap.this.world, entity, j, flag, this::broadcast, trackedPlayerMap); // CraftBukkit // Paper
            this.tracker = entity;
            this.trackingDistance = i;
            this.e = SectionPosition.a(entity);
        }

        public boolean equals(Object object) {
            return object instanceof PlayerChunkMap.EntityTracker ? ((PlayerChunkMap.EntityTracker) object).tracker.getId() == this.tracker.getId() : false;
        }

        public int hashCode() {
            return this.tracker.getId();
        }

        public void broadcast(Packet<?> packet) {
            Iterator iterator = this.trackedPlayers.iterator();

            while (iterator.hasNext()) {
                EntityPlayer entityplayer = (EntityPlayer) iterator.next();

                entityplayer.playerConnection.sendPacket(packet);
            }

        }

        public void broadcastIncludingSelf(Packet<?> packet) {
            this.broadcast(packet);
            if (this.tracker instanceof EntityPlayer) {
                ((EntityPlayer) this.tracker).playerConnection.sendPacket(packet);
            }

        }

        public void a() {
            Iterator iterator = this.trackedPlayers.iterator();

            while (iterator.hasNext()) {
                EntityPlayer entityplayer = (EntityPlayer) iterator.next();

                this.trackerEntry.a(entityplayer);
            }

        }

        public void clear(EntityPlayer entityplayer) {
            org.spigotmc.AsyncCatcher.catchOp( "player tracker clear"); // Spigot
            if (this.trackedPlayers.remove(entityplayer)) {
                this.trackerEntry.a(entityplayer);
            }

        }

        public void updatePlayer(EntityPlayer entityplayer) {
            org.spigotmc.AsyncCatcher.catchOp( "player tracker update"); // Spigot
            if (entityplayer != this.tracker) {
                Vec3D vec3d = (new Vec3D(entityplayer.locX, entityplayer.locY, entityplayer.locZ)).d(this.trackerEntry.b());
                int i = Math.min(this.trackingDistance, (PlayerChunkMap.this.viewDistance - 1) * 16);
                boolean flag = vec3d.x >= (double) (-i) && vec3d.x <= (double) i && vec3d.z >= (double) (-i) && vec3d.z <= (double) i && this.tracker.a(entityplayer);

                if (flag) {
                    boolean flag1 = this.tracker.attachedToPlayer;

                    if (!flag1) {
                        ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(this.tracker.chunkX, this.tracker.chunkZ);
                        PlayerChunk playerchunk = PlayerChunkMap.this.getVisibleChunk(chunkcoordintpair.pair());

                        if (playerchunk != null && playerchunk.getChunk() != null) {
                            flag1 = PlayerChunkMap.b(chunkcoordintpair, entityplayer, false) <= PlayerChunkMap.this.viewDistance;
                        }
                    }

                    // CraftBukkit start - respect vanish API
                    if (this.tracker instanceof EntityPlayer) {
                        Player player = ((EntityPlayer) this.tracker).getBukkitEntity();
                        if (!entityplayer.getBukkitEntity().canSee(player)) {
                            flag1 = false;
                        }
                    }

                    entityplayer.removeQueue.remove(Integer.valueOf(this.tracker.getId()));
                    // CraftBukkit end

                    if (flag1 && this.trackedPlayerMap.putIfAbsent(entityplayer, true) == null) { // Paper
                        this.trackerEntry.b(entityplayer);
                    }
                } else if (this.trackedPlayers.remove(entityplayer)) {
                    this.trackerEntry.a(entityplayer);
                }

            }
        }

        public void track(List<EntityPlayer> list) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                EntityPlayer entityplayer = (EntityPlayer) iterator.next();

                this.updatePlayer(entityplayer);
            }

        }
    }

    class a extends ChunkMapDistance {

        protected a(Executor executor, Executor executor1) {
            super(executor, executor1);
        }

        @Override
        protected boolean a(long i) {
            return PlayerChunkMap.this.unloadQueue.contains(i);
        }

        @Nullable
        @Override
        protected PlayerChunk b(long i) {
            return PlayerChunkMap.this.getUpdatingChunk(i);
        }

        @Nullable
        @Override
        protected PlayerChunk a(long i, int j, @Nullable PlayerChunk playerchunk, int k) {
            return PlayerChunkMap.this.a(i, j, playerchunk, k);
        }
    }
}
