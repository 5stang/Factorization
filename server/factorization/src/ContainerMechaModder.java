package factorization.src;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.src.Container;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IInventory;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Slot;

public class ContainerMechaModder extends Container {
	Coord benchLocation;
	InventoryPlayer inv;
	EntityPlayer player;
	public InventoryUpgrader upgrader;
	SlotMechaArmor armorSlot;

	public class InventoryUpgrader implements IInventory {
		public ItemStack armor;
		public ItemStack[] upgrades = new ItemStack[9];

		ItemStack lastArmor;

		@Override
		public int getSizeInventory() {
			return 9;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			if (slot == 100) {
				return armor;
			}
			return upgrades[slot - 100];
		}

		@Override
		public ItemStack decrStackSize(int slot, int i) {
			ItemStack is = getStackInSlot(slot);
			if (is == null) {
				return null;
			}
			ItemStack ret = is.copy();
			i = Math.min(i, is.stackSize);
			ret.stackSize = i;
			is.stackSize -= i;
			if (is.stackSize <= 0) {
				is = null;
			}
			if (slot == 100) {
				armor = is;
			}
			else {
				upgrades[slot - 100] = is;
			}
			return ret;
		}

		@Override
		public ItemStack getStackInSlotOnClosing(int slot) {
			//don't shove stuff in, I think???
			//Not used...?
			return getStackInSlot(slot);
		}

		@Override
		public void setInventorySlotContents(int slot, ItemStack is) {
			if (slot == 100) {
				armor = is;
			}
			else {
				upgrades[slot - 100] = is;
			}
		}

		@Override
		public String getInvName() {
			return "Mecha-Modder";
		}

		@Override
		public int getInventoryStackLimit() {
			return 1;
		}

		@Override
		public boolean isUseableByPlayer(EntityPlayer var1) {
			return true;
		}

		public void openChest() {
		}

		public void closeChest() {
		}

		@Override
		public void onInventoryChanged() {
		}

	}

	class SlotMechaArmor extends Slot {
		ArrayList<Slot> upgradeSlots;

		public SlotMechaArmor(IInventory par1iInventory, int par2, int par3, int par4,
				ArrayList<Slot> upgradeSlots) {
			super(par1iInventory, par2, par3, par4);
			this.upgradeSlots = upgradeSlots;
		}

		@Override
		public ItemStack decrStackSize(int par1) {
			//stuff that armor full
			ItemStack is = super.decrStackSize(par1);
			if (is == null) {
				return null;
			}
			if (!(is.getItem() instanceof MechaArmor)) {
				return is;
			}
			MechaArmor armor = (MechaArmor) is.getItem();
			for (int slot = 0; slot < armor.slotCount; slot++) {
				Slot upgradeSlot = upgradeSlots.get(slot);
				ItemStack upgrade = armor.getStackInSlot(is, slot);
				if (upgrade != null) {
					continue;
				}
				ItemStack up = upgradeSlot.getStack();
				if (armor.isValidUpgrade(up)) {
					armor.setStackInSlot(is, slot, upgradeSlot.decrStackSize(1));
				}
			}
			return is;
		}

		@Override
		public void putStack(ItemStack is) {
			//dump items out of the armor
			super.putStack(is);
			unpackArmor();
		}

		void unpackArmor() {
			ItemStack is = getStack();
			if (is == null) {
				return;
			}
			if (!(is.getItem() instanceof MechaArmor)) {
				return;
			}
			MechaArmor armor = (MechaArmor) is.getItem();
			for (int slot = 0; slot < armor.slotCount; slot++) {
				Slot upgradeSlot = upgradeSlots.get(slot);
				ItemStack upgrade = armor.getStackInSlot(is, slot);
				if (upgrade == null || upgradeSlot.getHasStack()) {
					continue;
				}
				upgradeSlot.putStack(upgrade);
				armor.setStackInSlot(is, slot, null);
			}
		}

	}

	public ContainerMechaModder(EntityPlayer player, Coord benchLocation) {
		this.benchLocation = benchLocation;
		this.inv = player.inventory;
		this.player = player;
		this.upgrader = new InventoryUpgrader();

		//player inventory
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 116 + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inv, col, 8 + col * 18, 174));
		}

		//slots for upgrades
		ArrayList<Slot> upgrades = new ArrayList(8);
		for (int col = 0; col < 8; col++) {
			Slot u = new Slot(upgrader, 101 + col, 27 + col * 18, 7);
			this.addSlot(u);
			upgrades.add(u);
		}
		//slot for the armor
		armorSlot = new SlotMechaArmor(upgrader, 100, 7, 7, upgrades);
		this.addSlot(armorSlot);
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		if (benchLocation.getId() == Core.resource_id && ResourceType.MECHAMODDER.is(benchLocation.getMd())) {
			Coord p = new Coord(player);
			return benchLocation.distance(p) <= 8;
		}
		return false;
	}

	@Override
	public void onCraftGuiClosed(EntityPlayer player) {
		super.onCraftGuiClosed(player);
		if (armorSlot.getHasStack()) {
			ItemStack is = armorSlot.decrStackSize(armorSlot.getStack().stackSize);
			player.dropPlayerItem(is);
		}
		for (Slot slot : armorSlot.upgradeSlots) {
			if (slot.getHasStack()) {
				ItemStack is = slot.decrStackSize(slot.getStack().stackSize);
				player.dropPlayerItem(is);
			}
		}
	}

	@Override
	public ItemStack transferStackInSlot(int slot) {
		try {
			ArrayList<Integer> invArea = new ArrayList();
			for (int i = 0; i < 9 * 4; i++) {
				invArea.add(i);
			}
			ArrayList<Integer> upgradeArea = new ArrayList();
			for (int i = 100; i < 109; i++) {
				upgradeArea.add(i);
			}
			List<Integer> shorty = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);

			if (slot == 44) {
				if (!armorSlot.getHasStack()) {
					return null;
				}
				//stuff the armor, then sneak it back in
				ItemStack is = armorSlot.decrStackSize(armorSlot.getStack().stackSize);
				armorSlot.inventory.setInventorySlotContents(100, is);

				return FactorizationUtil.transferStackToArea(upgrader, 100, inv, invArea);
			}
			else if (slot >= 9 * 4) {
				return FactorizationUtil.transferStackToArea(upgrader, slot - 9 * 4 + 101, inv, invArea);
			}
			slot += 9;
			if (slot >= 9 * 4) {
				slot -= 9 * 4;
			}
			return FactorizationUtil.transferStackToArea(inv, slot, upgrader, upgradeArea);

		} finally {
			armorSlot.unpackArmor();
		}
	}

}
