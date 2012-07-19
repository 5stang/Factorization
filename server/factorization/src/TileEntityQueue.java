package factorization.src;

import java.util.LinkedList;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.mod_Factorization;
import net.minecraft.src.forge.ISidedInventory;

public class TileEntityQueue extends TileEntityFactorization implements
		ISidedInventory {
	static final int maxQueueSize = 9 * 3;

	// save these guys
	LinkedList<ItemStack> items = new LinkedList<ItemStack>();

	@Override
	public BlockClass getBlockClass() {
		return BlockClass.Machine;
	}

	@Override
	public int getSizeInventory() {
		return 2;
	}

	boolean full() {
		cleanQueue();
		return items.size() >= maxQueueSize;
	}

	boolean empty() {
		cleanQueue();
		return items.size() == 0;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		// top is the front of the queue; all other sides are the back.
		// 0 is the top, 1 is all other sides
		cleanQueue();
		if (empty()) {
			return null;
		}
		if (slot == 0) {
			return items.getFirst();
		} else {
			return null;
		}
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack item) {
		// NOTE: This could potentially allow the queue to overfill
		cleanQueue();
		if (slot == 0) {
			// put in front
			if (item == null) {
				items.removeFirst();
			} else {
				items.addFirst(item);
			}
		} else {
			items.addLast(item);
		}
	}

	@Override
	public String getInvName() {
		return "Queue";
	}

	@Override
	public int getStartInventorySide(int side) {
		if (side == 1) {
			// the y+ (top) side; where the queue pops items out
			return 0;
		}
		return 1;
	}

	@Override
	public int getSizeInventorySide(int side) {
		cleanQueue();
		if (side == 1) {
			// top; front
			if (items.size() == 0) {
				// nothing for you
				return 0;
			}
		} else {
			if (full()) {
				return 0;
			}
		}
		return 1;
	}

	@Override
	public FactoryType getFactoryType() {
		return FactoryType.QUEUE;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		cleanQueue();
		super.writeToNBT(tag);
		NBTTagList list = new NBTTagList();
		for (ItemStack item : items) {
			NBTTagCompound comp = new NBTTagCompound();
			item.writeToNBT(comp);
			list.appendTag(comp);
		}
		tag.setTag("items", list);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		NBTTagList list = tag.getTagList("items");
		for (int i = 0; i != list.tagCount(); i++) {
			NBTBase t = list.tagAt(i);
			ItemStack toadd = ItemStack
					.loadItemStackFromNBT((NBTTagCompound) t);
			items.addLast(toadd);
		}
		cleanQueue();
	}

	static boolean isEmpty(ItemStack is) {
		return is == null || is.stackSize == 0;
	}

	void cleanQueue() {
		while (items.size() > 0 && isEmpty(items.getFirst())) {
			items.removeFirst();
		}
		while (items.size() > 0 && isEmpty(items.getLast())) {
			items.removeLast();
		}
	}

	@Override
	public void activate(EntityPlayer entityplayer) {
		// we've been right-clicked! Insert!
		int handslot = entityplayer.inventory.currentItem;
		if (handslot < 0 || handslot > 8) {
			return;
		}

		ItemStack is = entityplayer.inventory.getStackInSlot(handslot);
		if (is == null) {
			if (empty()) {
				entityplayer.addChatMessage("The queue is empty");
				return;
			}
			ItemStack f = items.getFirst();
			if (items.size() > 1) {
				mod_Factorization.instance.broadcastTranslate(entityplayer,
						"The queue containts %s %s and %s other items", ""
								+ f.stackSize,
						mod_Factorization.instance.translateItemStack(f), ""
								+ (items.size() - 1));
			} else {
				mod_Factorization.instance.broadcastTranslate(entityplayer,
						"The queue containts %s %s", "" + f.stackSize,
						mod_Factorization.instance.translateItemStack(f));
			}
			return;
		}
		if (full()) {
			entityplayer.addChatMessage("The queue is full");
			return;
		}
		items.addLast(is);
		entityplayer.inventory.setInventorySlotContents(handslot, null);
	}

	@Override
	public void click(EntityPlayer entityplayer) {
		if (empty()) {
			entityplayer.addChatMessage("The queue is empty");
			return;
		}
		ItemStack is = items.removeFirst();
		ejectItem(is, false, entityplayer);
	}

	@Override
	public void dropContents() {
		for (ItemStack is : items) {
			ejectItem(is, false, null);
		}
	}

	@Override
	void doLogic() {
	}
}
