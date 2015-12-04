package factorization.servo.instructions;

import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.servo.Instruction;
import factorization.servo.ServoMotor;
import factorization.servo.TileEntityServoRail;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;

import java.io.IOException;

public class EntryControl extends Instruction {
    public boolean blocking = false;
    
    @Override
    public IDataSerializable putData(String prefix, DataHelper data) throws IOException {
        blocking = data.asSameShare("block").putBoolean(blocking);
        return this;
    }
    
    @Override
    public boolean onClick(EntityPlayer player, Coord block, EnumFacing side) {
        if (!playerHasProgrammer(player)) {
            return false;
        }
        TileEntityServoRail sr = block.getTE(TileEntityServoRail.class);
        if (sr.priority >= 0) {
            sr.priority = -1;
            blocking = true;
        } else {
            sr.priority = 1;
            blocking = false;
        }
        return true;
    }
    
    @Override
    public void onPlacedOnRail(TileEntityServoRail sr) {
        sr.priority = 1;
        blocking = false;
    }

    @Override
    protected ItemStack getRecipeItem() {
        return new ItemStack(Blocks.fence_gate);
    }

    @Override
    public void motorHit(ServoMotor motor) { }

    @Override
    public IIcon getIcon(EnumFacing side) {
        return blocking ? BlockIcons.servo$entry_forbid : BlockIcons.servo$entry_require;
    }

    @Override
    public String getName() {
        return "fz.instruction.entryControl";
    }
    
    @Override
    public void afterClientLoad(TileEntityServoRail rail) {
        rail.priority = (byte) (blocking ? -1 : 1);
    }
}
