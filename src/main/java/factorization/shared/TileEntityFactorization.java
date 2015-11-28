package factorization.shared;

import factorization.api.Coord;
import factorization.api.ICoord;
import factorization.api.IFactoryType;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.shared.NetworkFactorization.MessageType;
import factorization.util.InvUtil;
import factorization.util.ItemUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;

public abstract class TileEntityFactorization extends TileEntityCommon
        implements IInventory, ISidedInventory, ICoord, IFactoryType {

    //Save & Share
    public byte draw_active;
    public byte facing_direction = 3;

    //Runtime
    protected transient boolean need_logic_check = true;

    @Override
    public abstract FactoryType getFactoryType();

    protected void makeNoise() {
    }

    protected abstract void doLogic();

    protected int getLogicSpeed() {
        return 4;
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side, float hitX, float hitY, float hitZ) {
        super.onPlacedBy(player, is, side, hitX, hitY, hitZ);
        if (player == null) {
            return;
        }
        facing_direction = (byte) side;
    }

    protected void needLogic() {
        need_logic_check = true;
    }

    @Override
    protected void onRemove() {
        super.onRemove();
        dropContents();
    }

    public void dropContents() {
        Coord here = getCoord();
        for (int i = 0; i < getSizeInventory(); i++) {
            InvUtil.spawnItemStack(here, getStackInSlot(i));
        }
    }

    @Override
    public ItemStack decrStackSize(int i, int amount) {
        ItemStack target = ItemUtil.normalize(getStackInSlot(i));
        if (target == null) {
            return null;
        }

        if (target.stackSize <= amount) {
            setInventorySlotContents(i, null);
            return target;
        }

        ItemStack ret = target.splitStack(amount);
        if (target.stackSize == 0) {
            setInventorySlotContents(i, null);
        }
        return ret;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        needLogic();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
            return false;
        }
        return 8 * 8 >= player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
    }

    @Override
    public final void openInventory() {
    }

    @Override
    public final void closeInventory() {
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public void putData(DataHelper data) throws IOException {
        draw_active = data.as(Share.VISIBLE, "draw_active_byte").putByte(draw_active);
        facing_direction = data.as(Share.VISIBLE, "facing").putByte(facing_direction);
    }

    public final void putSlots(DataHelper data) {
        if (!data.isNBT()) return;
        NBTTagCompound tag = data.getTag();
        if (data.isWriter()) {
            writeSlotsToNBT(tag);
        } else {
            readSlotsFromNBT(tag);
        }
    }

    private void readSlotsFromNBT(NBTTagCompound tag) {
        NBTTagList invlist = tag.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < invlist.tagCount(); i++) {
            NBTTagCompound comp = invlist.getCompoundTagAt(i);
            setInventorySlotContents(comp.getInteger("Slot"), ItemStack.loadItemStackFromNBT(comp));
        }
    }

    private void writeSlotsToNBT(NBTTagCompound tag) {
        NBTTagList invlist = new NBTTagList();
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            NBTTagCompound comp = new NBTTagCompound();
            comp.setInteger("Slot", i);
            stack.writeToNBT(comp);
            invlist.appendTag(comp);
        }
        tag.setTag("Items", invlist);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }
    
    @Override
    public boolean canInsertItem(int i, ItemStack itemstack, int j) {
        return isItemValidForSlot(i, itemstack);
    }
    
    @Override
    public boolean canExtractItem(int slot, ItemStack itemstack, int side) {
        return true;
    }

    public void drawActive(int add_time) {
        int new_active = draw_active + add_time;
        if (new_active < 0) {
            new_active = 0;
        }
        if (new_active > 32) {
            new_active = 32;
        }
        if (draw_active != new_active) {
            draw_active = (byte) new_active;
            if (!worldObj.isRemote) {
                broadcastMessage(null, MessageType.DrawActive, draw_active);
            }
        }
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            if (draw_active > 0) {
                makeNoise();
                worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
                draw_active--;
            }
        } else {
            draw_active = (draw_active > 0) ? (byte)(draw_active - 1) : 0;
            if (need_logic_check && 0 == worldObj.getTotalWorldTime() % getLogicSpeed()) {
                need_logic_check = false;
                doLogic();
            }
        }
    }

    @Override
    public boolean handleMessageFromServer(MessageType messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == MessageType.DrawActive) {
            draw_active = input.readByte();
            getCoord().redraw();
            return true;
        }
        return false;
    }
}
