package factorization.fzds;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import factorization.aabbdebug.AabbDebugger;
import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.api.Quaternion;
import factorization.common.FzConfig;
import factorization.fzds.api.DeltaCapability;
import factorization.fzds.api.IDeltaChunk;
import factorization.fzds.api.IFzdsCustomTeleport;
import factorization.fzds.api.IFzdsEntryControl;
import factorization.shared.Core;
import factorization.shared.FzUtil;

public class DimensionSliceEntity extends IDeltaChunk implements IFzdsEntryControl, IEntityAdditionalSpawnData {
    //Dang, this is a lot of fields
    
    private Coord hammerCell, farCorner;
    Vec3 centerOffset;
    
    private long capabilities = DeltaCapability.of(DeltaCapability.MOVE, DeltaCapability.COLLIDE, DeltaCapability.DRAG, DeltaCapability.REMOVE_ITEM_ENTITIES);
    
    AxisAlignedBB realArea = null;
    MetaAxisAlignedBB metaAABB = null;
    
    private AxisAlignedBB shadowArea = null, shadowCollisionArea = null, realCollisionArea = null, realDragArea = null;
    private boolean needAreaUpdate = true;
    ArrayList<DseCollider> children = null; //null so that we can check if we need to init them easily
    private double last_motion_hash = Double.NaN;
    
    private Quaternion rotation = new Quaternion(), rotationalVelocity = new Quaternion();
    private Quaternion last_shared_rotation = new Quaternion(), last_shared_rotational_velocity = new Quaternion(); //used on the server
    Quaternion prevTickRotation = new Quaternion(); //Client-side
    private double last_shared_posX = -99, last_shared_posY = -99, last_shared_posZ = -99;
    private double last_shared_motionX = 0, last_shared_motionY = 0, last_shared_motionZ = 0;
    
    float scale = 1;
    float opacity = 1;
    
    Object renderInfo = null; //Client-side
    
    PacketProxyingPlayer proxy = null;
    
    public DimensionSliceEntity(World world) {
        super(world);
        if (world == DeltaChunk.getWorld(world)) {
            setDead();
        }
        ignoreFrustumCheck = true; //kinda lame; we should give ourselves a proper bounding box?
        boundingBox.setBounds(0, 0, 0, 0, 0, 0);
    }
    
    public DimensionSliceEntity(World world, Coord lowerCorner, Coord upperCorner) {
        this(world);
        setCorners(lowerCorner, upperCorner);
    }
    
    private void setCorners(Coord lowerCorner, Coord upperCorner) {
        if (lowerCorner.w != DeltaChunk.getWorld(worldObj)) {
            throw new IllegalArgumentException("My corners are not shadow!");
        }
        Coord.sort(lowerCorner, upperCorner);
        this.hammerCell = lowerCorner;
        this.farCorner = upperCorner;
        DeltaCoord dc = upperCorner.difference(lowerCorner);
        centerOffset = Vec3.createVectorHelper(
                dc.x/2,
                dc.y/2,
                dc.z/2);
    }
    
    @Override
    public String toString() {
        return super.toString() + " - from " + hammerCell + "  to  " + farCorner + "   center at " + centerOffset;
    }
    
    @Override
    public Vec3 real2shadow(final Vec3 realVector) {
        // rotate⁻¹(real - DSE) + centerOffset + corner = shadow
        Vec3 buffer = Vec3.createVectorHelper(0, 0, 0);
        buffer.xCoord = realVector.xCoord - posX;
        buffer.yCoord = realVector.yCoord - posY;
        buffer.zCoord = realVector.zCoord - posZ;
        
        rotation.applyReverseRotation(buffer);
        
        buffer.xCoord += hammerCell.x + centerOffset.xCoord;
        buffer.yCoord += hammerCell.y + centerOffset.yCoord;
        buffer.zCoord += hammerCell.z + centerOffset.zCoord;
        return buffer;
    }
    
    @Override
    public Vec3 shadow2real(final Vec3 shadowVector) {
        // rotate(shadow - corner - centerOffset) + DSE = real
        Vec3 buffer = Vec3.createVectorHelper(0, 0, 0);
        buffer.xCoord = shadowVector.xCoord - hammerCell.x - centerOffset.xCoord;
        buffer.yCoord = shadowVector.yCoord - hammerCell.y - centerOffset.yCoord;
        buffer.zCoord = shadowVector.zCoord - hammerCell.z - centerOffset.zCoord;
        
        rotation.applyRotation(buffer);
        
        buffer.xCoord += posX;
        buffer.yCoord += posY;
        buffer.zCoord += posZ;
        return buffer;
    }
    
    @Override
    public void shadow2real(Coord c) {
        c.set(shadow2real(c.createVector()));
        c.w = worldObj;
    }
    
    @Override
    public void real2shadow(Coord c) {
        c.set(real2shadow(c.createVector()));
        c.w = DeltaChunk.getWorld(worldObj);
    }
    
    @Override
    public Coord getCorner() {
        return hammerCell;
    }
    
    @Override
    public Coord getCenter() {
        return hammerCell.center(farCorner);
    }
    
    @Override
    public Coord getFarCorner() {
        return farCorner;
    }
    
    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }
    
    @Override
    public void onCollideWithPlayer(EntityPlayer player) {
        //Maybe adjust our velocities?
    }
    
    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        capabilities = tag.getLong("cap");
        hammerCell = new Coord(null, 0, 0, 0);
        farCorner = hammerCell.copy();
        rotation = Quaternion.loadFromTag(tag, "r");
        rotationalVelocity = Quaternion.loadFromTag(tag, "w");
        centerOffset = Vec3.createVectorHelper(0, 0, 0);
        centerOffset.xCoord = tag.getFloat("cox");
        centerOffset.yCoord = tag.getFloat("coy");
        centerOffset.zCoord = tag.getFloat("coz");
        hammerCell.readFromNBT("min", tag);
        farCorner.readFromNBT("max", tag);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setLong("cap", capabilities);
        rotation.writeToTag(tag, "r");
        rotationalVelocity.writeToTag(tag, "w");
        tag.setFloat("cox", (float) centerOffset.xCoord);
        tag.setFloat("coy", (float) centerOffset.yCoord);
        tag.setFloat("coz", (float) centerOffset.zCoord);
        hammerCell.writeToNBT("min", tag);
        farCorner.writeToNBT("max", tag);
    }
    
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
    
    @Override
    public boolean canBePushed() {
        return false;
    }
    
    private AxisAlignedBB cloneAABB(AxisAlignedBB orig) {
        AxisAlignedBB ret = makeAABB();
        ret.setBB(orig);
        return ret;
    }
    
    private AxisAlignedBB makeAABB() {
        return AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
    }
    
    private AxisAlignedBB offsetAABB(AxisAlignedBB orig, double dx, double dy, double dz) {
        return AxisAlignedBB.getBoundingBox(
                orig.minX + dx, orig.minY + dy, orig.minZ + dz,
                orig.maxX + dx, orig.maxY + dy, orig.maxZ + dz);
    }
    
    private void updateRealArea() {
        Coord c = hammerCell;
        double odx = posX - c.x - centerOffset.xCoord;
        double ody = posY - c.y - centerOffset.yCoord;
        double odz = posZ - c.z - centerOffset.zCoord;
        realArea = offsetAABB(shadowArea, odx, ody, odz); //NOTE: Will need to update realArea when we move
        realCollisionArea = offsetAABB(shadowCollisionArea, odx, ody, odz);
        needAreaUpdate = false;
        //this.boundingBox.setBB(realArea);
        if (children == null && worldObj.isRemote) {
            children = new ArrayList();
            //The array will be filled as the server sends us children
        } else if (children == null && !worldObj.isRemote) {
            children = new ArrayList();
            DeltaCoord size = getFarCorner().difference(getCorner());
            DeltaCoord half = size.scale(0.5);
            for (int dx = 0; dx <= size.x; dx += 16) {
                for (int dy = 0; dy <= size.y; dy += 16) {
                    for (int dz = 0; dz <= size.z; dz += 16) {
                        //could theoretically re-use a single DseCollider for all chunks. Theoretically.
                        DseCollider e = new DseCollider(this, Vec3.createVectorHelper(dx - half.x, dy - half.y, dz - half.z));
                        e.onEntityUpdate();
                        worldObj.spawnEntityInWorld(e);
                    }
                }
            }
        }
        int r = 16;
        if (motionX == 0) {
            odx = Math.round(odx*r)/r;
        }
        if (motionY == 0) {
            ody = Math.round(ody*r)/r;
        }
        if (motionZ == 0) {
            odz = Math.round(odz*r)/r;
        }
        metaAABB = new MetaAxisAlignedBB(this, hammerCell.w);
        metaAABB.setUnderlying(realArea);
    }
    
    private void updateShadowArea() {
        Coord c = getCorner();
        Coord d = getFarCorner();
        AxisAlignedBB start = null;
        for (int x = c.x; x <= d.x; x++) {
            for (int y = c.y; y <= d.y; y++) {
                for (int z = c.z; z <= d.z; z++) {
                    Block block = c.w.getBlock(x, y, z);
                    if (block == null) {
                        continue;
                    }
                    AxisAlignedBB b = block.getCollisionBoundingBoxFromPool(c.w, x, y, z);
                    if (b == null) {
                        continue;
                    }
                    if (start == null) {
                        start = b;
                    } else {
                        start.minX = Math.min(start.minX, b.minX);
                        start.minY = Math.min(start.minY, b.minY);
                        start.minZ = Math.min(start.minZ, b.minZ);
                        start.maxX = Math.max(start.maxX, b.maxX);
                        start.maxY = Math.max(start.maxY, b.maxY);
                        start.maxZ = Math.max(start.maxZ, b.maxZ);
                    }
                }
            }
        }
        if (start == null) {
            if (worldObj.isRemote) {
                return;
            }
            /*if (!can(Caps.EMPTY)) { NORELEASE
                setDead();
            }*/
            shadowArea = makeAABB();
            shadowCollisionArea = makeAABB();
            realArea = makeAABB();
            realCollisionArea = makeAABB();
            return;
        }
        
        shadowArea = cloneAABB(start);
        shadowCollisionArea = shadowArea.expand(2, 2, 2);
        updateRealArea();
    }
    
    public void blocksChanged(int x, int y, int z) {
        if (shadowArea == null) {
            needAreaUpdate = true;
            return;
        }
        needAreaUpdate |= x < shadowArea.minX || y < shadowArea.minY || z < shadowArea.minZ
                || x > shadowArea.maxX || y > shadowArea.maxY || z > shadowArea.maxZ;
    }
    
    @Override
    public void setPosition(double par1, double par3, double par5) {
        super.setPosition(par1, par3, par5);
        needAreaUpdate = true;
    }
    
    boolean updateHashMotion() {
        double hash = motionX*10 + motionY*1010 + motionZ*101010;
        if (hash == last_motion_hash) {
            return false;
        }
        last_motion_hash = hash;
        return true;
    }
    
    private static final IEntitySelector excludeDseRelatedEntities = new IEntitySelector() {
        @Override
        public boolean isEntityApplicable(Entity entity) {
            if (entity instanceof EntityPlayer) {
                return entity.worldObj.isRemote;
            }
            return !(entity.getClass() == DseCollider.class || entity.boundingBox == null);
        }
    };
    
    void updateMotion() {
        if (motionX == 0 && motionY == 0 && motionZ == 0 && rotationalVelocity.isZero()) {
            return;
        }
        if (realArea == null || metaAABB == null) {
            return;
        }
        if (realDragArea == null || updateHashMotion() || !rotationalVelocity.isZero()) {
            realDragArea = realArea.addCoord(motionX, motionY, motionZ);
        }
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;

        Quaternion rotation_copy = new Quaternion(rotation);

        posX += motionX;
        posY += motionY;
        posZ += motionZ;
        rotation.incrMultiply(rotationalVelocity);
        last_shared_rotation.incrMultiply(last_shared_rotational_velocity);

        boolean moved = true;

        if (!noClip && can(DeltaCapability.COLLIDE)) {
            List<AxisAlignedBB> collisions = worldObj.getCollidingBoundingBoxes(this, realArea);
            AxisAlignedBB collision = null;
            for (int i = 0; i < collisions.size(); i++) {
                AxisAlignedBB solid = collisions.get(i);
                if (solid != metaAABB && metaAABB.intersectsWith(solid)) {
                    collision = solid;
                    break;
                }
            }
            if (collision != null) {
                // XXX TODO: This collision is terribad
                posX -= motionX;
                posY -= motionY;
                posZ -= motionZ;
                moved = false;
                setVelocity(0, 0, 0);
                rotationalVelocity.update(0, 0, 0, 0);
                rotation = rotation_copy;
            }
        }
        if (moved && can(DeltaCapability.DRAG)) {
            List ents = worldObj.getEntitiesWithinAABBExcludingEntity(this, metaAABB, excludeDseRelatedEntities);
            for (int i = 0; i < ents.size(); i++) {
                Entity e = (Entity) ents.get(i);
                double friction_expansion = -0.05*Math.sqrt(motionX*motionX + motionY*motionY + motionZ*motionZ);
                AxisAlignedBB ebb = e.boundingBox;
                if (ebb == null) {
                    continue;
                }
                if (motionY > 0) {
                    ebb = ebb.expand(-motionX, -motionY, -motionZ);
                }
                if (motionX != 0 || motionZ != 0) {
                    ebb = ebb.contract(friction_expansion, friction_expansion, friction_expansion);
                }
                // could multiply stuff by velocity
                if (!metaAABB.intersectsWith(ebb)) {
                    //NORELEASE: metaAABB.intersectsWith is very slow, especially with lots of entities
                    continue;
                }
                
                //e.moveEntity(motionX*d, motionY*d, motionZ*d);
                e.onGround = true;
                if (can(DeltaCapability.ENTITY_PHYSICS)) {
                    double instant_scale = 1;
                    double motion_scale = 2; // 2 is kind of fun.
                    Vec3 entityAt = Vec3.createVectorHelper(e.posX, e.posY, e.posZ);
                    Vec3 velocity = getInstantVelocityAtPoint(entityAt);
                    velocity.xCoord *= instant_scale;
                    velocity.yCoord *= instant_scale;
                    velocity.zCoord *= instant_scale;
                    velocity.xCoord = clipVelocity(velocity.xCoord*motion_scale, e.motionX);
                    velocity.yCoord = clipVelocity(velocity.yCoord*motion_scale, e.motionY);
                    velocity.zCoord = clipVelocity(velocity.zCoord*motion_scale, e.motionZ);
                    //e.motionY = (e.motionY + motionY)/2;
                    e.moveEntity(velocity.xCoord, velocity.yCoord, velocity.zCoord);
                    e.prevPosX += velocity.xCoord;
                    e.prevPosY += velocity.yCoord;
                    e.prevPosZ += velocity.zCoord;
                    e.onGround = false;
                    e.motionX = velocity.xCoord;
                    e.motionY = velocity.yCoord;
                    e.motionZ = velocity.zCoord;
                    /*
                    if (motionY > 0 && e.motionY < motionY) {
                        e.motionY = motionY;
                        e.fallDistance += (float) Math.abs(motionY - e.motionY);
                    }
                    e.onGround = true;*/
                } else {
                    double d = 1.01;
                    //e.moveEntity(motionX*d, motionY*d, motionZ*d);
                    e.onGround = true;
                    //e.motionY = (e.motionY + motionY)/2;
                    e.setPosition(e.posX + motionX, e.posY + motionY, e.posZ + motionZ);
                    e.prevPosX += motionX;
                    e.prevPosY += motionY;
                    e.prevPosZ += motionZ;
                    
                    
                    if (motionY > 0 && e.motionY < motionY) {
                        e.motionY = motionY;
                        e.fallDistance += (float) Math.abs(motionY - e.motionY);
                    }
                    e.onGround = true;
                }
            }
            updateRealArea();
        }
    }
    
    double clipVelocity(double impulse_velocity, double current_velocity) {
        if (impulse_velocity < 0) {
            return Math.min(impulse_velocity, current_velocity);
        } else if (impulse_velocity > 0) {
            return Math.max(impulse_velocity, current_velocity);
        } else {
            return current_velocity;
        }
    }
    
    void shareRotationInfo() {
        boolean d0 = !rotation.isEqual(last_shared_rotation), d1 = !rotationalVelocity.isEqual(last_shared_rotational_velocity);
        FMLProxyPacket toSend = null;
        if (d0 && d1) {
            toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotationBoth, getEntityId(), rotation, rotationalVelocity);
            last_shared_rotation.update(rotation);
            last_shared_rotational_velocity.update(rotationalVelocity);
        } else if (d0) {
            toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotation, getEntityId(), rotation);
            last_shared_rotation.update(rotation);
        } else if (d1) {
            toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotationVelocity, getEntityId(), rotationalVelocity);
            last_shared_rotational_velocity.update(rotationalVelocity);
        }
        if (toSend != null) {
            HammerNet.channel.sendToAllAround(toSend, new NetworkRegistry.TargetPoint(dimension, posX, posY, posZ, 64));
        }
    }
    
    void shareDisplacementInfo() {
        last_shared_posX += last_shared_motionX;
        last_shared_posY += last_shared_motionY;
        last_shared_posZ += last_shared_motionZ;
        boolean share_displacement = (last_shared_posX != posX) || (last_shared_posY != posY) || (last_shared_posZ != posZ);
        boolean share_velocity = (last_shared_motionX != motionX) || (last_shared_motionY != motionY) || (last_shared_motionZ != motionZ);
        if (!(share_displacement || share_velocity)) {
            return;
        }
        // Vanilla's packets don't give enough precision. We need ALL of the precision.
        FMLProxyPacket toSend = HammerNet.makePacket(HammerNet.HammerNetType.exactPositionAndMotion, getEntityId(), posX, posY, posZ, motionX, motionY, motionZ);
        HammerNet.channel.sendToAllAround(toSend, new NetworkRegistry.TargetPoint(dimension, posX, posY, posZ, 64));
        
        last_shared_posX = posX;
        last_shared_posY = posY;
        last_shared_posZ = posZ;
        last_shared_motionX = motionX;
        last_shared_motionY = motionY;
        last_shared_motionZ = motionZ;
    }
    
    void debugCollisions() {
        if (!FzConfig.debug_fzds_collisions) return;
        if (this.metaAABB == null) return;
        Coord low = getCorner();
        Coord hig = getFarCorner();
        Coord at = low.copy();
        
        for (int x = low.x; x <= hig.x; x++) {
            for (int y = low.y; y <= hig.y; y++) {
                for (int z = low.z; z <= hig.z; z++) {
                    at.x = x;
                    at.y = y;
                    at.z = z; // NORELEASE NORELEAS
                    if (at.isAir()) continue;
                    AxisAlignedBB box = at.getCollisionBoundingBoxFromPool();
                    if (box == null) continue;
                    AabbDebugger.addBox(this.metaAABB.convertShadowBoxToRealBox(box));
                }
            }
        }
    }
    
    void doUpdate() {
        Core.profileStart("init");
        if (worldObj.isRemote) {
            prevTickRotation.update(rotation);
            rayOutOfDate = true;
            if (ticksExisted == 1) {
                DeltaChunk.getSlices(worldObj).add(this);
            }
            debugCollisions();
        } else if (proxy == null && !isDead) {
            World target_world = can(DeltaCapability.ORACLE) ? worldObj : DeltaChunk.getServerShadowWorld();
            hammerCell.w = farCorner.w = target_world;
            DeltaChunk.getSlices(worldObj).add(this);
            World shadowWorld = DeltaChunk.getServerShadowWorld();
            proxy = new PacketProxyingPlayer(this, shadowWorld);
            proxy.worldObj.spawnEntityInWorld(proxy);
            return;
        }
        Core.profileEnd();
        Core.profileStart("updateMotion");
        updateMotion();
        Core.profileEnd();
        if (!worldObj.isRemote) {
            shareDisplacementInfo();
        }
        if (!worldObj.isRemote && can(DeltaCapability.ROTATE)) {
            shareRotationInfo();
        }
        if (needAreaUpdate) {
            Core.profileStart("updateArea");
            updateShadowArea();
            Core.profileEnd();
        }
        
        if (!worldObj.isRemote) {
            //Do teleportations and stuff
            if (shadowArea == null) {
                updateShadowArea();
            }
            if (shadowArea == null) {
                if (hammerCell.blockExists() && !can(DeltaCapability.EMPTY)) {
                    setDead();
                    Core.logFine("%s dying due to empty area", this.toString());
                } else {
                    needAreaUpdate = true; //Hopefully it will load up soon...
                }
            } else {
                if (can(DeltaCapability.TAKE_INTERIOR_ENTITIES)) {
                    takeInteriorEntities();
                }
                if (can(DeltaCapability.REMOVE_EXTERIOR_ENTITIES)) {
                    removeExteriorEntities();
                }
                if (can(DeltaCapability.REMOVE_ITEM_ENTITIES)) {
                    removeItemEntities();
                }
            }
            if (isDead) {
                endSlice();
                return;
            }
        }
    }
    
    @Override
    public void onEntityUpdate() {
        //We don't want to call super, because it does a bunch of stuff that makes no sense for us.
        Core.profileStart("FZDSEntityTick");
        doUpdate();
        Core.profileEnd();
    }
    
    private void takeInteriorEntities() {
        //Move entities inside our bounds in the real world into the shadow world
        List<Entity> realEntities = worldObj.getEntitiesWithinAABB(Entity.class, realArea); //
        for (int i = 0; i < realEntities.size(); i++) {
            Entity ent = realEntities.get(i);
            if (ent == this) {
                continue;
            }
            takeEntity(ent);
        }
    }
    
    
    
    private void removeExteriorEntities() {
        //Move entities outside the bounds in the shadow world into the real world
        for (int x = hammerCell.x; x <= farCorner.x; x += 16) {
            for (int z = hammerCell.z; z <= farCorner.z; z += 16) {
                if (!worldObj.blockExists(x, 64, z)) {
                    continue;
                }
                Chunk chunk = worldObj.getChunkFromBlockCoords(x, z);
                for (int j = 0; j < chunk.entityLists.length; j++) {
                    List<Entity> l = chunk.entityLists[j];
                    for (int k = 0; k < l.size(); k++) {
                        Entity ent = l.get(k); //This is probably an ArrayList.
                        if (ent.posY < 0 || ent.posY > worldObj.getActualHeight() || ent == this /* oh god what */) {
                            continue;
                        }
                        AxisAlignedBB bb = ent.boundingBox;
                        if (bb != null && !shadowArea.intersectsWith(bb)) {
                            ejectEntity(ent);
                        }
                    }
                }
                
            }
        }
        
    }
    
    private void removeItemEntities() {
        //Move entities outside the bounds in the shadow world into the real world
        World w = hammerCell.w;
        for (int x = hammerCell.x; x <= farCorner.x; x += 16) {
            for (int z = hammerCell.z; z <= farCorner.z; z += 16) {
                if (!w.blockExists(x, 64, z)) {
                    continue;
                }
                Chunk chunk = w.getChunkFromBlockCoords(x, z);
                for (int j = 0; j < chunk.entityLists.length; j++) {
                    List<Entity> l = chunk.entityLists[j];
                    for (int k = 0; k < l.size(); k++) {
                        Entity ent = l.get(k); //This is probably an ArrayList.
                        if (ent.posY < 0 || ent.posY > w.getActualHeight() || ent == this /* oh god what */) {
                            continue;
                        }
                        if (ent instanceof EntityItem) {
                            ejectEntity(ent);
                        }
                    }
                }
                
            }
        }
        
    }
    
    boolean forbidEntityTransfer(Entity ent) {
//		if (ent instanceof EntityPlayerMP) {
//			EntityPlayerMP player = (EntityPlayerMP) ent;
//			if (player.capabilities.isCreativeMode) {
//				return true;
//			}
//		}
        return ent.timeUntilPortal > 0;
    }
    
    void takeEntity(Entity ent) {
        //TODO: Take transformations into account
        if (forbidEntityTransfer(ent)) {
            return;
        }
        IFzdsEntryControl ifec = null;
        if (ent instanceof IFzdsEntryControl) {
            ifec = (IFzdsEntryControl) ent;
            if (!ifec.canEnter(this)) {
                return;
            }
        }
        World shadowWorld = DeltaChunk.getServerShadowWorld();
        Vec3 newLocation = real2shadow(Hammer.ent2vec(ent));
        transferEntity(ent, shadowWorld, newLocation);
        if (ifec != null) {
            ifec.onEnter(this);
        }
    }
    
    void ejectEntity(Entity ent) {
        if (forbidEntityTransfer(ent)) {
            return;
        }
        IFzdsEntryControl ifec = null;
        if (ent instanceof IFzdsEntryControl) {
            ifec = (IFzdsEntryControl) ent;
            if (!ifec.canExit(this)) {
                return;
            }
        }
        Vec3 newLocation = shadow2real(Hammer.ent2vec(ent));
        transferEntity(ent, worldObj, newLocation);
        if (ifec != null) {
            ifec.onExit(this);
        }
    }
    
    void transferEntity(Entity ent, World newWorld, Vec3 newPosition) {
        if (ent instanceof IFzdsCustomTeleport) {
            ((IFzdsCustomTeleport) ent).transferEntity(worldObj, newPosition);
            return;
        }
        if (ent instanceof EntityPlayerMP) {
            if (!can(DeltaCapability.TRANSFER_PLAYERS)) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) ent;
            MinecraftServer ms = MinecraftServer.getServer();
            ServerConfigurationManager manager = ms.getConfigurationManager();
            DSTeleporter tp = new DSTeleporter((WorldServer) newWorld);
            tp.preciseDestination = newPosition;
            manager.transferPlayerToDimension(player, newWorld.provider.dimensionId, tp);
        } else {
            //Inspired by Entity.travelToDimension
            ent.worldObj.removeEntity(ent); //setEntityDead
            ent.isDead = false;
            
            Entity phoenix = EntityList.createEntityByName(EntityList.getEntityString(ent), newWorld); //Like a phoenix rising from the ashes!
            if (phoenix == null) {
                return; //Or not.
            }
            phoenix.copyDataFrom(ent, true);
            phoenix.timeUntilPortal = phoenix.getPortalCooldown();
            ent.isDead = true;
            phoenix.setPosition(newPosition.xCoord, newPosition.yCoord, newPosition.zCoord);
            newWorld.spawnEntityInWorld(phoenix);
        }
    }
    
    void endSlice() {
        DeltaChunk.getSlices(worldObj).remove(this);
        //TODO: teleport entities/blocks into the real world?
    }
    
    @Override
    public void setDead() {
        super.setDead();
        endSlice();
    }
    
    @Override
    public boolean isInRangeToRenderDist(double distSquared) {
        //NOTE: This doesn't actually render entities as far as it should
        int s = 10*16;
        return distSquared < s*s;
    }
    
    @Override
    public boolean canEnter(IDeltaChunk dse) { return false; }
    
    @Override
    public boolean canExit(IDeltaChunk dse) { return true; }
    
    @Override
    public void onEnter(IDeltaChunk dse) { }
    
    @Override
    public void onExit(IDeltaChunk dse) { }
    
    @Override
    public void writeSpawnData(ByteBuf data) {
        data.writeLong(capabilities);
        rotation.write(data);
        rotationalVelocity.write(data);
        data.writeFloat((float) centerOffset.xCoord);
        data.writeFloat((float) centerOffset.yCoord);
        data.writeFloat((float) centerOffset.zCoord);
        hammerCell.writeToStream(data);
        farCorner.writeToStream(data);
        if (can(DeltaCapability.SCALE)) {
            data.writeFloat(scale);
        }
        if (can(DeltaCapability.TRANSPARENT)) {
            data.writeFloat(opacity);
        }
    }

    @Override
    public void readSpawnData(ByteBuf data) {
        try {
            capabilities = data.readLong();
            rotation = Quaternion.read(data);
            rotationalVelocity = Quaternion.read(data);
            centerOffset = Vec3.createVectorHelper(data.readFloat(), data.readFloat(), data.readFloat());
            if (can(DeltaCapability.ORACLE)) {
                hammerCell = new Coord(worldObj, 0, 0, 0);
            } else {
                hammerCell = new Coord(DeltaChunk.getClientShadowWorld(), 0, 0, 0);
            }
            hammerCell.readFromStream(data);
            farCorner = hammerCell.copy();
            farCorner.readFromStream(data);
            if (can(DeltaCapability.SCALE)) {
                scale = data.readFloat();
            }
            if (can(DeltaCapability.TRANSPARENT)) {
                opacity = data.readFloat();
            }
        } catch (IOException e) {
            //Not expected to happen ever
            e.printStackTrace();
        }
        boolean isNew = DeltaChunk.getSlices(worldObj).add(this);
    }
    
    @Override
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int the_number_three) {
        // This function is disabled because entity position packets call it with insufficiently precise variables.
//		this.setPosition(x, y, z);
//		this.setRotation(yaw, pitch);
    }
    
    @Override
    public void addVelocity(double par1, double par3, double par5) {
        super.addVelocity(par1, par3, par5);
        isAirBorne = false; //If this is true, we get packet spam
    }
    
    @Override
    public boolean can(DeltaCapability cap) {
        return cap.in(capabilities);
    }
    
    @Override
    public DimensionSliceEntity permit(DeltaCapability cap) {
        capabilities |= cap.bit;
        if (cap == DeltaCapability.ORACLE) {
            forbid(DeltaCapability.COLLIDE);
            forbid(DeltaCapability.DRAG);
            forbid(DeltaCapability.TAKE_INTERIOR_ENTITIES);
            forbid(DeltaCapability.REMOVE_EXTERIOR_ENTITIES);
            forbid(DeltaCapability.TRANSFER_PLAYERS);
            forbid(DeltaCapability.INTERACT);
            forbid(DeltaCapability.BLOCK_PLACE);
            forbid(DeltaCapability.BLOCK_MINE);
            forbid(DeltaCapability.REMOVE_ITEM_ENTITIES);
            forbid(DeltaCapability.REMOVE_ALL_ENTITIES);
            
            permit(DeltaCapability.SCALE);
            permit(DeltaCapability.TRANSPARENT);
        }
        return this;
    }
    
    @Override
    public DimensionSliceEntity forbid(DeltaCapability cap) {
        capabilities &= ~cap.bit;
        return this;
    }

    @Override
    public Quaternion getRotation() {
        return rotation;
    }

    @Override
    public Quaternion getRotationalVelocity() {
        return rotationalVelocity;
    }

    @Override
    public void setRotation(Quaternion r) {
        rotation = r;
    }

    @Override
    public void setRotationalVelocity(Quaternion w) {
        rotationalVelocity = w;
    }
    
    @Override
    public Vec3 getRotationalCenterOffset() {
        return centerOffset;
    }
    
    @Override
    public void setRotationalCenterOffset(Vec3 newOffset) {
        centerOffset = newOffset;
        if (worldObj.isRemote) return;
        FMLProxyPacket toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotationCenterOffset, getEntityId(), centerOffset);
        HammerNet.channel.sendToAllAround(toSend, new NetworkRegistry.TargetPoint(dimension, posX, posY, posZ, 64));
    }
    
    @Override
    public float getCollisionBorderSize() {
        return 0;
    }
    
    private DseRayTarget rayTarget = null;
    private Entity[] raypart = null;
    private boolean rayOutOfDate = true;
    
    @Override
    public Entity[] getParts() {
        if (!worldObj.isRemote) {
            return null;
        }
        if (!can(DeltaCapability.INTERACT)) {
            return null;
        }
        if (rayOutOfDate) {
            if (raypart == null) {
                raypart = new Entity[1];
                raypart[0] = rayTarget = new DseRayTarget(this);
            }
            rayOutOfDate = false;
            Hammer.proxy.updateRayPosition(rayTarget);
        }
        return raypart;
    }
    
    public Vec3 getInstantVelocityAtPoint(Vec3 real) {
        Vec3 linear = Vec3.createVectorHelper(motionX, motionY, motionZ);
        Vec3 dse_space = real.addVector( -posX - centerOffset.xCoord, -posY - centerOffset.yCoord, -posZ - centerOffset.zCoord);
        Vec3 point_a = dse_space;
        Vec3 point_b = dse_space.addVector(0, 0, 0);
        rotationalVelocity.applyRotation(point_b);
        Vec3 rotational = point_a.subtract(point_b);
        return FzUtil.add(rotational, linear);
    }
}
