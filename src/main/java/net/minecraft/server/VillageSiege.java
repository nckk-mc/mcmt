package net.minecraft.server;

import java.util.Iterator;
import javax.annotation.Nullable;

public class VillageSiege {

    private final WorldServer a;
    private boolean b;
    private VillageSiege.State c;
    private int d;
    private int e;
    private int f;
    private int g;
    private int h;

    public VillageSiege(WorldServer worldserver) {
        this.c = VillageSiege.State.SIEGE_DONE;
        this.a = worldserver;
    }

    public void a() {
        if (this.a.J()) {
            this.c = VillageSiege.State.SIEGE_DONE;
            this.b = false;
        } else {
            float f = this.a.j(0.0F);

            if ((double) f == 0.5D) {
                this.c = this.a.random.nextInt(10) == 0 ? VillageSiege.State.SIEGE_TONIGHT : VillageSiege.State.SIEGE_DONE;
            }

            if (this.c != VillageSiege.State.SIEGE_DONE) {
                if (!this.b) {
                    if (!this.b()) {
                        return;
                    }

                    this.b = true;
                }

                if (this.e > 0) {
                    --this.e;
                } else {
                    this.e = 2;
                    if (this.d > 0) {
                        this.c();
                        --this.d;
                    } else {
                        this.c = VillageSiege.State.SIEGE_DONE;
                    }

                }
            }
        }
    }

    private boolean b() {
        Iterator iterator = this.a.getPlayers().iterator();

        while (iterator.hasNext()) {
            EntityHuman entityhuman = (EntityHuman) iterator.next();

            if (!entityhuman.isSpectator()) {
                BlockPosition blockposition = new BlockPosition(entityhuman);

                if (this.a.b_(blockposition)) {
                    for (int i = 0; i < 10; ++i) {
                        float f = this.a.random.nextFloat() * 6.2831855F;

                        this.f = blockposition.getX() + MathHelper.d(MathHelper.cos(f) * 32.0F);
                        this.g = blockposition.getY();
                        this.h = blockposition.getZ() + MathHelper.d(MathHelper.sin(f) * 32.0F);
                    }

                    Vec3D vec3d = this.a(new BlockPosition(this.f, this.g, this.h));

                    if (vec3d != null) {
                        this.e = 0;
                        this.d = 20;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void c() {
        Vec3D vec3d = this.a(new BlockPosition(this.f, this.g, this.h));

        if (vec3d != null) {
            EntityZombie entityzombie;

            try {
                entityzombie = new EntityZombie(this.a);
                entityzombie.prepare(this.a, this.a.getDamageScaler(new BlockPosition(entityzombie)), EnumMobSpawn.EVENT, (GroupDataEntity) null, (NBTTagCompound) null);
            } catch (Exception exception) {
                exception.printStackTrace();
                return;
            }

            entityzombie.setPositionRotation(vec3d.x, vec3d.y, vec3d.z, this.a.random.nextFloat() * 360.0F, 0.0F);
            this.a.addEntity(entityzombie);
        }
    }

    @Nullable
    private Vec3D a(BlockPosition blockposition) {
        for (int i = 0; i < 10; ++i) {
            BlockPosition blockposition1 = blockposition.b(this.a.random.nextInt(16) - 8, this.a.random.nextInt(6) - 3, this.a.random.nextInt(16) - 8);

            if (this.a.b_(blockposition1) && SpawnerCreature.a(EntityPositionTypes.Surface.ON_GROUND, (IWorldReader) this.a, blockposition1, (EntityTypes) null)) {
                return new Vec3D((double) blockposition1.getX(), (double) blockposition1.getY(), (double) blockposition1.getZ());
            }
        }

        return null;
    }

    static enum State {

        SIEGE_CAN_ACTIVATE, SIEGE_TONIGHT, SIEGE_DONE;

        private State() {}
    }
}
