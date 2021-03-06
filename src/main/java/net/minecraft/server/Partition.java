package net.minecraft.server;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerInternalException;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;
import java.util.function.Consumer;

import org.bukkit.util.BoundingBox;
import org.spigotmc.ActivationRange;

public class Partition {

    public List<PlayerChunk> chunks;
    public List<Entity> entities;
    public TickListServer<Block> blockTickListServer;
    public TickListServer<FluidType> fluidTickListServer;
    private long age;
    private long lastTickTime;

    Partition(WorldServer world) {
        this.chunks = new ArrayList<>();
        this.entities = new ArrayList<>();
        this.age = 0;

        // Create individual TickListServer's for each Partition
        this.blockTickListServer = new TickListServer<Block>(world, (block) -> {
            return block == null || block.getBlockData().isAir();
        }, IRegistry.BLOCK::getKey, IRegistry.BLOCK::get, world::b, "Blocks");

        this.fluidTickListServer = new TickListServer<FluidType>(world, (fluidtype) -> {
            return fluidtype == null || fluidtype == FluidTypes.EMPTY;
        }, IRegistry.FLUID::getKey, IRegistry.FLUID::get, world::a, "Fluids");
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
       return isInRadius(pos, this.mergeRadius);
    }

    public boolean isInRadius(ChunkCoordIntPair pos, long radius) {
        for (int i = 0; i < this.chunks.size(); ++i) {
            Chunk partitionChunk = this.chunks.get(i).getFullChunk();
            ChunkCoordIntPair partitionPos = partitionChunk.getPos();
            if ((long)Math.abs(partitionPos.x - pos.x) <= radius && (long)Math.abs(partitionPos.z - pos.z) <= radius) {
                return true;
            }
        }
        for (int i = 0; i < this.entities.size(); ++i) {
            Chunk partitionChunk = this.entities.get(i).getCurrentChunk();
            if(partitionChunk != null) {
                ChunkCoordIntPair partitionPos = partitionChunk.getPos();
                if ((long) Math.abs(partitionPos.x - pos.x) <= radius && (long) Math.abs(partitionPos.z - pos.z) <= radius) {
                    return true;
                }
            }
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

    public TickListServer<Block> getBlockTickList()
    {
        return this.blockTickListServer;
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
            //MinecraftServer.LOGGER.warn("MCMT | Attempted to addChunk already loaded chunk!");
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

    public BoundingBox getChunkBoundingBox() {
        double minX = 1e99, maxX = -1e99;
        double minY = 0, maxY = 255;
        double minZ = 1e99, maxZ = -1e99;
        for (int i=0; i<this.chunks.size(); i++) {
            PlayerChunk playerChunk = this.chunks.get(i);
            ChunkCoordIntPair pos = playerChunk.getFullChunk().getPos();

            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public BoundingBox getEntityBoundingBox() {
        double minX = 1e99, maxX = -1e99;
        double minY = 0, maxY = 255;
        double minZ = 1e99, maxZ = -1e99;
        for (int i=0; i<this.entities.size(); i++) {
            Entity entity = this.entities.get(i);
            ChunkCoordIntPair pos = entity.getCurrentChunk().getPos();
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public BoundingBox getBlockTickBoundingBox() {
        double minX = 1e99, maxX = -1e99;
        double minY = 0, maxY = 255;
        double minZ = 1e99, maxZ = -1e99;
        for (NextTickListEntry<Block> entry : blockTickListServer.getNextTickList()) {
            BlockPosition blockPosition = entry.a;
            int x = Math.floorDiv(blockPosition.getX(), 16);
            int z = Math.floorDiv(blockPosition.getZ(), 16);
            ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z);
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public BoundingBox getFluidTickBoundingBox() {
        double minX = 1e99, maxX = -1e99;
        double minY = 0, maxY = 255;
        double minZ = 1e99, maxZ = -1e99;
        for (NextTickListEntry<FluidType> entry : fluidTickListServer.getNextTickList()) {
            BlockPosition blockPosition = entry.a;
            int x = Math.floorDiv(blockPosition.getX(), 16);
            int z = Math.floorDiv(blockPosition.getZ(), 16);
            ChunkCoordIntPair pos = new ChunkCoordIntPair(x, z);
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean removeChunk(PlayerChunk chunk)
    {
        return this.chunks.remove(chunk);
    }

    public boolean removeEntitiy(Entity entity)
    {
        return this.entities.remove(entity);
    }

    public void tickChunks(WorldServer world, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters) {
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

    boolean isEmpty()
    {
        return (this.chunks.isEmpty() && this.entities.isEmpty() && this.age > 5);
    }
    
    public void tickEntities(WorldServer world, Profile profile) {

        world.timings.tickEntities.startTiming();

        profile.profile("doBossBattle", p -> {
            world.worldProvider.doBossBattle();
        });

        GameProfilerFiller gameprofilerfiller = world.getMethodProfiler();
        gameprofilerfiller.enter("global");

        profile.profile("entityLoop", p -> {
            for (int i = 0; i < this.entities.size(); ++i) {
                Entity entity = this.entities.get(i);

                if (entity == null)
                {
                    System.out.println("MCMT | Removed Entity: " + entity.getName());
                    this.entities.remove(i--);
                    continue;
                }

                try {
                    world.timings.tickEntities.startTiming();
                    entity.tickTimer.startTiming();
                    // Spigot end
                    entity.ticksLived++;
                    entity.tick();
                } catch (Throwable throwable) {
                    // Paper start - Prevent tile entity and entity crashes
                    String msg = "Entity threw exception at " + entity.world.getWorld().getName() + ":" + entity.locX + "," + entity.locY + "," + entity.locZ;
                    System.err.println(msg);
                    throwable.printStackTrace();
                    world.getServer().getPluginManager().callEvent(new ServerExceptionEvent(new ServerInternalException(msg, throwable)));
                    entity.dead = true;
                    // Paper end
                } finally {
                    entity.tickTimer.stopTiming();
                    world.timings.tickEntities.stopTiming();
                }

                if (entity.dead)
                {
                    System.out.println("MCMT | Removed Entity: " + entity.getName());
                    this.entities.remove(i--);
                }
            }
        });

        gameprofilerfiller.exitEnter("regular");
        world.tickingEntities = true;

        profile.profile("activateEntities", p -> {
            ActivationRange.activateEntities(world);
        });

        world.timings.entityTick.startTiming();
        profile.profile("stop and remove", p -> {
            for (int i = 0; i < this.entities.size(); ++i) {
                Entity entity = this.entities.get(i);
                Entity vehicle = entity.getVehicle();
                if (vehicle != null) {
                    if (!vehicle.dead && vehicle.w(entity)) continue;
                    entity.stopRiding();
                }
                if (!entity.dead && !(entity instanceof EntityComplexPart)) {
                    world.a(world::entityJoinedWorld, entity);
                }

                if (entity.dead) {
                    world.removeEntityFromChunk(entity);
                    world.unregisterEntity(entity);
                }
            }
        });
        world.timings.entityTick.stopTiming(); // Spigot
        world.tickingEntities = false;

        gameprofilerfiller.exit();
        world.timings.tickEntities.stopTiming(); // Spigot
    }

    public void tick(WorldServer worldServer, PlayerChunkMap playerChunkMap, boolean allowAnimals, boolean allowMonsters) {
        profiler.profile("tick", tickProfile -> {
            tickProfile.profile("tickChunks", profile -> {
                tickChunks(worldServer, playerChunkMap, allowAnimals, allowMonsters);
            });

            tickProfile.profile("tickEntities", profile -> {
                tickEntities(worldServer, profile);
            });

            tickProfile.profile("fluidTickListServer", profile -> {
                fluidTickListServer.doTick();
            });

            tickProfile.profile("blockTickListServer", profile -> {
                blockTickListServer.doTick();
            });

            this.age++;
        });
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

    public static class Profile {
        private String key;
        private long started; // ns
        private long stopped; // ns
        private long elapsed;
        private Map<String, Profile> children = new HashMap<>();
        public Profile(String key) {
            this.key = key;
            this.started = System.nanoTime();
        }
        public Profile start(String key) {
            Profile child = children.get(key);
            if (child == null) {
                child = new Profile(key);
                children.put(key, child);
            } else {
                child.restart();
            }
            return child;
        }
        void restart() {
            this.started = System.nanoTime();
        }
        public void stop() {
            this.stopped = System.nanoTime();
            elapsed += this.stopped - this.started;
        }

        public String getKey() { return this.key;}
        public Collection<Profile> getChildren() { return this.children.values(); }
        public long getElapsed() { return this.elapsed; }

        public void profile(String key, Consumer<Profile> target) {
            Profile profile = start(key);
            target.accept(profile);
            profile.stop();
        }
    }
    private Profile profiler = new Profile("Partition");
    public Profile startProfiling() {
        profiler = new Profile("Partition");
        return profiler;
    }
    public Profile stopProfiling() {
        profiler.stop();
        return profiler;
    }
}
