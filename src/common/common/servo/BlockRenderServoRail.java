package factorization.common.servo;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import factorization.client.render.FactorizationBlockRender;
import factorization.common.BlockIcons;
import factorization.common.BlockRenderHelper;
import factorization.common.FactoryType;

public class BlockRenderServoRail extends FactorizationBlockRender {

    Icon[] central = new Icon[6];
    boolean[] sides = new boolean[6];
    BlockRenderHelper block;
    
    void removeTextures(int i, int j) {
        if (!world_mode) {
            return;
        }
        if (sides[i]) {
            block.setTexture(i, null);
        }
        if (sides[j]) {
            block.setTexture(j, null);
        }
    }
    
    void restoreTextures(int a, int b) {
        Icon icon = BlockIcons.dark_iron_block;
        block.setTexture(a, icon);
        block.setTexture(b, icon);
    }
    
    @Override
    protected void render(RenderBlocks rb) {
        Icon icon = BlockIcons.dark_iron_block;
        block = BlockRenderHelper.instance;
        block.useTexture(icon);
        float f = TileEntityServoRail.width;
        boolean any = false;
        if (world_mode) {
            TileEntity te = w.getBlockTileEntity(x, y, z);
            if (te instanceof TileEntityServoRail) {
                TileEntityServoRail rail = (TileEntityServoRail) te;
                rail.fillSideInfo(sides);
            } else {
                return;
            }
        } else {
            any = true;
            for (int i = 0; i < 6; i++) {
                sides[i] = true;
            }
        }
        for (int i = 0; i < central.length; i++) {
            central[i] = icon;
        }
        int count = 0;
        if (sides[0] || sides[1]) {
            //DOWN, UP
            central[2] = central[3] = central[4] = central[5] = null;
            count++;
            float low = sides[0] ? 0 : f;
            float high = sides[1] ? 1 : 1 - f;
            block.setBlockBounds(f, low, f, 1 - f, high, 1 - f);
            removeTextures(0, 1);
            renderBlock(rb, block);
            restoreTextures(0, 1);
        }
        if (sides[2] || sides[3]) {
            //NORTH, SOUTH
            central[0] = central[1] = central[4] = central[5] = null;
            count++;
            float low = sides[2] ? 0 : f;
            float high = sides[3] ? 1 : 1 - f;
            block.setBlockBounds(f, f, low, 1 - f, 1 - f, high);
            removeTextures(2, 3);
            renderBlock(rb, block);
            restoreTextures(2, 3);
        }
        if (sides[4] || sides[5]) {
            //WEST, EAST
            central[0] = central[1] = central[2] = central[3] = null;
            count++;
            float low = sides[4] ? 0 : f;
            float high = sides[5] ? 1 : 1 - f;
            block.setBlockBounds(low, f, f, high, 1 - f, 1 - f);
            removeTextures(4, 5);
            renderBlock(rb, block);
            restoreTextures(4, 5);
        }
        if (count != 3) {
            block.useTextures(central);
            block.setBlockBounds(f, f, f, 1 - f, 1 - f, 1 - f);
            renderBlock(rb, block);
        }
    }

    @Override
    protected FactoryType getFactoryType() {
        return FactoryType.SERVORAIL;
    }
}
