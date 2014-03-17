package factorization.shared;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import factorization.wrath.BlockLightAir;

public class FactorizationRender implements ISimpleBlockRenderingHandler {
    public FactorizationRender() {
        Core.factory_rendertype = RenderingRegistry.getNextAvailableRenderId();
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelID,
            RenderBlocks renderer) {
        if (block == Core.registry.factory_block) {
            FactorizationBlockRender FBR = FactorizationBlockRender.getRenderer(metadata);
            FBR.renderInInventory();
            FBR.setMetadata(metadata);
            FBR.render(renderer);
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
            Block block, int modelId, RenderBlocks renderBlocks) {
        Core.profileStart("fz");
        try {
            int md = world.getBlockMetadata(x, y, z);
            int renderPass = BlockFactorization.CURRENT_PASS; //MinecraftForgeClient.getRenderPass(); //Bluh
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityCommon) {
                TileEntityCommon tec = (TileEntityCommon) te;
                int fmd = tec.getFactoryType().md;
                FactorizationBlockRender FBR = FactorizationBlockRender.getRenderer(fmd);
                FBR.renderInWorld(world, x, y, z, fmd, tec);
                if (renderPass == 0) {
                    return FBR.render(renderBlocks);
                } else if (renderPass == 1) {
                    return FBR.renderSecondPass(renderBlocks);
                }
                return false;
            }
            if (block == Core.registry.lightair_block) {
                if (md == BlockLightAir.air_md) {
                    return false;
                }
                return true;
            }
            return false;
        } finally {
            Core.profileEnd();
        }
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return Core.factory_rendertype;
    }
}
