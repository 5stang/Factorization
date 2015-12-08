package factorization.darkiron;

import factorization.util.SpaceUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

public class BlockDarkIronOre extends Block {
    public BlockDarkIronOre() {
        super(Material.rock);
    }

    static int te_particles = 0;

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.getTotalWorldTime() % 3 != 0) {
            return;
        }
        if (te_particles > 80) {
            return;
        }
        if (!inRange(pos, Minecraft.getMinecraft().thePlayer)) {
            return;
        }
        if (world.getTileEntity(pos) != null) {
            return;
        }
        for (EnumFacing dir : EnumFacing.VALUES) {
            if (world.isBlockNormalCube(pos.offset(dir), true)) {
                continue;
            }
            TileEntity te = new Glint();
            world.setTileEntity(pos, te);
            world.markBlockForUpdate(pos);
            return;
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    static float maxDistSq = 6*6;
    
    static boolean inRange(BlockPos pos, EntityPlayer player) {
        if (player == null) {
            return false;
        }
        return pos.distanceSq(SpaceUtil.vInt(SpaceUtil.fromEntPos(player))) < maxDistSq;
    }
    
    public static class Glint extends TileEntity implements ITickable {
        public int age = 0;
        public long lastRenderedTick = Long.MAX_VALUE;
        
        @SideOnly(Side.CLIENT)
        @Override
        public void update() {
            age++;
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (lastRenderedTick + 60 < worldObj.getTotalWorldTime() && !inRange(new BlockPos(age, age, age), player)) {
                worldObj.removeTileEntity(pos);
            }
        }
        
        @Override
        @SideOnly(Side.CLIENT)
        public double getMaxRenderDistanceSquared() {
            return maxDistSq;
        }
        
        @Override
        @SideOnly(Side.CLIENT)
        public AxisAlignedBB getRenderBoundingBox() {
            Block b = getBlockType();
            return b.getCollisionBoundingBox(worldObj, pos, b.getDefaultState());
        }
        
        @Override
        public void invalidate() {
            super.invalidate();
            te_particles--;
        }
        
        @Override
        public void validate() {
            super.validate();
            te_particles++;
        }
    }
}
