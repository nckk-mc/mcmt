package net.minecraft.server;

import javax.annotation.Nullable;

public class EntityHorseSkeleton extends EntityHorseAbstract {

    private final PathfinderGoalHorseTrap bJ = new PathfinderGoalHorseTrap(this);
    private boolean bK;
    private int bL;

    public EntityHorseSkeleton(EntityTypes<? extends EntityHorseSkeleton> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttributeInstance(GenericAttributes.MAX_HEALTH).setValue(15.0D);
        this.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.20000000298023224D);
        this.getAttributeInstance(EntityHorseSkeleton.attributeJumpStrength).setValue(this.ey());
    }

    @Override
    protected void ee() {}

    @Override
    protected SoundEffect getSoundAmbient() {
        super.getSoundAmbient();
        return this.a(TagsFluid.WATER) ? SoundEffects.ENTITY_SKELETON_HORSE_AMBIENT_WATER : SoundEffects.ENTITY_SKELETON_HORSE_AMBIENT;
    }

    @Override
    protected SoundEffect getSoundDeath() {
        super.getSoundDeath();
        return SoundEffects.ENTITY_SKELETON_HORSE_DEATH;
    }

    @Override
    protected SoundEffect getSoundHurt(DamageSource damagesource) {
        super.getSoundHurt(damagesource);
        return SoundEffects.ENTITY_SKELETON_HORSE_HURT;
    }

    @Override
    protected SoundEffect getSoundSwim() {
        if (this.onGround) {
            if (!this.isVehicle()) {
                return SoundEffects.ENTITY_SKELETON_HORSE_STEP_WATER;
            }

            ++this.bI;
            if (this.bI > 5 && this.bI % 3 == 0) {
                return SoundEffects.ENTITY_SKELETON_HORSE_GALLOP_WATER;
            }

            if (this.bI <= 5) {
                return SoundEffects.ENTITY_SKELETON_HORSE_STEP_WATER;
            }
        }

        return SoundEffects.ENTITY_SKELETON_HORSE_SWIM;
    }

    @Override
    protected void d(float f) {
        if (this.onGround) {
            super.d(0.3F);
        } else {
            super.d(Math.min(0.1F, f * 25.0F));
        }

    }

    @Override
    protected void ev() {
        if (this.isInWater()) {
            this.a(SoundEffects.ENTITY_SKELETON_HORSE_JUMP_WATER, 0.4F, 1.0F);
        } else {
            super.ev();
        }

    }

    @Override
    public EnumMonsterType getMonsterType() {
        return EnumMonsterType.UNDEAD;
    }

    @Override
    public double aO() {
        return super.aO() - 0.1875D;
    }

    @Override
    public void movementTick() {
        super.movementTick();
        if (this.dV() && this.bL++ >= 18000) {
            this.die();
        }

    }

    @Override
    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        nbttagcompound.setBoolean("SkeletonTrap", this.dV());
        nbttagcompound.setInt("SkeletonTrapTime", this.bL);
    }

    @Override
    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
        this.r(nbttagcompound.getBoolean("SkeletonTrap"));
        this.bL = nbttagcompound.getInt("SkeletonTrapTime");
    }

    @Override
    public boolean be() {
        return true;
    }

    @Override
    protected float cZ() {
        return 0.96F;
    }

    public boolean dV() {
        return this.bK;
    }

    public void r(boolean flag) {
        if (flag != this.bK) {
            this.bK = flag;
            if (flag) {
                this.goalSelector.a(1, this.bJ);
            } else {
                this.goalSelector.a((PathfinderGoal) this.bJ);
            }

        }
    }

    @Nullable
    @Override
    public EntityAgeable createChild(EntityAgeable entityageable) {
        return (EntityAgeable) EntityTypes.SKELETON_HORSE.a(this.world);
    }

    @Override
    public boolean a(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.b(enumhand);

        if (itemstack.getItem() instanceof ItemMonsterEgg) {
            return super.a(entityhuman, enumhand);
        } else if (!this.isTamed()) {
            return false;
        } else if (this.isBaby()) {
            return super.a(entityhuman, enumhand);
        } else if (entityhuman.isSneaking()) {
            this.e(entityhuman);
            return true;
        } else if (this.isVehicle()) {
            return super.a(entityhuman, enumhand);
        } else {
            if (!itemstack.isEmpty()) {
                if (itemstack.getItem() == Items.SADDLE && !this.eq()) {
                    this.e(entityhuman);
                    return true;
                }

                if (itemstack.a(entityhuman, (EntityLiving) this, enumhand)) {
                    return true;
                }
            }

            this.g(entityhuman);
            return true;
        }
    }
}
