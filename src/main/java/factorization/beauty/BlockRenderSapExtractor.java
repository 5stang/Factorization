package factorization.beauty;

import factorization.api.FzOrientation;
import factorization.api.Quaternion;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.shared.BlockRenderHelper;
import factorization.shared.FactorizationBlockRender;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import org.lwjgl.opengl.GL11;

public class BlockRenderSapExtractor extends FactorizationBlockRender {

    @Override
    public boolean render(RenderBlocks rb) {
        if (world_mode) {
            doRender(rb, 0);
            return true;
        }
        if (!world_mode) {
            doRender(rb, 0);
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            doRender(rb, 1);
            GL11.glPopAttrib();
        }
        return false;
    }

    @Override
    public boolean renderSecondPass(RenderBlocks rb) {
        //NOTE: We can almost get away with doing this in the first render pass.
        //But GL_BLEND is not consistently enabled.
        doRender(rb, 1);
        //We can also almost get away with enabling GL_BLEND in this ISBRH.
        //But then my conscience attacks.
        return true;
    }

    public boolean doRender(RenderBlocks rb, int pass) {
        Block log = Blocks.log2;
        int md = 1;
        if (world_mode) {
            Block above = w.getBlock(x, y + 1, z);
            int mdAbove = w.getBlockMetadata(x, y + 1, z);
            if (above.getMaterial() == Material.wood && above instanceof BlockLog) {
                log = above;
                md = mdAbove;
            }
        }
        boolean any = false;
        BlockRenderHelper block = BlockRenderHelper.instance;
        block.setBlockBoundsOffset(0, 0, 0);
        if (pass == 0) {
            if (world_mode) {
                block.useTextures(
                        log.getIcon(0, md),
                        log.getIcon(1, md),
                        log.getIcon(2, md),
                        log.getIcon(3, md),
                        log.getIcon(4, md),
                        log.getIcon(5, md)
                );
                block.beginWithMirroredUVs();
                int rotation = md & 12;
                if (rotation == 4) {
                    block.rotateCenter(Quaternion.fromOrientation(FzOrientation.FACE_NORTH_POINT_DOWN));
                } else if (rotation == 8) {
                    block.rotateCenter(Quaternion.fromOrientation(FzOrientation.FACE_WEST_POINT_NORTH));
                } else {
                    block.rotateCenter(new Quaternion());
                }
                block.renderRotated(Tessellator.instance, x, y, z);
            } else {
                invLog(rb, log, md);
                any = true;
            }
        }
        if (pass == 1) {
            block.useTextures(BlockIcons.beauty$saptap_top, BlockIcons.beauty$saptap_top,
                    BlockIcons.beauty$saptap, BlockIcons.beauty$saptap,
                    BlockIcons.beauty$saptap, BlockIcons.beauty$saptap);
            float d = -1.0F / 1024F;
            block.setBlockBoundsOffset(d, d, d);
            if (world_mode) {
                any |= block.render(rb, x, y, z);
                block.setBlockBoundsOffset(0, 0, 0);
            } else {
                block.renderForInventory(rb);
                block.setBlockBoundsOffset(0, 0, 0);
                any = true;
            }
        }
        return any;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SAP_TAP;
    }

    private void invLog(RenderBlocks rb, Block block, int md) {
        BlockRenderHelper b = BlockRenderHelper.instance;
        b.setBlockBoundsOffset(0, 0, 0);
        b.useTextures(
                block.getBlockTextureFromSide(0),
                block.getBlockTextureFromSide(1),
                block.getBlockTextureFromSide(2),
                block.getBlockTextureFromSide(3),
                block.getBlockTextureFromSide(4),
                block.getBlockTextureFromSide(5));
        b.renderForInventory(rb);
    }

    private boolean notchLog(RenderBlocks rb, Block block, int md) {
        int rotation = md & 12;

        if (rotation == 4)
        {
            rb.uvRotateEast = 1;
            rb.uvRotateWest = 1;
            rb.uvRotateTop = 1;
            rb.uvRotateBottom = 1;
        }
        else if (rotation == 8)
        {
            rb.uvRotateSouth = 1;
            rb.uvRotateNorth = 1;
        }

        boolean flag = rb.renderStandardBlock(block, x, y, z);
        rb.uvRotateSouth = 0;
        rb.uvRotateEast = 0;
        rb.uvRotateWest = 0;
        rb.uvRotateNorth = 0;
        rb.uvRotateTop = 0;
        rb.uvRotateBottom = 0;
        return flag;
    }
}
