package factorization.charge;

import factorization.api.Charge;
import factorization.api.IChargeConductor;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.shared.BlockClass;
import factorization.shared.Core;
import factorization.net.StandardMessageType;
import factorization.shared.TileEntityCommon;
import factorization.util.ItemUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

import java.io.IOException;

public class TileEntityBattery extends TileEntityCommon implements IChargeConductor, ITickable {
    Charge charge = new Charge(this);
    int storage = 0;
    static final int max_storage = 6400;

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.BATTERY;
    }

    @Override
    public Charge getCharge() {
        return charge;
    }

    @Override
    public String getInfo() {
        float f = storage * 100 / max_storage;
        if (Core.dev_environ) {
            return "Storage: " + ((int) f) + "%\n" + storage + "/" + max_storage;
        }
        return "Storage: " + ((int) f) + "%";
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }

    @Override
    public void putData(DataHelper data) throws IOException {
        charge.serialize("", data);
        storage = data.as(Share.VISIBLE, "storage").putInt(storage);
    }

    public static float getFullness(int value) {
        return value / ((float) max_storage);
    }

    public float getFullness() {
        return getFullness(storage);
    }

    @Override
    public void update() {
        if (worldObj.isRemote) {
            return;
        }
        charge.update();
        //if (getCoord().seed() + worldObj.getTotalWorldTime() % 10 != 0) {
        //	return;
        //}
        int val = getCharge().getValue();
        int store_delta = 0;
        int charge_delta = 0;
        if (val < 20) {
            //dump a bit out
            charge_delta = Math.min(20, storage);
            store_delta = -charge_delta;
        } else if (val > 30) {
            //pull it all in!
            charge_delta = -val;
            int free = max_storage - storage;
            store_delta = Math.min(val*2/3, free);
        }
        int tier = storage * 32 / max_storage;
        if (store_delta != 0) {
            charge.addValue(charge_delta);
            storage += store_delta;
        }
        if (tier != storage * 32 / max_storage) {
            markDirty();
            updateMeter();
        }
    }

    void updateMeter() {
        Core.network.broadcastMessage(null, this, StandardMessageType.SetAmount, storage);
    }

    @Override
    public boolean handleMessageFromServer(Enum messageType, ByteBuf input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == StandardMessageType.SetAmount) {
            storage = input.readInt();
            getCoord().redraw();
            return true;
        }
        return false;
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, EnumFacing side, float hitX, float hitY, float hitZ) {
        super.onPlacedBy(player, is, side, hitX, hitY, hitZ);
        NBTTagCompound tag = ItemUtil.getTag(is);
        if (tag.hasKey("storage")) {
            storage = tag.getInteger("storage");
        } else {
            storage = max_storage;
        }
    }

    @Override
    public ItemStack getDroppedBlock() {
        ItemStack is = new ItemStack(Core.registry.battery);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("storage", storage);
        is.setTagCompound(tag);
        Core.registry.battery.normalizeDamage(is);
        return is;
    }
    
    @Override
    public int getComparatorValue(EnumFacing side) {
        return (int) (getFullness()*0xF);
    }
}
