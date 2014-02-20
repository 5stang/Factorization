package factorization.api;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class InvasiveCharge extends TileEntity implements IChargeConductor {
    int id, md;
    
    public void initialize(int id, int md) {
        this.id = id;
        this.md = md;
    }
    @Override
    public Coord getCoord() {
        return new Coord(this);
    }

    @Override
    public String getInfo() {
        return null;
    }
    
    Charge charge = new Charge(this);

    @Override
    public Charge getCharge() {
        return charge;
    }
    
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        charge.writeToNBT(tag);
    }
    
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        charge.readFromNBT(tag);
    }
    
    @Override
    public void updateEntity() {
        super.updateEntity();
        if (getWorldObj().getBlock(xCoord, yCoord, zCoord) != id || getWorldObj().getBlockMetadata(xCoord, yCoord, zCoord) != md) {
            Coord me = getCoord();
            worldObj.removeTileEntity(xCoord, yCoord, zCoord);
            return;
        }
        charge.update();
        if (!worldObj.isRemote) {
            //Core.notify(null, getCoord(), NotifyStyle.FORCE, "B");
        }
    }
    
    @Override
    public void invalidate() {
        super.invalidate();
        charge.invalidate();
    }
    
    @Override
    public boolean shouldRefresh(int oldID, int newID, int oldMeta, int newMeta, World world, int x, int y, int z) {
        return false;
    }
}
