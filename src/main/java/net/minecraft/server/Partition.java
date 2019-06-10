package net.minecraft.server;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Partition {

    public List<PlayerChunk> chunks;
    private long lastTickTime;

    Partition(PlayerChunk initial)
    {
        chunks = new ArrayList<>();
        chunks.add(initial);
    }

    long mergeRadius = 32;

    public boolean isInMergeDistance(PlayerChunk playerChunkToCheck)
    {
        Chunk playerChunk = playerChunkToCheck.getFullChunk();
        ChunkCoordIntPair playerPos = playerChunk.getPos();

        for(int i = 0; i < chunks.size(); i++)
        {
            Chunk partitionChunk = chunks.get(i).getFullChunk();
            ChunkCoordIntPair partitionPos  = partitionChunk.getPos();
            if (Math.abs(partitionPos.x - playerPos.x) <= mergeRadius && Math.abs(partitionPos.z - playerPos.z) <= mergeRadius) {
                return true;
            }
        }

        return false;
    }

    public void add(PlayerChunk chunk)
    {
        chunks.add(chunk);
    }

    public void tickChunks(WorldServer world, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters)
    {
        ChunkMapDistance chunkMapDistance = playerChunkMap.e();

        long currentTime = world.getTime();
        long timeSinceLastTick = currentTime - this.lastTickTime;

        this.lastTickTime = currentTime;

        WorldData worlddata = world.getWorldData();

        boolean doMobSpawning = world.getGameRules().getBoolean("doMobSpawning") && !world.getPlayers().isEmpty(); // CraftBukkit

        world.getMethodProfiler().enter("pollingChunks");
        int randomTickSpeed = world.getGameRules().c("randomTickSpeed");
        BlockPosition spawn = world.getSpawn();
        boolean flag2 = world.ticksPerAnimalSpawns != 0L && worlddata.getTime() % world.ticksPerAnimalSpawns == 0L; // CraftBukkit // PAIL: TODO monster ticks

        world.getMethodProfiler().enter("naturalSpawnCount");
        int l = chunkMapDistance.b();

        EnumCreatureType[] aenumcreaturetype = EnumCreatureType.values();
        Object2IntMap<EnumCreatureType> object2intmap = world.l();

        for (int i=0; i<chunks.size(); i++) {
            PlayerChunk playerchunk = chunks.get(i);

            Optional<Chunk> optional = ((Either) playerchunk.b().getNow(PlayerChunk.UNLOADED_CHUNK)).left();
            if (optional.isPresent()) {
                Chunk chunk = (Chunk) optional.get();

                world.getMethodProfiler().enter("broadcast");
                playerchunk.a(chunk);
                world.getMethodProfiler().exit();
                ChunkCoordIntPair chunkcoordintpair = playerchunk.h();

                if (!playerChunkMap.isOutsideOfRange(chunkcoordintpair, false)) { // Spigot
                    chunk.b(chunk.q() + timeSinceLastTick);
                    if (doMobSpawning && (allowMonsters || allowAnimals) && world.getWorldBorder().isInBounds(chunk.getPos()) && !playerChunkMap.isOutsideOfRange(chunkcoordintpair, true)) { // Spigot
                        world.getMethodProfiler().enter("spawner");
                        world.timings.mobSpawn.startTiming(); // Spigot
                        EnumCreatureType[] aenumcreaturetype1 = aenumcreaturetype;
                        int i1 = aenumcreaturetype.length;

                        for (int j1 = 0; j1 < i1; ++j1) {
                            EnumCreatureType enumcreaturetype = aenumcreaturetype1[j1];

                            // CraftBukkit start - Use per-world spawn limits
                            int limit = enumcreaturetype.b();
                            switch (enumcreaturetype) {
                                case MONSTER:
                                    limit = world.getWorld().getMonsterSpawnLimit();
                                    break;
                                case CREATURE:
                                    limit = world.getWorld().getAnimalSpawnLimit();
                                    break;
                                case WATER_CREATURE:
                                    limit = world.getWorld().getWaterAnimalSpawnLimit();
                                    break;
                                case AMBIENT:
                                    limit = world.getWorld().getAmbientSpawnLimit();
                                    break;
                            }

                            if (limit == 0) {
                                continue;
                            }
                            // CraftBukkit end

                            if (enumcreaturetype != EnumCreatureType.MISC && (!enumcreaturetype.c() || allowAnimals) && (enumcreaturetype.c() || allowMonsters) && (!enumcreaturetype.d() || flag2)) {
                                int k1 = limit * l / playerChunkMap.c(); // CraftBukkit - use per-world limits
                                if (object2intmap.getInt(enumcreaturetype) <= k1) {
                                    SpawnerCreature.a(enumcreaturetype, (World) world, chunk, spawn);
                                }
                            }
                        }

                        world.timings.mobSpawn.stopTiming(); // Spigot
                        world.getMethodProfiler().exit();
                    }

                    world.timings.chunkTicks.startTiming(); // Spigot
                    world.a(chunk, randomTickSpeed);
                    world.timings.chunkTicks.stopTiming(); // Spigot
                }
            }
        }
    }

    public void mergeInto(Partition partition) {
        for(int j = 0; j < partition.chunks.size(); j++)
        {
            add(partition.chunks.get(j));
        }
    }
}
