package net.minecraft.server;

import org.bukkit.craftbukkit.LoggerOutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PartitionManager {

    private List<Partition> partitions;
    private WorldServer world;
    public List<Partition> getPartitions() {
        return this.partitions;
    }
    private PartitionedTickList<Block> partionedBlockTickList;
    private PartitionedTickList<FluidType> partionedFluidTickList;

    public PartitionManager(WorldServer world) {

        this.world = world;

        this.partitions = new ArrayList<>();
        this.partionedBlockTickList = new PartitionedTickList<>(this, x -> x.blockTickListServer);
        this.partionedFluidTickList = new PartitionedTickList<>(this, x -> x.fluidTickListServer);
    }

    public PartitionedTickList<Block> getPartionBlockTickList() {
        return this.partionedBlockTickList;
    }
    
    public PartitionedTickList<FluidType> getPartionFluidTickList() {
        return this.partionedFluidTickList;
    }

    public Partition getPartition(BlockPosition blockPosition) {
        int x = Math.floorDiv(blockPosition.getX(), 16);
        int z = Math.floorDiv(blockPosition.getZ(), 16);
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x, z);
        return getPartition(chunk);
    }
    public Partition getPartition(ChunkCoordIntPair chunk) {
        for (int i = 0; i < this.partitions.size(); i++) {
            Partition partition = this.partitions.get(i);
            if(partition.isInMergeDistance(chunk))
            {
                return partition;
            }
        }
        System.out.println("MCMT | Requested partition for area that isn't loaded");
        return partitions.get(0); //Should never hit here
    }

    public void addChunk(PlayerChunk playerChunk) {
        //Chunk chunk = playerChunk.getFullChunk();
        //System.out.println("MCMT | Loaded Chunk: " + Integer.toString(chunk.getPos().x) + ", " + Integer.toString(chunk.getPos().z));
        add(p -> p.isInMergeDistance(playerChunk), p -> p.addChunk(playerChunk));
    }

    public void addEntity(Entity entity) {
        if(entity instanceof EntityPlayer)
        {
            System.out.println("MCMT | Ignorning player");
            return;
        }
        //System.out.println("MCMT | Loaded Entity: " + entity.getName());
        add(p -> p.isInMergeDistance(entity), p -> p.addEntity(entity));
    }

    private void add(Function<Partition, Boolean> isInMergeDistance, Consumer<Partition> addToPartition) {
        ArrayList<Partition> partitionsNotInRange = new ArrayList<>();
        ArrayList<Partition> partitionsInRange = new ArrayList<>();

        for (int i = 0; i < this.partitions.size(); ++i) {
            Partition partition = this.partitions.get(i);
            if (isInMergeDistance.apply(partition)) {
                partitionsInRange.add(partition);
            } else {
                partitionsNotInRange.add(partition);
            }
        }

        if (partitionsInRange.isEmpty()) {
            Partition target = new Partition(this.world);
            this.partitions.add(target);
            addToPartition.accept(target);
            System.out.println("MCMT | Created Partition " + (this.partitions.size() - 1));
        } else if (partitionsInRange.size() > 1) {
            System.out.println("MCMT | Chunk in range of more than one partition, merging partitions");
            Partition master = partitionsInRange.get(0);
            addToPartition.accept(master);
            for (int i = 1; i < partitionsInRange.size(); ++i) {
                Partition partition = partitionsInRange.get(i);
                master.mergeInto(partition);
            }
            partitionsNotInRange.add(master);
            this.partitions = partitionsNotInRange;
        } else {
            Partition target = partitionsInRange.get(0);
            addToPartition.accept(target);
        }
    }

    public void removeEntity(Entity entity)
    {
        remove(p -> p.removeEntitiy(entity));
    }

    public void removeChunk(PlayerChunk playerChunk)
    {
        remove(p -> p.removeChunk(playerChunk));
    }

    private void remove(Function<Partition, Boolean> removeFromPartition)
    {
        for(int i = 0; i < this.partitions.size(); i++)
        {
            Partition partition = this.partitions.get(i);
            if(removeFromPartition.apply(partition))
            {
                if(partition.isEmpty())
                {
                    System.out.println("MCMT | Removed partition");
                    partitions.remove(i);
                }
                return;
            }
        }
    }

    public void tickPartition(int index, WorldServer worldServer, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters) {
        Partition partition = this.partitions.get(index);
        partition.tick(worldServer, playerChunkMap, allowAnimals, allowMonsters);
        if(partition.isEmpty())
        {
            this.partitions.remove(partition);
        }
    }
}
