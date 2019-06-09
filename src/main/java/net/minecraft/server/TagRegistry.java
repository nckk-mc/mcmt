package net.minecraft.server;

import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class TagRegistry implements IReloadListener {

    private final TagsServer<Block> a;
    private final TagsServer<Item> b;
    private final TagsServer<FluidType> c;
    private final TagsServer<EntityTypes<?>> d;

    public TagRegistry() {
        this.a = new TagsServer<>(IRegistry.BLOCK, "tags/blocks", "block");
        this.b = new TagsServer<>(IRegistry.ITEM, "tags/items", "item");
        this.c = new TagsServer<>(IRegistry.FLUID, "tags/fluids", "fluid");
        this.d = new TagsServer<>(IRegistry.ENTITY_TYPE, "tags/entity_types", "entity_type");
    }

    public TagsServer<Block> a() {
        return this.a;
    }

    public TagsServer<Item> b() {
        return this.b;
    }

    public TagsServer<FluidType> c() {
        return this.c;
    }

    public TagsServer<EntityTypes<?>> d() {
        return this.d;
    }

    public void e() {
        this.a.b();
        this.b.b();
        this.c.b();
        this.d.b();
    }

    public void a(PacketDataSerializer packetdataserializer) {
        this.a.a(packetdataserializer);
        this.b.a(packetdataserializer);
        this.c.a(packetdataserializer);
        this.d.a(packetdataserializer);
    }

    public static TagRegistry b(PacketDataSerializer packetdataserializer) {
        TagRegistry tagregistry = new TagRegistry();

        tagregistry.a().b(packetdataserializer);
        tagregistry.b().b(packetdataserializer);
        tagregistry.c().b(packetdataserializer);
        tagregistry.d().b(packetdataserializer);
        return tagregistry;
    }

    @Override
    public CompletableFuture<Void> a(IReloadListener.a ireloadlistener_a, IResourceManager iresourcemanager, GameProfilerFiller gameprofilerfiller, GameProfilerFiller gameprofilerfiller1, Executor executor, Executor executor1) {
        CompletableFuture<Map<MinecraftKey, Tag.a<Block>>> completablefuture = this.a.a(iresourcemanager, executor);
        CompletableFuture<Map<MinecraftKey, Tag.a<Item>>> completablefuture1 = this.b.a(iresourcemanager, executor);
        CompletableFuture<Map<MinecraftKey, Tag.a<FluidType>>> completablefuture2 = this.c.a(iresourcemanager, executor);
        CompletableFuture<Map<MinecraftKey, Tag.a<EntityTypes<?>>>> completablefuture3 = this.d.a(iresourcemanager, executor);
        CompletableFuture<TagRegistry.a> completablefuture4 = completablefuture.thenCombine(completablefuture1, Pair::of).thenCombine(completablefuture2.thenCombine(completablefuture3, Pair::of), (pair, pair1) -> { // CraftBukkit - decompile error
            return new TagRegistry.a((Map) pair.getFirst(), (Map) pair.getSecond(), (Map) pair1.getFirst(), (Map) pair1.getSecond());
        });

        ireloadlistener_a.getClass();
        return completablefuture4.thenCompose(ireloadlistener_a::a).thenAcceptAsync((tagregistry_a) -> {
            this.e();
            this.a.a(tagregistry_a.a);
            this.b.a(tagregistry_a.b);
            this.c.a(tagregistry_a.c);
            this.d.a(tagregistry_a.d);
            TagsBlock.a((Tags) this.a);
            TagsItem.a((Tags) this.b);
            TagsFluid.a((Tags) this.c);
            TagsEntity.a((Tags) this.d);
            // CraftBukkit start
            this.a.version++;
            this.b.version++;
            this.c.version++;
            this.d.version++;
            // CraftBukkit end
        }, executor1);
    }

    public static class a {

        final Map<MinecraftKey, Tag.a<Block>> a;
        final Map<MinecraftKey, Tag.a<Item>> b;
        final Map<MinecraftKey, Tag.a<FluidType>> c;
        final Map<MinecraftKey, Tag.a<EntityTypes<?>>> d;

        public a(Map<MinecraftKey, Tag.a<Block>> map, Map<MinecraftKey, Tag.a<Item>> map1, Map<MinecraftKey, Tag.a<FluidType>> map2, Map<MinecraftKey, Tag.a<EntityTypes<?>>> map3) {
            this.a = map;
            this.b = map1;
            this.c = map2;
            this.d = map3;
        }
    }
}
