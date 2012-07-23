package factorization.src;

import net.minecraft.src.DamageSource;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EnumArmorMaterial;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.forge.ArmorProperties;
import net.minecraft.src.forge.ISpecialArmor;
import net.minecraft.src.forge.ITextureProvider;

public class MechaArmor extends ItemArmor
		implements ISpecialArmor, ITextureProvider {
	public int slotCount = 2;

	public enum ActivationMode {
		ALWAYS_ON, ALWAYS_OFF, HOLD_ON, SINGLEFIRE;

		int toInt() {
			for (int i = 0; i < values().length; i++) {
				if (values()[i] == this) {
					return i;
				}
			}
			return 0;
		}

		static ActivationMode fromInt(int i) {
			return values()[i];
		}

		public String describe(String action) {
			switch (this) {
			case ALWAYS_ON:
				return "Always on";
			case ALWAYS_OFF:
				return "Never on";
			case HOLD_ON:
				return "On while " + action;
			case SINGLEFIRE:
				return "Fires once if " + action;
			}
			return "Always off (?)";
		}
	}

	public static class MechaMode {
		public int key = 0;
		public ActivationMode mode = ActivationMode.HOLD_ON;

		void writeToNbt(int slot, NBTTagCompound tag) {
			tag.setInteger("modeKey" + slot, key);
			tag.setInteger("activationMode" + slot, mode.toInt());
		}

		static MechaMode loadFromNbt(int slot, NBTTagCompound tag) {
			MechaMode ret = new MechaMode();
			if (tag == null) {
				return ret;
			}
			ret.key = tag.getInteger("modeKey" + slot);
			ret.mode = ActivationMode.fromInt(tag.getInteger("activationMode" + slot));
			return ret;
		}

		boolean getState(EntityPlayer player) {
			if (mode == ActivationMode.ALWAYS_OFF) {
				return false;
			}
			if (mode == ActivationMode.ALWAYS_ON) {
				return true;
			}
			if (mode == ActivationMode.SINGLEFIRE) {
				return Core.instance.getPlayerKeyState(player, key) == Core.KeyState.KEYSTART;
			}
			if (mode == ActivationMode.HOLD_ON) {
				return Core.instance.getPlayerKeyState(player, key).isPressed();
			}
			//TOOD: Hmm, how are we going to do toggling?
			return false;
		}

		public boolean isConstant() {
			return mode == ActivationMode.ALWAYS_ON || mode == ActivationMode.ALWAYS_OFF;
		}

		public void nextActivationMode(int d) {
			int o = mode.toInt() + d;
			int end = ActivationMode.values().length - 1;
			if (o == -1) {
				mode = ActivationMode.values()[end];
				return;
			}
			if (o > end) {
				mode = ActivationMode.values()[0];
				return;
			}
			mode = ActivationMode.fromInt(o);
		}

		public void nextKey(int d) {
			key += d;
			if (key >= Registry.MechaKeyCount) {
				key = Core.ExtraKey_minimum;
			}
			if (key < Core.ExtraKey_minimum) {
				key = Registry.MechaKeyCount - 1;
			}
			if (mode == ActivationMode.SINGLEFIRE) {
				key = Math.max(0, key);
				//XXX TODO: Too lazy to keep track of states properly
			}
		}
	}

	public MechaArmor(int par1, int armorType) {
		super(par1, EnumArmorMaterial.CHAIN, 0, armorType);
		setMaxDamage(0); //never break!
		setItemName("item.mechaArmor" + armorType);
	}

	//mecha features
	MechaArmor setSlotCount(int count) {
		slotCount = count;
		return this;
	}

	public ItemStack getStackInSlot(ItemStack is, int slot) {
		if (slot < 0 || slot >= slotCount) {
			return null;
		}
		NBTTagCompound tag = is.getTagCompound();
		if (tag == null) {
			return null;
		}
		String index = "slot" + slot;
		if (!tag.hasKey(index)) {
			return null;
		}
		return ItemStack.loadItemStackFromNBT(tag.getCompoundTag(index));
	}

	public IMechaUpgrade getUpgradeInSlot(ItemStack is, int slot) {
		return getUpgrade(getStackInSlot(is, slot));
	}

	public IMechaUpgrade getUpgrade(ItemStack i) {
		if (i == null || !(i.getItem() instanceof IMechaUpgrade)) {
			return null;
		}
		if (i.stackSize == 0) {
			return null;
		}
		return (IMechaUpgrade) i.getItem();
	}

	public void setStackInSlot(ItemStack is, int slot, ItemStack stack) {
		if (slot < 0 || slot >= slotCount) {
			return;
		}
		if (is.getTagCompound() == null) {
			is.setTagCompound(new NBTTagCompound());
		}
		if (stack == null) {
			is.getTagCompound().setTag("slot" + slot, new NBTTagCompound());
			return;
		}
		NBTTagCompound itemTag = new NBTTagCompound();
		stack.writeToNBT(itemTag);
		is.getTagCompound().setCompoundTag("slot" + slot, itemTag);
	}

	public boolean isValidUpgrade(ItemStack is) {
		if (is == null) {
			return false;
		}
		if (is.getItem() instanceof IMechaUpgrade) {
			return true;
		}
		if (is.getItem().getClass() == ItemArmor.class) {
			if (((ItemArmor) is.getItem()).armorType == armorType) {
				return true;
			}
		}
		return false;
	}

	public void setSlotMechaMode(ItemStack is, int slot, MechaMode mode) {
		if (is.getTagCompound() == null) {
			is.setTagCompound(new NBTTagCompound());
		}
		mode.writeToNbt(slot, is.getTagCompound());
	}

	public MechaMode getSlotMechaMode(ItemStack is, int slot) {
		return MechaMode.loadFromNbt(slot, is.getTagCompound());
	}

	static void onTickPlayer(EntityPlayer player) {
		for (ItemStack armorStack : player.inventory.armorInventory) {
			if (armorStack == null) {
				continue;
			}
			if (armorStack.getItem() instanceof MechaArmor) {
				((MechaArmor) armorStack.getItem()).tickArmor(player, armorStack);
			}
		}
	}

	void tickArmor(EntityPlayer player, ItemStack armorStack) {
		for (int i = 0; i < slotCount; i++) {
			ItemStack is = getStackInSlot(armorStack, i);
			IMechaUpgrade up = getUpgrade(is);
			if (up != null) {
				boolean active = getSlotMechaMode(armorStack, i).getState(player);
				ItemStack ret = up.tickUpgrade(player, armorStack, is, active);
				getSlotMechaMode(armorStack, i).getState(player);
				if (ret == null) {
					continue;
				}
				if (ret.stackSize == 0) {
					setStackInSlot(armorStack, i, null);
				}
				setStackInSlot(armorStack, i, ret);
			}
		}
	}

	//vanilla armor feature
	@Override
	public int getItemEnchantability() {
		return -1;
	}

	public String getArmorTextureFile(ItemStack itemstack) {
		//presumably we'll have to change this depending on what type of armor we are
		//XXX NOTE: LexManos needs to put IArmorTextureProvider in common
		//For now, the client uses render.MechaArmorTextured
		if (armorType == 2) {
			return Core.texture_dir + "mecha_armor_2.png";
		}
		return Core.texture_dir + "mecha_armor_1.png";
	}

	// @Override -- XXX Waiting for MCP update
	public boolean getShareNBT() {
		return true;
	}

	// @Override -- XXX TODO SERVER get___?
	public boolean func_46003_i() {
		return getShareNBT();
	}

	// @Override -- XXX TODO CLIENT get___?
	public boolean func_46056_k() {
		return getShareNBT();
	}

	@Override
	public ArmorProperties getProperties(EntityLiving player, ItemStack armor, DamageSource source,
			double damage, int slot) {
		ArmorProperties prop = new ArmorProperties(0, 0, 0);
		MechaArmor ma = (MechaArmor) armor.getItem();
		boolean found_vanilla_armor = false;
		for (int i = 0; i < slotCount; i++) {
			ItemStack is = ma.getStackInSlot(armor, i);
			if (is == null) {
				continue;
			}
			if (is.getItem().getClass() == ItemArmor.class) {
				if (found_vanilla_armor) {
					continue;
				}
				found_vanilla_armor = true;
				ItemArmor ar = (ItemArmor) is.getItem();
				prop.AbsorbRatio += ar.damageReduceAmount / 25D;
				prop.AbsorbMax += ar.getMaxDamage() + 1 - is.getItemDamage();
			}
			IMechaUpgrade up = getUpgrade(is);
			if (up != null) {
				up.addArmorProperties(is, prop);
			}
		}
		return prop;
	}

	@Override
	public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
		int ret = 0;
		MechaArmor ma = (MechaArmor) armor.getItem();
		boolean found_vanilla_armor = false;
		for (int i = 0; i < slotCount; i++) {
			ItemStack is = ma.getStackInSlot(armor, i);
			if (is == null) {
				continue;
			}
			if (is.getItem().getClass() == ItemArmor.class) {
				if (found_vanilla_armor) {
					continue;
				}
				found_vanilla_armor = true;
				ret += ((ItemArmor) is.getItem()).damageReduceAmount;
				continue;
			}
			IMechaUpgrade up = getUpgrade(is);
			if (up != null) {
				ret += up.getArmorDisplay(is);
			}
		}
		return ret;
	}

	@Override
	public void damageArmor(EntityLiving entity, ItemStack armor, DamageSource source, int damage,
			int slot) {
		MechaArmor ma = (MechaArmor) armor.getItem();
		boolean found_vanilla_armor = false;
		for (int i = 0; i < slotCount; i++) {
			ItemStack is = ma.getStackInSlot(armor, i);
			if (is == null) {
				continue;
			}
			if (is.getItem().getClass() == ItemArmor.class) {
				if (found_vanilla_armor) {
					continue;
				}
				found_vanilla_armor = true;
				is.damageItem(damage, entity);
				if (is.stackSize <= 0) {
					is = null;
				}
				ma.setStackInSlot(armor, i, is);
				continue;
			}
			IMechaUpgrade up = getUpgrade(is);
			if (up != null) {
				if (up.damageArmor(entity, is, source, damage, slot)) {
					ma.setStackInSlot(armor, i, is);
				}
			}
		}
	}

	@Override
	public String getTextureFile() {
		return Core.texture_file_item;
	}

	//@Override seeerveerr
	public int getIconFromDamage(int par1) {
		return (4 + armorType) * 16;
	}
}
