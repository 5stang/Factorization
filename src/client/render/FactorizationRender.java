package factorization.client.render;

import java.util.ArrayList;

import net.minecraft.src.Block;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;
import net.minecraft.src.TileEntity;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;

import factorization.api.Coord;
import factorization.api.IFactoryType;
import factorization.common.BlockFactorization;
import factorization.common.Core;
import factorization.common.FactoryType;
import factorization.common.RenderingCube;
import factorization.common.RenderingCube.Vector;
import factorization.common.Texture;
import factorization.common.TileEntityBattery;
import factorization.common.TileEntityCommon;
import factorization.common.TileEntitySolarTurbine;
import factorization.common.TileEntityWire;
import factorization.common.WireConnections;

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
        int md = world.getBlockMetadata(x, y, z);
        int renderPass = MinecraftForgeClient.getRenderPass();
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof TileEntityCommon) {
            TileEntityCommon tec = (TileEntityCommon) te;
            int fmd = tec.getFactoryType().md;
            FactorizationBlockRender FBR = FactorizationBlockRender.getRenderer(fmd);
            FBR.renderInWorld(world, x, y, z);
            FBR.setMetadata(fmd);
            if (renderPass == 0) {
                FBR.render(renderBlocks);
            } else if (renderPass == 1) {
                FBR.renderSecondPass(renderBlocks);
            }
            return true;
        }
        if (block == Core.registry.lightair_block) {
            if (md == Core.registry.lightair_block.air_md) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldRender3DInInventory() {
        return true;
    }

    @Override
    public int getRenderId() {
        return Core.factory_rendertype;
    }
}
