package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public class Pathfinder {

    private final Path a = new Path();
    private final Set<PathPoint> b = Sets.newHashSet();
    private final PathPoint[] c = new PathPoint[32];
    private final int d;
    private PathfinderAbstract e;

    public Pathfinder(PathfinderAbstract pathfinderabstract, int i) {
        this.e = pathfinderabstract;
        this.d = i;
    }

    @Nullable
    public PathEntity a(IWorldReader iworldreader, EntityInsentient entityinsentient, double d0, double d1, double d2, float f) {
        this.a.a();
        this.e.a(iworldreader, entityinsentient);
        PathPoint pathpoint = this.e.b();
        PathPoint pathpoint1 = this.e.a(d0, d1, d2);
        PathEntity pathentity = this.a(pathpoint, pathpoint1, f);

        this.e.a();
        return pathentity;
    }

    @Nullable
    private PathEntity a(PathPoint pathpoint, PathPoint pathpoint1, float f) {
        pathpoint.e = 0.0F;
        pathpoint.f = pathpoint.a(pathpoint1);
        pathpoint.g = pathpoint.f;
        this.a.a();
        this.b.clear();
        this.a.a(pathpoint);
        PathPoint pathpoint2 = pathpoint;
        int i = 0;

        while (!this.a.e()) {
            ++i;
            if (i >= this.d) {
                break;
            }

            PathPoint pathpoint3 = this.a.c();

            pathpoint3.i = true;
            if (pathpoint3.equals(pathpoint1)) {
                pathpoint2 = pathpoint1;
                break;
            }

            if (pathpoint3.a(pathpoint1) < pathpoint2.a(pathpoint1)) {
                pathpoint2 = pathpoint3;
            }

            if (pathpoint3.a(pathpoint1) < f) {
                int j = this.e.a(this.c, pathpoint3);

                for (int k = 0; k < j; ++k) {
                    PathPoint pathpoint4 = this.c[k];
                    float f1 = pathpoint3.a(pathpoint4);

                    pathpoint4.j = pathpoint3.j + f1;
                    float f2 = pathpoint3.e + f1 + pathpoint4.k;

                    if (pathpoint4.j < f && (!pathpoint4.c() || f2 < pathpoint4.e)) {
                        pathpoint4.h = pathpoint3;
                        pathpoint4.e = f2;
                        pathpoint4.f = pathpoint4.a(pathpoint1) * 1.5F + pathpoint4.k;
                        if (pathpoint4.c()) {
                            this.a.a(pathpoint4, pathpoint4.e + pathpoint4.f);
                        } else {
                            pathpoint4.g = pathpoint4.e + pathpoint4.f;
                            this.a.a(pathpoint4);
                        }
                    }
                }
            }
        }

        if (pathpoint2.equals(pathpoint)) {
            return null;
        } else {
            PathEntity pathentity = this.a(pathpoint2);

            return pathentity;
        }
    }

    private PathEntity a(PathPoint pathpoint) {
        List<PathPoint> list = Lists.newArrayList();
        PathPoint pathpoint1 = pathpoint;

        list.add(0, pathpoint);

        while (pathpoint1.h != null) {
            pathpoint1 = pathpoint1.h;
            list.add(0, pathpoint1);
        }

        return new PathEntity(list);
    }
}
