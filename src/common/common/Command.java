package factorization.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import cpw.mods.fml.common.FMLCommonHandler;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.EntityClientPlayerMP;
import factorization.common.MechaArmor.MechaMode;

public enum Command {
    bagShuffle(1), craftClear(2), craftMove(3), craftBalance(4), craftOpen(5, true),
    bagShuffleReverse(6), mechaKeyOn(7, true), mechaKeyOff(8, true), mechaModLeftClick(9),
    mechaModRightClick(10);

    static class name {
        static HashMap<Byte, Command> map = new HashMap();
    }

    public byte id;
    boolean shareCommand = false;

    Command(int id) {
        this.id = (byte) id;
        name.map.put(this.id, this);
    }

    Command(int id, boolean shareCommand) {
        this(id);
        this.shareCommand = shareCommand;
    }

    static void fromNetwork(EntityPlayer player, byte s, byte arg) {
        Command c = name.map.get(s);
        if (c == null) {
            System.err.println("Received invalid command #" + s);
            return;
        }
        c.call(player, arg);
    }

    public void call(EntityPlayer player) {
        call(player, (byte) 0);
    }

    public void call(EntityPlayer player, byte arg) {
        if (player == null) {
            return;
        }
        if (player.worldObj.isRemote) {
            if (this == craftOpen && player.craftingInventory != null) {
                ((EntityClientPlayerMP)player).closeScreen();
            }
            Core.network.sendCommand(player, this, arg);
            if (!shareCommand) {
                return;
            }
        }
        switch (this) {
        case bagShuffle:
            Core.registry.bag_of_holding.useBag(player, false);
            break;
        case bagShuffleReverse:
            Core.registry.bag_of_holding.useBag(player, true);
            break;
        case craftClear:
            // move items from pocket crafting area into rest of inventory, or into a bag
            break;
        case craftMove:
            // do something smart with items in crafting area
            craftMove(player);
            break;
        case craftBalance:
            // move as many items as we can to fill in template in crafting area
            craftBalance(player);
            break;
        case craftOpen:
            Core.registry.pocket_table.tryOpen(player);
            break;
        case mechaKeyOff:
        case mechaKeyOn:
            Core.instance.putPlayerKey(player, arg, this == mechaKeyOn);
            break;
        case mechaModLeftClick:
        case mechaModRightClick:
            if (player.craftingInventory instanceof ContainerMechaModder) {
                ContainerMechaModder cont = (ContainerMechaModder) player.craftingInventory;
                ItemStack armor = cont.upgrader.armor;
                MechaArmor m = (MechaArmor) armor.getItem();
                int slot = arg / 2;
                boolean activationButton = 0 == (arg % 2);
                MechaMode mode = m.getSlotMechaMode(armor, slot);
                if (activationButton) {
                    if (this == mechaModLeftClick) {
                        mode.nextActivationMode(1);
                    }
                    else {
                        mode.nextActivationMode(-1);
                    }
                }
                else {
                    if (this == mechaModLeftClick) {
                        mode.nextKey(1);
                    }
                    else {
                        mode.nextKey(-1);
                    }
                }
                m.setSlotMechaMode(armor, slot, mode);
            }
            break;
        default:
            throw new RuntimeException("Command " + this + " is missing handler");
        }
    }
    
    
    void craftMove(EntityPlayer player) {
        InventoryPlayer inv = player.inventory;
        //spin the crafting grid
        int slot[] = {24, 15, 16, 17, 26, 35, 34, 33, 24, 15};
        ItemStack carry = null;
        for (int i = 1; i < 9; i++) {
            ItemStack here = inv.getStackInSlot(slot[i]);
            if (here == null && carry != null) {
                //drop just 1
                inv.setInventorySlotContents(slot[i], carry.splitStack(1));
            } else {
                //swap
                inv.setInventorySlotContents(slot[i], carry);
                carry = here;
            }
            carry = FactorizationUtil.normalize(carry);
        }
        if (inv.getStackInSlot(15) == null) {
            inv.setInventorySlotContents(15, carry);
        } else {
            player.dropPlayerItem(carry);
        }
    }
    
    void craftBalance(EntityPlayer player) {
        class Accumulator {
            ItemStack toMatch;
            int stackCount = 0;
            ArrayList<Integer> matchingSlots = new ArrayList(9);
            public Accumulator(ItemStack toMatch, int slot) {
                this.toMatch = toMatch;
                stackCount = toMatch.stackSize;
                toMatch.stackSize = 0;
                matchingSlots.add(slot);
            }
            
            boolean add(ItemStack ta, int slot) {
                if (toMatch.isItemEqual(ta)) {
                    stackCount += ta.stackSize;
                    ta.stackSize = 0;
                    matchingSlots.add(slot);
                    return true;
                }
                return false;
            }
        }
        InventoryPlayer inv = player.inventory;
        int slots[] = {15, 16, 17, 24, 25, 26, 33, 34, 35};
        ArrayList<Accumulator> list = new ArrayList(9);
        for (int slot : slots) {
            ItemStack here = inv.getStackInSlot(slot);
            if (here == null || here.stackSize == 0) {
                continue;
            }
            boolean found = false;
            for (Accumulator acc : list) {
                if (acc.add(here, slot)) {
                    found = true;
                }
            }
            if (!found) {
                list.add(new Accumulator(here, slot));
            }
        }
        
        for (Accumulator acc : list) {
            int delta = acc.stackCount/acc.matchingSlots.size(); //this should be incapable of being 0
            delta = Math.min(delta, 1); //...we'll make sure anyways.
            for (int slot : acc.matchingSlots) {
                if (acc.stackCount <= 0) {
                    break;
                }
                inv.getStackInSlot(slot).stackSize = delta;
                acc.stackCount -= delta;
            }
            //we now may have a few left over, which we'll distribute
            while (acc.stackCount > 0) {
                for (int slot : acc.matchingSlots) {
                    if (acc.stackCount <= 0) {
                        break;
                    }
                    inv.getStackInSlot(slot).stackSize++;
                    acc.stackCount--;
                }
            }
        }
    }
}