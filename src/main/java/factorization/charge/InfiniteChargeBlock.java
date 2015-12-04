package factorization.charge;

import factorization.api.Charge;
import factorization.api.IChargeConductor;
import factorization.api.datahelpers.DataHelper;
import factorization.common.FactoryType;
import factorization.shared.BlockClass;
import factorization.shared.TileEntityCommon;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;

import java.io.IOException;

public class InfiniteChargeBlock extends TileEntityCommon implements IChargeConductor {
    Charge charge = new Charge(this);
    
    @Override
    public void updateEntity() {
        if (charge.getValue() < 100) {
            charge.setValue(100);
        }
        charge.update();
    }

    @Override
    public String getInfo() {
        return null;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.CREATIVE_CHARGE;
    }

    @Override
    public void putData(DataHelper data) throws IOException {

    }

    @Override
    public Charge getCharge() {
        return charge;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Machine;
    }
    
    @Override
    public IIcon getIcon(EnumFacing dir) {
        if (dir.getDirectionVec().getY() != 0) return BlockIcons.battery_top;
        return Blocks.bedrock.getIcon(0, 0);
    }
}
