package factorization.common;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.ForgeDirection;
import factorization.api.Coord;
import factorization.api.ICoord;
import factorization.api.IFactoryType;
import factorization.common.NetworkFactorization.MessageType;

public abstract class TileEntityFactorization extends TileEntityCommon
        implements IInventory, ISidedInventory, ICoord, IFactoryType {

    //Save & Share
    public byte draw_active;
    public byte facing_direction = 3;

    //Runtime
    static Random rand = new Random();
    boolean need_logic_check = true;

    @Override
    public abstract FactoryType getFactoryType();

    public void click(EntityPlayer entityplayer) {
    }

    void makeNoise() {
    }

    abstract void doLogic();

    int getLogicSpeed() {
        return 4;
    }

    boolean canFaceVert() {
        return false;
    }

    @Override
    //Derived from TileEntityCommon
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side) {
        if (player == null) {
            return;
        }
        super.onPlacedBy(player, is, side);
        setFacingDirectionFromEntity(player);
    }

    //few of the stuff below would be overridden

    void setFacingDirectionFromEntity(Entity player) {
        float yaw = player.rotationYaw % 360F;
        if (yaw < 0) {
            //Fun fact: Java is retarded.
            yaw += 360F;
        }

        if (canFaceVert()) {
            if (player.rotationPitch <= -45F) {
                facing_direction = 0; //-Y
                return;
            }
            else if (player.rotationPitch >= 65F) {
                facing_direction = 1; //+Y
                return;
            }
        }
        int y = ((int) yaw) / 45;
        switch (y) {
        case 7:
        case 0:
            facing_direction = 2;
            break;
        case 1:
        case 2:
            facing_direction = 5;
            break;
        case 3:
        case 4:
            facing_direction = 3;
            break;
        case 5:
        case 6:
            facing_direction = 4;
            break;
        }
    }

    void needLogic() {
        need_logic_check = true;
    }

    @Override
    public final Coord getCoord() {
        return new Coord(this);
    }

    @Override
    byte getExtraInfo() {
        return facing_direction;
    }

    @Override
    byte getExtraInfo2() {
        return draw_active > Byte.MAX_VALUE ? Byte.MAX_VALUE : draw_active;
    }

    @Override
    void useExtraInfo(byte b) {
        if (worldObj.isRemote) {
            facing_direction = b;
        }
    }

    @Override
    void useExtraInfo2(byte b) {
        if (worldObj.isRemote) {
            draw_active = b;
        }
    }

    @Override
    protected void onRemove() {
        super.onRemove();
        dropContents();
    }

    public void dropContents() {
        // XXX TODO: ModLoader.genericContainerRemoval
        Coord here = getCoord();
        for (int i = 0; i < getSizeInventory(); i++) {
            FactorizationUtil.spawnItemStack(here, getStackInSlot(i));
        }
    }
    
    double round(double c) {
        if (Math.abs(c) < 0.5) {
            return 0;
        }
        return Math.copySign(1, c);
    }

    void ejectItem(ItemStack is, boolean violent, EntityPlayer player, int to_side) {
        if (worldObj.isRemote) {
            return;
        }
        if (is == null || is.stackSize == 0) {
            return;
        }
        if (player == null) {
            to_side = -1;
        }
        double mult = 0.02;
        if (violent) {
            mult = 0.2;
        }
        Vec3 pos = worldObj.getWorldVec3Pool().getVecFromPool(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
        Vec3 vel = worldObj.getWorldVec3Pool().getVecFromPool(0, 0, 0);
        if (to_side != -1) {
            ForgeDirection dir = ForgeDirection.getOrientation(to_side);
            /*
            ent.motionX = dir.offsetX;
            ent.motionY = dir.offsetY;
            ent.motionZ = dir.offsetZ;*/
            double d = 0.75;
            pos.xCoord += dir.offsetX*d;
            pos.yCoord += dir.offsetY*d;
            pos.zCoord += dir.offsetZ*d;
            vel.xCoord = dir.offsetX;
            vel.yCoord = dir.offsetY;
            vel.zCoord = dir.offsetZ;
        } else if (player != null) {
            // point velocity towards player
            Vec3 vec = Vec3.createVectorHelper(player.posX - xCoord, player.posY - yCoord,
                    player.posZ - zCoord);
            vec = vec.normalize();
            vel = vec;
            double d = 0.25;
            // move item to near the edge
            pos.xCoord += vec.xCoord*d;
            pos.yCoord += vec.yCoord*d;
            pos.zCoord += vec.zCoord*d;
            //Minecraft.getMinecraft().theWorld.spawnParticle("reddust", ent.posX, ent.posY, ent.posZ, 0, 0, 0);
        } else {
            // random velocity
            vel.xCoord = rand.nextGaussian();
            vel.yCoord = rand.nextGaussian();
            vel.zCoord = rand.nextGaussian();
        }
        EntityItem ent = new EntityItem(worldObj, pos.xCoord, pos.yCoord, pos.zCoord, is);
        ent.motionX = vel.xCoord*mult;
        ent.motionY = vel.yCoord*mult;
        ent.motionZ = vel.zCoord*mult;
        worldObj.spawnEntityInWorld(ent);
    }

    @Override
    public ItemStack decrStackSize(int i, int amount) {
        ItemStack target = getStackInSlot(i);
        if (target == null) {
            return null;
        }

        if (target.stackSize <= amount) {
            ItemStack ret = target;
            setInventorySlotContents(i, null);
            return ret;
        }

        ItemStack ret = target.splitStack(amount);
        if (target.stackSize == 0) {
            setInventorySlotContents(i, null);
        }
        return ret;
    }

    @Override
    public void onInventoryChanged() {
        super.onInventoryChanged();
        needLogic();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        if (worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) != this) {
            return false;
        }
        return 8 * 8 >= player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
    }

    @Override
    public final void openChest() {
    }

    @Override
    public final void closeChest() {
    }
    
    /*@Override
    public boolean acceptsStackInSlot(int i, ItemStack itemstack) {
        return true;
    }*/
    
    @Override
    public boolean isInvNameLocalized() {
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("draw_active_byte", draw_active);
        tag.setByte("facing", facing_direction);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        draw_active = tag.getByte("draw_active_byte");
        facing_direction = tag.getByte("facing");
    }

    public final void readSlotsFromNBT(NBTTagCompound tag) {
        NBTTagList invlist = tag.getTagList("Items");
        for (int i = 0; i < invlist.tagCount(); i++) {
            NBTTagCompound comp = (NBTTagCompound) invlist.tagAt(i);
            setInventorySlotContents(comp.getInteger("Slot"), ItemStack.loadItemStackFromNBT(comp));
        }
    }

    public final void writeSlotsToNBT(NBTTagCompound tag) {
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

    static void saveItem(String name, NBTTagCompound tag, ItemStack is) {
        // TODO: Are these two as used as they could be?
        if (is == null) {
            return;
        }
        NBTTagCompound itag = new NBTTagCompound();
        is.writeToNBT(itag);
        tag.setCompoundTag(name, itag);
    }

    static ItemStack readItem(String name, NBTTagCompound tag) {
        if (tag.hasKey(name)) {
            return ItemStack.loadItemStackFromNBT(tag.getCompoundTag(name));
        }
        return null;
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
                worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
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
    public boolean handleMessageFromServer(int messageType, DataInputStream input) throws IOException {
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
