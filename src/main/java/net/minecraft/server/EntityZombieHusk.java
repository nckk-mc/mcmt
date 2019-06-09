package net.minecraft.server;

public class EntityZombieHusk extends EntityZombie {

    public EntityZombieHusk(EntityTypes<? extends EntityZombieHusk> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    public boolean a(GeneratorAccess generatoraccess, EnumMobSpawn enummobspawn) {
        return super.a(generatoraccess, enummobspawn) && (enummobspawn == EnumMobSpawn.SPAWNER || generatoraccess.f(new BlockPosition(this)));
    }

    @Override
    protected boolean J_() {
        return false;
    }

    @Override
    protected SoundEffect getSoundAmbient() {
        return SoundEffects.ENTITY_HUSK_AMBIENT;
    }

    @Override
    protected SoundEffect getSoundHurt(DamageSource damagesource) {
        return SoundEffects.ENTITY_HUSK_HURT;
    }

    @Override
    protected SoundEffect getSoundDeath() {
        return SoundEffects.ENTITY_HUSK_DEATH;
    }

    @Override
    protected SoundEffect getSoundStep() {
        return SoundEffects.ENTITY_HUSK_STEP;
    }

    @Override
    public boolean C(Entity entity) {
        boolean flag = super.C(entity);

        if (flag && this.getItemInMainHand().isEmpty() && entity instanceof EntityLiving) {
            float f = this.world.getDamageScaler(new BlockPosition(this)).b();

            ((EntityLiving) entity).addEffect(new MobEffect(MobEffects.HUNGER, 140 * (int) f));
        }

        return flag;
    }

    @Override
    protected boolean dZ() {
        return true;
    }

    @Override
    protected void eb() {
        this.b(EntityTypes.ZOMBIE);
        this.world.a((EntityHuman) null, 1041, new BlockPosition(this), 0);
    }

    @Override
    protected ItemStack dY() {
        return ItemStack.a;
    }
}
