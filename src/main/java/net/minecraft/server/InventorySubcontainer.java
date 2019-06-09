package net.minecraft.server;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;

public class InventorySubcontainer implements IInventory, AutoRecipeOutput {

    private final int a;
    public final NonNullList<ItemStack> items;
    private List<IInventoryListener> c;

    public InventorySubcontainer(int i) {
        this.a = i;
        this.items = NonNullList.a(i, ItemStack.a);
    }

    public InventorySubcontainer(ItemStack... aitemstack) {
        this.a = aitemstack.length;
        this.items = NonNullList.a(ItemStack.a, aitemstack);
    }

    public void a(IInventoryListener iinventorylistener) {
        if (this.c == null) {
            this.c = Lists.newArrayList();
        }

        this.c.add(iinventorylistener);
    }

    public void b(IInventoryListener iinventorylistener) {
        this.c.remove(iinventorylistener);
    }

    @Override
    public ItemStack getItem(int i) {
        return i >= 0 && i < this.items.size() ? (ItemStack) this.items.get(i) : ItemStack.a;
    }

    @Override
    public ItemStack splitStack(int i, int j) {
        ItemStack itemstack = ContainerUtil.a(this.items, i, j);

        if (!itemstack.isEmpty()) {
            this.update();
        }

        return itemstack;
    }

    public ItemStack a(ItemStack itemstack) {
        ItemStack itemstack1 = itemstack.cloneItemStack();

        for (int i = 0; i < this.a; ++i) {
            ItemStack itemstack2 = this.getItem(i);

            if (itemstack2.isEmpty()) {
                this.setItem(i, itemstack1);
                this.update();
                return ItemStack.a;
            }

            if (ItemStack.c(itemstack2, itemstack1)) {
                int j = Math.min(this.getMaxStackSize(), itemstack2.getMaxStackSize());
                int k = Math.min(itemstack1.getCount(), j - itemstack2.getCount());

                if (k > 0) {
                    itemstack2.add(k);
                    itemstack1.subtract(k);
                    if (itemstack1.isEmpty()) {
                        this.update();
                        return ItemStack.a;
                    }
                }
            }
        }

        if (itemstack1.getCount() != itemstack.getCount()) {
            this.update();
        }

        return itemstack1;
    }

    @Override
    public ItemStack splitWithoutUpdate(int i) {
        ItemStack itemstack = (ItemStack) this.items.get(i);

        if (itemstack.isEmpty()) {
            return ItemStack.a;
        } else {
            this.items.set(i, ItemStack.a);
            return itemstack;
        }
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.items.set(i, itemstack);
        if (!itemstack.isEmpty() && itemstack.getCount() > this.getMaxStackSize()) {
            itemstack.setCount(this.getMaxStackSize());
        }

        this.update();
    }

    @Override
    public int getSize() {
        return this.a;
    }

    @Override
    public boolean isNotEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public void update() {
        if (this.c != null) {
            Iterator iterator = this.c.iterator();

            while (iterator.hasNext()) {
                IInventoryListener iinventorylistener = (IInventoryListener) iterator.next();

                iinventorylistener.a(this);
            }
        }

    }

    @Override
    public boolean a(EntityHuman entityhuman) {
        return true;
    }

    @Override
    public void clear() {
        this.items.clear();
    }

    @Override
    public void a(AutoRecipeStackManager autorecipestackmanager) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            autorecipestackmanager.b(itemstack);
        }

    }
}
