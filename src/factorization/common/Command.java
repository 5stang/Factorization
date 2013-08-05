package factorization.common;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public enum Command {
    bagShuffle(1), craftClear(2), craftMove(3), craftBalance(4), craftOpen(5, true),
    bagShuffleReverse(6), craftFill(11);

    static class name {
        static HashMap<Byte, Command> map = new HashMap<Byte, Command>();
    }
    
    static {
        bagShuffle.setReverse(bagShuffleReverse);
    }

    public byte id;
    boolean executeLocally = false;
    public Command reverse = this;

    Command(int id) {
        this.id = (byte) id;
        name.map.put(this.id, this);
    }

    Command(int id, boolean executeLocally) {
        this(id);
        this.executeLocally = executeLocally;
    }
    
    void setReverse(Command rev) {
        rev.reverse = this;
        this.reverse = rev;
    }

    static void fromNetwork(EntityPlayer player, byte s, byte arg) {
        Command c = name.map.get(s);
        if (c == null) {
            Core.logWarning("Received invalid command #" + s);
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
//			if (this == craftOpen && player.inventoryContainer != null) {
//				((EntityClientPlayerMP)player).closeScreen();
//			}
            Core.network.sendCommand(player, this, arg);
            if (!executeLocally) {
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
            craftClear(player);
            break;
        case craftMove:
            // do something smart with items in crafting area
            craftMove(player);
            break;
        case craftBalance:
            // move as many items as we can to fill in template in crafting area
            craftBalance(player);
            break;
        case craftFill:
            // fill the empty area in the crafting grid with the slot under the cursor
            craftFill(player, arg);
            break;
        case craftOpen:
            Core.registry.pocket_table.tryOpen(player);
            break;
        default:
            throw new RuntimeException("Command " + this + " is missing handler");
        }
    }
    
    void craftClear(EntityPlayer player) {
        if (!(player.openContainer instanceof ContainerPocket)) {
            return;
        }
        ContainerPocket pocket = (ContainerPocket) player.openContainer;
        for (int i : ContainerPocket.craftArea) {
            //transferStackInSlot
            pocket.transferStackInSlot(player, i);
        }
        if (player.openContainer instanceof ContainerPocket) {
            ((ContainerPocket) player.openContainer).updateMatrix();
        }
    }
    
    private boolean rotateAll(InventoryPlayer inv, int slots[]) {
        int empty = 0;
        for (int slot : slots) {
            if (FactorizationUtil.normalize(inv.getStackInSlot(slot)) == null) {
                empty++;
            }
        }
        if (empty >= 2) {
            return false;
        }
        ArrayList<ItemStack> buffer = new ArrayList<ItemStack>(8);
        for (int slot : slots) {
            ItemStack toAdd = inv.getStackInSlot(slot);
            buffer.add(buffer.size(), toAdd);
        }
        buffer.add(0, buffer.remove(buffer.size() - 1));
        for (int slot : slots) {
            ItemStack toSet = buffer.remove(0);
            inv.setInventorySlotContents(slot, toSet);
        }
        return true;
    }
    
    private boolean smear(InventoryPlayer inv, int slots[]) {
        int stackSrcSlotIndex = 0;
        boolean foundNonEmpty = false;
        for (int slot : slots) {
            if (inv.getStackInSlot(slot) != null) {
                foundNonEmpty = true;
                continue;
            }
            if (!foundNonEmpty) {
                continue;
            }
            //loop around looking for something that's spreadable
            ItemStack toDrop = null;
            for (int count = 0; count < slots.length; count++) {
                ItemStack here = inv.getStackInSlot(slots[stackSrcSlotIndex]);
                stackSrcSlotIndex++;
                if (stackSrcSlotIndex == slots.length) {
                    stackSrcSlotIndex = 0;
                }
                if (here == null || here.stackSize <= 1) {
                    continue;
                }
                toDrop = here;
                break;
            }
            if (toDrop == null) {
                return true;
            }
            inv.setInventorySlotContents(slot, toDrop.splitStack(1));
        }
        return true;
    }
    
    void craftMove(EntityPlayer player) {
        InventoryPlayer inv = player.inventory;
        //spin the crafting grid
        int slots[] = {15, 16, 17, 26, 35, 34, 33, 24};
        try {
            if (rotateAll(inv, slots)) {
                return;
            }
            if (smear(inv, slots)) {
                return;
            }			
        } finally {
            if (player.openContainer instanceof ContainerPocket) {
                ((ContainerPocket) player.openContainer).updateMatrix();
            }
        }
    }
    
    void craftBalance(EntityPlayer player) {
        class Accumulator {
            ItemStack toMatch;
            int stackCount = 0;
            ArrayList<Integer> matchingSlots = new ArrayList<Integer>(9);
            public Accumulator(ItemStack toMatch, int slot) {
                this.toMatch = toMatch;
                stackCount = toMatch.stackSize;
                toMatch.stackSize = 0;
                matchingSlots.add(slot);
            }
            
            boolean add(ItemStack ta, int slot) {
                if (FactorizationUtil.couldMerge(toMatch, ta)) {
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
        ArrayList<Accumulator> list = new ArrayList<Accumulator>(9);
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
        
        if (player.openContainer instanceof ContainerPocket) {
            ((ContainerPocket) player.openContainer).updateMatrix();
        }
    }
    
    void craftFill(EntityPlayer player, byte slot) {
        final InventoryPlayer inv = player.inventory;
        final ItemStack toMove = inv.getStackInSlot(slot);
        if (toMove == null) {
            return;
        }
        for (int i : ContainerPocket.playerCraftInvSlots) {
            if (toMove.stackSize <= 0) {
                break;
            }
            ItemStack is = inv.getStackInSlot(i);
            if (is != null) {
                continue;
            }
            inv.setInventorySlotContents(i, toMove.splitStack(1));
        }
        inv.setInventorySlotContents(slot, FactorizationUtil.normalize(toMove));
        if (player.openContainer instanceof ContainerPocket) {
            ((ContainerPocket) player.openContainer).updateMatrix();
        }
    }
}
