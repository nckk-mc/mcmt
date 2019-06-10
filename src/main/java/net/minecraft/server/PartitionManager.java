package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;

public class PartitionManager {

    private List<Partition> partitions;

    public PartitionManager() {
        this.partitions = new ArrayList<>();
    }

    public void addEntity(Entity entity) {
        System.out.println("MCMT | Loaded Entity: " + entity.getName());
        ArrayList<Partition> partitionsNotInRange = new ArrayList<Partition>();
        ArrayList<Partition> partitionsInRange = new ArrayList<Partition>();
        for (int i2 = 0; i2 < this.partitions.size(); ++i2) {
            Partition partition = this.partitions.get(i2);
            if (partition.isInMergeDistance(entity)) {
                partitionsInRange.add(partition);
                continue;
            }
            partitionsNotInRange.add(partition);
        }
        
        if (partitionsInRange.size() == 0)
        {
            System.out.println("MCMT | Entity does not belong to a partition!");
        }
        else if (partitionsInRange.size() > 1) {
            System.out.println("MCMT | Entity in range of more than one partition, merging partitions");
            Partition master = (Partition)partitionsInRange.get(0);
            master.addEntity(entity);
            for (int i = 1; i < partitionsInRange.size(); ++i) {
                Partition partition = (Partition)partitionsInRange.get(i);
                master.mergeInto(partition);
            }
            partitionsNotInRange.add(master);
            this.partitions = partitionsNotInRange;
        } else {
            Partition target = (Partition)partitionsInRange.get(0);
            target.addEntity(entity);
        }
    }
    
    public void load(PlayerChunk playerChunk) {
        Chunk chunk = playerChunk.getFullChunk();
        System.out.println("MCMT | Loaded Chunk: " + Integer.toString(chunk.getPos().x) + ", " + Integer.toString(chunk.getPos().z));
        ArrayList<Partition> partitionsNotInRange = new ArrayList<Partition>();
        ArrayList<Partition> partitionsInRange = new ArrayList<Partition>();
        for (int i2 = 0; i2 < this.partitions.size(); ++i2) {
            Partition partition = this.partitions.get(i2);
            if (partition.isInMergeDistance(playerChunk)) {
                partitionsInRange.add(partition);
                continue;
            }
            partitionsNotInRange.add(partition);
        }
        if (partitionsInRange.isEmpty()) {
            this.partitions.add(new Partition(playerChunk));
            System.out.println("MCMT | Created Partition " + (this.partitions.size() - 1));
        } else if (partitionsInRange.size() > 1) {
            System.out.println("MCMT | Chunk in range of more than one partition, merging partitions");
            Partition master = (Partition)partitionsInRange.get(0);
            master.addChunk(playerChunk);
            for (int i3 = 1; i3 < partitionsInRange.size(); ++i3) {
                Partition partition = (Partition)partitionsInRange.get(i3);
                master.mergeInto(partition);
            }
            partitionsNotInRange.add(master);
            this.partitions = partitionsNotInRange;
        } else {
            Partition target = (Partition)partitionsInRange.get(0);
            target.addChunk(playerChunk);
        }
    }

    public List<Partition> getPartitions() {
        return this.partitions;
    }

    public void tickPartitionChunks(int index, WorldServer worldServer, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters) {
        this.partitions.get(index).tickChunks(worldServer, playerChunkMap, allowAnimals, allowMonsters);
    }

    public void tickPartitionEntities(int index, WorldServer world) {
        this.partitions.get(index).tickEntities(world);
    }
}
