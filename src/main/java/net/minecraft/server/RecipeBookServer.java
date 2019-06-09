package net.minecraft.server;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecipeBookServer extends RecipeBook {

    private static final Logger LOGGER = LogManager.getLogger();
    private final CraftingManager l;

    public RecipeBookServer(CraftingManager craftingmanager) {
        this.l = craftingmanager;
    }

    public int a(Collection<IRecipe<?>> collection, EntityPlayer entityplayer) {
        List<MinecraftKey> list = Lists.newArrayList();
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            IRecipe<?> irecipe = (IRecipe) iterator.next();
            MinecraftKey minecraftkey = irecipe.getKey();

            if (!this.a.contains(minecraftkey) && !irecipe.isComplex()) {
                this.a(minecraftkey);
                this.c(minecraftkey);
                list.add(minecraftkey);
                CriterionTriggers.f.a(entityplayer, irecipe);
                ++i;
            }
        }

        this.a(PacketPlayOutRecipes.Action.ADD, entityplayer, list);
        return i;
    }

    public int b(Collection<IRecipe<?>> collection, EntityPlayer entityplayer) {
        List<MinecraftKey> list = Lists.newArrayList();
        int i = 0;
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            IRecipe<?> irecipe = (IRecipe) iterator.next();
            MinecraftKey minecraftkey = irecipe.getKey();

            if (this.a.contains(minecraftkey)) {
                this.b(minecraftkey);
                list.add(minecraftkey);
                ++i;
            }
        }

        this.a(PacketPlayOutRecipes.Action.REMOVE, entityplayer, list);
        return i;
    }

    private void a(PacketPlayOutRecipes.Action packetplayoutrecipes_action, EntityPlayer entityplayer, List<MinecraftKey> list) {
        entityplayer.playerConnection.sendPacket(new PacketPlayOutRecipes(packetplayoutrecipes_action, list, Collections.emptyList(), this.c, this.d, this.e, this.f));
    }

    public NBTTagCompound save() {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        nbttagcompound.setBoolean("isGuiOpen", this.c);
        nbttagcompound.setBoolean("isFilteringCraftable", this.d);
        nbttagcompound.setBoolean("isFurnaceGuiOpen", this.e);
        nbttagcompound.setBoolean("isFurnaceFilteringCraftable", this.f);
        NBTTagList nbttaglist = new NBTTagList();
        Iterator iterator = this.a.iterator();

        while (iterator.hasNext()) {
            MinecraftKey minecraftkey = (MinecraftKey) iterator.next();

            nbttaglist.add(new NBTTagString(minecraftkey.toString()));
        }

        nbttagcompound.set("recipes", nbttaglist);
        NBTTagList nbttaglist1 = new NBTTagList();
        Iterator iterator1 = this.b.iterator();

        while (iterator1.hasNext()) {
            MinecraftKey minecraftkey1 = (MinecraftKey) iterator1.next();

            nbttaglist1.add(new NBTTagString(minecraftkey1.toString()));
        }

        nbttagcompound.set("toBeDisplayed", nbttaglist1);
        return nbttagcompound;
    }

    public void a(NBTTagCompound nbttagcompound) {
        this.c = nbttagcompound.getBoolean("isGuiOpen");
        this.d = nbttagcompound.getBoolean("isFilteringCraftable");
        this.e = nbttagcompound.getBoolean("isFurnaceGuiOpen");
        this.f = nbttagcompound.getBoolean("isFurnaceFilteringCraftable");
        NBTTagList nbttaglist = nbttagcompound.getList("recipes", 8);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            MinecraftKey minecraftkey = new MinecraftKey(nbttaglist.getString(i));
            Optional<? extends IRecipe<?>> optional = this.l.a(minecraftkey);

            if (!optional.isPresent()) {
                RecipeBookServer.LOGGER.error("Tried to load unrecognized recipe: {} removed now.", minecraftkey);
            } else {
                this.a((IRecipe) optional.get());
            }
        }

        NBTTagList nbttaglist1 = nbttagcompound.getList("toBeDisplayed", 8);

        for (int j = 0; j < nbttaglist1.size(); ++j) {
            MinecraftKey minecraftkey1 = new MinecraftKey(nbttaglist1.getString(j));
            Optional<? extends IRecipe<?>> optional1 = this.l.a(minecraftkey1);

            if (!optional1.isPresent()) {
                RecipeBookServer.LOGGER.error("Tried to load unrecognized recipe: {} removed now.", minecraftkey1);
            } else {
                this.f((IRecipe) optional1.get());
            }
        }

    }

    public void a(EntityPlayer entityplayer) {
        entityplayer.playerConnection.sendPacket(new PacketPlayOutRecipes(PacketPlayOutRecipes.Action.INIT, this.a, this.b, this.c, this.d, this.e, this.f));
    }
}
