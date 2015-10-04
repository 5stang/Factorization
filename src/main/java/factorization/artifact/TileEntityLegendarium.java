package factorization.artifact;

import com.google.common.base.Strings;
import factorization.api.Coord;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.Share;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.notify.Notice;
import factorization.shared.BlockClass;
import factorization.shared.Core;
import factorization.shared.TileEntityCommon;
import factorization.util.FzUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import java.io.IOException;
import java.util.ArrayList;

public class TileEntityLegendarium extends TileEntityCommon {
    static final int MIN_SIZE = 7; // The queue must be this size before something can be removed
    static final int POSTER_RANGE = 16;
    static final int MAX_USAGES_LEFT = 32;

    private static final int WAIT_TIME = 7 * 24 * 60 * 60 * 1000;

    long last_insert_time = 0;
    ArrayList<ItemStack> queue = new ArrayList<ItemStack>();

    @Override
    public void putData(DataHelper data) throws IOException {
        last_insert_time = data.as(Share.PRIVATE, "lastInsertTime").putLong(last_insert_time);
        queue = data.as(Share.PRIVATE, "queue").putItemList(queue);
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.DarkIron;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.LEGENDARIUM;
    }

    boolean isDroid(EntityPlayer player) {
        if (player instanceof FakePlayer) {
            new Notice(this, "factorization.legendarium.nodroidsallowed").sendToAll();
            return true;
        }
        return false;
    }

    static boolean isTool(ItemStack is) {
        if (is == null) return false;
        if (is.stackSize != 1) return false;
        if (is.getMaxStackSize() > 1) return false;
        if (!is.getItem().isRepairable()) return false;
        if (is.getMaxDamage() <= 1) return false;
        if (is.getHasSubtypes()) return false;
        return true;
    }

    static String analyzeItem(ItemStack is) {
        if (is == null) return "noitem";
        if (!isTool(is)) return "not_tool";
        if (is.getItemDamage() < is.getMaxDamage() - MAX_USAGES_LEFT) return "not_broken";
        return null;
    }

    void sound(String name) {

    }

    long getWaitTicks() {
        long now = System.currentTimeMillis();
        long to_wait = last_insert_time + WAIT_TIME - now;
        return (to_wait /* ms */ / 1000) /* s */ * 20 /* ticks */;
    }

    @Override
    public boolean activate(EntityPlayer player, ForgeDirection side) {
        // Store an item
        if (worldObj.isRemote) return true;
        if (isDroid(player)) return false;
        ItemStack held = player.getHeldItem();
        if (held == null) {
            if (canRemove()) {
                new Notice(this, "factorization.legendarium.canremove").sendTo(player);
                return true;
            }
            long ticks = getWaitTicks();
            if (ticks > 0) {
                new Notice(this, "factorization.legendarium.wait", FzUtil.unitify(FzUtil.unit_time, ticks, 2)).sendTo(player);
            } else {
                new Notice(this, "factorization.legendarium.caninsert").sendTo(player);
            }
            return true;
        }

        String analysis = analyzeItem(held);
        if (analysis != null) {
            new Notice(this, "factorization.legendarium.item_analysis." + analysis).sendTo(player);
            return true;
        }


        long ticks = getWaitTicks();
        if (ticks > 0) {
            new Notice(this, "factorization.legendarium.wait", FzUtil.unitify(FzUtil.unit_time, ticks, 2)).sendTo(player);
            return true;
        }
        last_insert_time = System.currentTimeMillis();
        queue.add(held);
        player.setCurrentItemOrArmor(0, null);
        markDirty();
        sound("insert");
        return true;
    }

    boolean canRemove() {
        return queue.size() >= MIN_SIZE;
    }

    @Override
    public void click(EntityPlayer player) {
        // Remove an item
        if (isDroid(player)) return;
        if (!canRemove()) {
            new Notice(this, "factorization.legendarium.notfull").sendTo(player);
            return;
        }
        markDirty();
        sound("remove");
    }

    static final String legendariumCount = "legendariumCount";
    public static class LegendariumPopulation extends WorldSavedData {
        NBTTagCompound data = new NBTTagCompound();

        public LegendariumPopulation(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            data = tag;
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            for (String key : (Iterable<String>) data.func_150296_c()) {
                tag.setInteger(key, data.getInteger(key));
            }
        }

        private static String getName(World world) {
            final IChunkProvider chunkGenerator = world.provider.createChunkGenerator();
            return chunkGenerator.getClass().getName();
        }

        String isFree(World world) {
            String name = getName(world);
            return data.getString(getName(world));
        }

        void setOccupied(Coord src, EntityPlayer user, boolean v) {
            String who = "someone";
            if (user != null) who = user.getCommandSenderName();
            String worldName = getName(src.w);
            if (v) {
                data.setString(worldName, src.toShortString());
                Core.logInfo(who + " placed the hall of legends from " + src + "; worldName=" + worldName);
            } else if (data.hasKey(worldName)) {
                data.removeTag(worldName);
                Core.logInfo(who + " removed the hall of legends from " + src + "; worldName=" + worldName);
            }
            save();
        }

        static LegendariumPopulation load() {
            World w = MinecraftServer.getServer().worldServerForDimension(0);
            LegendariumPopulation ret = (LegendariumPopulation) w.loadItemData(LegendariumPopulation.class, legendariumCount);
            if (ret == null) {
                ret = new LegendariumPopulation(legendariumCount);
            }
            return ret;
        }

        public void save() {
            World w = MinecraftServer.getServer().worldServerForDimension(0);
            w.setItemData(legendariumCount, this);
            this.setDirty(true);
            w.perWorldStorage.saveAllData();
        }
    }

    @Override
    public boolean canPlaceAgainst(EntityPlayer player, Coord c, int side) {
        if (c.w.isRemote) return true;
        LegendariumPopulation population = LegendariumPopulation.load();
        final String free = population.isFree(c.w);
        if (!Strings.isNullOrEmpty(free)) {
            new Notice(c, "factorization.legendarium.occupied", free).sendTo(player);
            return false;
        }
        return true;
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side, float hitX, float hitY, float hitZ) {
        super.onPlacedBy(player, is, side, hitX, hitY, hitZ);
        if (worldObj.isRemote) return;
        LegendariumPopulation population = LegendariumPopulation.load();
        population.setOccupied(new Coord(this), player, true);
    }

    @Override
    protected boolean removedByPlayer(EntityPlayer player, boolean willHarvest) {
        if (!worldObj.isRemote) {
            LegendariumPopulation population = LegendariumPopulation.load();
            population.setOccupied(new Coord(this), player, false);
        }
        return super.removedByPlayer(player, willHarvest);
    }

    @Override
    protected void onRemove() {
        super.onRemove();
        LegendariumPopulation population = LegendariumPopulation.load();
        population.setOccupied(new Coord(this), null, false);
    }

    @Override
    public IIcon getIcon(ForgeDirection dir) {
        if (dir.offsetY == 0) return BlockIcons.artifact$legendarium_side;
        return BlockIcons.artifact$legendarium_top;
    }
}
