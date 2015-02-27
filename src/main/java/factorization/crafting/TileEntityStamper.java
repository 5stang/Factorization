package factorization.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.shared.*;
import factorization.util.CraftUtil;
import factorization.util.ItemUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;


public class TileEntityStamper extends TileEntityFactorization {
    // save these naughty juvenile males
    ItemStack input;
    ItemStack output;
    final ArrayList<ItemStack> outputBuffer = new ArrayList(4);

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }
    
    @Override
    public IIcon getIcon(ForgeDirection dir) {
        return BlockIcons.stamper.get(this, dir);
    }

    @Override
    public int getSizeInventory() {
        return 2;
    }

    static int[] OUTPUT_sides = {0}, INPUT_sides = {1};
    @Override
    public int[] getAccessibleSlotsFromSide(int s) {
        switch (ForgeDirection.getOrientation(s)) {
        case DOWN: return INPUT_sides;
        default: return OUTPUT_sides;
        }
    }
    
    @Override
    public boolean isItemValidForSlot(int slotIndex, ItemStack itemstack) {
        return slotIndex == 0;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        switch (i) {
        case 0:
            return input;
        case 1:
            return output;
        default:
            return null;
        }
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack) {
        if (i == 0) {
            input = itemstack;
        }
        if (i == 1) {
            output = itemstack;
        }
    }
    
    @Override
    public String getInventoryName() {
        return "Stamper";
    }

    @Override
    public void putData(DataHelper data) throws IOException {
        super.putData(data);
        input = data.as(Share.PRIVATE, "input").putItemStack(input);
        output = data.as(Share.PRIVATE, "output").putItemStack(output);
        if (data.isNBT()) {
            NBTTagCompound tag = data.getTag();
            if (data.isWriter()) {
                writeBuffer("buffer", tag, outputBuffer);
            } else {
                readBuffer("buffer", tag, outputBuffer);
            }
        }
    }

    boolean canMerge(List<ItemStack> items) {
        if (items == null) {
            return true;
        }
        if (output == null) {
            return true;
        }
        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }
            if (!ItemUtil.couldMerge(output, item)) {
                return false;
            }
            if (output.stackSize + item.stackSize > output.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    void dumpBuffer() {
        if (outputBuffer.isEmpty()) {
            return;
        }
        // put outputBuffer into output
        Iterator<ItemStack> it = outputBuffer.iterator();
        boolean any = false;
        while (it.hasNext()) {
            ItemStack here = it.next();
            if (here == null) {
                it.remove();
                continue;
            }
            if (output == null) {
                output = here;
                it.remove();
                any = true;
                continue;
            }
            if (ItemUtil.couldMerge(output, here)) {
                any = true;
                int can_take = output.getMaxStackSize() - output.stackSize;
                if (here.stackSize > can_take) {
                    output.stackSize += can_take;
                    here.stackSize -= can_take; // will be > 0, keep in list
                    break; // output's full
                }
                output.stackSize += here.stackSize;
                it.remove();
            }
        }
        if (any) {
            markDirty();
        }
    }
    
    protected List<ItemStack> tryCrafting() {
        List<ItemStack> fakeResult = CraftUtil.craft1x1(this, true, input);
        if (!CraftUtil.craft_succeeded) {
            return null;
        }
        if (canMerge(fakeResult)) {
            return CraftUtil.craft1x1(this, false, input.splitStack(1));
        }
        return null;
    }
    
    @Override
    protected void doLogic() {
        int input_count = (input == null) ? 0 : input.stackSize;
        boolean can_add = output == null
                || output.stackSize < output.getMaxStackSize();
        if (outputBuffer.size() == 0 && can_add && input_count > 0) {
            List<ItemStack> craft = tryCrafting();
            if (craft != null) {
                outputBuffer.addAll(craft);
                markDirty();
                drawActive(3);
            }
        }

        if (input != null && input.stackSize <= 0) {
            input = null;
        }

        dumpBuffer();
        
        int new_input_count = (input == null) ? 0 : input.stackSize;
        if (input_count != new_input_count) {
            needLogic();
        }
        if (need_logic_check) {
            pulse();
        }
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.STAMPER;
    }

    @Override
    protected void makeNoise() {
        Sound.stamperUse.playAt(this);
    }
    
    @Override
    protected int getLogicSpeed() {
        return 16;
    }
}
