package net.minecraft.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class PartitionedTickList<T> implements TickList<T> {

    private WorldServer world;
    private Consumer<NextTickListEntry<T>> consumer;

    public PartitionedTickList(WorldServer world, Consumer<NextTickListEntry<T>> consumer)
    {
        this.world = world;
        this.consumer = consumer;
    }

    @Override
    public boolean a(BlockPosition blockPosition, T t) {
        return false;
    }

    @Override
    public void a(BlockPosition var0, T var1, int var2) {
        this.a(var0, var1, var2, TickListPriority.NORMAL);
    }

    public List<NextTickListEntry<T>> a(ChunkCoordIntPair chunkcoordintpair, boolean flag, boolean flag1) {
        int i = (chunkcoordintpair.x << 4) - 2;
        int j = i + 16 + 2;
        int k = (chunkcoordintpair.z << 4) - 2;
        int l = k + 16 + 2;

        return this.a(new StructureBoundingBox(i, 0, k, j, 256, l), flag, flag1);
    }

    public NBTTagList a(ChunkCoordIntPair chunkcoordintpair) {
        List<NextTickListEntry<T>> list = this.a(chunkcoordintpair, false, true);

        return new NBTTagList(); // NEEDS Fixing//a(this.b, list, world.getTime());
    }

    public List<NextTickListEntry<T>> a(StructureBoundingBox structureboundingbox, boolean flag, boolean flag1) {

        return new ArrayList<NextTickListEntry<T>>();
        /*
        List<NextTickListEntry<T>> list = this.a((List) null, this.nextTickList, structureboundingbox, flag);

        if (flag && list != null) {
            this.nextTickListHash.removeAll(list);
        }

        list = this.a(list, this.g, structureboundingbox, flag);
        if (!flag1) {
            list = this.a(list, this.h, structureboundingbox, flag);
        }

        return list == null ? Collections.emptyList() : list;*/
    }

    @Override
    public void a(BlockPosition blockposition, T t0, int i, TickListPriority ticklistpriority) {
        this.assign(new NextTickListEntry<>(blockposition, t0, (long) i + world.getTime(), ticklistpriority));

    }

    @Override
    public boolean b(BlockPosition blockPosition, T t) {
        return false;
    }

    @Override
    public void a(Stream<NextTickListEntry<T>> stream) {
        stream.forEach(this::assign);

    }

    public void assign(NextTickListEntry<T> nextticklistentry) {
        consumer.accept(nextticklistentry);
    }
}
