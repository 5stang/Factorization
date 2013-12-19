package factorization.servo.instructions;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;
import net.minecraftforge.common.ForgeDirection;
import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.common.BlockIcons;
import factorization.servo.Instruction;
import factorization.servo.ServoMotor;
import factorization.shared.TileEntityCommon;

public class RedstonePulse extends Instruction {

    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        return this;
    }

    @Override
    public void motorHit(ServoMotor motor) {
        if (motor.worldObj.isRemote) {
            return;
        }
        TileEntityCommon tef = motor.getCurrentPos().getTE(TileEntityCommon.class);
        if (tef == null) {
            return; //Just back away, very slowly...
        }
        tef.pulse();
    }

    @Override
    public Icon getIcon(ForgeDirection side) {
        return BlockIcons.servo$pulse;
    }

    @Override
    public boolean onClick(EntityPlayer player, Coord block, ForgeDirection side) {
        return false;
    }

    @Override
    public boolean onClick(EntityPlayer player, ServoMotor motor) {
        return false;
    }

    @Override
    public String getName() {
        return "fz.instruction.pulse";
    }

    @Override
    protected ItemStack getRecipeItem() {
        return new ItemStack(Block.pressurePlateStone);
    }
}
