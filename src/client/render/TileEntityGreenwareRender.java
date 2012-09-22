package factorization.client.render;

import static org.lwjgl.opengl.GL11.*;
import factorization.common.Core;
import factorization.common.FactoryType;
import factorization.common.RenderingCube;
import factorization.common.TileEntityGreenware;

import net.minecraft.src.RenderBlocks;
import net.minecraft.src.Tessellator;
import net.minecraft.src.TileEntity;
import net.minecraft.src.TileEntitySpecialRenderer;


public class TileEntityGreenwareRender extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double viewx, double viewy, double viewz, float partial) {
        TileEntityGreenware gw = (TileEntityGreenware) te;
        if (!gw.canEdit()) {
            return;
        }
        Core.profileStartRender("ceramics");
        
        glPushMatrix();
        glTranslated(viewx, viewy, viewz);
        
        
        TileEntityGreenware greenware = (TileEntityGreenware) te;
        BlockRenderSculpture.instance.renderInInventory();
        BlockRenderSculpture.instance.setTileEntity(greenware);
        BlockRenderSculpture.instance.renderDynamic(greenware);
        glPopMatrix();
        Core.profileEndRender();
    }

}
