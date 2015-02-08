package factorization.util;

import factorization.api.DeltaCoord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Operations on AxisAlignedBB (aka 'Box'), Vec3, Entities, and conversions between the three.
 *
 * Vec3 is an unreliable class; it's best to use this.
 */
public final class SpaceUtil {

    public static final byte GET_POINT_MIN = 0x0;
    public static final byte GET_POINT_MAX = 0x7;

    private static ThreadLocal<ArrayList<ForgeDirection>> direction_cache = new ThreadLocal<ArrayList<ForgeDirection>>();

    public static int determineOrientation(EntityPlayer player) {
        if (player.rotationPitch > 75) {
            return 0;
        }
        if (player.rotationPitch <= -75) {
            return 1;
        }
        return determineFlatOrientation(player);
    }

    public static int determineFlatOrientation(EntityPlayer player) {
        //stolen from BlockPistonBase.determineOrientation. It was reversed, & we handle the y-axis differently
        int var7 = MathHelper.floor_double((double) ((180 + player.rotationYaw) * 4.0F / 360.0F) + 0.5D) & 3;
        return var7 == 0 ? 2 : (var7 == 1 ? 5 : (var7 == 2 ? 3 : (var7 == 3 ? 4 : 0)));
    }

    public static DeltaCoord getFlatDiagonalFacing(EntityPlayer player) {
        double angle = Math.toRadians(90 + player.rotationYaw);
        int dx = Math.cos(angle) > 0 ? 1 : -1;
        int dz = Math.sin(angle) > 0 ? 1 : -1;
        return new DeltaCoord(dx, 0, dz);
    }

    public static byte getOpposite(int dir) {
        return (byte) ForgeDirection.getOrientation(dir).getOpposite().ordinal();
    }


    public static Vec3 copy(Vec3 a) {
        return Vec3.createVectorHelper(a.xCoord, a.yCoord, a.zCoord);
    }

    public static Vec3 newVec() {
        return Vec3.createVectorHelper(0, 0, 0);
    }

    public static Vec3 fromEntPos(Entity ent) {
        return Vec3.createVectorHelper(ent.posX, ent.posY, ent.posZ);
    }

    public static void toEntPos(Entity ent, Vec3 pos) {
        ent.posX = pos.xCoord;
        ent.posY = pos.yCoord;
        ent.posZ = pos.zCoord;
    }

    public static Vec3 set(Vec3 dest, Vec3 orig) {
        dest.xCoord = orig.xCoord;
        dest.yCoord = orig.yCoord;
        dest.zCoord = orig.zCoord;
        return dest;
    }

    public static void setMin(AxisAlignedBB aabb, Vec3 v) {
        aabb.minX = v.xCoord;
        aabb.minY = v.yCoord;
        aabb.minZ = v.zCoord;
    }

    public static Vec3 getMax(AxisAlignedBB aabb) {
        return Vec3.createVectorHelper(aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    public static Vec3 getMin(AxisAlignedBB aabb) {
        return Vec3.createVectorHelper(aabb.minX, aabb.minY, aabb.minZ);
    }

    public static void getMax(AxisAlignedBB box, Vec3 target) {
        target.xCoord = box.maxX;
        target.yCoord = box.maxY;
        target.zCoord = box.maxZ;
    }

    public static void getMin(AxisAlignedBB box, Vec3 target) {
        target.xCoord = box.minX;
        target.yCoord = box.minY;
        target.zCoord = box.minZ;
    }

    public static void setMax(AxisAlignedBB aabb, Vec3 v) {
        aabb.maxX = v.xCoord;
        aabb.maxY = v.yCoord;
        aabb.maxZ = v.zCoord;
    }

    public static void setMiddle(AxisAlignedBB ab, Vec3 v) {
        v.xCoord = (ab.minX + ab.maxX)/2;
        v.yCoord = (ab.minY + ab.maxY)/2;
        v.zCoord = (ab.minZ + ab.maxZ)/2;
    }

    public static AxisAlignedBB newBox() {
        return AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
    }

    public static Vec3 newVec3() {
        return Vec3.createVectorHelper(0, 0, 0);
    }

    public static void sort(Vec3 min, Vec3 max) {
        if (min.xCoord > max.xCoord) {
            double big = min.xCoord;
            min.xCoord = max.xCoord;
            max.xCoord = big;
        }
        if (min.yCoord > max.yCoord) {
            double big = min.yCoord;
            min.yCoord = max.yCoord;
            max.yCoord = big;
        }
        if (min.zCoord > max.zCoord) {
            double big = min.zCoord;
            min.zCoord = max.zCoord;
            max.zCoord = big;
        }
    }

    /**
     * Copies a point on box into target.
     * pointFlags is a bit-flag, like <Z, Y, X>.
     * So if the value is 0b000, then target is the minimum point,
     * and 0b111 the target is the maximum.
     */
    public static void getPoint(AxisAlignedBB box, byte pointFlags, Vec3 target) {
        boolean xSide = (pointFlags & 1) == 1;
        boolean ySide = (pointFlags & 2) == 2;
        boolean zSide = (pointFlags & 4) == 4;
        target.xCoord = xSide ? box.minX : box.maxX;
        target.yCoord = ySide ? box.minY : box.maxY;
        target.zCoord = zSide ? box.minZ : box.maxZ;
    }

    public static double getDiagonalLength(AxisAlignedBB ab) {
        double x = ab.maxX - ab.minX;
        double y = ab.maxY - ab.minY;
        double z = ab.maxZ - ab.minZ;
        return Math.sqrt(x*x + y*y + z*z);
    }

    public static Vec3 averageVec(Vec3 a, Vec3 b) {
        return Vec3.createVectorHelper((a.xCoord + b.xCoord)/2, (a.yCoord + b.yCoord)/2, (a.zCoord + b.zCoord)/2);
    }

    public static void assignVecFrom(Vec3 dest, Vec3 orig) {
        dest.xCoord = orig.xCoord;
        dest.yCoord = orig.yCoord;
        dest.zCoord = orig.zCoord;
    }

    public static Vec3 incrAdd(Vec3 base, Vec3 add) {
        base.xCoord += add.xCoord;
        base.yCoord += add.yCoord;
        base.zCoord += add.zCoord;
        return base;
    }

    public static Vec3 add(Vec3 a, Vec3 b) {
        Vec3 ret = Vec3.createVectorHelper(a.xCoord, a.yCoord, a.zCoord);
        incrAdd(ret, b);
        return ret;
    }

    public static Vec3 incrSubtract(Vec3 base, Vec3 sub) {
        base.xCoord -= sub.xCoord;
        base.yCoord -= sub.yCoord;
        base.zCoord -= sub.zCoord;
        return base;
    }

    public static Vec3 subtract(Vec3 a, Vec3 b) {
        Vec3 ret = Vec3.createVectorHelper(a.xCoord, a.yCoord, a.zCoord);
        incrSubtract(ret, b);
        return ret;
    }



    public static double getAngle(Vec3 a, Vec3 b) {
        return Math.abs(a.dotProduct(b));
    }

    public static void setAABB(AxisAlignedBB target, Vec3 min, Vec3 max) {
        target.minX = min.xCoord;
        target.minY = min.yCoord;
        target.minZ = min.zCoord;
        target.maxX = max.xCoord;
        target.maxY = max.yCoord;
        target.maxZ = max.zCoord;
    }

    public static void incrScale(Vec3 base, double s) {
        base.xCoord *= s;
        base.yCoord *= s;
        base.zCoord *= s;
    }

    public static AxisAlignedBB createAABB(Vec3 min, Vec3 max) {
        double minX = Math.min(min.xCoord, max.xCoord);
        double minY = Math.min(min.yCoord, max.yCoord);
        double minZ = Math.min(min.zCoord, max.zCoord);
        double maxX = Math.max(min.xCoord, max.xCoord);
        double maxY = Math.max(min.yCoord, max.yCoord);
        double maxZ = Math.max(min.zCoord, max.zCoord);
        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static void updateAABB(AxisAlignedBB box, Vec3 min, Vec3 max) {
        box.minX = min.xCoord;
        box.minY = min.yCoord;
        box.minZ = min.zCoord;

        box.maxX = max.xCoord;
        box.maxY = max.yCoord;
        box.maxZ = max.zCoord;
    }

    public static void assignBoxFrom(AxisAlignedBB dest, AxisAlignedBB orig) {
        dest.setBB(orig);
    }

    public static void incrAddCoord(AxisAlignedBB box, Vec3 vec) {
        if (vec.xCoord < box.minX) box.minX = vec.xCoord;
        if (box.maxX < vec.xCoord) box.maxX = vec.xCoord;
        if (vec.yCoord < box.minY) box.minY = vec.yCoord;
        if (box.maxY < vec.yCoord) box.maxY = vec.yCoord;
        if (vec.zCoord < box.minZ) box.minZ = vec.zCoord;
        if (box.maxZ < vec.zCoord) box.maxZ = vec.zCoord;
    }

    public static Vec3[] getCorners(AxisAlignedBB box) {
        return new Vec3[]{
                Vec3.createVectorHelper(box.minX, box.minY, box.minZ),
                Vec3.createVectorHelper(box.minX, box.maxY, box.minZ),
                Vec3.createVectorHelper(box.maxX, box.maxY, box.minZ),
                Vec3.createVectorHelper(box.maxX, box.minY, box.minZ),

                Vec3.createVectorHelper(box.minX, box.minY, box.maxZ),
                Vec3.createVectorHelper(box.minX, box.maxY, box.maxZ),
                Vec3.createVectorHelper(box.maxX, box.maxY, box.maxZ),
                Vec3.createVectorHelper(box.maxX, box.minY, box.maxZ)
        };
    }

    public static ArrayList<ForgeDirection> getRandomDirections(Random rand) {
        ArrayList<ForgeDirection> ret = direction_cache.get();
        if (ret == null) {
            ret = new ArrayList(6);
            for (int i = 0; i < 6; i++) {
                ret.add(ForgeDirection.getOrientation(i));
            }
            direction_cache.set(ret);
        }
        Collections.shuffle(ret, rand);
        return ret;
    }

    public static int getAxis(ForgeDirection fd) {
        if (fd.offsetX != 0) {
            return 1;
        }
        if (fd.offsetY != 0) {
            return 2;
        }
        if (fd.offsetZ != 0) {
            return 3;
        }
        return 0;
    }

    public static boolean isZero(Vec3 vec) {
        return vec.xCoord == 0 && vec.yCoord == 0 && vec.zCoord == 0;
    }
}
