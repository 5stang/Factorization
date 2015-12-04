package factorization.misc;

import factorization.api.Coord;
import factorization.common.FzConfig;
import factorization.util.DataUtil;
import factorization.util.FzUtil;
import factorization.util.ItemUtil;
import factorization.util.PlayerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class BlockUndo {
    public static final String channelName = "FZ|blockundo";
    public static final FMLEventChannel channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(channelName);
    public static final BlockUndo instance = new BlockUndo();
    private BlockUndo() {
        channel.register(this);
    }

    private static void send(EntityPlayer player, PlacedBlock at) {
        ByteBuf payload = Unpooled.buffer();
        at.write(payload);
        FMLProxyPacket packet = new FMLProxyPacket(payload, channelName);
        channel.sendTo(packet, (EntityPlayerMP) player);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void addBlockUndo(FMLNetworkEvent.ClientCustomPacketEvent event) {
        PlacedBlock at = PlacedBlock.read(event.packet.payload());
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        markPlacement(player, at);
    }

    private static class PlacedBlock {
        final int w, pos, idmd;
        final ItemStack orig;

        private PlacedBlock(int w, BlockPos pos, int idmd, ItemStack orig) {
            this.w = w;
            this.x = x;
            this.y = y;
            this.z = z;
            this.idmd = idmd;
            this.orig = orig;
        }

        @Override
        public String toString() {
            return x + " " + z;
        }

        void write(ByteBuf out) {
            out.writeInt(w);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.writeInt(idmd);
            ByteBufUtils.writeItemStack(out, orig);
        }

        static PlacedBlock read(ByteBuf in) {
            return new PlacedBlock(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                    ByteBufUtils.readItemStack(in));
        }
    }

    public static int UNDO_MAX = 6;
    public static float MAX_TRUE_SPEED_STANDARD = 0.25F / 2;
    public static float MAX_TRUE_SPEED_TILEENTITY = 0.125F / 2;
    public static float ANTI_WARP_SPEED = 64;

    HashMap<String, ArrayList<PlacedBlock>> recentlyPlaced = new HashMap<String, ArrayList<PlacedBlock>>();

    private static String getName(EntityPlayer player) {
        return player.getCommandSenderName() + " #" + player.worldObj.isRemote;
    }

    private static ItemStack toItem(Block b, World w, BlockPos pos, int md) {
        for (ItemStack is : b.getDrops(w, pos, md, 0)) {
            return is;
        }
        return null;
    }

    void markPlacement(EntityPlayer player, PlacedBlock at) {
        ArrayList<PlacedBlock> coords;
        String playerName = getName(player);
        if (!recentlyPlaced.containsKey(playerName)) {
            recentlyPlaced.put(playerName, coords = new ArrayList<PlacedBlock>());
        } else {
            coords = recentlyPlaced.get(playerName);
        }
        for (Iterator<PlacedBlock> it = coords.iterator(); it.hasNext(); ) {
            PlacedBlock c = it.next();
            World w = DimensionManager.getWorld(c.w);
            if (w == null || w.isAirBlock(c.x, c.y, c.z)) {
                it.remove();
            } else if (c.x == at.x && c.y == at.y && c.z == at.z) {
                it.remove();
            }
        }
        coords.add(at);
        if (coords.size() > UNDO_MAX) {
            coords.remove(0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // Act after any cancellations
    public void recordBlock(BlockEvent.PlaceEvent event) {
        if (event.player == null) return;
        if (event.world.isRemote) return;
        if (event.player instanceof FakePlayer) return;
        if (event.block.getBlockHardness(event.world, event.x, event.y, event.z) <= 0F) return;
        if (PlayerUtil.isPlayerCreative(event.player)) return;
        int md = event.world.getBlockMetadata(event.x, event.y, event.z); // Notable for NOT being the same as event.blockMetadata for Railcraft quarried stone! :|
        int idmd = (DataUtil.getId(event.block) << 4) /*+ event.blockMetadata*/;
        final ItemStack theItem = toItem(event.block, event.world, event.x, event.y, event.z, md);
        final PlacedBlock at = new PlacedBlock(FzUtil.getWorldDimension(event.world), event.x, event.y, event.z, idmd, theItem);
        markPlacement(event.player, at);
        if (!event.world.isRemote && event.player instanceof EntityPlayerMP) {
            send((EntityPlayerMP) event.player, at);
        }
    }

    private ThreadLocal<Boolean> working = new ThreadLocal<Boolean>();
    @SubscribeEvent
    public void boostBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (working.get() != null) {
            return;
        }
        working.set(true);
        try {
            determineBreakSpeed(event);
        } finally {
            working.remove();
        }
    }

    private HashMap<Integer, Long> playerBreakage = new HashMap<Integer, Long>();
    private boolean stillBusy(EntityPlayer player) {
        Integer code = player.hashCode();
        Long last = playerBreakage.get(code);
        if (last == null) last = -1000L;
        final long end = player.worldObj.getTotalWorldTime() + 8;
        return last > end;
    }

    private void markBusy(EntityPlayer player) {
        Integer code = player.hashCode();
        playerBreakage.put(code, player.worldObj.getTotalWorldTime());
    }


    private void determineBreakSpeed(PlayerEvent.BreakSpeed event) {
        final int y = event.y;
        if (y == -1) return; // Event specifies that 'y' might be -1 for unknown usage?
        final Block block = event.block;
        final int md = event.metadata;
        final int x = event.x;
        final int z = event.z;
        final EntityPlayer player = event.entityPlayer;
        if (stillBusy(player)) return;
        if (!canUndo(event, pos, block, md)) return;
        // Duplicate logic to figure out what the *actual* break speed will be, so that we don't make this actual break speed too fast
        float hardness = block.getBlockHardness(player.world, pos);
        if (hardness < 0.0F) {
            // Block is invulnerable
            return;
        }
        String heldName = DataUtil.getName(player.getHeldItem());
        if (heldName == null) heldName = "";
        if (heldName.startsWith("TConstruct:")) {
            return; // avoid warp-speed issues
        }
        final float harvestingSpeed = ForgeHooks.canHarvestBlock(block, player, md) ? 30F : 100F;
        final float max_true_speed = block.hasTileEntity(md) ? MAX_TRUE_SPEED_TILEENTITY : MAX_TRUE_SPEED_STANDARD;
        float true_speed = event.newSpeed / hardness / harvestingSpeed;
        if (true_speed > max_true_speed) return;
        float boost = max_true_speed * hardness * harvestingSpeed;
        event.newSpeed = Math.max(event.newSpeed * boost, event.newSpeed);
        event.newSpeed = Math.min(ANTI_WARP_SPEED, event.newSpeed);
        // ... this code is wrong. It's suuuper fast for enderchests. Everything too complicated?
        // Maybe just a single blind speed, and be done with it?
    }

    private boolean canUndo(PlayerEvent event, BlockPos pos, Block block, int metadata) {
        final EntityPlayer player = event.entityPlayer;
        ArrayList<PlacedBlock> coords = recentlyPlaced.get(getName(player));
        if (coords == null) return false;

        int w = FzUtil.getWorldDimension(player.worldObj);
        for (PlacedBlock hot : coords) {
            if (hot.w == w && hot.x == x && hot.y == y && hot.z == z) {
                int idmd = (DataUtil.getId(block) << 4) /*+ metadata*/;
                if (idmd != hot.idmd) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH) // Cancel before most things, but permission-handlers can cancel before us
    public void playerRemovedBlock(BlockEvent.BreakEvent event) {
        EntityPlayer thePlayer = event.getPlayer();
        markBusy(thePlayer);
        ArrayList<PlacedBlock> coords = recentlyPlaced.get(getName(thePlayer));
        if (coords == null) return;
        PlacedBlock heat = null;
        final World w = event.world;
        final int x = event.x;
        final int y = event.y;
        final int z = event.z;
        final Block block = event.block;
        final int md = event.blockMetadata;
        int wDim = FzUtil.getWorldDimension(w);
        if (PlayerUtil.isPlayerCreative(thePlayer)) return;
        for (Iterator<PlacedBlock> iterator = coords.iterator(); iterator.hasNext(); ) {
            PlacedBlock hot = iterator.next();
            if (hot.w == wDim && hot.x == x && hot.y == y && hot.z == z) {
                heat = hot;
                iterator.remove();
                break;
            }
        }
        if (heat == null || !(thePlayer instanceof EntityPlayerMP)) {
            return;
        }
        if (!ItemUtil.identical(heat.orig, toItem(block, w, pos, md))) {
            return;
        }
        EntityPlayerMP real_player = (EntityPlayerMP) thePlayer;
        final ItemStack heldItem = real_player.getHeldItem();
        String heldName = DataUtil.getName(heldItem);
        if (heldName == null) heldName = "";
        if (heldName.startsWith("TConstruct:")) {
            return; // avoid warp-speed issues
        }
        if (ForgeHooks.canToolHarvestBlock(block, md, heldItem)) {
            return;
        }
        if (block.getItemDropped(md, thePlayer.worldObj.rand, 0) != DataUtil.getItem(block)) {
            if (!real_player.isSneaking() && block.getBlockHardness(w, pos) < 1) {
                return;
            }
        }
        String harvestTool = block.getHarvestTool(md);
        int harvestLevel = block.getHarvestLevel(md);
        Item harvester = findAppropriateTool(harvestTool, harvestLevel);
        if (harvester == null) return;
        event.setCanceled(true);
        ItemStack tool = new ItemStack(harvester);
        tool.setItemDamage(tool.getMaxDamage());
        tool.addEnchantment(Enchantment.silkTouch, 1);
        tool.stackSize = 0;
        EntityPlayer fake_player = PlayerUtil.makePlayer(new Coord(w, pos), "BlockUndo");
        fake_player.setCurrentItemOrArmor(0, tool);
        {
            double r = 0.5;
            AxisAlignedBB box = new AxisAlignedBB(x - r, y - r, z - r, x + 1 + r, y + 1 + r, z + 1 + r);
            block.onBlockHarvested(w, pos, md, fake_player);
            boolean canDestroy = block.removedByPlayer(w, fake_player, pos, true);

            if (canDestroy) {
                block.onBlockDestroyedByPlayer(w, pos, md);
            }
            block.harvestBlock(w, fake_player, pos, md);
            if (canDestroy) {
                int xp = block.getExpDrop(w, md, 0);
                block.dropXpOnBlockBreak(w, pos, xp);
            }
            if (FzConfig.blockundo_grab) {
                for (Object o : w.getEntitiesWithinAABB(EntityItem.class, box)) {
                    EntityItem ei = (EntityItem) o;
                    int orig_delay = ei.delayBeforeCanPickup;
                    ei.delayBeforeCanPickup = 0;
                    ei.onCollideWithPlayer(real_player);
                    ei.delayBeforeCanPickup = orig_delay;
                }
            }
        }
        PlayerUtil.recycleFakePlayer(fake_player);
    }


    private final HashMap<String, Item> cache = new HashMap<String, Item>();
    private Item findAppropriateTool(String tool, int level) {
        if (tool == null && level == -1) {
            return Items.diamond_pickaxe;
        }
        String name = tool + "#" + level;
        Item ret = cache.get(name);
        if (ret != null) {
            return ret;
        }
        if (cache.containsKey(name)) return null;
        for (Object obj : Item.itemRegistry) {
            Item item = (Item) obj;
            final ItemStack dummy = new ItemStack(item);
            if (item.getToolClasses(dummy).contains(tool)) {
                if (item.getHarvestLevel(dummy, tool) >= level) {
                    cache.put(name, item);
                    return item;
                }
            }
        }
        cache.put(name, null);
        return null;

    }

}
