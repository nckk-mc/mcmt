package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;

public class PartitionManager {

    private List<Partition> partitions;

    public PartitionManager() {
        this.partitions = new ArrayList<>();
    }

    public void load(PlayerChunk playerChunk)
    {
//        Chunk chunk = playerChunk.getFullChunk();
//        System.out.println("MCMT | Loaded Chunk: " + Integer.toString(chunk.getPos().x) + ", " + Integer.toString(chunk.getPos().z));

        List<Partition> partitionsNotInRange = new ArrayList<>();
        List<Partition> partitionsInRange = new ArrayList<>();
        for (int i = 0; i<partitions.size(); i++) {
            Partition partition = partitions.get(i);
            if (partition.isInMergeDistance(playerChunk)) {
                partitionsInRange.add(partition);
            } else {
                partitionsNotInRange.add(partition);
            }
        }

        if(partitionsInRange.isEmpty()) {
            partitions.add(new Partition(playerChunk));
            System.out.println("MCMT | Created Partition " + (partitions.size() - 1));
        }
        else if(partitionsInRange.size() > 1)
        {
            System.out.println("MCMT | Chunk in range of more than one partition, merging partitions");

            Partition master = partitionsInRange.get(0);
            master.add(playerChunk);
            for(int i = 1; i < partitionsInRange.size(); i++)
            {
                Partition partition = partitionsInRange.get(i);
                master.mergeInto(partition);
            }
            partitionsNotInRange.add(master);

            this.partitions = partitionsNotInRange;
        }
        else
        {
            Partition target = partitionsInRange.get(0);
            target.add(playerChunk);
        }
    }

    public List<Partition> getPartitions() {
        return this.partitions;
    }

    public void tickGroup(int i, WorldServer worldServer, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters)
    {
        partitions.get(i).tickChunks(worldServer, playerChunkMap, allowAnimals, allowMonsters);
    }
}
