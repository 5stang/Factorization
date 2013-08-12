package factorization.common.servo.instructions;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;
import net.minecraftforge.common.ForgeDirection;
import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.common.BlockIcons;
import factorization.common.servo.Instruction;
import factorization.common.servo.ServoMotor;

public class ActivateActuator extends Instruction {

    @Override
    public Icon getIcon(ForgeDirection side) {
        if (sneaky) {
            return BlockIcons.servo$activate_sneaky;
        }
        return BlockIcons.servo$activate;
    }
    
    boolean sneaky = false;

    @Override
    public void motorHit(ServoMotor motor) {
        if (motor.worldObj.isRemote) {
            return;
        }
        motor.click(sneaky);
    }

    
    @Override
    public boolean onClick(EntityPlayer player, Coord block, ForgeDirection side) {
        if (playerHasProgrammer(player)) {
            sneaky = !sneaky;
            return true;
        }
        return false;
    }
    
    @Override
    public String getInfo() {
        if (sneaky) {
            return "Shift Click";
        }
        return "Normal Click";
    }

    @Override
    public String getName() {
        return "fz.instruction.activateactuator";
    }

    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        sneaky = data.asSameShare(prefix + "sneak").putBoolean(sneaky);
        return this;
    }
    
    @Override
    protected ItemStack getRecipeItem() {
        return new ItemStack(Item.redstone);
    }
    
    @Override
    public boolean interrupts() {
        return true;
    }
}
