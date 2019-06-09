package net.minecraft.server;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CraftingManager implements IResourcePackListener {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final int a = "recipes/".length();
    public static final int b = ".json".length();
    public Map<Recipes<?>, it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<MinecraftKey, IRecipe<?>>> recipes = SystemUtils.a(Maps.newHashMap(), CraftingManager::initializeRecipeMap); // CraftBukkit
    private boolean e;

    public CraftingManager() {}

    @Override
    public void a(IResourceManager iresourcemanager) {
        Gson gson = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();

        this.e = false;
        initializeRecipeMap(this.recipes);
        Iterator iterator = iresourcemanager.a("recipes", (s) -> {
            return s.endsWith(".json");
        }).iterator();

        while (iterator.hasNext()) {
            MinecraftKey minecraftkey = (MinecraftKey) iterator.next();
            String s = minecraftkey.getKey();
            MinecraftKey minecraftkey1 = new MinecraftKey(minecraftkey.b(), s.substring(CraftingManager.a, s.length() - CraftingManager.b));

            try {
                IResource iresource = iresourcemanager.a(minecraftkey);
                Throwable throwable = null;

                try {
                    JsonObject jsonobject = (JsonObject) ChatDeserializer.a(gson, IOUtils.toString(iresource.b(), StandardCharsets.UTF_8), JsonObject.class);

                    if (jsonobject == null) {
                        CraftingManager.LOGGER.error("Couldn't load recipe {} as it's null or empty", minecraftkey1);
                    } else {
                        this.addRecipe(a(minecraftkey1, jsonobject));
                    }
                } catch (Throwable throwable1) {
                    throwable = throwable1;
                    throw throwable1;
                } finally {
                    if (iresource != null) {
                        if (throwable != null) {
                            try {
                                iresource.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        } else {
                            iresource.close();
                        }
                    }

                }
            } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
                CraftingManager.LOGGER.error("Parsing error loading recipe {}", minecraftkey1, jsonparseexception);
                this.e = true;
            } catch (IOException ioexception) {
                CraftingManager.LOGGER.error("Couldn't read custom advancement {} from {}", minecraftkey1, minecraftkey, ioexception);
                this.e = true;
            }
        }

        CraftingManager.LOGGER.info("Loaded {} recipes", this.recipes.size());
    }

    public void addRecipe(IRecipe<?> irecipe) {
        it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<MinecraftKey, IRecipe<?>> map = this.recipes.get(irecipe.g()); // CraftBukkit

        if (map.containsKey(irecipe.getKey())) {
            throw new IllegalStateException("Duplicate recipe ignored with ID " + irecipe.getKey());
        } else {
            map.putAndMoveToFirst(irecipe.getKey(), irecipe); // CraftBukkit - SPIGOT-4638: last recipe gets priority
        }
    }

    public <C extends IInventory, T extends IRecipe<C>> Optional<T> craft(Recipes<T> recipes, C c0, World world) {
        // CraftBukkit start
        Optional<T> recipe = this.a(recipes).values().stream().flatMap((irecipe) -> {
            return SystemUtils.a(recipes.a(irecipe, world, c0));
        }).findFirst();
        c0.setCurrentRecipe(recipe.orElse(null)); // CraftBukkit - Clear recipe when no recipe is found
        // CraftBukkit end
        return recipe;
    }

    public <C extends IInventory, T extends IRecipe<C>> List<T> b(Recipes<T> recipes, C c0, World world) {
        return (List) this.a(recipes).values().stream().flatMap((irecipe) -> {
            return SystemUtils.a(recipes.a(irecipe, world, c0));
        }).sorted(Comparator.comparing((irecipe) -> {
            return irecipe.c().j();
        })).collect(Collectors.toList());
    }

    private <C extends IInventory, T extends IRecipe<C>> Map<MinecraftKey, IRecipe<C>> a(Recipes<T> recipes) {
        return (Map) this.recipes.getOrDefault(recipes, new it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<>()); // CraftBukkit
    }

    public <C extends IInventory, T extends IRecipe<C>> NonNullList<ItemStack> c(Recipes<T> recipes, C c0, World world) {
        Optional<T> optional = this.craft(recipes, c0, world);

        if (optional.isPresent()) {
            return ((IRecipe) optional.get()).b(c0);
        } else {
            NonNullList<ItemStack> nonnulllist = NonNullList.a(c0.getSize(), ItemStack.a);

            for (int i = 0; i < nonnulllist.size(); ++i) {
                nonnulllist.set(i, c0.getItem(i));
            }

            return nonnulllist;
        }
    }

    public Optional<? extends IRecipe<?>> a(MinecraftKey minecraftkey) {
        return this.recipes.values().stream().map((map) -> {
            return map.get(minecraftkey); // CraftBukkit - decompile error
        }).filter(Objects::nonNull).findFirst();
    }

    public Collection<IRecipe<?>> b() {
        return (Collection) this.recipes.values().stream().flatMap((map) -> {
            return map.values().stream();
        }).collect(Collectors.toSet());
    }

    public Stream<MinecraftKey> c() {
        return this.recipes.values().stream().flatMap((map) -> {
            return map.keySet().stream();
        });
    }

    public static IRecipe<?> a(MinecraftKey minecraftkey, JsonObject jsonobject) {
        String s = ChatDeserializer.h(jsonobject, "type");

        return ((RecipeSerializer) IRegistry.RECIPE_SERIALIZER.getOptional(new MinecraftKey(s)).orElseThrow(() -> {
            return new JsonSyntaxException("Invalid or unsupported recipe type '" + s + "'");
        })).a(minecraftkey, jsonobject);
    }

    public static void initializeRecipeMap(Map<Recipes<?>, it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<MinecraftKey, IRecipe<?>>> map) { // CraftBukkit
        map.clear();
        Iterator iterator = IRegistry.RECIPE_TYPE.iterator();

        while (iterator.hasNext()) {
            Recipes<?> recipes = (Recipes) iterator.next();

            map.put(recipes, new it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<>()); // CraftBukkit
        }

    }
}
