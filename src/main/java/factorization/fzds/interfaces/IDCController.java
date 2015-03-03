package factorization.fzds.interfaces;

import factorization.api.Coord;
import net.minecraft.entity.player.EntityPlayer;

public interface IDCController {
    boolean placeBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit);
    boolean breakBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit);
    boolean hitBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit);
    boolean useBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit);

    static final IDCController default_controller = new IDCController() {
        // Has to be a do-nothing, 'cause if it were a do-something then something might get overridden.
        @Override public boolean placeBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) { return false; }
        @Override public boolean breakBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) { return false; }
        @Override public boolean hitBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) { return false; }
        @Override public boolean useBlock(IDeltaChunk idc, EntityPlayer player, Coord at, byte sideHit) { return false; }
    };
}
