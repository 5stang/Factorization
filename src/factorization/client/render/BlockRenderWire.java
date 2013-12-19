package factorization.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import factorization.common.TileEntityWire;
import factorization.common.WireConnections;
import factorization.common.WireRenderingCube;
import factorization.shared.Core;
import factorization.shared.FactorizationBlockRender;
import factorization.shared.FactoryType;

public class BlockRenderWire extends FactorizationBlockRender {
    @Override
    public void render(RenderBlocks rb) {
        if (world_mode) {
            Tessellator.instance.setBrightness(Core.registry.factory_block.getMixedBrightnessForBlock(w, x, y, z));
            if (te == null) {
                return;
            }
            for (WireRenderingCube rc : new WireConnections((TileEntityWire) te).getParts()) {
                renderCube(rc);
            }
        } else {
            for (WireRenderingCube rc : WireConnections.getInventoryParts()) {
                renderCube(rc);
            }
        }
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.LEADWIRE;
    }
}
