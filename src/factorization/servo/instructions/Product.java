package factorization.servo.instructions;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.common.BlockIcons;
import factorization.servo.Executioner;
import factorization.servo.Instruction;
import factorization.servo.ServoMotor;
import factorization.servo.ServoStack;

public class Product extends Instruction {
    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        return this;
    }

    @Override
    protected ItemStack getRecipeItem() {
        return new ItemStack(Block.fenceIron);
    }
    
    @Override
    protected ItemStack getInstructionPlate() {
        return (new Sum()).toItem();
    }

    @Override
    public void motorHit(ServoMotor motor) {
        ServoStack stack = motor.getArgStack();
        Integer a = stack.popType(Integer.class);
        Integer b = stack.popType(Integer.class);
        if (a == null) a = 0;
        if (b == null) b = 0;
        stack.push(a*b);
    }

    @Override
    public IIcon getIIcon(ForgeDirection side) {
        return BlockIcons.servo$product;
    }

    @Override
    public String getName() {
        return "fz.instruction.product";
    }
}
