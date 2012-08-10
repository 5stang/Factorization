package factorization.common;

import net.minecraft.src.BlockFurnace;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.TileEntityFurnace;
import factorization.api.Charge;
import factorization.api.Coord;
import factorization.api.IChargeConductor;

public class TileEntityHeater extends TileEntityCommon implements IChargeConductor {
    Charge charge = new Charge();
    public int heat = 0;
    public static final int maxHeat = 32;

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.HEATER;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }

    @Override
    public Charge getCharge() {
        return charge;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        charge.writeToNBT(tag, "charge");
    }

    @Override
    public void readFromNBT(NBTTagCompound par1nbtTagCompound) {
        super.readFromNBT(par1nbtTagCompound);
        charge.readFromNBT(par1nbtTagCompound, "charge");
    }

    int charge2heat(int i) {
        return (int) (i / 1.5);
    }

    @Override
    public void updateEntity() {
        Coord here = getCoord();
        int delta = Math.min(maxHeat - heat, charge.getValue());
        long now = worldObj.getWorldTime() + here.seed();
        if (charge2heat(delta) > 0) {
            if (now % 4 == 0 || heat == 0) {
                heat += charge2heat(delta);
                charge.addValue(-delta);
            }
        } else if (now % 200 == 0) {
            //lose some heat if we're not being powered
            int toLose = Math.max(1, heat / 8);
            heat -= toLose;
            heat = Math.max(0, heat);
        }
        charge.update(this);

        if (heat <= 0) {
            return;
        }
        for (Coord c : here.getRandomNeighborsAdjacent()) {
            sendHeat(c.getTE());
            if (heat <= 0) {
                return;
            }
        }
    }

    boolean shouldHeat(int cookTime) {
        if (heat >= maxHeat * 0.5) {
            return true;
        }
        return cookTime > 0;
    }

    int addGraceHeat(int burnTime) {
        return Math.max(4, burnTime);
    }
    
    private class ProxiedHeatingResult {
        int burnTime, cookTime, topBurnTime;

        public ProxiedHeatingResult(Coord furnace, int burnTime, int cookTime) {
            this.burnTime = burnTime;
            this.cookTime = cookTime;
            this.topBurnTime = 200;
            calculate(furnace);
        }
        
        private void calculate(Coord furnace) {
            for (int i = 0; i < 2; i++) {
                if (heat <= maxHeat / 2) {
                    return;
                }
                if (burnTime < topBurnTime) {
                    burnTime += 1;
                } else {
                    cookTime += 1;
                }
                heat--;
            }
        }
    }

    void sendHeat(TileEntity te) {
        if (te instanceof TileEntityFurnace) {
            TileEntityFurnace furnace = (TileEntityFurnace) te;
            if (!TEF_canSmelt(furnace)) {
                return;
            }
            ProxiedHeatingResult pf = new ProxiedHeatingResult(new Coord(te), furnace.furnaceBurnTime, furnace.furnaceCookTime);
            furnace.furnaceBurnTime = pf.burnTime;
            furnace.furnaceCookTime = Math.min(pf.cookTime, 200-1);
            BlockFurnace.updateFurnaceBlockState(furnace.furnaceCookTime > 0, te.worldObj, te.xCoord, te.yCoord, te.zCoord);
        }
        if (te instanceof TileEntitySlagFurnace) {
            TileEntitySlagFurnace furnace = (TileEntitySlagFurnace) te;
            if (!furnace.canSmelt()) {
                return;
            }
            ProxiedHeatingResult pf = new ProxiedHeatingResult(new Coord(te), furnace.furnaceBurnTime, furnace.furnaceCookTime);
            furnace.furnaceBurnTime = pf.burnTime;
            furnace.furnaceCookTime = pf.cookTime;
        }
    }

    boolean TEF_canSmelt(TileEntityFurnace diss) {
        //private function for TileEntityFurnace.canSmelt, boooo
        if (diss.getStackInSlot(0) == null) {
            return false;
        } else {
            ItemStack var1 = FurnaceRecipes.smelting().getSmeltingResult(diss.getStackInSlot(0));
            if (var1 == null)
                return false;
            if (diss.getStackInSlot(2) == null)
                return true;
            if (!diss.getStackInSlot(2).isItemEqual(var1))
                return false;
            int result = diss.getStackInSlot(2).stackSize + var1.stackSize;
            return (result <= diss.getInventoryStackLimit() && result <= var1.getMaxStackSize());
        }
    }
}
