package factorization.beauty;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import factorization.api.*;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.api.wind.IWindmill;
import factorization.api.wind.WindModel;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.fzds.DeltaChunk;
import factorization.fzds.TransferLib;
import factorization.fzds.interfaces.DeltaCapability;
import factorization.fzds.interfaces.IDCController;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.shared.*;
import factorization.util.FzUtil;
import factorization.util.PlayerUtil;
import factorization.util.SpaceUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.io.IOException;

public class TileEntityWindMill extends TileEntityCommon implements IRotationalEnergySource, IDCController, IWindmill, IMeterInfo {
    ForgeDirection sailDirection = ForgeDirection.UP;
    boolean dirty = true;
    double power_per_tick, power_this_tick, target_velocity, velocity;
    double wind_strength = 0, efficiency = 0;
    double radius = 0;
    final EntityReference<IDeltaChunk> idcRef = new EntityReference<IDeltaChunk>().whenFound(new IDCController.AutoControl(this));

    @Override
    public void setWorldObj(World w) {
        super.setWorldObj(w);
        idcRef.setWorld(w);
        WindModel.activeModel.registerWindmillTileEntity(this);
        if (radius > 0) {
            WindModel.activeModel.registerWindmillTileEntity(this);
        }
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.WIND_MILL_GEN;
    }

    @Override
    public boolean canConnect(ForgeDirection direction) {
        return direction == this.sailDirection.getOpposite();
    }

    @Override
    public double availableEnergy(ForgeDirection direction) {
        if (direction != sailDirection.getOpposite()) return 0;
        return power_this_tick;
    }

    @Override
    public double takeEnergy(ForgeDirection direction, double maxPower) {
        if (direction != sailDirection.getOpposite()) return 0;
        if (maxPower > power_this_tick) {
            maxPower = power_this_tick;
        }
        power_this_tick -= maxPower;
        return maxPower;
    }

    @Override
    public double getVelocity(ForgeDirection direction) {
        if (direction != sailDirection.getOpposite()) return 0;
        return velocity;
    }

    @Override
    protected boolean removedByPlayer(EntityPlayer player, boolean willHarvest) {
        if (!willHarvest) return super.removedByPlayer(player, willHarvest);
        if (PlayerUtil.isPlayerCreative(player) && player.isSneaking()) {
            IDeltaChunk idc = idcRef.getEntity();
            if (idc != null) idc.setDead();
            return super.removedByPlayer(player, willHarvest);
        }
        if (idcRef.trackedAndAlive()) return false;
        return super.removedByPlayer(player, willHarvest);
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side, float hitX, float hitY, float hitZ) {
        super.onPlacedBy(player, is, side, hitX, hitY, hitZ);
        sailDirection = ForgeDirection.getOrientation(side);
    }

    @Override
    public boolean isTileEntityInvalid() {
        return this.isInvalid();
    }

    @Override
    public boolean placeBlock(IDeltaChunk idc, EntityPlayer player, Coord at) {
        dirty = true;
        return false;
    }

    @Override
    public boolean breakBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) {
        dirty = true;
        return false;
    }

    @Override public boolean hitBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) { return false; }
    @Override public boolean useBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) { return false; }
    @Override public void idcDied(IDeltaChunk idc) { }
    @Override public void beforeUpdate(IDeltaChunk idc) { }
    @Override public void afterUpdate(IDeltaChunk idc) { }
    @Override public boolean onAttacked(IDeltaChunk idc, DamageSource damageSource, float damage) { return false; }

    @Override
    public void putData(DataHelper data) throws IOException {
        sailDirection = data.as(Share.VISIBLE, "sailDirection").putEnum(sailDirection);
        dirty = data.as(Share.PRIVATE, "dirty").putBoolean(dirty);
        data.as(Share.VISIBLE, "idcRef").put(idcRef);
        power_per_tick = data.as(Share.VISIBLE, "powerPerTick").putDouble(power_per_tick);
        power_this_tick = data.as(Share.VISIBLE, "powerThisTick").putDouble(power_this_tick);
        target_velocity = data.as(Share.VISIBLE, "targetVelocity").putDouble(target_velocity);
        velocity = data.as(Share.VISIBLE, "velocity").putDouble(velocity);
        wind_strength = data.as(Share.PRIVATE, "wind_strength").putDouble(wind_strength);
        efficiency = data.as(Share.PRIVATE, "efficiency").putDouble(efficiency);
        radius = data.as(Share.PRIVATE, "radius").putDouble(radius);
    }

    @Override
    public void representYoSelf() {
        super.representYoSelf();
        channel_id = DeltaChunk.getHammerRegistry().makeChannelFor(Core.modId, "fluidMill", channel_id, -1, "waterwheels & windmills");
    }

    static int channel_id = 100;
    static final int MAX_OUT = 2;
    static final int MAX_IN = 2;
    static final int MAX_RADIUS = 6;

    boolean working = false;

    @Override
    public void onNeighborTileChanged(int tilex, int tiley, int tilez) {
        neighborChanged(null);
    }

    @Override
    public void neighborChanged(Block neighbor) {
        if (working) return;
        if (idcRef.trackedAndAlive()) return;
        working = true;
        try {
            trySpawnMill();
        } finally {
            working = false;
        }
    }

    @Override
    public boolean handleMessageFromServer(NetworkFactorization.MessageType messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == NetworkFactorization.MessageType.MillVelocity) {
            velocity = input.readDouble();
        }
        return false;
    }

    void sendVelocity() {
        broadcastMessage(null, NetworkFactorization.MessageType.MillVelocity, velocity);
    }

    private boolean trySpawnMill() {
        Coord fenceLocation = new Coord(this);
        fenceLocation.adjust(sailDirection);
        if (!isFenceish(fenceLocation)) return false;
        DeltaCoord idcSize = new DeltaCoord(MAX_RADIUS * 2, MAX_OUT + MAX_IN, MAX_RADIUS * 2);
        DeltaCoord offset = new DeltaCoord(MAX_RADIUS, MAX_OUT, MAX_RADIUS);
        IDeltaChunk idc = DeltaChunk.allocateSlice(worldObj, channel_id, idcSize);
        idc.permit(DeltaCapability.BLOCK_PLACE,
                DeltaCapability.BLOCK_MINE,
                DeltaCapability.INTERACT,
                DeltaCapability.ROTATE,
                DeltaCapability.DIE_WHEN_EMPTY,
                DeltaCapability.REMOVE_ALL_ENTITIES);
        idc.forbid(DeltaCapability.COLLIDE_WITH_WORLD,
                DeltaCapability.COLLIDE,
                DeltaCapability.VIOLENT_COLLISIONS,
                DeltaCapability.DRAG);
        idc.setRotationalCenterOffset(offset.toVector().addVector(0.5, 0.5, 0.5));
        final ForgeDirection normal = sailDirection.getOpposite();
        Coord at = new Coord(this).add(sailDirection);
        at.setAsEntityLocation(idc);
        if (normal.offsetY == 0) {
            Vec3 up = SpaceUtil.fromDirection(ForgeDirection.UP);
            Vec3 vnorm = SpaceUtil.fromDirection(normal);
            Vec3 axis = up.crossProduct(vnorm);
            Quaternion rot = Quaternion.getRotationQuaternionRadians(-Math.PI / 2, axis);
            idc.setRotation(rot);
            idc.posY += 0.5;
        } else {
            double a = .5;
            idc.posX += sailDirection.offsetX * a;
            idc.posY += sailDirection.offsetY * a;
            idc.posZ += sailDirection.offsetZ * a;
            if (normal.offsetY == 1) {
                idc.posY += 1;
            }
        }
        TransferLib.move(fenceLocation, idc.getCenter(), false /* We'll do it this way to synchronize them */, true);
        fenceLocation.setAir();
        worldObj.spawnEntityInWorld(idc);
        idcRef.trackEntity(idc);
        updateWindStrength(true);
        idc.setPartName("Windmill");
        return true;
    }

    private boolean isFenceish(Coord fenceLocation) {
        final Block block = fenceLocation.getBlock();
        if (block instanceof BlockFence) return true;
        if (block.getRenderType() == Blocks.fence.getRenderType()) return true;
        if (block instanceof BlockWall) return true;
        if (block.getRenderType() == Blocks.cobblestone_wall.getRenderType()) return true;
        if (block instanceof BlockLog) return true;
        if (block instanceof BlockCompressed) return true;
        return false;
    }

    @Override
    public boolean canUpdate() {
        return FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (!idcRef.entityFound()) {
            if (velocity != 0) {
                velocity = 0;
                sendVelocity();
            }
            idcRef.getEntity();
            return;
        }
        updateWindStrength(false);
        power_this_tick = power_per_tick;
        if (velocity != target_velocity) {
            double error = target_velocity > velocity ? (velocity / target_velocity) : 1;
            velocity = (velocity * 5 + target_velocity) / 6;
            power_this_tick *= error;
            if (error > 0.9999) {
                velocity = target_velocity;
            }
            IDeltaChunk idc = idcRef.getEntity();
            if (idc != null) {
                idc.setRotationalVelocity(Quaternion.getRotationQuaternionRadians(velocity, sailDirection));
            }
            sendVelocity();
        }
        if (!dirty) return;
        if (worldObj.getTotalWorldTime() % 40 != 0) return;
        calculate();
    }

    static double MAX_SPEED = IRotationalEnergySource.MAX_SPEED / 4; // Maximum velocity (doesn't change power output)
    static double V_SCALE = 0.05; // Scales down velocity (also doesn't change power output)
    static double WIND_POWER_SCALE = 1.0 / 10.0; // Boosts the power output (and does not influence velocity)

    void calculate() {
        IDeltaChunk idc = idcRef.getEntity();
        if (idc == null) return;
        dirty = false;
        int score = 0;
        int asymetry = 0;
        Coord center = idc.getCenter();
        center.y -= MAX_IN;
        int ys = MAX_IN + MAX_OUT;
        int max_score = 1;
        double new_radius = 0;
        while (ys-- > 0) {
            Symmetry symmetry = new Symmetry(center, MAX_RADIUS, ForgeDirection.UP);
            symmetry.calculate();
            score += symmetry.score;
            asymetry += symmetry.asymetry;
            center.y++;
            max_score = symmetry.max_score;
            new_radius = Math.max(symmetry.measured_radius, new_radius);
        }
        if (radius != new_radius) {
            if (radius != 0) {
                WindModel.activeModel.deregisterWindmillTileEntity(this);
            }
            radius = new_radius;
            if (radius != 0) {
                WindModel.activeModel.registerWindmillTileEntity(this);
            }
        }
        int sum = score - asymetry * 20;
        if (sum < 0) {
            efficiency = 0;
            return;
        }
        max_score /= 5;
        if (sum > max_score) {
            sum = max_score;
        }
        efficiency = sum / (double) max_score;
        updatePowerPerTick();
    }

    void updatePowerPerTick() {
        power_per_tick = efficiency * wind_strength;
        if (power_this_tick < 0) power_this_tick = 0;
        target_velocity = power_per_tick * V_SCALE;
        if (target_velocity > MAX_SPEED) {
            target_velocity = MAX_SPEED;
        }
        power_per_tick *= WIND_POWER_SCALE;
    }

    @Override
    public int getWindmillRadius() {
        return MAX_RADIUS;
    }

    @Override
    public ForgeDirection getDirection() {
        return sailDirection;
    }

    void updateWindStrength(boolean force) {
        if (!force && worldObj.getTotalWorldTime() % 200 != 0) return;
        if (getCoord().isWeaklyPowered()) {
            wind_strength = 0;
            updatePowerPerTick();
            return;
        }
        Vec3 wind = WindModel.activeModel.getWindPower(worldObj, xCoord, yCoord, zCoord, this);
        // TODO: Dot product or something to reverse the velocity
        wind_strength = wind.lengthVector();
        updatePowerPerTick();
    }

    @Override
    public String getInfo() {
        if (!idcRef.trackedAndAlive()) {
            return "No windmill";
        }
        String speed;
        if (velocity == 0) {
            speed = "stopped";
        } else {
            double spr = 1 / (Math.toDegrees(velocity) * 20 / 360);
            if (spr > 60) {
                speed = (int) spr + " SPR";
            } else {
                speed = FzUtil.toRpm(velocity);
            }
        }
        return "Efficiency: " + (int) (efficiency * 100) + "%" +
                "\nWind: " + wind_strength +
                "\nSpeed: " + speed;
    }

    @Override
    public IIcon getIcon(ForgeDirection dir) {
        return BlockIcons.beauty$wind_side;
    }
}
