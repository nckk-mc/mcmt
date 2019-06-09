package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class ChunkMapDistance {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int b = 33 + ChunkStatus.a(ChunkStatus.FULL) - 2;
    private final Long2ObjectMap<ObjectSet<EntityPlayer>> c = new Long2ObjectOpenHashMap();
    private final Long2ObjectMap<ObjectSet<EntityPlayer>> d = new Long2ObjectOpenHashMap();
    private final Long2ObjectOpenHashMap<ObjectSortedSet<Ticket<?>>> tickets = new Long2ObjectOpenHashMap();
    private final ChunkMapDistance.a f = new ChunkMapDistance.a();
    private final ChunkMapDistance.c g = new ChunkMapDistance.c();
    private int entitydistance;
    private final ChunkMapDistance.b i = new ChunkMapDistance.b(8);
    private final ChunkMapDistance.d j = new ChunkMapDistance.d(33);
    private final Set<PlayerChunk> k = Sets.newHashSet();
    private final PlayerChunk.c l;
    private final Mailbox<ChunkTaskQueueSorter.a<Runnable>> m;
    private final Mailbox<ChunkTaskQueueSorter.b> n;
    private final LongSet o = new LongOpenHashSet();
    private final Executor p;
    private long currentTick;

    protected ChunkMapDistance(Executor executor, Executor executor1) {
        ThreadedMailbox<Runnable> threadedmailbox = ThreadedMailbox.a(executor1, "player ticket throttler");
        ChunkTaskQueueSorter chunktaskqueuesorter = new ChunkTaskQueueSorter(ImmutableList.of(threadedmailbox), executor, 15);

        this.l = chunktaskqueuesorter;
        this.m = chunktaskqueuesorter.a(threadedmailbox, true);
        this.n = chunktaskqueuesorter.a((Mailbox) threadedmailbox);
        this.p = executor1;
    }

    protected void setEntityDistance(int i) {
        int j = this.e();

        this.entitydistance = i;
        int k = this.e();
        ObjectIterator objectiterator = this.d.long2ObjectEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<ObjectSet<EntityPlayer>> entry = (Entry) objectiterator.next();

            this.g.b(entry.getLongKey(), k, k < j);
        }

    }

    protected void purgeTickets() {
        ++this.currentTick;
        ObjectIterator objectiterator = this.tickets.long2ObjectEntrySet().fastIterator();

        while (objectiterator.hasNext()) {
            Entry<ObjectSortedSet<Ticket<?>>> entry = (Entry) objectiterator.next();

            if (((ObjectSortedSet) entry.getValue()).removeIf((ticket) -> {
                return ticket.a(this.currentTick);
            })) {
                this.f.b(entry.getLongKey(), this.a((ObjectSortedSet) entry.getValue()), false);
            }

            if (((ObjectSortedSet) entry.getValue()).isEmpty()) {
                objectiterator.remove();
            }
        }

    }

    private int a(ObjectSortedSet<Ticket<?>> objectsortedset) {
        ObjectBidirectionalIterator<Ticket<?>> objectbidirectionaliterator = objectsortedset.iterator();

        return objectbidirectionaliterator.hasNext() ? ((Ticket) objectbidirectionaliterator.next()).b() : PlayerChunkMap.GOLDEN_TICKET + 1;
    }

    protected abstract boolean a(long i);

    @Nullable
    protected abstract PlayerChunk b(long i);

    @Nullable
    protected abstract PlayerChunk a(long i, int j, @Nullable PlayerChunk playerchunk, int k);

    public boolean a(PlayerChunkMap playerchunkmap) {
        this.i.a();
        this.j.a();
        this.g.a();
        int i = Integer.MAX_VALUE - this.f.a(Integer.MAX_VALUE);
        boolean flag = i != 0;

        if (flag) {
            ;
        }

        if (!this.k.isEmpty()) {
            this.k.forEach((playerchunk) -> {
                playerchunk.a(playerchunkmap);
            });
            this.k.clear();
            return true;
        } else {
            if (!this.o.isEmpty()) {
                LongIterator longiterator = this.o.iterator();

                while (longiterator.hasNext()) {
                    long j = longiterator.nextLong();

                    if (this.d(j).stream().anyMatch((ticket) -> {
                        return ticket.getTicketType() == TicketType.PLAYER;
                    })) {
                        PlayerChunk playerchunk = playerchunkmap.getUpdatingChunk(j);

                        if (playerchunk == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<Either<Chunk, PlayerChunk.Failure>> completablefuture = playerchunk.b();

                        completablefuture.thenAccept((either) -> {
                            this.p.execute(() -> {
                                this.n.a((Object) ChunkTaskQueueSorter.a(() -> {
                                }, j, false));
                            });
                        });
                    }
                }

                this.o.clear();
            }

            return flag;
        }
    }

    private void a(long i, Ticket<?> ticket) {
        ObjectSortedSet<Ticket<?>> objectsortedset = this.d(i);
        ObjectBidirectionalIterator<Ticket<?>> objectbidirectionaliterator = objectsortedset.iterator();
        int j;

        if (objectbidirectionaliterator.hasNext()) {
            j = ((Ticket) objectbidirectionaliterator.next()).b();
        } else {
            j = PlayerChunkMap.GOLDEN_TICKET + 1;
        }

        if (objectsortedset.add(ticket)) {
            ;
        }

        if (ticket.b() < j) {
            this.f.b(i, ticket.b(), true);
        }

    }

    private void b(long i, Ticket<?> ticket) {
        ObjectSortedSet<Ticket<?>> objectsortedset = this.d(i);

        if (objectsortedset.remove(ticket)) {
            ;
        }

        if (objectsortedset.isEmpty()) {
            this.tickets.remove(i);
        }

        this.f.b(i, this.a(objectsortedset), false);
    }

    public <T> void a(TicketType<T> tickettype, ChunkCoordIntPair chunkcoordintpair, int i, T t0) {
        this.a(chunkcoordintpair.pair(), new Ticket<>(tickettype, i, t0, this.currentTick));
    }

    public <T> void b(TicketType<T> tickettype, ChunkCoordIntPair chunkcoordintpair, int i, T t0) {
        Ticket<T> ticket = new Ticket<>(tickettype, i, t0, this.currentTick);

        this.b(chunkcoordintpair.pair(), ticket);
    }

    public <T> void addTicket(TicketType<T> tickettype, ChunkCoordIntPair chunkcoordintpair, int i, T t0) {
        this.a(chunkcoordintpair.pair(), new Ticket<>(tickettype, 33 - i, t0, this.currentTick));
    }

    public <T> void removeTicket(TicketType<T> tickettype, ChunkCoordIntPair chunkcoordintpair, int i, T t0) {
        Ticket<T> ticket = new Ticket<>(tickettype, 33 - i, t0, this.currentTick);

        this.b(chunkcoordintpair.pair(), ticket);
    }

    private ObjectSortedSet<Ticket<?>> d(long i) {
        return (ObjectSortedSet) this.tickets.computeIfAbsent(i, (j) -> {
            return new ObjectAVLTreeSet();
        });
    }

    protected void a(ChunkCoordIntPair chunkcoordintpair, boolean flag) {
        Ticket<ChunkCoordIntPair> ticket = new Ticket<>(TicketType.FORCED, 32, chunkcoordintpair, this.currentTick);

        if (flag) {
            this.a(chunkcoordintpair.pair(), ticket);
        } else {
            this.b(chunkcoordintpair.pair(), ticket);
        }

    }

    private int e() {
        return 16 - this.entitydistance;
    }

    public void a(SectionPosition sectionposition, EntityPlayer entityplayer) {
        long i = sectionposition.u().pair();

        entityplayer.a(sectionposition);
        entityplayer.playerConnection.sendPacket(new PacketPlayOutViewCentre(sectionposition.a(), sectionposition.c()));
        ((ObjectSet) this.d.computeIfAbsent(sectionposition.v(), (j) -> {
            return new ObjectOpenHashSet();
        })).add(entityplayer);
        ((ObjectSet) this.c.computeIfAbsent(i, (j) -> {
            return new ObjectOpenHashSet();
        })).add(entityplayer);
        this.i.b(i, 0, true);
        this.j.b(i, 0, true);
        this.g.b(sectionposition.v(), this.e(), true);
    }

    public void b(SectionPosition sectionposition, EntityPlayer entityplayer) {
        long i = sectionposition.u().pair();
        ObjectSet<EntityPlayer> objectset = (ObjectSet) this.d.get(sectionposition.v());

        if (objectset != null) {
            objectset.remove(entityplayer);
            if (objectset.isEmpty()) {
                this.d.remove(sectionposition.v());
                this.g.b(sectionposition.v(), Integer.MAX_VALUE, false);
            }

            ObjectSet<EntityPlayer> objectset1 = (ObjectSet) this.c.get(i);

            objectset1.remove(entityplayer);
            if (objectset1.isEmpty()) {
                this.c.remove(i);
                this.i.b(i, Integer.MAX_VALUE, false);
                this.j.b(i, Integer.MAX_VALUE, false);
            }

        }
    }

    protected void b(int i) {
        this.j.a(i);
    }

    public int b() {
        this.i.a();
        return this.i.a.size();
    }

    class a extends ChunkMap {

        public a() {
            super(PlayerChunkMap.GOLDEN_TICKET + 2, 16, 256);
        }

        @Override
        protected int b(long i) {
            ObjectSortedSet<Ticket<?>> objectsortedset = (ObjectSortedSet) ChunkMapDistance.this.tickets.get(i);

            if (objectsortedset == null) {
                return Integer.MAX_VALUE;
            } else {
                ObjectBidirectionalIterator<Ticket<?>> objectbidirectionaliterator = objectsortedset.iterator();

                return !objectbidirectionaliterator.hasNext() ? Integer.MAX_VALUE : ((Ticket) objectbidirectionaliterator.next()).b();
            }
        }

        @Override
        protected int c(long i) {
            if (!ChunkMapDistance.this.a(i)) {
                PlayerChunk playerchunk = ChunkMapDistance.this.b(i);

                if (playerchunk != null) {
                    return playerchunk.getTicketLevel();
                }
            }

            return PlayerChunkMap.GOLDEN_TICKET + 1;
        }

        @Override
        protected void a(long i, int j) {
            PlayerChunk playerchunk = ChunkMapDistance.this.b(i);
            int k = playerchunk == null ? PlayerChunkMap.GOLDEN_TICKET + 1 : playerchunk.getTicketLevel();

            if (k != j) {
                playerchunk = ChunkMapDistance.this.a(i, j, playerchunk, k);
                if (playerchunk != null) {
                    ChunkMapDistance.this.k.add(playerchunk);
                }

            }
        }

        public int a(int i) {
            return this.b(i);
        }
    }

    class d extends ChunkMapDistance.b {

        private int e = 0;
        private final Long2IntMap f = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet g = new LongOpenHashSet();

        protected d(int i) {
            super(i);
            this.f.defaultReturnValue(i + 2);
        }

        @Override
        protected void a(long i, int j, int k) {
            this.g.add(i);
        }

        public void a(int i) {
            ObjectIterator objectiterator = this.a.long2ByteEntrySet().iterator();

            while (objectiterator.hasNext()) {
                it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry it_unimi_dsi_fastutil_longs_long2bytemap_entry = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry) objectiterator.next();
                byte b0 = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getByteValue();
                long j = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getLongKey();

                this.a(j, b0, this.c(b0), b0 <= i - 2);
            }

            this.e = i;
        }

        private void a(long i, int j, boolean flag, boolean flag1) {
            if (flag != flag1) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, ChunkMapDistance.b, new ChunkCoordIntPair(i), ChunkMapDistance.this.currentTick);

                if (flag1) {
                    ChunkMapDistance.this.m.a((Object) ChunkTaskQueueSorter.a(() -> {
                        ChunkMapDistance.this.p.execute(() -> {
                            ChunkMapDistance.this.a(i, ticket);
                            ChunkMapDistance.this.o.add(i);
                        });
                    }, i, () -> {
                        return j;
                    }));
                } else {
                    ChunkMapDistance.this.n.a((Object) ChunkTaskQueueSorter.a(() -> {
                        ChunkMapDistance.this.p.execute(() -> {
                            ChunkMapDistance.this.b(i, ticket);
                        });
                    }, i, true));
                }
            }

        }

        @Override
        public void a() {
            super.a();
            if (!this.g.isEmpty()) {
                LongIterator longiterator = this.g.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.f.get(i);
                    int k = this.c(i);

                    if (j != k) {
                        ChunkMapDistance.this.l.a(new ChunkCoordIntPair(i), () -> {
                            return this.f.get(i);
                        }, k, (l) -> {
                            this.f.put(i, l);
                        });
                        this.a(i, k, this.c(j), this.c(k));
                    }
                }

                this.g.clear();
            }

        }

        private boolean c(int i) {
            return i <= this.e - 2;
        }
    }

    class c extends LightEngineGraphSection {

        protected final Long2ByteMap a = new Long2ByteOpenHashMap();

        protected c() {
            super(18, 16, 256);
            this.a.defaultReturnValue((byte) 18);
        }

        @Override
        protected int c(long i) {
            return this.a.get(i);
        }

        @Override
        protected void a(long i, int j) {
            if (j > 16) {
                this.a.remove(i);
            } else {
                this.a.put(i, (byte) j);
            }

        }

        @Override
        protected int b(long i) {
            return this.d(i) ? ChunkMapDistance.this.e() : Integer.MAX_VALUE;
        }

        private boolean d(long i) {
            ObjectSet<EntityPlayer> objectset = (ObjectSet) ChunkMapDistance.this.d.get(i);

            return objectset != null && !objectset.isEmpty();
        }

        public void a() {
            this.b(Integer.MAX_VALUE);
        }
    }

    class b extends ChunkMap {

        protected final Long2ByteMap a = new Long2ByteOpenHashMap();
        protected final int b;

        protected b(int i) {
            super(i + 2, 16, 256);
            this.b = i;
            this.a.defaultReturnValue((byte) (i + 2));
        }

        @Override
        protected int c(long i) {
            return this.a.get(i);
        }

        @Override
        protected void a(long i, int j) {
            byte b0;

            if (j > this.b) {
                b0 = this.a.remove(i);
            } else {
                b0 = this.a.put(i, (byte) j);
            }

            this.a(i, b0, j);
        }

        protected void a(long i, int j, int k) {}

        @Override
        protected int b(long i) {
            return this.d(i) ? 0 : Integer.MAX_VALUE;
        }

        private boolean d(long i) {
            ObjectSet<EntityPlayer> objectset = (ObjectSet) ChunkMapDistance.this.c.get(i);

            return objectset != null && !objectset.isEmpty();
        }

        public void a() {
            this.b(Integer.MAX_VALUE);
        }
    }
}
