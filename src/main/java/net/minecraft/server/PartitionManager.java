package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PartitionManager {

    private List<Partition> partitions;
    public List<Partition> getPartitions() {
        return this.partitions;
    }

    public PartitionManager() {
        this.partitions = new ArrayList<>();
    }

    public void addChunk(PlayerChunk playerChunk) {
        //Chunk chunk = playerChunk.getFullChunk();
        //System.out.println("MCMT | Loaded Chunk: " + Integer.toString(chunk.getPos().x) + ", " + Integer.toString(chunk.getPos().z));

        add(p -> p.isInMergeDistance(playerChunk), p -> p.addChunk(playerChunk));
    }
    public void addEntity(Entity entity) {
        System.out.println("MCMT | Loaded Entity: " + entity.getName());

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
            Partition target = new Partition();
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
        for(int i = 0; i < partitions.size(); i++)
        {
            Partition partition = this.partitions.get(i);
            if(partition.entities.remove(entity))
            {
                System.out.println("MCMT | Removed Entity: " + entity.getName());
                return;
            }
        }
    }

    public void tickPartition(int index, WorldServer worldServer, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters) {
        Partition partition = this.partitions.get(index);
        partition.tickChunks(worldServer, playerChunkMap, allowAnimals, allowMonsters);
        partition.tickEntities(worldServer);
    }
}
