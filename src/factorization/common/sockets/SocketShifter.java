package factorization.common.sockets;

import java.io.IOException;

import javax.swing.text.StyledEditorKit.ForegroundAction;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.Quaternion;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.common.BlockIcons;
import factorization.common.BlockRenderHelper;
import factorization.common.FactorizationUtil;
import factorization.common.FactorizationUtil.FzInv;
import factorization.common.servo.ServoMotor;
import factorization.common.FactoryType;
import factorization.common.ISocketHolder;
import factorization.common.TileEntitySocketBase;

public class SocketShifter extends TileEntitySocketBase {
    public static enum ShifterMode {
        MODE_STREAM, MODE_PULSE_EXACT, MODE_PULSE_SOME;
    }
    //public boolean streamMode = true; // be like a hopper or a filter
    public ShifterMode mode = ShifterMode.MODE_STREAM;
    public int foreignSlot = -1;
    public boolean exporting = true;
    public byte transferLimit = 1;
    byte cooldown = 0;
    
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SOCKET_SHIFTER;
    }
    
    @Override
    public boolean canUpdate() {
        return true;
    }
    
    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        exporting = data.as(Share.MUTABLE, "exp").putBoolean(exporting);
        if (data.hasLegacy("strm")) {
            mode = data.as(Share.MUTABLE, "strm").putBoolean(true) ? ShifterMode.MODE_STREAM : ShifterMode.MODE_PULSE_EXACT;
        } else {
            mode = data.as(Share.MUTABLE, "mode").putEnum(mode);
        }
        transferLimit = data.as(Share.MUTABLE, "lim").putByte(transferLimit);
        foreignSlot = data.as(Share.MUTABLE, "for").putInt(foreignSlot);
        cooldown = data.as(Share.PRIVATE, "wait").putByte(cooldown);
        if (data.isWriter()) {
            return this;
        }
        //Validate input
        if (mode == ShifterMode.MODE_STREAM && transferLimit != 1) {
            transferLimit = 1;
            data.log("transfer limit must be 1 in stream mode");
        }
        if (foreignSlot < -1) {
            foreignSlot = -1;
            data.log("foreign slot was < -1");
        }
        if (transferLimit > 64) {
            transferLimit = 64;
            data.log("transfer limit was > 64");
        }
        if (transferLimit < 1) {
            transferLimit = 1;
            data.log("transfer limit was < 1");
        }
        return this;
    }
    
    @Override
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) {
        if (worldObj.isRemote) {
            return;
        }
        if (mode == ShifterMode.MODE_STREAM) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (!powered) {
                return;
            }
        } else {
            if (!powered && cooldown > 0) {
                cooldown--;
                return;
            }
            if (cooldown > 0) {
                return;
            }
            if (!powered) {
                return;
            }
        }
        
        FzInv localInv, foreignInv;
        ForgeDirection back = facing.getOpposite();
        if (socket != this) {
            // meaning we're on a Servo
            localInv = FactorizationUtil.openInventory((IInventory) socket, facing);
        } else {
            coord.adjust(back);
            localInv = FactorizationUtil.openInventory(coord.getTE(IInventory.class), facing);
            coord.adjust(facing);
        }
        if (localInv == null) {
            return;
        }
        coord.adjust(facing);
        foreignInv = FactorizationUtil.openInventory(coord.getTE(IInventory.class), back);
        coord.adjust(back);
        if (foreignInv == null) {
            final ForgeDirection top = facing;
            
            for (Entity entity : getEntities(socket, coord, top, 0)) {
                if (!(entity instanceof IInventory)) {
                    continue;
                }
                foreignInv = FactorizationUtil.openInventory(entity, false);
                if (foreignInv != null) {
                    break;
                }
            }
            if (foreignInv == null) {
                return;
            }
        }
        
        FzInv pullInv, pushInv;
        int pullStart, pullEnd, pushStart, pushEnd;
        if (foreignSlot >= foreignInv.size()) {
            return;
        }
        if (exporting) {
            pullInv = localInv;
            pushInv = foreignInv;
            pullStart = 0;
            pullEnd = localInv.size() - 1;
            if (foreignSlot == -1) {
                pushStart = 0;
                pushEnd = foreignInv.size() - 1;
            } else {
                pushStart = pushEnd = foreignSlot;
            }
        } else {
            pullInv = foreignInv;
            pushInv = localInv;
            pushStart = 0;
            pushEnd = localInv.size() - 1;
            if (foreignSlot == -1) {
                pullStart = 0;
                pullEnd = foreignInv.size() - 1;
            } else {
                pullStart = pullEnd = foreignSlot;
            }
        }
        
        pushInv.setCallOnInventoryChanged(false);
        pullInv.setCallOnInventoryChanged(false);
        boolean had_change = false;
        if (mode == ShifterMode.MODE_PULSE_SOME) {
            int toMove = transferLimit;
            out: for (int pull = pullStart; pull <= pullEnd; pull++) {
                for (int push = 0; push < pushInv.size(); push++) {
                    int delta = pullInv.transfer(pull, pushInv, push, toMove);
                    toMove -= delta;
                    if (delta > 0) {
                        had_change = true;
                    }
                    if (toMove <= 0) {
                        break out;
                    }
                }
            }
        } else { //NOTE: An optimization is available if limit == 1
            boolean[] visitedSlots = new boolean[pullInv.size()];
            out: for (int pull = pullStart; pull <= pullEnd; pull++) {
                if (countItem(pullInv, pull, transferLimit, visitedSlots) < transferLimit) {
                    continue;
                }
                ItemStack is = pullInv.get(pull);
                int freeForIs = pushInv.getFreeSpaceFor(is, transferLimit);
                if (freeForIs < transferLimit) {
                    continue;
                }
                //We've found an item to move. We shall move this item. This item will fit.
                //If it doesn't fit, then the inventory is weird and should stop being weird.
                had_change = true;
                int limit = transferLimit;
                for (int i = pull; i <= pullEnd; i++) {
                    if (!FactorizationUtil.couldMerge(is, pullInv.get(i))) {
                        continue;
                    }
                    while (limit > 0) {
                        int origLimit = limit;
                        //old stack pass
                        for (int push = pushStart; push <= pushEnd; push++) {
                            if (pushInv.get(push) == null) continue;
                            int delta = pullInv.transfer(i, pushInv, push, limit);
                            limit -= delta;
                            if (limit <= 0) break out;
                        }
                        if (limit <= 0) break out;
                        //new stack pass
                        for (int push = pushStart; push <= pushEnd; push++) {
                            if (pushInv.get(push) != null) continue;
                            int delta = pullInv.transfer(i, pushInv, push, limit);
                            limit -= delta;
                            if (limit <= 0) break out;
                        }
                        if (limit == origLimit) break;
                    }
                }
                break out;
            }
        }
        if (had_change) {
            pullInv.setCallOnInventoryChanged(true);
            pushInv.setCallOnInventoryChanged(true);
            pullInv.onInvChanged();
            pushInv.onInvChanged();
        }
        cooldown = (byte) (mode == ShifterMode.MODE_STREAM ? 8 : 1);
    }
    
    int countItem(FzInv inv, int start, int minimum, boolean[] visitedSlots) {
        if (visitedSlots[start]) {
            return 0;
        }
        visitedSlots[start] = true;
        ItemStack seed = inv.get(start);
        if (seed == null || seed.stackSize == 0) {
            return 0;
        }
        if (!inv.canExtract(start, seed)) {
            return 0;
        }
        int count = seed.stackSize;
        if (count >= minimum) {
            return count;
        }
        start += 1;
        for (int i = start; i < inv.size(); i++) {
            if (visitedSlots[i]) continue;
            ItemStack is = inv.get(i);
            if (is == null) {
                visitedSlots[i] = true;
                continue;
            }
            if (FactorizationUtil.couldMerge(seed, is)) {
                visitedSlots[i] = true;
                if (!inv.canExtract(i, is)) {
                    continue;
                }
                count += is.stackSize;
                if (count >= minimum) {
                    return count;
                }
            }
        }
        return count;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(ServoMotor motor, Tessellator tess) {
        BlockRenderHelper block = BlockRenderHelper.instance;
        block.useTextures(BlockIcons.socket$shifter_front, null,
                BlockIcons.socket$shifter_side, BlockIcons.socket$shifter_side,
                BlockIcons.socket$shifter_side, BlockIcons.socket$shifter_side,
                BlockIcons.socket$shifter_side, BlockIcons.socket$shifter_side);
        final float minYs[] = new float[] { 8F/16F, 3F/16F, -2F/16F };
        final float ds[] = new float[] { 4F/16F, 5F/16F, 6F/16F };
        int end = ds.length;
        if (motor != null) end--;
        for (int i = 0; i < end; i++) {
            float d = ds[i];
            float minY = minYs[i];
            block.setBlockBounds(d, minY, d, 1-d, 12F/16F, 1-d);
            block.begin();
            block.rotateCenter(Quaternion.fromOrientation(FzOrientation.fromDirection(facing.getOpposite())));
            block.renderRotated(tess, xCoord, yCoord, zCoord);
        }
    }
}
