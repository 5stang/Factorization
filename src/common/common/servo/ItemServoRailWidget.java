package factorization.common.servo;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.common.BlockIcons;
import factorization.common.Core;
import factorization.common.FactorizationUtil;
import factorization.common.Core.TabType;

public class ItemServoRailWidget extends Item {
    public ItemServoRailWidget(int itemId) {
        super(itemId);
        setUnlocalizedName("factorization:servo/component");
        Core.tab(this, TabType.SERVOS);
        setMaxStackSize(1);
    }
    
    public String getUnlocalizedName(ItemStack is) {
        ServoComponent sc = get(is);
        if (sc == null) {
            return super.getUnlocalizedName();
        }
        return super.getUnlocalizedName(is) + "." + sc.getName();
    }
    
    public String getItemDisplayName(ItemStack is) {
        String s = super.getItemDisplayName(is);
        if (s == null || s.length() == 0) {
            s = getUnlocalizedName(is);
            //System.out.println(s); //NORELEASE
        }
        return s;
    };
    
    ServoComponent get(ItemStack is) {
        if (!is.hasTagCompound()) {
            return null;
        }
        return ServoComponent.load(is.getTagCompound());
    }
    
    void update(ItemStack is, ServoComponent sc) {
        if (sc != null) {
            sc.save(FactorizationUtil.getTag(is));
        } else {
            is.setTagCompound(null);
        }
    }
    
    @Override
    public ItemStack onItemRightClick(ItemStack is, World world, EntityPlayer player) {
        ServoComponent sc = get(is);
        if (sc == null) {
            return is;
        }
        return is;
    }
    
    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float vx, float vy, float vz) {
        ServoComponent sc = get(is);
        if (sc == null) {
            return false;
        }
        if (sc instanceof Decorator) {
            Decorator dec = (Decorator) sc;
            Coord here = new Coord(world, x, y, z);
            TileEntityServoRail rail = here.getTE(TileEntityServoRail.class);
            if (rail != null && rail.decoration == null) {
                rail.setDecoration(dec);
            }
            if (world.isRemote){
                here.redraw();
            } else {
                here.markBlockForUpdate();
            }
            if (!dec.isFreeToPlace() && !player.capabilities.isCreativeMode) {
                is.stackSize--;
            }
        }
        return super.onItemUse(is, player, world, x, y, z, side, vx, vy, vz);
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack is, EntityPlayer player, List list, boolean verbose) {
        super.addInformation(is, player, list, verbose);
        Core.brand(is, list);
        ServoComponent sc = get(is);
        if (sc != null) {
            sc.addInformation(list);
        }
    }
    
    
    private List<ItemStack> subItemsCache = null;
    
    void loadSubItems() {
        if (subItemsCache != null) {
            return;
        }
        subItemsCache = new ArrayList<ItemStack>(100);
        ArrayList<Object> temp = new ArrayList();
        for (Class<? extends ServoComponent> scClass : ServoComponent.getComponents()) {
            try {
                ServoComponent sc = scClass.newInstance();
                subItemsCache.add(sc.toItem());
            } catch (Throwable e) {
                e.printStackTrace();
                continue;
            }
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(int id, CreativeTabs tab, List list) {
        loadSubItems();
        list.addAll(subItemsCache);
    }
    
    public Icon getIcon(ItemStack stack, int renderPass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
        return getIcon(stack, renderPass);
    }
    
    @Override
    public Icon getIcon(ItemStack stack, int pass) {
        if (pass != 0) {
            return null;
        }
        ServoComponent sc = get(stack);
        Icon ret = null;
        if (sc instanceof Decorator) {
            ret = ((Decorator) sc).getIcon(ForgeDirection.UNKNOWN);
        }
        if (ret == null) {
            ret = BlockIcons.uv_test;
        }
        return ret;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }
    
    @Override
    public int getRenderPasses(int metadata) {
        return 1;
    }
}
