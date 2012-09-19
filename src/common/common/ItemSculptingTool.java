package factorization.common;

import java.util.List;

import org.lwjgl.opengl.SharedDrawable;

import net.minecraft.src.Block;
import net.minecraft.src.CreativeTabs;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import net.minecraftforge.common.ForgeDirection;
import factorization.api.Coord;
import factorization.api.VectorUV;
import factorization.common.NetworkFactorization.MessageType;
import factorization.common.TileEntityGreenware.ClayState;
import factorization.common.TileEntityGreenware.SelectionInfo;

public class ItemSculptingTool extends Item {

    protected ItemSculptingTool(int id) {
        super(id);
        setTextureFile(Core.texture_file_item);
        setIconIndex(6 + 16*4);
        setNoRepair();
        setMaxDamage(0);
        setMaxStackSize(1);
        setItemName("item.sculptTool");
        setTabToDisplayOn(CreativeTabs.tabDeco);
        setFull3D();
    }
    
    static void addModeChangeRecipes() {
        int length = ToolMode.values().length;
        ToolMode mode[] = ToolMode.values();
        for (int i = 0; i < length; i++) {
            int j = i + 1;
            if (j == length) {
                j = 0;
            }
            Core.registry.shapelessRecipe(fromMode(mode[j]), fromMode(mode[i]));
        }
    }

    enum ToolMode {
        SELECTOR("Select"), MOVER("Move"), STRETCHER("Stretch"), REMOVER("Remove"), ROTATOR("Rotate");
        
        String english;
        private ToolMode(String english) {
            this.english = english;
        }
    }
    
    ToolMode getMode(int damage) {
        if (damage < 0) {
            return ToolMode.SELECTOR;
        }
        if (damage >= ToolMode.values().length) {
            return ToolMode.SELECTOR;
        }
        return ToolMode.values()[damage];
    }
    
    static ItemStack fromMode(ToolMode mode) {
        return new ItemStack(Core.registry.sculpt_tool, 1, mode.ordinal());
    }
    
    @Override
    public int getIconFromDamage(int damage) {
        return iconIndex + getMode(damage).ordinal();
    }
    
    @Override
    public void addInformation(ItemStack is, List list) {
        list.add(getMode(is.getItemDamage()).english);
        Core.brand(list);
    }
    
    @Override
    public boolean tryPlaceIntoWorld(ItemStack is, EntityPlayer player,
            World w, int x, int y, int z, int side,
            float vx, float vy, float vz) {
        Coord here = new Coord(w, x, y, z);
        TileEntityGreenware gw = here.getTE(TileEntityGreenware.class);
        if (gw == null) {
            return false;
        }
        ClayState state = gw.getState();
        if (state != ClayState.WET) {
            if (w.isRemote) {
                return false;
            }
            switch (state) {
            case DRY:
                Core.notify(player, gw.getCoord(), "This clay is too dry. Add water with a bucket.");
                break;
            case BISQUED:
            case GLAZED:
                Core.notify(player, gw.getCoord(), "This clay has been fired and can not be reshaped.");
                break;
            default:
                Core.notify(player, gw.getCoord(), "This clay can not be reshaped.");
                break;
            }
            return false;
        }
        ToolMode mode = getMode(is.getItemDamage());
        if (w.isRemote) {
            if (mode == ToolMode.SELECTOR) {
                if (gw.parts.size() == 0) {
                    return true;
                }
                int id = gw.parts.indexOf(gw.selected);
                if (player.isSneaking()) {
                    id--;
                    if (id <= -1) {
                        id = gw.parts.size() - 1;
                    }
                } else {
                    id++;
                    if (id == gw.parts.size()) {
                        id = 0;
                    }
                }
                gw.selected = gw.parts.get(id);
                gw.broadcastMessage(null, MessageType.SculptSelect, id);
            }
            return true;
        }
        if (mode == ToolMode.SELECTOR) {
            return true;
        }
        SelectionInfo sel = TileEntityGreenware.selections.get(player.username);
        if (sel == null) {
            return false;
        }
        if (sel.gw != gw) {
            return false;
        }
        if (sel.id < 0 || sel.id >= gw.parts.size()) {
            return false;
        }
        RenderingCube selection = gw.parts.get(sel.id);
        RenderingCube test;
        switch (mode) {
        case MOVER:
            test = selection.copy();
            move(test, player.isSneaking(), side);
            if (TileEntityGreenware.isValidLump(test)) {
                move(selection, player.isSneaking(), side);
                gw.shareLump(sel.id, selection);
            }
            break;
        case STRETCHER:
            //move the nearest face of selected cube towards (of away from) the player
            test = selection.copy();
            stretch(test, player.isSneaking(), side);
            if (TileEntityGreenware.isValidLump(test)) {
                stretch(selection, player.isSneaking(), side);
                gw.shareLump(sel.id, selection);
            }
            break;
        case REMOVER:
            //delete selected
            gw.removeLump(sel.id);
            EntityItem drop;
            if (gw.parts.size() == 0) {
                here.setId(0);
                drop = new EntityItem(w, gw.xCoord, gw.yCoord, gw.zCoord, Core.registry.greenware_item.copy());
            } else {
                drop = new EntityItem(w, gw.xCoord, gw.yCoord, gw.zCoord, new ItemStack(Item.clay));
            }
            w.spawnEntityInWorld(drop);
            break;
        case ROTATOR:
            test = selection.copy();
            rotate(test, player.isSneaking(), side);
            if (TileEntityGreenware.isValidLump(test)) {
                rotate(selection, player.isSneaking(), side);
                gw.shareLump(sel.id, selection);
            }
            break;
        }
        
        return true;
    }
    
    void rotate(RenderingCube cube, boolean reverse, int side) {
        if (cube.theta == 0) {
            ForgeDirection direction = ForgeDirection.getOrientation(side);
            cube.axis = new VectorUV(direction.offsetX, direction.offsetY, direction.offsetZ);
        }
        float delta = -360F/32F;
        if (reverse) {
            delta *= -1;
        }
        cube.theta += delta;
        if (cube.theta >= 360) {
            cube.theta -= 360;
        }
        if (cube.theta < 0) {
            cube.theta += 360;
        }
        if (cube.theta == 0) {
            cube.axis = new VectorUV(0, 0, 0);
        }
    }
    
    void move(RenderingCube cube, boolean reverse, int side) {
        //shift origin 0.5, and corner by 0.5.
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        stretch(cube, reverse, dir.ordinal());
        stretch(cube, !reverse, dir.getOpposite().ordinal());
    }
    
    void stretch(RenderingCube cube, boolean reverse, int side) {
        //shift origin 0.5, and corner by 0.5.
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        float delta = reverse ? -0.5F : 0.5F;
        switch (dir) {
        case SOUTH:
            cube.origin.z += delta;
            cube.corner.z += delta;
            break;
        case NORTH:
            cube.origin.z -= delta;
            cube.corner.z += delta;
            break;
        case EAST:
            cube.origin.x += delta;
            cube.corner.x += delta;
            break;
        case WEST:
            cube.origin.x -= delta;
            cube.corner.x += delta;
            break;
        case UP:
            cube.origin.y += delta;
            cube.corner.y += delta;
            break;
        case DOWN:
            cube.origin.y -= delta;
            cube.corner.y += delta;
            break;
        }
    }
}
