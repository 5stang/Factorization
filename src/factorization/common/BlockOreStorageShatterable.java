package factorization.common;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOreStorage;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import factorization.api.Coord;

public class BlockOreStorageShatterable extends BlockOreStorage {
    private final Block original;
    public BlockOreStorageShatterable(int id, Block original) {
        super(id);
        this.original = original;
    }
    
    @Override
    public boolean canDropFromExplosion(Explosion boom) {
        return false;
    }
    
    @Override
    public void onBlockDestroyedByExplosion(World world, int x, int y, int z, Explosion explosion) {
        super.onBlockDestroyedByExplosion(world, x, y, z, explosion);
        if (world.isRemote) {
            return;
        }
        Coord c = new Coord(world, x, y, z);
        int i = 18;
        while (i > 0) {
            int spawn = world.rand.nextInt(3) + 2;
            spawn = Math.min(spawn, i);
            i -= spawn;
            EntityItem ent = c.spawnItem(new ItemStack(Core.registry.diamond_shard, spawn));
            ent.invulnerable = true;
            ent.motionX = world.rand.nextGaussian()/4;
            ent.motionY = world.rand.nextGaussian()/4;
            ent.motionZ = world.rand.nextGaussian()/4;
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IconRegister register) {
        original.registerIcons(register);
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public Icon getIcon(int side, int md) {
        return original.getIcon(side, md);
    }
}
