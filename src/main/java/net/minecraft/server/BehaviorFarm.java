package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class BehaviorFarm extends Behavior<EntityVillager> {

    @Nullable
    private BlockPosition a;
    private boolean b;
    private boolean c;
    private long d;
    private int e;

    public BehaviorFarm() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    protected boolean a(WorldServer worldserver, EntityVillager entityvillager) {
        if (!worldserver.getGameRules().getBoolean("mobGriefing")) {
            return false;
        } else if (entityvillager.getVillagerData().getProfession() != VillagerProfession.FARMER) {
            return false;
        } else {
            Set<BlockPosition> set = (Set) (entityvillager.getBehaviorController().getMemory(MemoryModuleType.SECONDARY_JOB_SITE).get()).stream().map(GlobalPos::getBlockPosition).collect(Collectors.toSet()); // CraftBukkit - decompile error
            BlockPosition blockposition = new BlockPosition(entityvillager);
            Stream stream = ImmutableList.of(blockposition.down(), blockposition.south(), blockposition.north(), blockposition.east(), blockposition.west()).stream();

            set.getClass();
            List<BlockPosition> list = (List) stream.filter(set::contains).collect(Collectors.toList());

            this.b = entityvillager.ep();
            this.c = entityvillager.eo();
            List<BlockPosition> list1 = (List) list.stream().map(BlockPosition::up).filter((blockposition1) -> {
                return this.a(worldserver.getType(blockposition1));
            }).collect(Collectors.toList());

            if (!list1.isEmpty()) {
                this.a = (BlockPosition) list1.get(worldserver.getRandom().nextInt(list1.size()));
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean a(IBlockData iblockdata) {
        Block block = iblockdata.getBlock();

        return block instanceof BlockCrops && ((BlockCrops) block).isRipe(iblockdata) && this.c || iblockdata.isAir() && this.b;
    }

    protected void a(WorldServer worldserver, EntityVillager entityvillager, long i) {
        if (i > this.d && this.a != null) {
            entityvillager.getBehaviorController().setMemory(MemoryModuleType.LOOK_TARGET, (new BehaviorTarget(this.a))); // CraftBukkit - decompile error
            entityvillager.getBehaviorController().setMemory(MemoryModuleType.WALK_TARGET, (new MemoryTarget(new BehaviorTarget(this.a), 0.5F, 1))); // CraftBukkit - decompile error
        }

    }

    protected void f(WorldServer worldserver, EntityVillager entityvillager, long i) {
        entityvillager.getBehaviorController().removeMemory(MemoryModuleType.LOOK_TARGET);
        entityvillager.getBehaviorController().removeMemory(MemoryModuleType.WALK_TARGET);
        this.e = 0;
        this.d = i + 40L;
    }

    protected void d(WorldServer worldserver, EntityVillager entityvillager, long i) {
        if (this.e > 15 && this.a != null && i > this.d) {
            IBlockData iblockdata = worldserver.getType(this.a);
            Block block = iblockdata.getBlock();

            if (block instanceof BlockCrops && ((BlockCrops) block).isRipe(iblockdata) && this.c) {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entityvillager, this.a, Blocks.AIR.getBlockData()).isCancelled()) {
                    worldserver.b(this.a, true);
                }
                // CraftBukkit end
            } else if (iblockdata.isAir() && this.b) {
                InventorySubcontainer inventorysubcontainer = entityvillager.getInventory();

                for (int j = 0; j < inventorysubcontainer.getSize(); ++j) {
                    ItemStack itemstack = inventorysubcontainer.getItem(j);
                    boolean flag = false;

                    if (!itemstack.isEmpty()) {
                        // CraftBukkit start
                        Block planted = null;
                        if (itemstack.getItem() == Items.WHEAT_SEEDS) {
                            planted = Blocks.WHEAT;
                            flag = true;
                        } else if (itemstack.getItem() == Items.POTATO) {
                            planted = Blocks.POTATOES;
                            flag = true;
                        } else if (itemstack.getItem() == Items.CARROT) {
                            planted = Blocks.CARROTS;
                            flag = true;
                        } else if (itemstack.getItem() == Items.BEETROOT_SEEDS) {
                            planted = Blocks.BEETROOTS;
                            flag = true;
                        }

                        if (planted != null && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entityvillager, this.a, planted.getBlockData()).isCancelled()) {
                            worldserver.setTypeAndData(this.a, planted.getBlockData(), 3);
                        } else {
                            flag = false;
                        }
                        // CraftBukkit end
                    }

                    if (flag) {
                        worldserver.a((EntityHuman) null, (double) this.a.getX(), (double) this.a.getY(), (double) this.a.getZ(), SoundEffects.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        itemstack.subtract(1);
                        if (itemstack.isEmpty()) {
                            inventorysubcontainer.setItem(j, ItemStack.a);
                        }
                        break;
                    }
                }
            }
        }

        ++this.e;
    }

    protected boolean g(WorldServer worldserver, EntityVillager entityvillager, long i) {
        return this.e < 30;
    }
}
