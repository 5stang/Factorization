package factorization.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import factorization.common.BlockIcons;
import factorization.common.Core;
import factorization.common.FactoryType;

public class BlockRenderCrystallizer extends FactorizationBlockRender {

    @Override
    protected
    void render(RenderBlocks rb) {
        Core.profileStart("crystallizer");
        int metal = 14, wood = 8 + 16, hollow = 10 + 16;
        float width = 2F / 16F;
        float mheight = 1 - 0;
        renderCauldron(rb, BlockIcons.cauldron_top, BlockIcons.cauldron_side);

        float start = 7F / 16F;
        float sheight = 1 - width;
        renderPart(rb, BlockIcons.wood, width, sheight, start, 1 - width, 1, start + width);
        Core.profileEnd();
    }

    @Override
    protected
    FactoryType getFactoryType() {
        return FactoryType.CRYSTALLIZER;
    }

}
