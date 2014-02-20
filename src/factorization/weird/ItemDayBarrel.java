package factorization.weird;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.ItemBlockProxy;
import factorization.weird.TileEntityDayBarrel.Type;

public class ItemDayBarrel extends ItemBlockProxy {

    public ItemDayBarrel(String name) {
        super(Core.registry.daybarrel_item_hidden, name, TabType.BLOCKS);
        setMaxDamage(0);
        setNoRepair();
    }
    
    @Override
    public boolean getShareTag() {
        return true;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }
    
    @Override
    public String getItemStackDisplayName(ItemStack is) {
        Type upgrade = TileEntityDayBarrel.getUpgrade(is);
        String lookup = "factorization.factoryBlocks.DAYBARREL.format";
        if (upgrade != Type.NORMAL) {
            lookup = "factorization.factoryBlocks.DAYBARREL.format2";
        }
        String type = Core.translate("factorization.factoryBlocks.DAYBARREL." + upgrade);
        return Core.translateWithCorrectableFormat(lookup, type, TileEntityDayBarrel.getLog(is).getDisplayName());
    }
    
    @Override
    protected void addExtraInformation(ItemStack is, EntityPlayer player, List list, boolean verbose) {
        Type upgrade = TileEntityDayBarrel.getUpgrade(is);
        if (upgrade == Type.SILKY) {
            list.add(Core.translateThis("factorization.factoryBlocks.DAYBARREL.SILKY.silkhint"));
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister par1IIconRegister) { }
}
