package net.minecraft.server;

import javax.annotation.Nullable;

public abstract class NavigationAbstract {

    protected final EntityInsentient a;
    protected final World b;
    @Nullable
    protected PathEntity c;
    protected double d;
    private final AttributeInstance p;
    protected int e;
    protected int f;
    protected Vec3D g;
    protected Vec3D h;
    protected long i;
    protected long j;
    protected double k;
    protected float l;
    protected boolean m;
    protected long n;
    protected PathfinderAbstract o;
    private BlockPosition q;
    private Pathfinder r;

    public NavigationAbstract(EntityInsentient entityinsentient, World world) {
        this.g = Vec3D.a;
        this.h = Vec3D.a;
        this.l = 0.5F;
        this.a = entityinsentient;
        this.b = world;
        this.p = entityinsentient.getAttributeInstance(GenericAttributes.FOLLOW_RANGE);
        this.r = this.a(MathHelper.floor(this.p.getValue() * 16.0D));
    }

    public BlockPosition h() {
        return this.q;
    }

    protected abstract Pathfinder a(int i);

    public void a(double d0) {
        this.d = d0;
    }

    public float i() {
        return (float) this.p.getValue();
    }

    public boolean j() {
        return this.m;
    }

    public void k() {
        if (this.b.getTime() - this.n > 20L) {
            if (this.q != null) {
                this.c = null;
                this.c = this.b(this.q);
                this.n = this.b.getTime();
                this.m = false;
            }
        } else {
            this.m = true;
        }

    }

    @Nullable
    public final PathEntity a(double d0, double d1, double d2) {
        return this.b(new BlockPosition(d0, d1, d2));
    }

    @Nullable
    public PathEntity b(BlockPosition blockposition) {
        float f = (float) blockposition.getX() + 0.5F;
        float f1 = (float) blockposition.getY() + 0.5F;
        float f2 = (float) blockposition.getZ() + 0.5F;

        return this.a(blockposition, (double) f, (double) f1, (double) f2, 8, false);
    }

    @Nullable
    public PathEntity a(Entity entity) {
        BlockPosition blockposition = new BlockPosition(entity);
        double d0 = entity.locX;
        double d1 = entity.getBoundingBox().minY;
        double d2 = entity.locZ;

        return this.a(blockposition, d0, d1, d2, 16, true);
    }

    @Nullable
    protected PathEntity a(BlockPosition blockposition, double d0, double d1, double d2, int i, boolean flag) {
        if (this.a.locY < 0.0D) {
            return null;
        } else if (!this.a()) {
            return null;
        } else if (this.c != null && !this.c.b() && blockposition.equals(this.q)) {
            return this.c;
        } else {
            this.q = blockposition.immutableCopy();
            float f = this.i();

            this.b.getMethodProfiler().enter("pathfind");
            BlockPosition blockposition1 = flag ? (new BlockPosition(this.a)).up() : new BlockPosition(this.a);
            int j = (int) (f + (float) i);
            ChunkCache chunkcache = new ChunkCache(this.b, blockposition1.b(-j, -j, -j), blockposition1.b(j, j, j));
            PathEntity pathentity = this.r.a(chunkcache, this.a, d0, d1, d2, f);

            this.b.getMethodProfiler().exit();
            return pathentity;
        }
    }

    public boolean a(double d0, double d1, double d2, double d3) {
        return this.a(this.a(d0, d1, d2), d3);
    }

    public boolean a(Entity entity, double d0) {
        PathEntity pathentity = this.a(entity);

        return pathentity != null && this.a(pathentity, d0);
    }

    public boolean a(@Nullable PathEntity pathentity, double d0) {
        if (pathentity == null) {
            this.c = null;
            return false;
        } else {
            if (!pathentity.a(this.c)) {
                this.c = pathentity;
            }

            this.D_();
            if (this.c.e() <= 0) {
                return false;
            } else {
                this.d = d0;
                Vec3D vec3d = this.b();

                this.f = this.e;
                this.g = vec3d;
                return true;
            }
        }
    }

    @Nullable
    public PathEntity l() {
        return this.c;
    }

    public void c() {
        ++this.e;
        if (this.m) {
            this.k();
        }

        if (!this.n()) {
            Vec3D vec3d;

            if (this.a()) {
                this.m();
            } else if (this.c != null && this.c.f() < this.c.e()) {
                vec3d = this.b();
                Vec3D vec3d1 = this.c.a(this.a, this.c.f());

                if (vec3d.y > vec3d1.y && !this.a.onGround && MathHelper.floor(vec3d.x) == MathHelper.floor(vec3d1.x) && MathHelper.floor(vec3d.z) == MathHelper.floor(vec3d1.z)) {
                    this.c.c(this.c.f() + 1);
                }
            }

            PacketDebug.a(this.b, this.a, this.c, this.l);
            if (!this.n()) {
                vec3d = this.c.a((Entity) this.a);
                BlockPosition blockposition = new BlockPosition(vec3d);

                this.a.getControllerMove().a(vec3d.x, this.b.getType(blockposition.down()).isAir() ? vec3d.y : PathfinderNormal.a((IBlockAccess) this.b, blockposition), vec3d.z, this.d);
            }
        }
    }

    protected void m() {
        Vec3D vec3d = this.b();

        this.l = this.a.getWidth() > 0.75F ? this.a.getWidth() / 2.0F : 0.75F - this.a.getWidth() / 2.0F;
        Vec3D vec3d1 = this.c.g();

        if (Math.abs(this.a.locX - (vec3d1.x + 0.5D)) < (double) this.l && Math.abs(this.a.locZ - (vec3d1.z + 0.5D)) < (double) this.l && Math.abs(this.a.locY - vec3d1.y) < 1.0D) {
            this.c.c(this.c.f() + 1);
        }

        this.a(vec3d);
    }

    protected void a(Vec3D vec3d) {
        if (this.e - this.f > 100) {
            if (vec3d.distanceSquared(this.g) < 2.25D) {
                this.o();
            }

            this.f = this.e;
            this.g = vec3d;
        }

        if (this.c != null && !this.c.b()) {
            Vec3D vec3d1 = this.c.g();

            if (vec3d1.equals(this.h)) {
                this.i += SystemUtils.getMonotonicMillis() - this.j;
            } else {
                this.h = vec3d1;
                double d0 = vec3d.f(this.h);

                this.k = this.a.da() > 0.0F ? d0 / (double) this.a.da() * 1000.0D : 0.0D;
            }

            if (this.k > 0.0D && (double) this.i > this.k * 3.0D) {
                this.h = Vec3D.a;
                this.i = 0L;
                this.k = 0.0D;
                this.o();
            }

            this.j = SystemUtils.getMonotonicMillis();
        }

    }

    public boolean n() {
        return this.c == null || this.c.b();
    }

    public void o() {
        this.c = null;
    }

    protected abstract Vec3D b();

    protected abstract boolean a();

    protected boolean p() {
        return this.a.au() || this.a.aC();
    }

    protected void D_() {
        if (this.c != null) {
            for (int i = 0; i < this.c.e(); ++i) {
                PathPoint pathpoint = this.c.a(i);
                PathPoint pathpoint1 = i + 1 < this.c.e() ? this.c.a(i + 1) : null;
                IBlockData iblockdata = this.b.getType(new BlockPosition(pathpoint.a, pathpoint.b, pathpoint.c));
                Block block = iblockdata.getBlock();

                if (block == Blocks.CAULDRON) {
                    this.c.a(i, pathpoint.a(pathpoint.a, pathpoint.b + 1, pathpoint.c));
                    if (pathpoint1 != null && pathpoint.b >= pathpoint1.b) {
                        this.c.a(i + 1, pathpoint1.a(pathpoint1.a, pathpoint.b + 1, pathpoint1.c));
                    }
                }
            }

        }
    }

    protected abstract boolean a(Vec3D vec3d, Vec3D vec3d1, int i, int j, int k);

    public boolean a(BlockPosition blockposition) {
        BlockPosition blockposition1 = blockposition.down();

        return this.b.getType(blockposition1).g(this.b, blockposition1);
    }

    public PathfinderAbstract q() {
        return this.o;
    }

    public void d(boolean flag) {
        this.o.c(flag);
    }

    public boolean r() {
        return this.o.e();
    }

    public void c(BlockPosition blockposition) {
        if (this.c != null && !this.c.b() && this.c.e() != 0) {
            PathPoint pathpoint = this.c.c();
            Vec3D vec3d = new Vec3D(((double) pathpoint.a + this.a.locX) / 2.0D, ((double) pathpoint.b + this.a.locY) / 2.0D, ((double) pathpoint.c + this.a.locZ) / 2.0D);

            if (blockposition.a((IPosition) vec3d, (double) (this.c.e() - this.c.f()))) {
                this.k();
            }

        }
    }
}
