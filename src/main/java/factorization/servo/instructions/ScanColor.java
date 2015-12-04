package factorization.servo.instructions;

import factorization.api.Coord;
import factorization.api.FzColor;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.notify.Notice;
import factorization.servo.Decorator;
import factorization.servo.ServoMotor;
import factorization.shared.Core;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockReed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.IPlantable;

import java.io.IOException;

public class ScanColor extends Decorator {
    @Override
    public IDataSerializable putData(String prefix, DataHelper data) throws IOException {
        return this;
    }
    
    static final FzColor[] colorArray = new FzColor[] {
            FzColor.LIME, FzColor.LIME, FzColor.LIME,
            FzColor.GREEN, FzColor.GREEN, FzColor.GREEN, FzColor.GREEN, 
            FzColor.YELLOW, FzColor.YELLOW, FzColor.YELLOW, FzColor.YELLOW, 
            FzColor.ORANGE, FzColor.ORANGE, FzColor.ORANGE, FzColor.ORANGE, 
            FzColor.RED
    };

    @Override
    public void motorHit(ServoMotor motor) {
        Coord at = motor.getCurrentPos();
        at = at.add(motor.getOrientation().top);
        FzColor col = FzColor.readColor(at);
        if (col != FzColor.NO_COLOR) {
            motor.getArgStack().push(col);
            return;
        }
        Block block = at.getBlock();
        if (block instanceof BlockReed || block instanceof BlockCactus) return; // Colors don't change, so give nothing.
        if (block instanceof IPlantable) {
            int md = ((IPlantable) block).getPlantMetadata(at.w, at.x, at.y, at.z);
            motor.getArgStack().push(colorArray[md]);
            return;
        }
    }

    @Override
    public IIcon getIcon(EnumFacing side) {
        return BlockIcons.servo$scan_color;
    }

    @Override
    public boolean onClick(EntityPlayer player, Coord block, EnumFacing side) {
        return false;
    }

    @Override
    public boolean onClick(EntityPlayer player, ServoMotor motor) {
        return false;
    }

    @Override
    public String getName() {
        return "fz.decorator.scancolor";
    }
    
    @Override
    protected void addRecipes() {
        Core.registry.oreRecipe(toItem(),
                "+Q+",
                "Q#Q",
                "+Q+",
                '+', FactoryType.SERVORAIL.itemStack(),
                'Q', Items.quartz,
                '#', Core.registry.logicMatrixIdentifier);
    }
    
    @Override
    public float getSize() {
        return super.getSize() - 1F/32F;
    }
    
    @Override
    public boolean collides() {
        return false;
    }
    
    @Override
    public void onItemUse(Coord here, EntityPlayer player) {
        FzColor color = FzColor.readColor(here);
        if (color == FzColor.NO_COLOR) return;
        new Notice(here, "color." + color).sendTo(player);
    }
}
