package factorization.fzds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import factorization.api.Coord;
import factorization.api.Quaternion;
import factorization.common.Core;
import factorization.fzds.api.IFzdsCustomTeleport;
import factorization.fzds.api.IFzdsEntryControl;

public class DimensionSliceEntity extends Entity implements IFzdsEntryControl, IEntityAdditionalSpawnData {
    int cell;
    private int capabilities = Caps.of(Caps.MOVE, Caps.COLLIDE, Caps.DRAG);
    
    public Coord hammerCell;
    Object renderInfo = null;
    AxisAlignedBB shadowArea = null, shadowCollisionArea = null, realArea = null, realCollisionArea = null, realDragArea = null;
    MetaAxisAlignedBB metaAABB = null;
    public Quaternion rotation = new Quaternion(), rotationalVelocity = new Quaternion();
    public Quaternion prevTickRotation = new Quaternion(); //used on the client
    private Quaternion last_shared_rotation = new Quaternion(), last_shared_rotational_velocity = new Quaternion(); //used on the server
    
    PacketProxyingPlayer proxy = null;
    boolean needAreaUpdate = true;
    ArrayList<DseCollider> children = null; //we don't init here so that it's easy to see if we need to init it
    
    static final double offsetXZ = Hammer.cellWidth*16/2.0;
    static final double offsetY = 0; //TODO?
    private double last_motion_hash = Double.NaN;
    
    public DimensionSliceEntity(World world) {
        super(world);
        ignoreFrustumCheck = true; //kinda lame; we should give ourselves a proper bounding box?
        boundingBox.setBounds(0, 0, 0, 0, 0, 0);
    }
    
    public DimensionSliceEntity(World world, int cell) {
        this(world);
        this.cell = cell;
        this.hammerCell = Hammer.getCellCorner(world, cell);
    }
    
    private static Vec3 buffer = Vec3.createVectorHelper(0, 0, 0);
    
    public Vec3 real2shadow(Vec3 realCoords) {
        //TODO NOTE: This ignores transformations! Need to fix!
        double diffX = realCoords.xCoord + offsetXZ - posX;
        double diffY = realCoords.yCoord + offsetY - posY;
        double diffZ = realCoords.zCoord + offsetXZ - posZ;
        buffer.xCoord = hammerCell.x + diffX;
        buffer.yCoord = hammerCell.y + diffY;
        buffer.zCoord = hammerCell.z + diffZ;
        return buffer;
    }
    
    public Vec3 shadow2real(Vec3 shadowCoords) {
        double diffX = shadowCoords.xCoord - hammerCell.x;
        double diffY = shadowCoords.yCoord - hammerCell.y;
        double diffZ = shadowCoords.zCoord - hammerCell.z;
        buffer.xCoord = diffX - offsetXZ;
        buffer.yCoord = diffY - offsetY;
        buffer.zCoord = diffZ - offsetXZ;
        
        rotation.applyRotation(buffer);
        
        buffer.xCoord += posX;
        buffer.yCoord += posY;
        buffer.zCoord += posZ;
        return buffer;
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
        cell = tag.getInteger("cell");
        capabilities = tag.getInteger("cap");
        rotation = Quaternion.loadFromTag(tag, "r");
        rotationalVelocity = Quaternion.loadFromTag(tag, "w");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("cell", cell);
        tag.setInteger("cap", capabilities);
        rotation.writeToTag(tag, "r");
        rotationalVelocity.writeToTag(tag, "w");
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
    
    public void updateRealArea() {
        Coord c = hammerCell;
        double odx = posX - c.x - offsetXZ, ody = posY - c.y - offsetY, odz = posZ - c.z - offsetXZ;
        realArea = offsetAABB(shadowArea, odx, ody, odz); //NOTE: Will need to update realArea when we move
        realCollisionArea = offsetAABB(shadowCollisionArea, odx, ody, odz);
        needAreaUpdate = false;
        //this.boundingBox.setBB(realArea);
        if (children == null && worldObj.isRemote) {
            children = new ArrayList((int) Math.pow(Hammer.cellWidth, 3));
            //The array will be filled as the server sends us children
        } else if (children == null && !worldObj.isRemote) {
            children = new ArrayList((int) Math.pow(Hammer.cellWidth, 3));
            int width = Hammer.cellWidth*16;
            int height = Hammer.cellHeight*16;
            for (int dx = -width/2; dx < width/2; dx += 16) {
                for (int dy = 0; dy < height; dy += 16) {
                    for (int dz = -width/2; dz < width/2; dz += 16) {
                        //could theoretically re-use a single DseCollider for each chunk. Theoretically.
                        Entity e = new DseCollider(this, Vec3.createVectorHelper(dx, dy, dz));
                        e.onEntityUpdate();
                        worldObj.spawnEntityInWorld(e);
                    }
                }
            }
        }
        metaAABB = new MetaAxisAlignedBB(hammerCell.w, shadowArea, Vec3.createVectorHelper(odx, ody, odz), rotation, real2shadow(Vec3.createVectorHelper(posX, posY, posZ)));
        metaAABB.setUnderlying(realArea);
    }
    
    public void updateShadowArea() {
        Coord c = hammerCell;
        Coord d = Hammer.getCellOppositeCorner(worldObj, this.cell);
        
        AxisAlignedBB start = null;
        for (int x = c.x; x < d.x; x++) {
            for (int y = c.y; y < d.y; y++) {
                for (int z = c.z; z < d.z; z++) {
                    Block block = Block.blocksList[c.w.getBlockId(x, y, z)];
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
            setDead();
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
    
    void init() {
        if (hammerCell == null) {
            this.hammerCell = Hammer.getCellCorner(worldObj, cell);
        }
        Hammer.getSlices(worldObj).add(this);
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
        
        if (!noClip && can(Caps.COLLIDE)) {
            List<AxisAlignedBB> collisions = worldObj.getCollidingBoundingBoxes(this, realArea);
            AxisAlignedBB collision = null;
            for (int i = 0; i < collisions.size(); i++) {
                AxisAlignedBB solid = collisions.get(i);
                if (metaAABB.intersectsWith(solid) && solid != metaAABB) {
                    collision = solid;
                    break;
                }
            }
            if (collision != null) {
                //XXX TODO: This collision is terribad
                posX -= motionX;
                posY -= motionY;
                posZ -= motionZ;
                moved = false;
                setVelocity(0, 0, 0);
                rotationalVelocity.update(0, 0, 0, 0);
                rotation = rotation_copy;
            }
        }
        if (moved) {
            double s = 5;
            double dx = (posX - prevPosX)*s;
            double dy = (posY - prevPosY)*s;
            double dz = (posZ - prevPosZ)*s;
            List ents = worldObj.getEntitiesWithinAABBExcludingEntity(this, realDragArea);
            for (int i = 0; i < ents.size(); i++) {
                Entity e = (Entity) ents.get(i);
                e.posX += dx;
                e.posY += dy;
                e.posZ += dz;
            }
            updateRealArea();
        }
    }
    
    void shareRotationInfo() {
        boolean d0 = !rotation.isEqual(last_shared_rotation), d1 = !rotationalVelocity.isEqual(last_shared_rotational_velocity);
        Packet toSend = null;
        if (d0 && d1) {
            toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotationBoth, entityId, rotation, rotationalVelocity);
            last_shared_rotation.update(rotation);
            last_shared_rotational_velocity.update(rotationalVelocity);
        } else if (d0) {
            toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotation, entityId, rotation);
            last_shared_rotation.update(rotation);
        } else if (d1) {
            toSend = HammerNet.makePacket(HammerNet.HammerNetType.rotationVelocity, entityId, rotationalVelocity);
            last_shared_rotational_velocity.update(rotationalVelocity);
        }
        if (toSend != null) {
            PacketDispatcher.sendPacketToAllAround(posX, posY, posZ, 64, this.dimension, toSend);
        }
    }
    
    
    void doUpdate() {
        if (worldObj.isRemote) {
            prevTickRotation.update(rotation);
            if (hammerCell == null) {
                init();
                return;
            }
        } else if (proxy == null && !isDead) {
            init();
            proxy = new PacketProxyingPlayer(this);
            proxy.worldObj.spawnEntityInWorld(proxy);
            return;
        }
        updateMotion();
        if (!worldObj.isRemote && can(Caps.ROTATE)) {
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
                if (hammerCell.blockExists()) {
                    setDead();
                    Core.logFine("%s dying due to empty area", this.toString());
                } else {
                    needAreaUpdate = true; //Hopefully it will load up soon...
                }
            } else {
                Coord corner = Hammer.getCellCorner(worldObj, cell);
                if (can(Caps.TAKE_INTERIOR_ENTITIES)) {
                    takeInteriorEntities();
                }
                if (can(Caps.REMOVE_EXTERIOR_ENTITIES)) {
                    removeExteriorEntities();
                }
            }
            if (isDead) {
                endSlice();
                return;
            }
        }
        //Do collisions...? :(
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
        Chunk[] mychunks = Hammer.getChunks(worldObj, cell);
        for (int i = 0; i < mychunks.length; i++) {
            Chunk chunk = mychunks[i];
            for (int j = 0; j < chunk.entityLists.length; j++) {
                List<Entity> l = chunk.entityLists[j];
                for (int k = 0; k < l.size(); k++) {
                    Entity ent = l.get(k); //This is probably an ArrayList.
                    if (ent.posY < 0 || ent.posY > Hammer.wallHeight || ent == this /* oh god what */) {
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
        World shadowWorld = Hammer.getServerShadowWorld();
        Vec3 newLocation = real2shadow(Hammer.ent2vec(ent));
        transferEntity(ent, shadowWorld, newLocation);
        if (ifec != null) {
            ifec.onEnter(this);
        }
    }
    
    void ejectEntity(Entity ent) {
        //TODO: Take transformations into account
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
            if (!can(Caps.TRANSFER_PLAYERS)) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) ent;
            MinecraftServer ms = MinecraftServer.getServer();
            ServerConfigurationManager manager = ms.getConfigurationManager();
            DSTeleporter tp = new DSTeleporter((WorldServer) newWorld);
            tp.preciseDestination = newPosition;
            manager.transferPlayerToDimension(player, newWorld.getWorldInfo().getDimension(), tp);
        } else {
            //Inspired by Entity.travelToDimension
            ent.worldObj.setEntityDead(ent);
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
        Hammer.getSlices(worldObj).remove(this);
        //TODO: teleport entities/blocks into the real world
    }
    
    @Override
    public void setDead() {
        super.setDead();
        Hammer.getSlices(worldObj).remove(this);
    }
    
    @Override
    public boolean isInRangeToRenderDist(double distSquared) {
        //NOTE: This doesn't actually render entities as far as it should
        int s = 8*16;
        return distSquared < s*s;
    }
    
    @Override
    public boolean canEnter(DimensionSliceEntity dse) { return false; }
    
    @Override
    public boolean canExit(DimensionSliceEntity dse) { return true; }
    
    @Override
    public void onEnter(DimensionSliceEntity dse) { }
    
    @Override
    public void onExit(DimensionSliceEntity dse) { }

    void writeAABB(ByteArrayDataOutput data, AxisAlignedBB bb) {
        data.writeDouble(bb.maxX);
        data.writeDouble(bb.maxY);
        data.writeDouble(bb.maxZ);
        data.writeDouble(bb.minX);
        data.writeDouble(bb.minY);
        data.writeDouble(bb.minZ);
    }
    
    AxisAlignedBB readAABB(ByteArrayDataInput data) {
        double maxX = data.readDouble();
        double maxY = data.readDouble();
        double maxZ = data.readDouble();
        double minX = data.readDouble();
        double minY = data.readDouble();
        double minZ = data.readDouble();
        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    
    @Override
    public void writeSpawnData(ByteArrayDataOutput data) {
        data.writeInt(cell);
        data.writeInt(capabilities);
        rotation.write(data);
        rotationalVelocity.write(data);
    }

    @Override
    public void readSpawnData(ByteArrayDataInput data) {
        cell = data.readInt();
        capabilities = data.readInt();
        try {
            rotation = Quaternion.read(data);
            rotationalVelocity = Quaternion.read(data);
        } catch (IOException e) {
            //Not expected to happen ever
            e.printStackTrace();
        }
    }
    
    @Override
    public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9)
    {
        //This is required because we have some self-interference
        this.setPosition(par1, par3, par5);
        this.setRotation(par7, par8);
    }
    
    @Override
    public void addVelocity(double par1, double par3, double par5) {
        super.addVelocity(par1, par3, par5);
        isAirBorne = false; //If this is true, we get packet spam
    }
    
    public boolean can(Caps cap) {
        return cap.in(capabilities);
    }
    
    public static enum Caps {
        COLLIDE, MOVE, ROTATE, DRAG, TAKE_INTERIOR_ENTITIES, REMOVE_EXTERIOR_ENTITIES, TRANSFER_PLAYERS; //Do not re-order this list, only append.
        public int bit;
        
        Caps() {
            this.bit = 1 << ordinal();
        }
        
        public boolean in(int field) {
            return (field & this.bit) != 0;
        }
        
        public static int of(Caps ...args) {
            int ret = 0;
            for (int i = 0; i < args.length; i++) {
                ret |= args[i].bit;
            }
            return ret;
        }
    }
}
