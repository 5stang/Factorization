package factorization.utiligoo;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.common.BlockIcons;
import factorization.common.Command;
import factorization.common.ItemIcons;
import factorization.shared.BlockRenderHelper;
import factorization.shared.Core;
import factorization.shared.Core.TabType;
import factorization.shared.FzUtil;
import factorization.shared.ItemFactorization;
import factorization.shared.NetworkFactorization.MessageType;

public class ItemGoo extends ItemFactorization {
    public ItemGoo(String name, TabType tabType) {
        super(name, tabType);
        setMaxStackSize(32);
        Core.loadBus(this);
    }
    
    @Override
    public boolean onItemUseFirst(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return false;
        }
        if (player.isSneaking()) {
            ForgeDirection fd = ForgeDirection.getOrientation(side);
            GooData data = GooData.getGooData(is, world);
            for (int i = 0; i < data.coords.length; i += 3) {
                data.coords[i + 0] = data.coords[i + 0] + fd.offsetX;
                data.coords[i + 1] = data.coords[i + 1] + fd.offsetY;
                data.coords[i + 2] = data.coords[i + 2] + fd.offsetZ;
            }
            data.markDirty();
            return false;
        }
        if (is.stackSize <= 1) return false;
        GooData data = GooData.getGooData(is, world);
        for (int i = 0; i < data.coords.length; i += 3) {
            int ix = data.coords[i + 0];
            int iy = data.coords[i + 1];
            int iz = data.coords[i + 2];
            if (x == ix && y == iy && z == iz) {
                expandSelection(is, data, world, x, y, z, ForgeDirection.getOrientation(side));
                return true;
            }
        }
        data.coords = ArrayUtils.addAll(data.coords, x, y, z);
        data.markDirty();
        is.stackSize--;
        return true;
    }
    
    public void executeCommand(Command command, EntityPlayerMP player) {
        MovingObjectPosition mop = getMovingObjectPositionFromPlayer(player.worldObj, player, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        
        ItemStack held = player.getHeldItem();
        if (command == Command.gooRightClick) {
            if (held == null) return;
            if (held.getItem() != this && !(held.getItem() instanceof ItemBlock)) return;
        }
        
        for (int slot = 0; slot < 9; slot++) {
            ItemStack is = player.inventory.getStackInSlot(slot);
            if (is == null || is.getItem() != this) continue;
            GooData data = GooData.getGooData(is, player.worldObj);
            for (int i = 0; i < data.coords.length; i += 3) {
                int ix = data.coords[i + 0];
                int iy = data.coords[i + 1];
                int iz = data.coords[i + 2];
                if (ix == mop.blockX && iy == mop.blockY && iz == mop.blockZ) {
                    if (command == Command.gooLeftClick) {
                        leftClick(player, data, is, held, mop);
                    } else if (command == Command.gooRightClick) {
                        rightClick(player, data, is, held, mop);
                    }
                    return;
                }
            }
        }
    }
    
    private void leftClick(EntityPlayerMP player, GooData data, ItemStack gooItem, ItemStack held, MovingObjectPosition mop) {
        // Punch with tool: remove all blocks that can be harvested by the tool
        // Normal punch: degoo 3x3x3 goo area
        // shift-punch: degoo punched block
        if (held != null && (held.getItem() instanceof ItemTool || !held.getItem().getToolClasses(held).isEmpty())) {
            mineSelection(gooItem, data, player.worldObj, mop, player, held);
        } else {
            int radius = player.isSneaking() ? 0 : 1;
            degooArea(player, data, gooItem, mop, radius);
        }
    }
    
    private void rightClick(EntityPlayer player, GooData data, ItemStack gooItem, ItemStack held, MovingObjectPosition mop) {
        // goo click: expand the selection.
        // ItemBlock click: replace everything with held item
        if (held == null) return;
        if (held.getItem() == this) {
            expandSelection(gooItem, data, player.worldObj, mop.blockX, mop.blockY, mop.blockZ, ForgeDirection.getOrientation(mop.sideHit));
        } else if (held.getItem() instanceof ItemBlock) {
            replaceBlocks(gooItem, data, player.worldObj, player, mop, held);
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack is = player.inventory.getStackInSlot(i);
                if (is != null && FzUtil.normalize(is) == null) {
                    player.inventory.setInventorySlotContents(i, null);
                }
            }
        }
    }
    
    private boolean check(int offset, int i, int x) {
        return offset == 0 || i == x;
    }
    
    private void expandSelection(ItemStack is, GooData data, World world, int x, int y, int z, ForgeDirection dir) {
        HashSet<Coord> found = new HashSet();
        for (int i = 0; i < data.coords.length; i += 3) {
            int ix = data.coords[i + 0];
            int iy = data.coords[i + 1];
            int iz = data.coords[i + 2];
            if (check(dir.offsetX, ix, x) && check(dir.offsetY, iy, y) && check(dir.offsetZ, iz, z)) {
                Coord at = new Coord(world, ix, iy, iz);
                if (at.add(dir).isSolid()) continue;
                Block atBlock = at.getBlock();
                ItemStack atDrop = at.getBrokenBlock();
                for (ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
                    if (fd == dir || fd == dir.getOpposite()) continue;
                    Coord n = at.add(fd);
                    Block nBlock = n.getBlock();
                    if (atBlock == nBlock && (atDrop == null || FzUtil.couldMerge(atDrop, n.getBrokenBlock())) && !n.add(dir).isSolid()) {
                        found.add(n);
                    }
                }
            }
        }
        ArrayList<Coord> addable = new ArrayList();
        nextCoord: for (Coord c : found) {
            for (int i = 0; i < data.coords.length; i += 3) {
                int ix = data.coords[i + 0];
                int iy = data.coords[i + 1];
                int iz = data.coords[i + 2];
                if (c.x == ix && c.y == iy && c.z == iz) continue nextCoord;
            }
            addable.add(c);
        }
        Collections.sort(addable);
        int count = Math.min(addable.size(), is.stackSize - 1);
        count = Math.min(maxStackSize - 1 - data.coords.length / 3, count);
        if (count <= 0) return;
        int[] use = new int[count * 3];
        for (int i = 0; i < count; i++) {
            Coord c = addable.get(i);
            use[i * 3 + 0] = c.x;
            use[i * 3 + 1] = c.y;
            use[i * 3 + 2] = c.z;
        }
        is.stackSize -= count;
        data.coords = ArrayUtils.addAll(data.coords, use);
        data.markDirty();
    }
    
    private boolean degooArea(EntityPlayer player, GooData data, ItemStack gooItem, MovingObjectPosition mop, int radius) {
        if (player.worldObj.isRemote) return false;
        if (data == null) return false;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        for (int i = 0; i < data.coords.length; i += 3) {
            int ix = data.coords[i + 0];
            int iy = data.coords[i + 1];
            int iz = data.coords[i + 2];
            if (mop.blockX == ix && mop.blockY == iy && mop.blockZ == iz) {
                int d = radius;
                boolean any = false;
                for (int dx = -d; dx <= d; dx++) {
                    for (int dy = -d; dy <= d; dy++) {
                        for (int dz = -d; dz <= d; dz++) {
                            any |= deselectCoord(gooItem, data, player.worldObj, ix + dx, iy + dy, iz + dz, false);
                        }
                    }
                }
                if (any) {
                    data.markDirty();
                }
                return false;
            }
        }
        return false;
    }
    
    private void replaceBlocks(ItemStack is, GooData data, World world, EntityPlayer player, MovingObjectPosition mop, ItemStack source) {
        if (FzUtil.normalize(source) == null) return;
        int removed = 0;
        Coord at = new Coord(world, 0, 0, 0);
        boolean creative = player.capabilities.isCreativeMode;
        ArrayList<Integer> to_remove = new ArrayList();
        for (int i = 0; i < data.coords.length; i += 3) {
            if (source.stackSize <= 0) {
                for (ItemStack replace : player.inventory.mainInventory) {
                    if (source != replace && FzUtil.identical(source, replace)) {
                        if (FzUtil.normalize(replace) != null) {
                            source = replace;
                            break;
                        }
                    }
                }
                if (FzUtil.normalize(source) == null) break;
            }
            at.x = data.coords[i + 0];
            at.y = data.coords[i + 1];
            at.z = data.coords[i + 2];
            if (creative) {
                at.setAir();
            } else {
                Block block = at.getBlock();
                int md = at.getMd();
                // NORELEASE: This doesn't work right with eg. spruce wood logs
                block.onBlockHarvested(world, at.x, at.y, at.z, mop.sideHit, player);
                if (block.removedByPlayer(world, player, at.x, at.y, at.z, true)) {
                    block.onBlockDestroyedByPlayer(world, at.x, at.y, at.z, md);
                    block.harvestBlock(world, player, at.x, at.y, at.z, 0);
                }
            }
            to_remove.add(i + 0);
            to_remove.add(i + 1);
            to_remove.add(i + 2);
            
            ItemBlock ib = (ItemBlock) source.getItem();
            ib.onItemUse(source, player, player.worldObj, at.x, at.y, at.z, mop.sideHit, (float) mop.hitVec.xCoord, (float) mop.hitVec.yCoord, (float) mop.hitVec.zCoord);
            removed++;
        }
        if (removed <= 0) return;
        data.removeIndices(to_remove, is, world);
        misplaceSomeGoo(is, world.rand, removed);
    }
    
    private boolean deselectCoord(ItemStack is, GooData data, World world, int x, int y, int z, boolean bulkAction) {
        for (int i = 0; i < data.coords.length; i += 3) {
            int ix = data.coords[i + 0];
            int iy = data.coords[i + 1];
            int iz = data.coords[i + 2];
            if (ix == x && iy == y && iz == z) {
                data.coords = ArrayUtils.removeAll(data.coords, i, i + 1, i + 2);
                is.stackSize++;
                if (data.coords.length == 0) {
                    data.wipe(is, world);
                } else if (!bulkAction) {
                    data.markDirty();
                }
                return true;
            }
        }
        return false;
    }
    
    private void mineSelection(ItemStack is, GooData data, World world, MovingObjectPosition mop, EntityPlayerMP player, ItemStack tool) {
        Item toolItem = tool.getItem();
        ArrayList<Integer> toRemove = new ArrayList();
        int removed = 0;
        for (int i = 0; i < data.coords.length; i += 3) {
            int ix = data.coords[i + 0];
            int iy = data.coords[i + 1];
            int iz = data.coords[i + 2];
            Block b = world.getBlock(ix, iy, iz);
            if (b.isAir(world, ix, iy, iz)) continue;
            float hardness = b.getBlockHardness(world, ix, iy, iz);
            if (hardness < 0) continue;
            if (hardness == 0 || toolItem.canHarvestBlock(b, tool) || toolItem.func_150893_a(tool, b) > 1)  {
                if (player.theItemInWorldManager.tryHarvestBlock(ix, iy, iz)) {
                    removed++;
                    toRemove.add(i);
                    toRemove.add(i + 1);
                    toRemove.add(i + 2);
                }
            }
        }
        if (removed == 0) return;
        data.removeIndices(toRemove, is, world);
        misplaceSomeGoo(is, world.rand, removed);
    }
    
    private void misplaceSomeGoo(ItemStack is, Random rand, int removed) {
        if (rand.nextInt(100) < 80) {
            removed--;
        }
        is.stackSize += removed;
    }
    
    @Override
    public void onUpdate(ItemStack is, World world, Entity player, int inventoryIndex, boolean isHeld) {
        if (world.isRemote) return;
        GooData data = GooData.getNullGooData(is, world);
        if (data == null) return;
        if (!data.isPlayerOutOfDate(player)) return;
        if (!(player instanceof EntityPlayer)) return;
        NBTTagCompound dataTag = new NBTTagCompound();
        data.writeToNBT(dataTag);
        FMLProxyPacket toSend = Core.network.entityPacket(player, MessageType.UtilityGooState, dataTag);
        Core.network.broadcastPacket((EntityPlayer) player, new Coord(player), toSend);
    }
    
    @SideOnly(Side.CLIENT)
    public static void handlePacket(DataInput input) throws IOException {
        NBTTagCompound dataTag = FzUtil.readTag(input);
        World world = Minecraft.getMinecraft().theWorld;
        GooData data = new GooData(dataTag.getString("mapname")); // NOTE: this resets data.last_traced_index to -1. We might have to reset it manually if networking gets more complicated.
        data.readFromNBT(dataTag);
        world.setItemData(data.mapName, data);
    }
    

    @Override
    public IIcon getIconIndex(ItemStack is) {
        int size = is.stackSize;
        if (size <= 1) return ItemIcons.utiligoo$empty;
        int third = is.getMaxStackSize() / 3;
        int fullness = size / third;
        if (fullness <= 1) return ItemIcons.utiligoo$low;
        if (fullness <= 2) return ItemIcons.utiligoo$medium;
        return ItemIcons.utiligoo$high;
    }
    
    @Override
    public IIcon getIcon(ItemStack is, int pass) {
        return getIconIndex(is);
    }
    
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void renderGoo(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        boolean rendered_something = false;
        for (int i = 0; i < 9; i++) {
            ItemStack is = player.inventory.getStackInSlot(i);
            if (is == null || is.getItem() != this) continue;
            GooData data = GooData.getNullGooData(is, mc.theWorld);
            if (data == null) continue; 
            if (data.dimensionId != mc.theWorld.provider.dimensionId) continue;
            if (data.coords.length == 0) continue;
            if (!rendered_something) {
                rendered_something = true;
                EntityLivingBase camera = Minecraft.getMinecraft().renderViewEntity;
                double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
                double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
                double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
                mc.renderEngine.bindTexture(Core.blockAtlas);
                GL11.glPushMatrix();
                GL11.glTranslated(-cx, -cy, -cz);
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glColor4d(1, 1, 1, 0.9);
                OpenGlHelper.glBlendFunc(774, 768, 1, 0);
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-3.0F, -3F);
            }
            renderGooFor(event, data, player);
        }
        if (rendered_something) {
            GL11.glPolygonOffset(0.0F, 0.0F);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }
    
    @SideOnly(Side.CLIENT)
    void renderGooFor(RenderWorldLastEvent event, GooData data, EntityPlayer player) {
        boolean rendered_something = false;
        double render_dist_sq = 32*32;
        Tessellator tess = Tessellator.instance;
        BlockRenderHelper block = BlockRenderHelper.instance;
        RenderBlocks rb = FzUtil.getRB();
        for (int i = 0; i < data.coords.length; i += 3) {
            int x = data.coords[i + 0];
            int y = data.coords[i + 1];
            int z = data.coords[i + 2];
            if (player.getDistanceSq(x, y, z) > render_dist_sq) continue;
            if (!rendered_something) {
                tess.startDrawingQuads();
                tess.disableColor();
                rendered_something = true;
            }
            Block b = player.worldObj.getBlock(x, y, z);
            Material mat = b.getMaterial();
            if (mat.blocksMovement() && !b.hasTileEntity()) {
                rb.renderBlockUsingTexture(b, x, y, z, BlockIcons.utiligoo$invasion);
            } else if (b.getRenderType() == 2 /* torches */) {
                // Torches stupidly don't support setBlockBoundsBasedOnState. How stupid.
                float d = 0.25F;
                block.setBlockBoundsOffset(d, d, d);
                block.useTexture(BlockIcons.utiligoo$invasion);
                block.render(rb, x, y, z);
            } else {
                b.setBlockBoundsBasedOnState(player.worldObj, x, y, z);
                block.setBlockBounds((float)b.getBlockBoundsMinX(), (float)b.getBlockBoundsMinY(), (float)b.getBlockBoundsMinZ(), (float)b.getBlockBoundsMaxX(), (float)b.getBlockBoundsMaxY(), (float)b.getBlockBoundsMaxZ()); // Hello, Notch! 
                block.useTexture(BlockIcons.utiligoo$invasion);
                block.render(rb, x, y, z);
            }
        }
        if (rendered_something) {
            tess.draw();
        }
    }
    
    @Override
    protected void addExtraInformation(ItemStack is, EntityPlayer player, List list, boolean verbose) {
        super.addExtraInformation(is, player, list, verbose);
        if (verbose) {
            list.add("Goo Index: " + is.getItemDamage());
            GooData data = GooData.getNullGooData(is, player.worldObj);
            if (data == null) {
                list.add("Not loaded...");
            } else {
                list.add((data.coords.length / 3) + " points");
            }
        }
    }
    
    public static class ClickInterceptor extends Entity {
        
        public ClickInterceptor(World world) {
            super(world);
        }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound p_70037_1_) { }
        @Override protected void writeEntityToNBT(NBTTagCompound p_70014_1_) { }
        
        @Override
        public boolean canBeCollidedWith() {
            return true;
        }
        
        @Override
        public boolean interactFirst(EntityPlayer player) {
            ItemStack held = player.getHeldItem();
            if (held == null) return false;
            if (!(held.getItem() instanceof ItemBlock) && held.getItem() != Core.registry.utiligoo) return false;
            Command.gooRightClick.call(player);
            hideInterceptor();
            return true;
        }
        
        @Override
        public boolean hitByEntity(Entity ent) {
            if (!(ent instanceof EntityPlayer)) return false;
            Command.gooLeftClick.call((EntityPlayer) ent);
            hideInterceptor();
            return true;
        }
    }
    
    static ClickInterceptor interceptor = null;
    
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void discardWorldReference(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            interceptor = null;
        }
    }
    
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void positionInterceptionEntity(ClientTickEvent event) {
        if (event.phase == Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver == null) {
            hideInterceptor();
            return;
        }
        if (mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            if (mc.objectMouseOver.entityHit != interceptor) {
                hideInterceptor();
            }
            return;
        }
        if (mc.thePlayer == null) return; // world unloading
        if (!position(mc)) {
            hideInterceptor();
        }
        
    }
    
    private boolean position(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack is = mc.thePlayer.inventory.getStackInSlot(i);
            if (is == null || is.getItem() != this) continue;
            GooData data = GooData.getNullGooData(is, mc.theWorld);
            if (data == null) continue;
            if (positionInterceptorFor(data, mc.theWorld, mc.objectMouseOver)) return true;
        }
        return false;
    }
    
    static void hideInterceptor() {
        if (interceptor != null) {
            interceptor.setPosition(interceptor.posX, -60, interceptor.posZ);
        }
    }
    
    private boolean positionInterceptorFor(GooData data, World world, MovingObjectPosition mop) {
        if (data.last_traced_index >= 0 && data.last_traced_index <= data.coords.length - 3) {
            int i = data.last_traced_index;
            int x = data.coords[i + 0];
            int y = data.coords[i + 1];
            int z = data.coords[i + 2];
            if (check(x, y, z, world, mop)) {
                return true;
            }
        }
        // O(n) slow!  >__>
        // At least n is always less than 32.
        // And the array'll fit perfectly in the CPU cache.
        for (int i = 0; i < data.coords.length; i += 3) {
            int x = data.coords[i + 0];
            int y = data.coords[i + 1];
            int z = data.coords[i + 2];
            if (check(x, y, z, world, mop)) {
                data.last_traced_index = i;
                return true;
            }
        }
        return false;
    }
    
    boolean check(int x, int y, int z, World world, MovingObjectPosition mop) {
        if (!(mop.blockX == x && mop.blockY == y && mop.blockZ == z)) return false;
        boolean spawn = false;
        if (interceptor == null || interceptor.worldObj != world) {
            interceptor = new ClickInterceptor(world);
            spawn = true;
        }
        interceptor.posX = x;
        interceptor.posY = y;
        interceptor.posZ = z;
        double d = 0F/16F;
        double e = 1 + d;
        interceptor.boundingBox.minX = x - d;
        interceptor.boundingBox.minY = y - d;
        interceptor.boundingBox.minZ = z - d;
        interceptor.boundingBox.maxX = x + e;
        interceptor.boundingBox.maxY = y + e;
        interceptor.boundingBox.maxZ = z + e;
        if (spawn) {
            interceptor.isDead = false;
            world.spawnEntityInWorld(interceptor);
            interceptor.isDead = false;
        }
        mop.typeOfHit = MovingObjectType.ENTITY;
        mop.entityHit = interceptor;
        return true;
    }
}
