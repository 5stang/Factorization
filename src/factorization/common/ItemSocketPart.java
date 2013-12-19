package factorization.common;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.shared.FactoryType;
import factorization.shared.ItemFactorization;
import factorization.shared.Core.TabType;
import factorization.sockets.SocketEmpty;

public class ItemSocketPart extends ItemFactorization {

    public ItemSocketPart(int itemId, String name, TabType tabType) {
        super(itemId, name, tabType);
        setHasSubtypes(true);
        setMaxDamage(0);
    }
    
    
    static ArrayList<FactoryType> loadSockets() {
        ArrayList<FactoryType> ret = new ArrayList();
        for (FactoryType ft : FactoryType.values()) {
            if (ft == FactoryType.SOCKET_EMPTY) {
                continue;
            }
            Class theClass = ft.getFactoryTypeClass();
            while (theClass != null) {
                theClass = theClass.getSuperclass();
                if (theClass == TileEntitySocketBase.class) {
                    ret.add(ft);
                    break;
                }
            }
        }
        return ret;
    }
    
    FactoryType[] socketTypes = null;
    FactoryType[] getSockets() {
        if (socketTypes == null) {
            ArrayList<FactoryType> aft = loadSockets();
            socketTypes = new FactoryType[aft.size()];
            for (int i = 0; i < socketTypes.length; i++) {
                socketTypes[i] = aft.get(i);
            }
        }
        return socketTypes;
    }
    
    @SideOnly(Side.CLIENT)
    Icon[] socketIcons;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IconRegister register) {
        socketIcons = new Icon[FactoryType.MAX_ID];
        ItemStack me = new ItemStack(this);
        for (FactoryType ft : getSockets()) {
            me.setItemDamage(ft.md);
            socketIcons[ft.md] = register.registerIcon(getUnlocalizedName(me).replace("item.", ""));
        }
    }
    
    @Override
    public String getUnlocalizedName(ItemStack is) {
        int md = is.getItemDamage();
        String ret = getUnlocalizedName() + FactoryType.fromMd(md);
        return ret;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(int itemId, CreativeTabs tab, List list) {
        FactoryType[] ss = getSockets();
        for (int i = 0; i < ss.length; i++) {
            FactoryType ft = ss[i];
            list.add(ft.asSocketItem());
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public Icon getIconFromDamage(int md) {
        if (md > 0 && md < socketIcons.length) {
            return socketIcons[md];
        }
        return super.getIconFromDamage(md);
    }
    
    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player,
            World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {
        int md = is.getItemDamage();
        if (world.getBlockTileEntity(x, y, z) instanceof SocketEmpty) {
            is.stackSize--;
            SocketEmpty se = (SocketEmpty) world.getBlockTileEntity(x, y, z);
            if (md > 0 && md < FactoryType.MAX_ID) {
                try {
                    TileEntitySocketBase socket = (TileEntitySocketBase) FactoryType.fromMd(md).getFactoryTypeClass().newInstance();
                    world.setBlockTileEntity(x, y, z, socket);
                    socket.facing = se.facing;
                    world.markBlockForUpdate(x, y, z);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        return super.onItemUse(is, player, world, x, y, z, side, hitX, hitY, hitZ);
    }
    
    @Override
    protected void addExtraInformation(ItemStack is, EntityPlayer player, List list, boolean verbose) {
        list.add("Socket part");
    }
}
