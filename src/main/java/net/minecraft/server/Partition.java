package net.minecraft.server;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.spigotmc.ActivationRange;

public class Partition {

    public List<PlayerChunk> chunks;
    public List<Entity> entities;
    private long lastTickTime;

    Partition(PlayerChunk initial)
    {
        this.chunks = new ArrayList<>();
        this.chunks.add(initial);
        
        this.entities = new ArrayList<>();
    }

    long mergeRadius = 32;

    public boolean isInMergeDistance(PlayerChunk playerChunkToCheck) {
        Chunk playerChunk = playerChunkToCheck.getFullChunk();
        ChunkCoordIntPair playerPos = playerChunk.getPos();
        return this.isInMergeDistance(playerPos);
    }

    public boolean isInMergeDistance(Entity ent) {
        ChunkCoordIntPair pos = ent.getCurrentChunk().getPos();
        return this.isInMergeDistance(pos);
    }

    public boolean isInMergeDistance(ChunkCoordIntPair pos) {
        for (int i2 = 0; i2 < this.chunks.size(); ++i2) {
            Chunk partitionChunk = this.chunks.get(i2).getFullChunk();
            ChunkCoordIntPair partitionPos = partitionChunk.getPos();
            if ((long)Math.abs(partitionPos.x - pos.x) > this.mergeRadius || (long)Math.abs(partitionPos.z - pos.z) > this.mergeRadius) continue;
            return true;
        }
        return false;
    }
    
    public boolean alreadyHasChunk(PlayerChunk playerChunkToCheck)
    {
        ChunkCoordIntPair posToCheck = playerChunkToCheck.getFullChunk().getPos();
        for(int i = 0; i < this.chunks.size(); i++)
        {
            ChunkCoordIntPair pos = this.chunks.get(i).getFullChunk().getPos();
            if(pos.x == posToCheck.x && pos.z == posToCheck.x)
            {
                return true;
            }
        }
        return false;
    }
    
    public boolean alreadyHasEntity(Entity entityToCheck)
    {
        String uuid = entityToCheck.getName();
        for(int i = 0; i < this.entities.size(); i++)
        {
            Entity entity = this.entities.get(i);
            if(uuid.equals(entity.getName()))
            {
                return true;
            }
        }
        return false;
    }

    public void addChunk(PlayerChunk chunk)
    {
        if(!alreadyHasChunk(chunk))
        {
            chunks.add(chunk);
        }
        else
        {
            //MinecraftServer.LOGGER.warn("MCMT | Attempted to load already loaded chunk!");
        }
    }
    
    public void addEntity(Entity entity) {
        if(!alreadyHasEntity(entity))
        {
            this.entities.add(entity);
            if (entity instanceof EntityEnderDragon) {
                for (EntityComplexPart entitycomplexpart : ((EntityEnderDragon)entity).dT()) {
                    this.entities.add(entitycomplexpart);
                }
            }
        }
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
    
    public void tickEntities(WorldServer world) {
        /*world.timings.tickEntities.startTiming();
        world.worldProvider.l();
        GameProfilerFiller gameprofilerfiller = world.getMethodProfiler();
        gameprofilerfiller.enter("global");*/
        for (int i = 0; i < this.entities.size(); ++i) {
            Entity entity = this.entities.get(i);
            if (entity == null) continue;
            world.a(entity1 -> {
                ++entity1.ticksLived;
                entity1.tick();
            }, entity);
            
            if (entity.dead)
            {
                System.out.println("MCMT | Removed Entity: " + entity.getName());
                this.entities.remove(i--);
            }
        }
        /*
        gameprofilerfiller.exitEnter("regular");
        world.tickingEntities = true;
        
        ActivationRange.activateEntities(world);
        
        world.timings.entityTick.startTiming();
        for (int i3 = 0; i3 < this.entities.size(); ++i3) {
            Entity entity12 = this.entities.get(i3);
            Entity entity2 = entity12.getVehicle();
            if (entity2 != null) {
                if (!entity2.dead && entity2.w(entity12)) continue;
                entity12.stopRiding();
            }
            gameprofilerfiller.enter("tick");
            if (!entity12.dead && !(entity12 instanceof EntityComplexPart)) {
                world.a(world::entityJoinedWorld, entity12);
            }
            gameprofilerfiller.exit();
            gameprofilerfiller.enter("remove");
            if (entity12.dead) {
                world.removeEntityFromChunk(entity12);
                world.unregisterEntity(entity12);
            }
            gameprofilerfiller.exit();
        }
        world.timings.entityTick.stopTiming();
        world.tickingEntities = false;
        gameprofilerfiller.exit();
        world.timings.tickEntities.stopTiming();*/
    }


    public void mergeInto(Partition partition) {
        int j;
        for (j = 0; j < partition.chunks.size(); ++j) {
            this.addChunk(partition.chunks.get(j));
        }
        for (j = 0; j < partition.entities.size(); ++j) {
            this.addEntity(partition.entities.get(j));
        }
    }
}
