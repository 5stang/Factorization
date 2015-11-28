package factorization.servo.instructions;

import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.servo.Instruction;
import factorization.servo.ServoComponent;
import factorization.servo.ServoMotor;
import factorization.shared.NORELEASE;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import java.io.IOException;

/**
 * Exists only for serialization purposes.
 */
public class GenericPlaceholder extends Instruction {
    @Override
    protected ItemStack getRecipeItem() {
        return null;
    }

    @Override
    public void motorHit(ServoMotor motor) {

    }

    @Override
    public IIcon getIcon(ForgeDirection side) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        if (data.isWriter()) return this; // Better not happen!
        NBTTagCompound tag = data.asSameShare(prefix).putTag(new NBTTagCompound());
        return ServoComponent.load(tag);
    }
}
