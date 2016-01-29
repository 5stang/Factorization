package factorization.fzds.interfaces;

import factorization.api.Coord;
import factorization.api.Quaternion;
import factorization.fzds.DeltaChunk;
import factorization.fzds.Hammer;
import factorization.shared.EntityFz;
import factorization.util.SpaceUtil;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

import javax.annotation.CheckReturnValue;

import static factorization.fzds.interfaces.DeltaCapability.*;

public abstract class IDeltaChunk extends EntityFz {
    //This would be an actual interface, but it needs to extend Entity.
    public IDeltaChunk(World w) { super(w); }
    
    /**
     * Sets the parent. The parent can be null. Reparenting probably isn't synchronized, so set it before spawning in the IDC.
     * Attatches the slice to its parent. When the parent translates & rotates, the child follows, *and* does the same physics calculations as if it had native motion.
     * The child can rotate independently of the parent.
     * Local linear velocities in the child are not supported at this time, nor is dynamic displacement.
     *
     * The return value of {@link factorization.fzds.interfaces.IDeltaChunk#getParentJoint} is determined from the positioning of the parent and this child.
     *
     * @param parent The IDC to be the parent
     * @throws java.lang.IllegalArgumentException if a loop is attempted.
     */
    public abstract void setParent(IDeltaChunk parent);
    
    public abstract IDeltaChunk getParent();

    /**
     * @return the center of location, in the parent's IDC space. Returns null if there is no parent.
     */
    public abstract Vec3 getParentJoint();
    
    public abstract List<IDeltaChunk> getChildren();
    
    /**
     * @return the {@link Quaternion} representing the rotation (theta). This is the global rotation.
     */
    public abstract Quaternion getRotation();

    /**
     * @return the rotational offset. This is relative to getCorner().
     */
    public abstract Vec3 getRotationalCenterOffset();

    /**
     * @return the {@link Quaternion} representing the rotational velocity (omega).
     * 
     * This does not take into account setTargetRotation
     */
    public abstract Quaternion getRotationalVelocity();

    /**
     * Sets the rotation (theta), relative to the world. The default Quaternion represents zero rotation.
     * @param r A {@link Quaternion}
     */
    public abstract void setRotation(Quaternion r);

    /**
     * Sets the rotational velocity (omega), relative to the world. The default Quaternion represents zero angular velocity.
     * The IDC's Entity position is the origin of the rotation, and the getRotationalCenterOffset defines the center of mass.
     * @param w A {@link Quaternion}
     */
    public abstract void setRotationalVelocity(Quaternion w);
    
    /**
     * Orders the IDC to move to a target rotation. This will set the rotational velocity to 0.
     * If setTargetRotation is called before a previous order has completed, the old order will be interrupted,
     * and rotation will continue to the new order from whatever rotation it was at when interrupted.
     * 
     * This method causes the IDC to sweep its rotation between its current rotation, and the target rotation.
     * The target rotation is *NOT* globally relative; it is instead relative to the rotation of its parent.
     * Consider a carousel with a grandfather clock standing on it. The cabinet and the carousel would be represented with 1 IDC,
     * and the hands of the clock would be each represented with an IDC. Let the carousel be still and at its default position,
     * with the clock facing NORTH. When it is 12 o'clock, the hand will have zero rotation, and when it's 6 o'clock, the hand will
     * have half a turn around the NORTH axis. Using this method to order rotations between 6 o'clock and 12 o'clock will have the
     * natural, desired effect.
     * 
     * However, using setRotation or setRotationalVelocity can result in the hand not being flush with the face of the clock, since
     * those methods are relative to the world.
     * 
     * @param target The target rotation. The method will make a copy of that parameter.
     * @param tickTime How many ticks to reach that rotation
     * @param interp How to interpolate between them. SMOOTH is a good default value.
     */
    public abstract void orderTargetRotation(Quaternion target, int tickTime, Interpolation interp);
    
    public abstract boolean hasOrderedRotation();
    
    /**
     * @return how much time remains on the rotation. The return value is undefined if !hasOrderedRotation(), so be sure to check that first.
     */
    public abstract int getRemainingRotationTime();
    
    /**
     * Interrupts the rotation order. The rotation will stop at wherever it was when this method is called.
     */
    public abstract void cancelOrderedRotation();
    
    public abstract Quaternion getOrderedRotationTarget();
    
    /**
     * Helper function.
     * @return the ordered rotation target, else the current rotation.
     */
    public Quaternion getEventualRotation() {
        if (hasOrderedRotation()) return getOrderedRotationTarget();
        return getRotation();
    }
    
    
    
    /**
     * @param newOffset The new rotational offset. This is relative to getCorner(). Calling this method after the entity has been
     *                  spawned is untested.
     */
    public abstract void setRotationalCenterOffset(Vec3 newOffset);
    
    /**
     * Helper function.
     * @param newCenter takes a real-world vector, and makes it the center of rotation, preserving the apparent position of the slice. (The IDC's actual position will change, however.)
     * 
     * TODO: If the rotation is not zero, things won't be preserved?
     * TODO: Flashy glitchiness.
     */
    public void changeRotationCenter(Vec3 newCenter) {
        Vec3 origCenter = getRotationalCenterOffset();
        Coord min = getCorner();
        Vec3 shadowCenter = real2shadow(newCenter).subtract(min.x, min.y, min.z);
        setRotationalCenterOffset(shadowCenter);
        Vec3 ds = origCenter.subtract(shadowCenter).add(SpaceUtil.fromEntPos(this));
        setPosition(ds.xCoord, ds.yCoord, ds.zCoord);
    }
    
    /**
     * Helper function. Applies all parent rotations to rot.
     */
    public void multiplyParentRotations(Quaternion rot) {
        IDeltaChunk parent = getParent();
        while (parent != null) {
            parent.getRotation().incrToOtherMultiply(rot);
            parent = parent.getParent();
        }
    }
    
    
    /**
     * Checks if the {@link DeltaCapability} is enabled.
     * @param cap A {@link DeltaCapability}
     * @return true if enabled
     */
    public abstract boolean can(DeltaCapability cap);

    /**
     * Enables a {@link DeltaCapability}. This should be done before spawning the entity, as clients will not get updates to this.
     * @param cap A {@link DeltaCapability}
     * @return this
     */
    public abstract IDeltaChunk permit(DeltaCapability cap);

    /**
     * Disables a {@link DeltaCapability}. This should be done before spawning the entity, as clients will not get updates to this.
     * @param cap A {@link DeltaCapability}
     * @return this
     */
    public abstract IDeltaChunk forbid(DeltaCapability cap);

    public IDeltaChunk permit(DeltaCapability... caps) {
        for (DeltaCapability cap : caps) permit(cap);
        return this;
    }

    public IDeltaChunk forbid(DeltaCapability... caps) {
        for (DeltaCapability cap : caps) forbid(cap);
        return this;
    }
    
    public void loadUsualCapabilities() {
        for (DeltaCapability cap : DeltaCapability.values()) {
            forbid(cap);
        }
        for (DeltaCapability cap : new DeltaCapability[] {
                COLLIDE,
                MOVE,
                ROTATE,
                DRAG,
                REMOVE_EXTERIOR_ENTITIES,
                INTERACT,
                BLOCK_PLACE,
                BLOCK_MINE,
                REMOVE_ITEM_ENTITIES,
                ENTITY_PHYSICS,
        }) {
            permit(cap);
        }
    }
    
    
    
    /**
     * @param realVector A {@link Vec3} in real-world coordinates.
     * @return a new {@link Vec3} in shadow coordinates with translations & rotations applied.
     */
    @CheckReturnValue
    public abstract Vec3 real2shadow(final Vec3 realVector);

    /**
     * @param shadowVector A {@link Vec3} in shadow coordinates
     * @return a new {@link Vec3} in real-world coordinates with translations & rotations unapplied
     */
    @CheckReturnValue
    public abstract Vec3 shadow2real(final Vec3 shadowVector);

    public World getShadowWorld() {
        if (worldObj.isRemote) {
            return DeltaChunk.getClientShadowWorld();
        } else {
            return DeltaChunk.getServerShadowWorld();
        }
    }

    @CheckReturnValue
    public Coord real2shadow(Coord real) {
        Vec3 s = real2shadow(real.toMiddleVector());
        return new Coord(getShadowWorld(), (int) Math.floor(s.xCoord), (int) Math.floor(s.yCoord), (int) Math.floor(s.zCoord));
    }

    @CheckReturnValue
    public Coord shadow2real(Coord shadow) {
        Vec3 r = shadow2real(shadow.toMiddleVector());
        return new Coord(worldObj, (int) Math.floor(r.xCoord), (int) Math.floor(r.yCoord), (int) Math.floor(r.zCoord));
    }

    @CheckReturnValue
    public BlockPos real2shadow(BlockPos real) {
        return new BlockPos(real2shadow(new Vec3(real)));
    }

    @CheckReturnValue
    public BlockPos shadow2real(BlockPos shadow) {
        return new BlockPos(shadow2real(new Vec3(shadow)));
    }

    /**
     * @param shadowBox Box to convert from shadow space to real space.
     * @return The real-space box. Warning: not guaranteed to match MetaAABB's converted collision box!
     */
    @CheckReturnValue
    public abstract AxisAlignedBB shadow2real(AxisAlignedBB shadowBox);

    /**
     * @param realBox Box to convert from real space to shadow space.
     * @return The shadow-space box. Warning: Not guaranteed to match MetaAABB's converted collision box!
     */
    @CheckReturnValue
    public abstract AxisAlignedBB real2shadow(AxisAlignedBB realBox);

    /**
     * Helper method.
     * @param dir EnumFacing in shadow space
     * @return dir with rotation applied. Slight possibility for it to be the UNKNOWN direction.
     */
    @CheckReturnValue
    public EnumFacing shadow2real(EnumFacing dir) {
        Vec3 v = SpaceUtil.fromDirection(dir);
        getRotation().applyRotation(v);
        return SpaceUtil.round(v, null);
    }

    /**
     * Helper method.
     * @param dir EnumFacing in real space
     * @return dir with reverse-rotation applied. Slight possibility for it to be the UNKNOWN direction.
     */
    @CheckReturnValue
    public EnumFacing real2shadow(EnumFacing dir) {
        Vec3 v = SpaceUtil.fromDirection(dir);
        getRotation().applyReverseRotation(v);
        return SpaceUtil.round(v, null);
    }
    
    /**
     * @return the lower corner, in shadow coordinates.
     */
    public abstract Coord getCorner();

    /**
     * @return the center, in shadow coordinates. (Justs averages getCorner() and getFarCorner())
     */
    public Coord getCenter() {
        return getCorner().center(getFarCorner());
    }

    /**
     * @return the upper corner, in shadow coordinates
     */
    public abstract Coord getFarCorner();
    
    public void setPartName(String name) {
        if (name == null) throw new NullPointerException();
        this.partName = name;
    }
    
    protected String partName = "";
    
    @Override
    public String toString() {
        String ret;
        if (partName != null && !partName.isEmpty()) {
            ret = "[DSE " + partName + " " + getEntityId() + "]";
        } else {
            ret = super.toString() + " - from " + getCorner() + "  to  " + getFarCorner() +
            "   center at " + getRotationalCenterOffset();
        }
        if (getParent() != null) {
            ret += ":PARENT=" + getParent().getEntityId();
        }
        ret += " " + ((int) posX) + " " + ((int) posY) + " " + ((int) posZ);
        return ret;
    }
    
    protected IDCController controller = IDCController.default_controller;
    
    /**
     * @return the object set by setController.
     */
    public IDCController getController() {
        return controller;
    }
    
    /**
     * @param controller The controller responsible for this IDC. The IDC will not track this value across serializations; it is the controller's responsibility to set it.
     */
    public void setController(IDCController controller) {
        if (controller == null) controller = IDCController.default_controller;
        this.controller = controller;
    }

    /**
     * Same thing as Entity.setVelocity, but not client-only.
     */
    @Override
    public void setVelocity(double vx, double vy, double vz) {
        this.motionX = vx;
        this.motionY = vy;
        this.motionZ = vz;
    }

    /**
     * Vec3 version of {@link IDeltaChunk#setVelocity(double, double, double)}.
     * @param v The new velocity.
     */
    public void setVelocity(Vec3 v) {
        setVelocity(v.xCoord, v.yCoord, v.zCoord);
    }

    /**
     * Checks if the IDC's area is clear. If it is not clear, then the controller is
     * notified using {@link IDCController#collidedWithWorld(World, AxisAlignedBB, World, AxisAlignedBB)}.
     */
    public abstract void findAnyCollidingBox();
}
