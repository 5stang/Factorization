package factorization.shared;

import static org.lwjgl.opengl.GL11.glGetError;

import java.awt.Toolkit;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.stats.StatBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatMessageComponent;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.DeltaCoord;

public class FzUtil {
    //ItemStack handling
    public static final int WILDCARD_DAMAGE = OreDictionary.WILDCARD_VALUE;
    
    public static ItemStack makeWildcard(Item item) {
        return new ItemStack(item, 1, WILDCARD_DAMAGE);
    }
    
    public static ItemStack makeWildcard(Block item) {
        return new ItemStack(item, 1, WILDCARD_DAMAGE);
    }
    
    /**
     * return if the two itemstacks are identical, excepting stacksize
     */
    public static boolean identical(ItemStack a, ItemStack b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null || b == null) {
            return false;
        }
        return couldMerge(a, b);
    }
    
    /**
     * Compare includes NBT and damage value; ignores stack size
     */
    public static boolean couldMerge(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return true;
        }
        return a.itemID == b.itemID && a.getItemDamage() == b.getItemDamage() && sameItemTags(a, b);
    }
    
    public static boolean sameItemTags(ItemStack a, ItemStack b) {
        if (a.stackTagCompound == null || b.stackTagCompound == null) {
            return a.stackTagCompound == b.stackTagCompound;
        }
        a.stackTagCompound.setName("tag"); //Notch.
        b.stackTagCompound.setName("tag"); //Notch.
        return a.stackTagCompound.equals(b.stackTagCompound);
    }
    
    /**
     * Compare includes damage value; ignores stack size and NBT
     */
    public static boolean similar(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.itemID == b.itemID && a.getItemDamage() == b.getItemDamage();
    }
    
    /**
     * Compare only itemIDs and damage value, taking into account that a damage value of -1 matches any
     */
    public static boolean wildcardSimilar(ItemStack template, ItemStack stranger) {
        if (template == null || stranger == null) {
            return template == stranger;
        }
        if (template.getItemDamage() == WILDCARD_DAMAGE) {
            return template.itemID == stranger.itemID;
        }
        return similar(template, stranger);
    }
    
    public static boolean oreDictionarySimilar(Object template, ItemStack stranger) {
        if (template instanceof String) {
            ArrayList<ItemStack> ores = OreDictionary.getOres((String) template);
            for (int i = 0; i < ores.size(); i++) {
                if (wildcardSimilar(ores.get(i), stranger)) {
                    return true;
                }
            }
            return false;
        } else if (template instanceof List) {
            for (Object o : (List)template) {
                if (oreDictionarySimilar(o, stranger)) {
                    return true;
                }
            }
            return false;
        } else {
            return wildcardSimilar((ItemStack) template, stranger);
        }
    }
    
    public static int stackSize(ItemStack is) {
        return (is == null) ? 0 : is.stackSize;
    }
    
    public static ItemStack normalDecr(ItemStack is) {
        is.stackSize--;
        return is.stackSize <= 0 ? null : is;
    }
    
    /** Makes sure there is an NBT tag on the ItemStack, and then returns it. */
    public static NBTTagCompound getTag(ItemStack is) {
        NBTTagCompound ret = is.getTagCompound();
        if (ret == null) {
            ret = new NBTTagCompound();
            is.setTagCompound(ret);
        }
        return ret;
    }
    
    public static long getItemHash(ItemStack is) {
        if (is == null) {
            return Long.MIN_VALUE;
        }
        long ih = is.itemID;
        long md = is.getItemDamage();
        long tg = 0;
        if (is.hasTagCompound()) {
            tg = is.getTagCompound().hashCode();
        }
        return (ih << 48) + (md << 32) + tg + is.stackSize*100;
    }
    
    public static String getCustomItemName(ItemStack is) {
        if (is != null && is.hasDisplayName()) {
            return is.getDisplayName();
        }
        return null;
    }

    public static boolean itemCanFire(World w, ItemStack is, int tickDelay) {
        NBTTagCompound tag = getTag(is);
        long t = tag.getLong("lf");
        if (t > w.getTotalWorldTime()) {
            tag.setLong("lf", w.getTotalWorldTime());
            return true;
        }
        if (t + tickDelay > w.getTotalWorldTime()) {
            return false;
        }
        tag.setLong("lf", w.getTotalWorldTime());
        return true;
    }

    public static ItemStack normalize(ItemStack is) {
        if (is == null || is.stackSize <= 0) {
            return null;
        }
        return is;
    }

    public static int getStackSize(ItemStack is) {
        if (is == null) {
            return 0;
        }
        return is.stackSize;
    }
    
    public static int getFreeSpace(ItemStack is, int stackLimit) {
        int max = Math.min(is.getMaxStackSize(), stackLimit);
        return Math.max(0, max - is.stackSize);
    }
    
    //Slot transfering

    /**
     * Use transferSlotToSlots
     * @param srcInv
     * @param slotIndex
     * @param destInv
     * @param targetSlots
     * @return
     */
    @Deprecated
    public static ItemStack transferStackToArea(IInventory srcInv, int slotIndex,
            IInventory destInv, Iterable<Integer> targetSlots) {
        //this is probably all wrong. >_>
        ItemStack is = srcInv.getStackInSlot(slotIndex);
        if (is == null || is.stackSize == 0) {
            return null;
        }
        // fill up pre-existing stacks
        for (int i : targetSlots) {
            ItemStack target = destInv.getStackInSlot(i);
            if (target == null) {
                continue;
            }
            if (FzUtil.couldMerge(is, target)) {
                int free_space = target.getMaxStackSize() - target.stackSize;
                int incr = Math.min(free_space, is.stackSize);
                if (incr <= 0) {
                    continue;
                }
                is.stackSize -= incr;
                target.stackSize += incr;
            }
            if (is.stackSize <= 0) {
                srcInv.setInventorySlotContents(slotIndex, null);
                return null;
            }
        }
        // make new stacks
        for (int i : targetSlots) {
            ItemStack target = destInv.getStackInSlot(i);
            if (target == null) {
                destInv.setInventorySlotContents(i, is.copy());
                is.stackSize = 0;
                srcInv.setInventorySlotContents(slotIndex, null);
                return null;
            }
        }
        if (is.stackSize <= 0) {
            srcInv.setInventorySlotContents(slotIndex, null);
            return null;
        }
        srcInv.setInventorySlotContents(slotIndex, is);
        return is;
    }
    
    public static ItemStack transferSlotToSlots(EntityPlayer player, Slot clickSlot, Iterable<Slot> destinations) {
        ItemStack got = tryTransferSlotToSlots(player, clickSlot, destinations);
        if (got != null) {
            clickSlot.putStack(got);
        }
        return null;
    }
    
    public static ItemStack tryTransferSlotToSlots(EntityPlayer player, Slot clickSlot, Iterable<Slot> destinations) {
        ItemStack clickStack = normalize(clickSlot.getStack());
        if (clickStack == null) {
            return null;
        }
        clickSlot.onPickupFromSlot(player, clickStack);
        //try to fill up partially filled slots
        for (Slot slot : destinations) {
            ItemStack is = normalize(slot.getStack());
            if (is == null || !FzUtil.couldMerge(is, clickStack)) {
                continue;
            }
            int freeSpace = Math.min(is.getMaxStackSize() - is.stackSize, slot.getSlotStackLimit() - is.stackSize);
            if (freeSpace <= 0) {
                continue;
            }
            if (!slot.isItemValid(clickStack)) {
                continue;
            }
            int delta = Math.min(freeSpace, clickStack.stackSize);
            is.stackSize += delta;
            slot.putStack(is);
            clickStack.stackSize -= delta;
            if (clickStack.stackSize <= 0) {
                clickSlot.putStack(null);
                return null;
            }
        }
        //try to fill up empty slots
        for (Slot slot : destinations) {
            if (slot.getHasStack() || !slot.isItemValid(clickStack)) {
                continue;
            }
            int freeSpace = Math.min(slot.getSlotStackLimit(), clickStack.getMaxStackSize());
            int delta = Math.min(freeSpace, clickStack.stackSize);
            ItemStack toPut = clickStack.copy();
            toPut.stackSize = delta;
            slot.putStack(toPut);
            clickStack.stackSize -= delta;
            clickStack = normalize(clickStack);
            if (clickStack == null) {
                clickSlot.putStack(null);
                return null;
            }
        }
        
        return normalize(clickStack);
    }

    public static abstract class FzInv {
        public abstract int size();
        abstract int slotIndex(int i);
        
        boolean forceInsert = false;
        boolean callInvChanged = true;
        
        public final IInventory under;
        
        public FzInv(IInventory inv) {
            this.under = inv;
        }
        
        public void setInsertForce(boolean b) {
            forceInsert = b;
        }
        
        public void setCallOnInventoryChanged(boolean b) {
            callInvChanged = b;
        }
        
        public void onInvChanged() {
            if (callInvChanged) {
                under.onInventoryChanged();
            }
        }
        
        public ItemStack get(int i) {
            return under.getStackInSlot(slotIndex(i));
        }
        
        public void set(int i, ItemStack is) {
            under.setInventorySlotContents(slotIndex(i), is);
        }
        
        public int getFreeSpace(int i) {
            ItemStack dest = get(i);
            if (dest == null) {
                return under.getInventoryStackLimit();
            }
            int ret = Math.min(under.getInventoryStackLimit(), dest.getMaxStackSize()) - dest.stackSize;
            return Math.max(0, ret);
        }
        
        public int getFreeSpaceFor(ItemStack target, int maxNeeded) {
            int space = 0;
            int spaceInEmpty = Math.min(target.getMaxStackSize(), under.getInventoryStackLimit());
            for (int i = 0; i < size(); i++) {
                if (!canInsert(i, target)) continue;
                ItemStack is = get(i);
                if (is == null) {
                    space += spaceInEmpty;
                } else if (couldMerge(target, is)) {
                    space += spaceInEmpty - is.stackSize;
                } else {
                    continue;
                }
                if (space >= maxNeeded) {
                    return space;
                }
            }
            return space;
        }
        
        public boolean canPush(ItemStack is) {
            for (int i = 0; i < size(); i++) {
                ItemStack here = get(i);
                if (get(i) == null) {
                    return true;
                }
                if (couldMerge(here, is)) {
                    return true;
                }
            }
            return false;
        }
        
        public ItemStack pushInto(int i, ItemStack is) {
            int slotIndex = slotIndex(i);
            if (!canInsert(i, is)) {
                return is;
            }
            ItemStack dest = under.getStackInSlot(slotIndex);
            if (dest == null) {
                ItemStack toPut = is;
                int stack_limit = under.getInventoryStackLimit();
                if (toPut.stackSize > stack_limit) {
                    toPut = is.splitStack(stack_limit);
                } else {
                    is = null;
                }
                under.setInventorySlotContents(slotIndex, toPut);
                onInvChanged();
                return is;
            }
            if (!FzUtil.couldMerge(dest, is)) {
                return is;
            }

            int dest_free = getFreeSpace(i);
            if (dest_free < 1) {
                return is;
            }
            int delta = Math.min(dest_free, is.stackSize);
            dest.stackSize += delta;
            is.stackSize -= delta;
            under.setInventorySlotContents(slotIndex, dest);
            onInvChanged();
            return normalize(is);
        }
        
        public boolean canExtract(int slot, ItemStack is) {
            return true;
        }
        
        public boolean canInsert(int i, ItemStack is) {
            if (forceInsert) {
                return true;
            }
            return under.isItemValidForSlot(slotIndex(i), is) && couldMerge(get(i), is);
        }
        
        public boolean isEmpty() {
            for (int i = 0; i < size(); i++) {
                if (get(i) != null) {
                    return false;
                }
            }
            return true;
        }
        
        public boolean transfer(FzInv dest_inv, int max_transfer, ItemStack exclude) {
            for (int i = 0; i < size(); i++) {
                ItemStack is = normalize(get(i));
                if (is == null || is == exclude) {
                    continue;
                }
                if (!canExtract(i, is)) {
                    continue;
                }
                if (is.stackSize <= max_transfer) {
                    int orig_size = is.stackSize;
                    is = dest_inv.push(is);
                    if (orig_size != getStackSize(is)) {
                        set(i, is);
                        onInvChanged();
                        return true;
                    }
                } else {
                    ItemStack to_push = is.copy();
                    int orig_size = Math.min(to_push.stackSize, max_transfer);
                    to_push.stackSize = orig_size;
                    to_push = dest_inv.push(to_push);
                    int taken = orig_size - getStackSize(to_push);
                    if (taken > 0) {
                        is.stackSize -= taken;
                        is = normalize(is);
                        set(i, is);
                        onInvChanged();
                        return true;
                    }
                }
            }
            return false;
        }
        
        public int transfer(int i, FzInv dest_inv, int dest_i, int max_transfer) {
            ItemStack src = normalize(get(i));
            if (src == null) {
                return 0;
            }
            if (!canExtract(i, src)) {
                return 0;
            }
            ItemStack dest = dest_inv.get(dest_i);
            if (dest == null) {
                dest = src.copy();
                dest.stackSize = 0;
            } else if (!couldMerge(src, dest)) {
                return 0;
            }
            if (!dest_inv.canInsert(dest_i, src)) {
                return 0;
            }
            int dest_free = dest_inv.getFreeSpace(dest_i);
            if (dest_free < 1) {
                return 0;
            }
            int delta = Math.min(dest_free, src.stackSize);
            delta = Math.min(max_transfer, delta);
            dest.stackSize += delta;
            src.stackSize -= delta;
            src = normalize(src);
            dest_inv.set(dest_i, dest);
            set(i, src);
            if (callInvChanged) {
                dest_inv.under.onInventoryChanged();
                under.onInventoryChanged();
            }
            return delta;
        }
        
        public ItemStack push(ItemStack is) {
            is = normalize(is);
            //First, fill up already existing stacks
            for (int i = 0; i < size(); i++) {
                if (is == null) {
                    return null;
                }
                ItemStack dest = get(i);
                if (dest != null) {
                    is = normalize(pushInto(i, is));
                }
            }
            //Second, add to null stacks
            for (int i = 0; i < size(); i++) {
                if (is == null) {
                    return null;
                }
                ItemStack dest = get(i);
                if (dest == null) {
                    is = normalize(pushInto(i, is));
                }
            }
            return is;
        }
        
        public ItemStack peek() {
            for (int i = 0; i < size(); i++) {
                ItemStack is = normalize(get(i));
                if (is != null) {
                    return is;
                }
            }
            return null;
        }
        
        public ItemStack pull() {
            for (int i = 0; i < size(); i++) {
                ItemStack ret = pull(i, 64);
                if (ret != null) {
                    return ret;
                }
            }
            return null;
        }
        
        public ItemStack pullFromSlot(int slot) {
            return pull(slot, 64);
        }
        
        public ItemStack pullWithLimit(int limit) {
            for (int i = 0; i < size(); i++) {
                ItemStack ret = pull(i, limit);
                if (ret != null) {
                    return ret;
                }
            }
            return null;
        }
        
        public ItemStack pull(int slot, int limit) {
            int i = slotIndex(slot);
            ItemStack is = under.getStackInSlot(i);
            if (normalize(is) == null) {
                return null;
            }
            if (!canExtract(slot, is)) {
                return null;
            }
            return under.decrStackSize(i, limit);
        }
        
        public ItemStack pull(ItemStack toMatch, int limit, boolean strict) {
            ItemStack ret = null;
            for (int i = 0; i < size(); i++) {
                ItemStack is = get(i);
                if (strict) {
                    if (!FzUtil.couldMerge(toMatch, is)) {
                        continue;
                    }
                } else {
                    if (!FzUtil.wildcardSimilar(toMatch, is)) {
                        continue;
                    }
                }
                ItemStack pulled = FzUtil.normalize(pull(i, limit));
                if (pulled == null) {
                    continue;
                }
                limit -= pulled.stackSize;
                if (ret == null) {
                    ret = pulled;
                } else {
                    ret.stackSize += pulled.stackSize;
                }
                if (limit <= 0) {
                    break;
                }
            }
            return ret;
        }
        
        private int slice_index(int i) {
            int size = size();
            while (i < 0 && size > 0) {
                i += size; //super inefficient!
            }
            return i;
        }
        
        public FzInv slice(int start, int end) {
            start = slice_index(start);
            end = slice_index(end);
            start = Math.max(start, 0);
            end = Math.min(end, size());
            if (end < start) {
                end = start;
            }
            if (start > end) {
                start = end;
            }
            return new SubsetInv(this, start, end);
        }
    }
    
    public static class SubsetInv extends FzInv {
        final FzInv ui;
        int start, end;
        public SubsetInv(FzInv ui, int start, int end) {
            super(ui.under);
            this.ui = ui;
            this.start = start;
            this.end = end;
        }
        
        @Override
        public int size() {
            return end - start;
        }

        @Override
        int slotIndex(int i) {
            return ui.slotIndex(start + i);
        }
        
    }

    public static class PlainInvWrapper extends FzInv {
        final int length;
        public PlainInvWrapper(IInventory inv) {
            super(inv);
            length = inv.getSizeInventory();
        }
        
        @Override
        int slotIndex(int i) {
            return i;
        }
        
        @Override
        public int size() {
            return length;
        }
    }
    
    public static class Container2IInventory implements IInventory {
        Container cont;
        public Container2IInventory(Container cont) {
            this.cont = cont;
        }
        @Override
        public int getSizeInventory() {
            return cont.getInventory().size();
        }
        
        @Override
        public ItemStack getStackInSlot(int i) {
            return cont.getSlot(i).getStack();
        }
        @Override
        public ItemStack decrStackSize(int i, int j) {
            return cont.getSlot(i).decrStackSize(j);
        }
        @Override
        public ItemStack getStackInSlotOnClosing(int i) {
            return null;
        }
        @Override
        public void setInventorySlotContents(int i, ItemStack itemstack) {
            cont.putStackInSlot(i, itemstack);
        }
        
        @Override
        public boolean isItemValidForSlot(int i, ItemStack itemstack) {
            return cont.getSlot(i).isItemValid(itemstack);
        }
        
        @Override
        public String getInvName() { return "Container2IInventory wrapper"; }
        
        @Override
        public boolean isInvNameLocalized() { return false; }
        @Override
        public int getInventoryStackLimit() { return 64; }
        @Override
        public void onInventoryChanged() { }
        @Override
        public boolean isUseableByPlayer(EntityPlayer entityplayer) { return false; }
        @Override
        public void openChest() { }
        @Override
        public void closeChest() { }
    }
    
    public static FzInv openInventory(IInventory orig_inv, ForgeDirection side) {
        return openInventory(orig_inv, side.ordinal(), true);
    }
    
    public static FzInv openInventory(IInventory orig_inv, ForgeDirection side, boolean openBothChests) {
        return openInventory(orig_inv, side.ordinal(), openBothChests);
    }
    
    public static FzInv openInventory(IInventory orig_inv, final int side) {
        return openInventory(orig_inv, side, true);
    }
    
    public static FzInv openInventory(IInventory orig_inv, final int side, boolean openBothChests) {
        if (orig_inv == null) {
            return null;
        }
        if (orig_inv instanceof TileEntityChest) {
            orig_inv = openDoubleChest((TileEntityChest) orig_inv, openBothChests);
            if (orig_inv == null) {
                return null;
            }
        }
        if (orig_inv instanceof net.minecraft.inventory.ISidedInventory) {
            final net.minecraft.inventory.ISidedInventory inv = (net.minecraft.inventory.ISidedInventory) orig_inv;
            int[] _ = inv.getAccessibleSlotsFromSide(side);
            if (_ == null) {
                _ = new int[0];
            }
            final int[] slotMap = _;
            return new FzInv(inv) {
                @Override
                int slotIndex(int i) {
                    return slotMap[i];
                }
                
                @Override
                public int size() {
                    return slotMap.length;
                }
                
                @Override
                public boolean canExtract(int slot, ItemStack is) {
                    if (is == null) {
                        return false;
                    }
                    return inv.canExtractItem(slotMap[slot], is, side);
                }
                
                @Override
                public boolean canInsert(int i, ItemStack is) {
                    if (forceInsert) {
                        return true;
                    }
                    return super.canInsert(i, is) && inv.canInsertItem(slotIndex(i), is, side);
                }};
        } else {
            return new PlainInvWrapper(orig_inv);
        }
    }
    
    public static FzInv openInventory(Entity ent, boolean access_players) {
        if (ent instanceof EntityPlayer && !access_players) {
            return null;
        }
        if (ent instanceof IInventory) {
            return openInventory((IInventory) ent, ForgeDirection.UP);
        }
        if (ent instanceof EntityPlayer) {
            InventoryPlayer ip = ((EntityPlayer)ent).inventory;
            return openInventory(ip, ForgeDirection.UP).slice(0, ip.mainInventory.length);
        }
        return null;		
    }

    public static boolean canAccessSlot(IInventory inv, int slot) {
        if (inv instanceof net.minecraft.inventory.ISidedInventory) {
            net.minecraft.inventory.ISidedInventory isi = (net.minecraft.inventory.ISidedInventory) inv;
            //O(n). Ugh.
            for (int i = 0; i < 6; i++) {
                int[] slots = isi.getAccessibleSlotsFromSide(i);
                for (int j = 0; j < slots.length; j++) {
                    if (slots[j] == slot) {
                        return true;
                    }
                }
            }
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * If you are accessing multiple chests, and some might be adjacent you'll want to treat them as a double chest. Calling this function with a lower chest
     * will return 'null'; calling with an upper chest will return an InventoryLargeChest. If it's a single chest, it'll return that chest.
     * 
     * @param chest
     * @return
     */
    public static IInventory openDoubleChest(TileEntityChest chest, boolean openBothSides) {
        IInventory origChest = (TileEntityChest) chest;
        World world = chest.worldObj;
        int i = chest.xCoord, j = chest.yCoord, k = chest.zCoord;
        Block cb = chest.getBlockType();
        if (cb == null) {
            return null;
        }
        int chestBlock = cb.blockID;
        if (world.getBlockId(i - 1, j, k) == chestBlock) {
            return new InventoryLargeChest(origChest.getInvName(), (TileEntityChest) world.getBlockTileEntity(i - 1, j, k), origChest);
        }
        if (world.getBlockId(i, j, k - 1) == chestBlock) {
            return new InventoryLargeChest(origChest.getInvName(), (TileEntityChest) world.getBlockTileEntity(i, j, k - 1), origChest);
        }
        // If we're the lower chest, skip ourselves
        if (world.getBlockId(i + 1, j, k) == chestBlock) {
            if (openBothSides) {
                return new InventoryLargeChest(origChest.getInvName(), origChest, (TileEntityChest) world.getBlockTileEntity(i + 1, j, k));
            }
            return null;
        }
        if (world.getBlockId(i, j, k + 1) == chestBlock) {
            if (openBothSides) {
                return new InventoryLargeChest(origChest.getInvName(), origChest, (TileEntityChest) world.getBlockTileEntity(i, j, k + 1));
            }
            return null;
        }

        return chest;
    }
    
    public static IInventory openDoubleChest(IInventory inv, boolean openBothSides) {
        if (inv instanceof TileEntityChest) {
            return openDoubleChest((TileEntityChest) inv, openBothSides);
        }
        return inv;
    }
    
    //Recipe creation
    public static IRecipe createShapedRecipe(ItemStack result, Object... args) {
        String var3 = "";
        int var4 = 0;
        int var5 = 0;
        int var6 = 0;

        if (args[var4] instanceof String[]) {
            String[] var7 = (String[]) ((String[]) args[var4++]);

            for (int var8 = 0; var8 < var7.length; ++var8) {
                String var9 = var7[var8];
                ++var6;
                var5 = var9.length();
                var3 = var3 + var9;
            }
        } else {
            while (args[var4] instanceof String) {
                String var11 = (String) args[var4++];
                ++var6;
                var5 = var11.length();
                var3 = var3 + var11;
            }
        }

        HashMap var12;

        for (var12 = new HashMap(); var4 < args.length; var4 += 2) {
            Character var13 = (Character) args[var4];
            ItemStack var14 = null;

            if (args[var4 + 1] instanceof Item) {
                var14 = new ItemStack((Item) args[var4 + 1]);
            } else if (args[var4 + 1] instanceof Block) {
                var14 = new ItemStack((Block) args[var4 + 1], 1, -1);
            } else if (args[var4 + 1] instanceof ItemStack) {
                var14 = (ItemStack) args[var4 + 1];
            }

            var12.put(var13, var14);
        }

        ItemStack[] var15 = new ItemStack[var5 * var6];

        for (int var16 = 0; var16 < var5 * var6; ++var16) {
            char var10 = var3.charAt(var16);

            if (var12.containsKey(Character.valueOf(var10))) {
                var15[var16] = ((ItemStack) var12.get(Character.valueOf(var10))).copy();
            } else {
                var15[var16] = null;
            }
        }

        return new ShapedRecipes(var5, var6, var15, result);
    }

    public static IRecipe createShapelessRecipe(ItemStack result, Object... args) {
        ArrayList var3 = new ArrayList();
        int var5 = args.length;

        for (int var6 = 0; var6 < var5; ++var6)
        {
            Object var7 = args[var6];

            if (var7 instanceof ItemStack)
            {
                var3.add(((ItemStack) var7).copy());
            }
            else if (var7 instanceof Item)
            {
                var3.add(new ItemStack((Item) var7));
            }
            else
            {
                if (!(var7 instanceof Block))
                {
                    throw new RuntimeException("Invalid shapeless recipy!");
                }

                var3.add(new ItemStack((Block) var7));
            }
        }

        return new ShapelessRecipes(result, var3);
    }

    static Random rand = new Random();

    public static EntityItem spawnItemStack(Coord c, ItemStack item) {
        if (item == null) {
            return null;
        }
        double dx = rand.nextFloat() * 0.5 - 0.5;
        double dy = rand.nextFloat() * 0.5 - 0.5;
        double dz = rand.nextFloat() * 0.5 - 0.5;

        EntityItem entityitem = new EntityItem(c.w, c.x + 0.5, c.y + 0.5, c.z + 0.5, item);
        entityitem.motionY = 0.2 + rand.nextGaussian() * 0.02;
        entityitem.motionX = rand.nextGaussian() * 0.02;
        entityitem.motionZ = rand.nextGaussian() * 0.02;
        c.w.spawnEntityInWorld(entityitem);
        return entityitem;
    }
    
    public static EntityItem spawnItemStack(Entity c, ItemStack item) {
        if (item == null) {
            return null;
        }
        double dx = rand.nextFloat() * 0.5 - 0.5;
        double dy = rand.nextFloat() * 0.5 - 0.5;
        double dz = rand.nextFloat() * 0.5 - 0.5;

        EntityItem entityitem = new EntityItem(c.worldObj, c.posX + c.width/2, c.posY + c.height/2, c.posZ + c.width/2, item);
        entityitem.motionY = 0.2 + rand.nextGaussian() * 0.02;
        entityitem.motionX = rand.nextGaussian() * 0.02;
        entityitem.motionZ = rand.nextGaussian() * 0.02;
        c.worldObj.spawnEntityInWorld(entityitem);
        return entityitem;
    }
    

    public static int determineOrientation(EntityPlayer player) {
        if (player.rotationPitch > 75) {
            return 0;
        }
        if (player.rotationPitch <= -75) {
            return 1;
        }
        return determineFlatOrientation(player);
    }
    
    public static int determineFlatOrientation(EntityPlayer player) {
        //stolen from BlockPistonBase.determineOrientation. It was reversed, & we handle the y-axis differently
        int var7 = MathHelper.floor_double((double) ((180 + player.rotationYaw) * 4.0F / 360.0F) + 0.5D) & 3;
        return var7 == 0 ? 2 : (var7 == 1 ? 5 : (var7 == 2 ? 3 : (var7 == 3 ? 4 : 0)));
    }
    
    public static DeltaCoord getFlatDiagonalFacing(EntityPlayer player) {
        double angle = Math.toRadians(90 + player.rotationYaw);
        int dx = Math.cos(angle) > 0 ? 1 : -1;
        int dz = Math.sin(angle) > 0 ? 1 : -1;
        return new DeltaCoord(dx, 0, dz);
    }
    
    public static byte getOpposite(int dir) {
        return (byte) ForgeDirection.getOrientation(dir).getOpposite().ordinal();
    }
    
    public static <E extends Enum> E shiftEnum(E current, E values[], int delta) {
        int next = current.ordinal() + delta;
        if (next < 0) {
            return values[values.length - 1];
        }
        if (next >= values.length) {
            return values[0];
        }
        return values[next];
    }
    
    
    //Liquid tank handling
    
    public static void writeTank(NBTTagCompound tag, FluidTank tank, String name) {
        FluidStack ls = tank.getFluid();
        if (ls == null) {
            return;
        }
        NBTTagCompound liquid_tag = new NBTTagCompound(name);
        ls.writeToNBT(liquid_tag);
        tag.setTag(name, liquid_tag);
    }
    
    public static void readTank(NBTTagCompound tag, FluidTank tank, String name) {
        NBTTagCompound liquid_tag = tag.getCompoundTag(name);
        FluidStack ls = FluidStack.loadFluidStackFromNBT(liquid_tag);
        tank.setFluid(ls);
    }
    
    public static void spill(Coord where, FluidStack what) {
        //TODO: Should be in Coord, no?
        if (what == null || what.amount < 0) {
            return;
        }
        FluidEvent.fireEvent(new FluidEvent.FluidSpilledEvent(what, where.w, where.x, where.y, where.z));
    }
    
    //AxisAlignedBB & Vec3 stuff
    public static Vec3 getMin(AxisAlignedBB aabb) {
        return Vec3.createVectorHelper(aabb.minX, aabb.minY, aabb.minZ);
    }
    
    public static void setMin(AxisAlignedBB aabb, Vec3 v) {
        aabb.minX = v.xCoord;
        aabb.minY = v.yCoord;
        aabb.minZ = v.zCoord;
    }
    
    public static Vec3 getMax(AxisAlignedBB aabb) {
        return Vec3.createVectorHelper(aabb.maxX, aabb.maxY, aabb.maxZ);
    }
    
    public static void setMax(AxisAlignedBB aabb, Vec3 v) {
        aabb.maxX = v.xCoord;
        aabb.maxY = v.yCoord;
        aabb.maxZ = v.zCoord;
    }
    
    public static Vec3 averageVec(Vec3 a, Vec3 b) {
        return Vec3.createVectorHelper((a.xCoord + b.xCoord)/2, (a.yCoord + b.yCoord)/2, (a.zCoord + b.zCoord)/2);
    }
    
    public static boolean intersect(double la, double ha, double lb, double hb) {
        //If we're not intersecting, then one is to the right of the other.
        //<--  (la ha) -- (lb hb) -->
        //<--- (lb hb) -- (la ha) -->
        return !(ha < lb || hb < la);
    }
    
    public static InventoryCrafting makeCraftingGrid() {
        return new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer entityplayer) {
                return false;
            }

            @Override
            public void onCraftMatrixChanged(IInventory iinventory) {
            }
        }, 3, 3);
    }
    
    private static class FzFakePlayer extends EntityPlayer {
        Coord where;

        private FzFakePlayer(World par1World, String par2Str, Coord where) {
            super(par1World, par2Str);
            this.where = where;
        }

        //TODO: Forge'll probably have this working properly by the time I catch up to it.
        public void sendChatToPlayer(String s) {}
        @Override public void sendChatToPlayer(ChatMessageComponent chatmessagecomponent) { }
        public boolean canCommandSenderUseCommand(int i, String s) { return false; }
        @Override public ChunkCoordinates getPlayerCoordinates() { return new ChunkCoordinates(where.x, where.y, where.z); }
        @Override public void addStat(StatBase par1StatBase, int par2) { }
        @Override public void openGui(Object mod, int modGuiId, World world, int x, int y, int z) { }
    }
    
    private static HashMap<String, WeakHashMap<World, FzFakePlayer>> usedPlayerCache = new HashMap();
    
    public static EntityPlayer makePlayer(final Coord where, String use) {
        WeakHashMap<World, FzFakePlayer> fakePlayerCache = usedPlayerCache.get(use);
        if (fakePlayerCache == null) {
            fakePlayerCache = new WeakHashMap<World, FzFakePlayer>();
            usedPlayerCache.put(use, fakePlayerCache);
        }
        FzFakePlayer found = fakePlayerCache.get(where.w);
        if (found == null) {
            found = new FzFakePlayer(where.w, "[FZ." + use + "]", where);
            fakePlayerCache.put(where.w, found);
        }
        found.where = where;
        where.setAsEntityLocation(found);
        Arrays.fill(found.inventory.armorInventory, null);
        Arrays.fill(found.inventory.mainInventory, null);
        return found;
    }
    
    public static void addInventoryToArray(IInventory inv, ArrayList<ItemStack> ret) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack is = FzUtil.normalize(inv.getStackInSlot(i));
            if (is != null) {
                ret.add(is);
            }
        }
    }
    
    private static ThreadLocal<ArrayList<ForgeDirection>> direction_cache = new ThreadLocal<ArrayList<ForgeDirection>>();

    public static ArrayList<ForgeDirection> getRandomDirections(Random rand) {
        ArrayList<ForgeDirection> ret = direction_cache.get();
        if (ret == null) {
            ret = new ArrayList(6);
            for (int i = 0; i < 6; i++) {
                ret.add(ForgeDirection.getOrientation(i));
            }
            direction_cache.set(ret);
        }
        Collections.shuffle(ret, rand);
        return ret;
    }
    
    private static ThreadLocal<Random> random_cache = new ThreadLocal<Random>();
    
    public static Random dirtyRandomCache() {
        Random ret = random_cache.get();
        if (ret == null) {
            ret = new Random();
            random_cache.set(ret);
        }
        return ret;
    }
    
    @SideOnly(Side.CLIENT)
    public static RenderBlocks getRB() {
        return Minecraft.getMinecraft().renderGlobal.globalRenderBlocks;
    }
    
    static InventoryCrafting getCrafter(ItemStack...slots) {
        InventoryCrafting craft = FzUtil.makeCraftingGrid();
        for (int i = 0; i < 9; i++) {
            craft.setInventorySlotContents(i, slots[i]);
        }
        return craft;
    }
    
    private static final ItemStack[] slots3x3 = new ItemStack[9];
    
    static boolean wantSize(int size, TileEntity where, ItemStack...slots) {
        if (slots.length != size) {
            System.out.println("Tried to craft with items.length != " + size);
            if (where != null) {
                System.out.println("At " + new Coord(where));
            }
            Thread.dumpStack();
            return true;
        }
        return false;
    }
    
    public static List<ItemStack> craft1x1(TileEntity where, boolean fake, ItemStack what) {
        for (int i = 0; i < slots3x3.length; i++) {
            slots3x3[i] = null;
        }
        slots3x3[4] = what;
        return craft3x3(where, fake, false, slots3x3);
    }
    
    public static List<ItemStack> craft2x2(TileEntity where, boolean fake, ItemStack...slots) {
        if (wantSize(4, where, slots)) {
            return Arrays.asList(slots);
        }
        for (int i = 0; i < slots3x3.length; i++) {
            slots3x3[i] = null;
        }
        slots3x3[0] = slots[0];
        slots3x3[1] = slots[1];
        slots3x3[3] = slots[2];
        slots3x3[4] = slots[3];
        return craft3x3(where, fake, false, slots3x3);
    }
    
    public static boolean craft_succeeded = false;
    public static ArrayList<ItemStack> emptyArrayList = new ArrayList(0);
    public static List<ItemStack> craft3x3(TileEntity where, boolean fake, boolean leaveSlots, ItemStack... slots) {
        craft_succeeded = false;
        // Return the crafting result, and any leftover ingredients (buckets)
        // If the crafting recipe fails, return our contents.
        if (wantSize(9, where, slots)) {
            return leaveSlots ? emptyArrayList : Arrays.asList(slots);
        }

        InventoryCrafting craft = getCrafter(slots);

        IRecipe recipe = findMatchingRecipe(craft, where == null ? null : where.worldObj);
        ItemStack result = null;
        if (recipe != null) {
            result = recipe.getCraftingResult(craft);
        }
        
        if (result == null) {
            // crafting failed, dump everything
            return leaveSlots ? emptyArrayList : Arrays.asList(slots);
        }
        final ArrayList<ItemStack> ret = new ArrayList<ItemStack>();
        if (fake) {
            ret.add(result);
            craft_succeeded = true;
            return ret;
        }
        Coord pos = null;
        if (where != null) {
            pos = new Coord(where); 
        }
        EntityPlayer fakePlayer = FzUtil.makePlayer(pos, "Crafting");
        if (pos != null) {
            pos.setAsEntityLocation(fakePlayer);
        }

        IInventory craftResult = new InventoryCraftResult();
        craftResult.setInventorySlotContents(0, result);
        SlotCrafting slot = new SlotCrafting(fakePlayer, craft, craftResult, 0, 0, 0);
        slot.onPickupFromSlot(fakePlayer, result);
        ret.add(result);
        if (!leaveSlots) {
            FzUtil.addInventoryToArray(craft, ret);
        }
        FzUtil.addInventoryToArray(fakePlayer.inventory, ret);

        craft_succeeded = true;
        return ret;
    }
    
    
    static ArrayList<IRecipe> recipeCache = new ArrayList();
    private static int cache_fear = 10;
    public static IRecipe findMatchingRecipe(InventoryCrafting inv, World world) {
        List<IRecipe> craftingManagerRecipes = CraftingManager.getInstance().getRecipeList();
        if (Core.serverStarted) {
            cache_fear--;
            if (cache_fear > 0) {
                return lookupRecipeUncached(inv, world);
            }
            if (craftingManagerRecipes.size() != recipeCache.size()) {
                if (cache_fear < 0) {
                    cache_fear = 10;
                    return lookupRecipeUncached(inv, world);
                }
                recipeCache.clear();
                recipeCache.ensureCapacity(craftingManagerRecipes.size());
                recipeCache.addAll(craftingManagerRecipes);
                recipeCache.add(stupid_hacky_vanilla_item_repair_recipe);
            }
            for (int i = 0; i < recipeCache.size(); i++) {
                IRecipe recipe = recipeCache.get(i);
                if (recipe.matches(inv, world)) {
                    if (i > 50) {
                        int j = i/3;
                        IRecipe swapeh = recipeCache.get(j);
                        recipeCache.set(j, recipe);
                        recipeCache.set(i, swapeh);
                    }
                    return recipe;
                }
            }
        } else {
            return lookupRecipeUncached(inv, world);
        }
        
        return null;
    }
    
    public static IRecipe lookupRecipeUncached(InventoryCrafting inv, World world) {
        List<IRecipe> craftingManagerRecipes = CraftingManager.getInstance().getRecipeList();
        for (int i = 0; i < craftingManagerRecipes.size(); i++) {
            IRecipe recipe = craftingManagerRecipes.get(i);
            ItemStack output = recipe.getRecipeOutput();
            if (recipe.matches(inv, world)) {
                return recipe;
            }
        }
        return null;
    }
    
    private static IRecipe stupid_hacky_vanilla_item_repair_recipe = new IRecipe() {
        ItemStack firstItem, secondItem, result;
        
        void update(IInventory par1InventoryCrafting) {
            //This is copied from CraftingManager.findMatchingRecipe
            firstItem = secondItem = result = null;
            int i = 0;
            int j;

            for (j = 0; j < par1InventoryCrafting.getSizeInventory(); ++j)
            {
                ItemStack itemstack2 = par1InventoryCrafting.getStackInSlot(j);

                if (itemstack2 != null)
                {
                    if (i == 0)
                    {
                        firstItem = itemstack2;
                    }

                    if (i == 1)
                    {
                        secondItem = itemstack2;
                    }

                    ++i;
                }
            }

            if (i == 2 && firstItem.itemID == secondItem.itemID && firstItem.stackSize == 1 && secondItem.stackSize == 1 && Item.itemsList[firstItem.itemID].isRepairable())
            {
                Item item = Item.itemsList[firstItem.itemID];
                int k = item.getMaxDamage() - firstItem.getItemDamageForDisplay();
                int l = item.getMaxDamage() - secondItem.getItemDamageForDisplay();
                int i1 = k + l + item.getMaxDamage() * 5 / 100;
                int j1 = item.getMaxDamage() - i1;

                if (j1 < 0)
                {
                    j1 = 0;
                }

                result = new ItemStack(firstItem.itemID, 1, j1);
            }
        }
        
        @Override
        public boolean matches(InventoryCrafting inventorycrafting, World world) {
            update(inventorycrafting);
            return result != null;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inventorycrafting) {
            update(inventorycrafting);
            return result;
        }

        @Override
        public int getRecipeSize() {
            return 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return null;
        }
        
    };
    
    //Returns the (approximate) size of an NBT tag.
    public static int isTagBig(NBTBase tag, int bignessThreshold) {
        if (tag == null) {
            return 0;
        }
        if (tag instanceof NBTTagByte) {
            return 1;
        }
        if (tag instanceof NBTTagByteArray) {
            return 4 + 1*((NBTTagByteArray) tag).byteArray.length;
        }
        if (tag instanceof NBTTagDouble) {
            return 8;
        }
        if (tag instanceof NBTTagCompound || tag instanceof NBTTagList) {
            int sum_size = 1;
            Iterable<NBTBase> collection;
            if (tag instanceof NBTTagCompound) {
                NBTTagCompound tc = (NBTTagCompound) tag;
                collection = tc.getTags();
            } else {
                NBTTagList tl = (NBTTagList) tag;
                collection = tl.tagList;
            }
            for (NBTBase sub : collection) {
                sum_size += 1 + isTagBig(sub, bignessThreshold - sum_size);
                String s = sub.getName();
                sum_size += s == null ? 0 : s.length();
                if (sum_size > bignessThreshold) {
                    return sum_size;
                }
            }
            return sum_size;
        }
        if (tag instanceof NBTTagFloat) {
            return 4;
        }
        if (tag instanceof NBTTagInt) {
            return 4;
        }
        if (tag instanceof NBTTagIntArray) {
            return 4 + 4*((NBTTagIntArray) tag).intArray.length;
        }
        if (tag instanceof NBTTagLong) {
            return 4;
        }
        return 1;
    }
    
    static public ItemStack readStack(DataInput input) throws IOException {
        ItemStack is = ItemStack.loadItemStackFromNBT((NBTTagCompound) NBTBase.readNamedTag(input));
        if (is == null || is.itemID == 0) {
            return null;
        }
        return is;
    }

    @SideOnly(Side.CLIENT)
    public static void rotateForDirection(ForgeDirection dir) {
        switch (dir) {
        case WEST:
            break;
        case EAST:
            GL11.glRotatef(180, 0, 1, 0);
            break;
        case NORTH:
            GL11.glRotatef(-90, 0, 1, 0);
            break;
        case SOUTH:
            GL11.glRotatef(90, 0, 1, 0);
            break;
        case UP:
            GL11.glRotatef(-90, 0, 0, 1);
            break;
        case DOWN:
            GL11.glRotatef(90, 0, 0, 1);
            break;
        case UNKNOWN: break;
        }
    }
    
    public static NBTTagCompound item2tag(ItemStack is) {
        NBTTagCompound tag = new NBTTagCompound();
        is.writeToNBT(tag);
        return tag;
    }
    
    public static void collapseItemList(List<ItemStack> total) {
        int i = 0;
        while (i < total.size()) {
            ItemStack is = normalize(total.get(i));
            if (is == null) {
                total.remove(i);
                continue;
            }
            int s = i + 1;
            while (s < total.size()) {
                ItemStack other = normalize(total.get(s));
                if (other == null) {
                    total.remove(s);
                    continue;
                }
                if (FzUtil.couldMerge(is, other)) {
                    int free = is.getMaxStackSize() - is.stackSize;
                    if (free <= 0) {
                        break;
                    }
                    int delta = Math.min(free, other.stackSize);
                    is.stackSize += delta;
                    other.stackSize -= delta;
                    if (other.stackSize <= 0) {
                        total.remove(s);
                        continue;
                    }
                }
                s++;
            }
            i++;
        }
    }
    
    public static boolean significantChange(float a, float b) {
        if (a == b) {
            return false;
        }
        if (a == 0 || b == 0) {
            a = Math.abs(a);
            b = Math.abs(b);
        }
        float thresh = Math.abs(a - b)/Math.max(a, b);
        return thresh > 0.05;
    }
    
    /**
     * 
     * @param oldValue
     * @param newValue
     * @param partial
     * @return the linear interpolation of the two values
     */
    public static float interp(float oldValue, float newValue, float partial) {
        return oldValue*(1 - partial) + newValue*partial;
    }
    
    public static int getAxis(ForgeDirection fd) {
        if (fd.offsetX != 0) {
            return 1;
        }
        if (fd.offsetY != 0) {
            return 2;
        }
        if (fd.offsetZ != 0) {
            return 3;
        }
        return 0;
    }
    
    @SideOnly(Side.CLIENT)
    public static boolean checkGLError(String op) {
        int errSym = glGetError();
        if (errSym != 0) {
            Core.logSevere("GL Error @ " + op);
            Core.logSevere(errSym + ": " + GLU.gluErrorString(errSym));
            return true;
        }
        return false;
    }
    
    public static int getWorldDimension(World world) {
        return world.provider.dimensionId;
    }
    
    public static FluidStack drainSpecificBlockFluid(World worldObj, int x, int y, int z, boolean doDrain, Fluid targetFluid) {
        Block b = Block.blocksList[worldObj.getBlockId(x, y, z)];
        if (!(b instanceof IFluidBlock)) {
            Fluid vanilla;
            if (b == Block.waterStill || b == Block.waterMoving) {
                vanilla = FluidRegistry.WATER;
            } else if (b == Block.lavaStill || b == Block.lavaMoving) {
                vanilla = FluidRegistry.LAVA;
            } else {
                return null;
            }
            if (worldObj.getBlockMetadata(x, y, z) != 0) {
                return null;
            }
            if (doDrain) {
                worldObj.setBlockToAir(x, y, z);
            }
            return new FluidStack(vanilla, FluidContainerRegistry.BUCKET_VOLUME);
        }
        IFluidBlock block = (IFluidBlock) b;
        if (!block.canDrain(worldObj, x, y, z)) return null;
        FluidStack fs = block.drain(worldObj, x, y, z, false);
        if (fs == null) return null;
        if (fs.getFluid() != targetFluid) return null;
        if (doDrain) {
            fs = block.drain(worldObj, x, y, z, true);
        }
        if (fs == null || fs.amount <= 0) return null;
        return fs;
    }
    
    public static TileEntity cloneTileEntity(TileEntity orig) {
        NBTTagCompound tag = new NBTTagCompound();
        orig.writeToNBT(tag);
        return TileEntity.createAndLoadEntity(tag);
    }
    
    @SideOnly(Side.CLIENT)
    public static void copyStringToClipboard(String text) {
        StringSelection stringselection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringselection, (ClipboardOwner)null);
    }
    
    public static <E> ArrayList<E> copyWithoutNull(Collection<E> orig) {
        ArrayList<E> ret = new ArrayList();
        if (orig == null) return ret;
        for (E e : orig) {
            if (e != null) ret.add(e);
        }
        return ret;
    }
}
