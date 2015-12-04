package factorization.charge;

import java.io.IOException;

import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import factorization.api.Charge;
import factorization.api.Coord;
import factorization.api.IChargeConductor;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.shared.BlockClass;
import factorization.shared.Core;
import factorization.shared.TileEntityCommon;
import factorization.shared.NetworkFactorization.MessageType;

public class TileEntityWire extends TileEntityCommon implements IChargeConductor {
    public byte supporting_side;
    private boolean extended_wire = false;
    Charge charge = new Charge(this);

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.LEADWIRE;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Wire;
    }
    @Override
    public boolean activate(EntityPlayer entityplayer, EnumFacing side) {
        return false;
    }

    @Override
    public Charge getCharge() {
        return charge;
    }

    @Override
    public String getInfo() {
        return null;
    }

    @Override
    public void putData(DataHelper data) throws IOException {
        supporting_side = data.as(Share.VISIBLE, "side").putByte(supporting_side);
        charge.serialize("", data);
    }

    boolean find_support() {
        if (!extended_wire) {
            return false;
        }
        for (byte side = 0; side < 6; side++) {
            if (canPlaceAgainst(null, getCoord().towardSide(side), side)) {
                supporting_side = side;
                shareInfo();
                return true;
            }
            //			if (here.towardSide(side).isSolidOnSide(Coord.oppositeSide(side))) {
            //				supporting_side = side;
            //				return true;
            //			}
        }
        return false;
    }

    boolean is_directly_supported() {
        Coord supporter = getCoord().towardSide(supporting_side);
        if (!supporter.blockExists()) {
            return true; //block isn't loaded, so just hang tight.
        }
        if (supporter.isSolidOnSide(supporting_side)) {
            return true;
        }
        return false;
    }

    boolean is_supported() {
        if (is_directly_supported()) {
            return true;
        }
        Coord supporter = getCoord().towardSide(supporting_side);
        TileEntityWire parent = supporter.getTE(TileEntityWire.class);
        if (parent != null) {
            extended_wire = true;
            return parent.is_supported();
        }
        return false;
    }

    @Override
    public boolean canPlaceAgainst(EntityPlayer player, Coord supporter, EnumFacing side) {
        if (supporter.isSolidOnSide(side)) {
            return true;
        }
        TileEntityWire parent = supporter.getTE(TileEntityWire.class);
        if (parent != null) {
            if (parent.is_directly_supported()) {
                int opposite = SpaceUtil.getOrientation(side).getOpposite().ordinal();
                if (parent.supporting_side == side || parent.supporting_side == opposite) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            return;
        }
        charge.update();
    }
    
    @Override
    public void neighborChanged() {
        if (!is_supported() /*&& !find_support()*/ ) {
            Core.registry.factory_block.dropBlockAsItem(worldObj, pos.getX(), pos.getY(), pos.getZ(), BlockClass.Wire.md, 0);
            Coord here = getCoord();
            here.setAir();
            here.rmTE();
        }
    }

    int getComplexity(byte new_side) {
        supporting_side = new_side;
        int complexity = new WireConnections(this).getComplexity();
        for (Coord ne : getCoord().getNeighborsAdjacent()) {
            TileEntityWire w = ne.getTE(TileEntityWire.class);
            if (w == null) {
                continue;
            }
            complexity += new WireConnections(w).getComplexity();
        }
        TileEntityWire below = getCoord().add(SpaceUtil.getOrientation(supporting_side)).getTE(TileEntityWire.class);
        if (below != null && below.supporting_side == supporting_side) {
            complexity += 16;
        }
        return complexity;
    }


    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, EnumFacing side, float hitX, float hitY, float hitZ) {
        side = new int[] { 1, 0, 3, 2, 5, 4, }[side];
        if (player.isSneaking()) {
            supporting_side = (byte) side;
            if (is_supported()) {
                shareInfo();
                return;
            }
        }
        byte best_side = (byte) side;
        int best_complexity = getComplexity(best_side) - 1;
        if (!is_supported()) {
            best_complexity = 0x999;
        }
        for (byte s = 0; s < 6; s++) {
            if (s == side) {
                continue;
            }
            supporting_side = s;
            if (!is_supported()) {
                continue;
            }
            int test = getComplexity(s);
            if (test < best_complexity) {
                best_complexity = test;
                best_side = s;
            }
        }
        supporting_side = best_side;
        shareInfo();
    }

    void shareInfo() {
        broadcastMessage(null, MessageType.WireFace, supporting_side);
    }

    @Override
    public boolean isBlockSolidOnSide(EnumFacing side) {
        return false;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(Vec3 startVec, Vec3 endVec) {
        return new WireConnections(this).collisionRayTrace(worldObj, pos.getX(), pos.getY(), pos.getZ(), startVec, endVec);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool() {
        return null;
//		setBlockBounds(Core.registry.resource_block);
//		AxisAlignedBB ret = Core.registry.resource_block.getCollisionBoundingBoxFromPool(worldObj, xCoord, yCoord, zCoord);
//		Core.registry.resource_block.setBlockBounds(0, 0, 0, 1, 1, 1);
//		return ret;
    }

    @Override
    public void setBlockBounds(Block b) {
        new WireConnections(this).setBlockBounds(b);
    }

    @Override
    public boolean handleMessageFromServer(MessageType messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == MessageType.WireFace) {
            byte new_side = input.readByte();
            if (new_side != supporting_side) {
                supporting_side = new_side;
                getCoord().redraw();
            }
            return true;
        }
        return false;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(EnumFacing dir) {
        return BlockIcons.wire;
    }
}
