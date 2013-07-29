package factorization.client.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Icon;
import net.minecraftforge.common.ForgeDirection;
import factorization.api.Quaternion;
import factorization.common.BlockIcons;
import factorization.common.BlockRenderHelper;
import factorization.common.Core;
import factorization.common.FactoryType;
import factorization.common.TileEntityDayBarrel;

public class BlockRenderDayBarrel extends FactorizationBlockRender {

    @Override
    protected void render(RenderBlocks rb) {
        if (world_mode) {
            doRender(rb, 0);
        }
        if (!world_mode) {
            doRender(rb, 0);
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            doRender(rb, 1);
            GL11.glPopAttrib();
        }
    }
    
    @Override
    void renderSecondPass(RenderBlocks rb) {
        doRender(rb, 1);
    }
    
    void doRender(RenderBlocks rb, int pass) {
        BlockRenderHelper block = Core.registry.blockRender;
        Icon plank, log;
        TileEntityDayBarrel barrel;
        if (world_mode) {
            barrel = getCoord().getTE(TileEntityDayBarrel.class);
        } else {
            barrel = (TileEntityDayBarrel) FactoryType.DAYBARREL.getRepresentative();
            barrel.loadFromStack(is);
        }
        if (pass == 0) {
            for (int i = 0; i < 6; i++) {
                block.setTexture(i, barrel.getIcon(ForgeDirection.getOrientation(i)));
            }
        } else {
            block.useTexture(BlockIcons.daybarrel_side);
            block.setTexture(0, BlockIcons.daybarrel_top);
            block.setTexture(1, BlockIcons.daybarrel_top);
            block.setTexture(4, BlockIcons.daybarrel_front);
        }
        float blockOffset = pass == 0 ? 0 : -1F/512F;
        block.setBlockBoundsOffset(blockOffset, blockOffset, blockOffset);
        if (world_mode) {
            Tessellator.instance.setBrightness(block.getMixedBrightnessForBlock(w, x, y, z));
            Quaternion q = Quaternion.fromOrientation(barrel.orientation.getSwapped());
            block.begin();
            block.rotateMiddle(q);
            block.renderRotated(Tessellator.instance, x, y, z);
        } else {
            block.renderForInventory(rb);
            final float d = 1F/64F;
            block.setBlockBoundsOffset(d, 0, d);
        }
    }
    
    
    @Override
    protected FactoryType getFactoryType() {
        return FactoryType.DAYBARREL;
    }

}
