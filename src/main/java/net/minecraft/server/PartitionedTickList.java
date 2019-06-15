package net.minecraft.server;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PartitionedTickList<T> implements TickList<T> {

    private PartitionManager partitionManager;
    private Function<Partition, TickListServer<T>> getTickListForPartition;

    public PartitionedTickList(PartitionManager partitionManager, Function<Partition, TickListServer<T>> getTickListForPartition) {
        this.partitionManager = partitionManager;
        this.getTickListForPartition = getTickListForPartition;
    }

    private TickListServer<T> getTickListServer(BlockPosition blockPosition) {
        Partition partition = this.partitionManager.getPartition(blockPosition);
        return this.getTickListForPartition.apply(partition);
    }
    private TickListServer<T> getTickListServer(ChunkCoordIntPair chunkCoordIntPair) {
        Partition partition = this.partitionManager.getPartition(chunkCoordIntPair);
        return this.getTickListForPartition.apply(partition);
    }

    @Override
    public boolean a(BlockPosition blockPosition, T t) {
        return getTickListServer(blockPosition).a(blockPosition, t);
    }

    @Override
    public void a(BlockPosition var0, T var1, int var2) {
        getTickListServer(var0).a(var0, var1, var2);
    }

    public List<NextTickListEntry<T>> a(ChunkCoordIntPair chunkcoordintpair, boolean flag, boolean flag1) {
        return getTickListServer(chunkcoordintpair).a(chunkcoordintpair, flag, flag1);
    }

    public NBTTagList a(ChunkCoordIntPair chunkcoordintpair) {
        return getTickListServer(chunkcoordintpair).a(chunkcoordintpair);
    }

    @Override
    public void a(BlockPosition blockposition, T t0, int i, TickListPriority ticklistpriority) {
        this.getTickListServer(blockposition).a(blockposition, t0, i, ticklistpriority);
    }

    @Override
    public boolean b(BlockPosition blockPosition, T t) {
        return getTickListServer(blockPosition).b(blockPosition, t);
    }

    @Override
    public void a(Stream<NextTickListEntry<T>> stream) {
        stream.forEach(entry -> {
            getTickListServer(entry.a).add(entry);
        });
    }
}
