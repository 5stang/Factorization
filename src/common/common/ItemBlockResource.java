package factorization.common;

import java.util.List;

import net.minecraft.src.ItemBlock;
import net.minecraft.src.ItemStack;

public class ItemBlockResource extends ItemBlock {
    public ItemBlockResource(int id) {
        super(id);
        //Y'know, that -256 is really retarded.
        setMaxDamage(0);
        setHasSubtypes(true);
    }

    public int getIconFromDamage(int damage) {
        return Core.registry.resource_block.getBlockTextureFromSideAndMetadata(0, damage);
    }

    public int getMetadata(int i) {
        return i;
    }

    @Override
    public String getItemNameIS(ItemStack itemstack) {
        // I don't think this actually gets called...
        int md = itemstack.getItemDamage();
        if (md < ResourceType.values().length && md >= 0) {
            ResourceType rs = ResourceType.values()[md];
            return getItemName() + "." + rs;
        } 
        return getItemName() + ".unknownMd" + md;
    }
    
    @Override
    public void addInformation(ItemStack is, List infoList) {
        super.addInformation(is, infoList);
        Core.brand(infoList);
    }
}
