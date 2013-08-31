package factorization.common;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Icon;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Charge;
import factorization.api.Coord;
import factorization.api.IChargeConductor;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.DataInNBT;
import factorization.api.datahelpers.DataOutNBT;
import factorization.api.datahelpers.Share;
import factorization.client.render.ChargeSparks;
import factorization.common.NetworkFactorization.MessageType;

public class TileEntityLeydenJar extends TileEntityCommon implements IChargeConductor {
    private Charge charge = new Charge(this);
    int storage = 0;
    
    static final double max_efficiency = 0.75, min_efficiency = 0.05;
    static final int charge_threshold = 70;
    static final int discharge_threshold = 40;
    static final int max_storage = 6400*200;
    static final int max_charge_per_tick = 200;
    static final int max_discharge_per_tick = 50;

    public ChargeSparks sparks = null;
    
    char last_light = (char) -1;
    
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.LEYDENJAR;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.MachineDynamicLightable;
    }
    
    public double getLevel() {
        return (((double)storage) / max_storage);
    }

    @Override
    public String getInfo() {
        return "Storage: " + (int)(getLevel()*100) + "%";
    }

    @Override
    public Charge getCharge() {
        return charge;
    }
    
    public double getEfficiency() {
        
        double range = max_efficiency - min_efficiency;
        return min_efficiency + range*(1 - getLevel());
    }
    
    
    private static Random rand = new Random();
    private static double randomizeDirection(int i) {
        if (i == 0) {
            final double turn = 2*Math.PI;
            double r = Math.cos(rand.nextDouble()*turn) - 1;
            r += r < -1 ? 2 : 0;
            return r*0.3 + 0.5;
        }
        return i*0.4 + 0.5;
    }
    
    public void updateSparks(ChargeSparks the_sparks) {
        double level = getLevel()*4/5;
        if (level > rand.nextDouble()) {
            Vec3 src = Vec3.createVectorHelper(0.5, randomizeDirection(0)/2 + 0.2, 0.5);
            ForgeDirection fo = ForgeDirection.getOrientation(2 + rand.nextInt(4));
            Vec3 dest = Vec3.createVectorHelper(randomizeDirection(fo.offsetX), randomizeDirection(fo.offsetY), randomizeDirection(fo.offsetZ));
            the_sparks.spark(src, dest, 12, 1, 3, 2.0, 8.0, /*0xF0FF00*/ /*0xEEDB02*/ 0xEEE59D);
        }
        the_sparks.update();
    }
    
    @Override
    public void updateEntity() {
        charge.update();
        final Coord here = getCoord();
        if (worldObj.isRemote) {
            if (sparks == null) {
                sparks = new ChargeSparks(10);
            }
            updateSparks(sparks);
            char now_light = (char) getDynamicLight();
            if (last_light == -1) {
                last_light = now_light;
            }
            if (Math.abs(last_light - now_light) > 1) {
                last_light = now_light;
                here.updateBlockLight();
                here.redraw();
            }
            return;
        }
        if (here.isPowered()) {
            return;
        }
        boolean change = false;
        int charge_value = charge.getValue();
        if (charge_value > charge_threshold) {
            double efficiency = getEfficiency();
            int free = max_storage - storage;
            int to_take = Math.min(charge_value - charge_threshold, max_charge_per_tick);
            to_take = Math.min(free, to_take);
            int gain = (int) (to_take*efficiency);
            if (gain > 0) {
                storage += charge.deplete(to_take)*efficiency;
                change = true;
            }
        } else if (charge_value < discharge_threshold) {
            int free = discharge_threshold - charge_value;
            int to_give = Math.min(Math.min(storage, free), max_discharge_per_tick);
            if (to_give > 0) {
                storage -= charge.addValue(to_give);
                change = true;
            }
        }
        if (change) {
            onInventoryChanged();
            updateClients();
        }
    }
    
    int last_storage = -1;
    
    void updateClients() {
        if (storage != last_storage || storage < 10 || last_storage < 10) {
            broadcastMessage(null, MessageType.LeydenjarLevel, storage);
            last_storage = storage;
        }
    }
    
    @Override
    public boolean handleMessageFromServer(int messageType, DataInputStream input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == MessageType.LeydenjarLevel) {
            storage = input.readInt();
            return true;
        }
        return false;
    }
    
    void handleData(DataHelper data) throws IOException {
        storage = data.as(Share.VISIBLE, "store").putInt(storage);
    }
    
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        try {
            handleData(new DataOutNBT(tag));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        try {
            handleData(new DataInNBT(tag));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public Icon getIcon(ForgeDirection dir) {
        return BlockIcons.leyden_metal;
    }
    
    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side) {
        if (is.hasTagCompound()) {
            NBTTagCompound tag = FactorizationUtil.getTag(is);
            storage = tag.getInteger("storage");
        }
    }
    
    @Override
    public Packet getDescriptionPacket() {
        return super.getDescriptionPacketWith(MessageType.LeydenjarLevel, storage);
    }
    
    @Override
    public int getDynamicLight() {
        return (int) (getLevel()*7);
    }
    
    @Override
    public ItemStack getDroppedBlock() {
        ItemStack is = new ItemStack(Core.registry.item_factorization, 1, getFactoryType().md);
        NBTTagCompound tag = FactorizationUtil.getTag(is);
        tag.setInteger("storage", storage);
        return is;
    }
    
    @Override
    public int getComparatorValue(ForgeDirection side) {
        return (int) (getLevel()*0xF);
    }
}
