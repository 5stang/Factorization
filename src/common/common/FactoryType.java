package factorization.common;

import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TileEntity;

public enum FactoryType {
    ROUTER(0, true, TileEntityRouter.class, "factory_router"), // Send/retrieve items from connected inventories
    @Deprecated
    CUTTER(1, false, TileEntityCutter.class, "factory_cutter"),
    MAKER(2, true, TileEntityMaker.class, "factory_maker"), // Create Craft Packets and put items into them.
    STAMPER(3, true, TileEntityStamper.class, "factory_stamper"), // Crafts craft packets, and outputs results
    @Deprecated
    QUEUE(4, false, TileEntityQueue.class, "factory_queue"), // Store up to 16 items in a list, without merging.
    BARREL(5, false, TileEntityBarrel.class, "factory_barrel"), // Store huge quantities of identical items
    LAMP(6, false, TileEntityWrathLamp.class, "factory_lamp"), //spawn a bunch of AIR blocks around and below
    //7 -- this was the BlockDarkIron, which got moved.
    PACKAGER(8, true, STAMPER.gui, TileEntityPackager.class, "factory_packager"), //crafts its input as a 3x3 or 2x2
    SENTRYDEMON(9, false, TileEntityWatchDemon.class, "factory_watchdemon"), //load a chunk
    WRATHFIRE(10, false, TileEntityWrathFire.class, "factory_fire"), //burn things
    SLAGFURNACE(11, true, TileEntitySlagFurnace.class, "factory_slag"), //get extra ore output
    BATTERY(12, false, TileEntityBattery.class, "factory_battery"),
    SOLARTURBINE(13, false, TileEntitySolarTurbine.class, "factory_solarturbine"), //sun-powered steam turbine
    LEADWIRE(14, false, TileEntityWire.class, "factory_solder"),
    HEATER(15, false, TileEntityHeater.class, "factory_heater"), //work furnaces without fuel
    MIRROR(16, false, TileEntityMirror.class, "factory_mirror"), //reflect sunlight onto IReflectionTargets
    GRINDER(17, true, TileEntityGrinder.class, "factory_grinder"), //grind
    MIXER(18, true, TileEntityMixer.class, "factory_mixer"), //mix
    CRYSTALLIZER(19, true, TileEntityCrystallizer.class, "factory_crystal"), //grow metallic crystals

    POCKETCRAFTGUI(101, true),
    MECHATABLEGUICONFIG(102, true), //Mecha-armor editor
    NULLGUI(103, true), ;

    final public int md;
    final public int gui;
    final public boolean hasGui;
    final private Class clazz;
    final public String te_id;

    static class mapper {
        //bluh java
        static FactoryType mapping[] = new FactoryType[128];
    }

    FactoryType(int metadata, boolean use_gui, int gui_id, Class clazz, String name) {
        md = metadata;
        if (use_gui) {
            gui = gui_id;
        } else {
            gui = -1;
        }
        hasGui = use_gui;
        assert mapper.mapping[md] == null;
        mapper.mapping[md] = this;
        this.clazz = clazz;
        this.te_id = name;
    }

    FactoryType(int md, boolean use_gui, Class clazz, String name) {
        this(md, use_gui, md, clazz, name);
    }

    FactoryType(int md, boolean use_gui) {
        this(md, use_gui, md, null, null);
    }

    TileEntityCommon makeTileEntity() {
        if (clazz == null) {
            System.err.println("Note: " + this + " is a FactoryType with no associated TE");
            return null;
        }
        try {
            return (TileEntityCommon) clazz.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isInstance(TileEntityFactorization ent) {
        if (ent == null) {
            return false;
        }
        return ent.getFactoryType() == this;
    }

    public boolean is(int md) {
        return md == this.md;
    }

    public static FactoryType fromMd(int md) {
        return mapper.mapping[md];
    }

    ItemStack itemStack(String name) {
        ItemStack ret = new ItemStack(Core.registry.item_factorization, 1, this.md);
        Core.proxy.addName(ret, name);
        return ret;
    }

    public static boolean isDark(int md) {
        return md == ROUTER.md || md == LAMP.md;
    }

    public static void registerTileEntities() {
        for (FactoryType ft : FactoryType.values()) {
            if (ft.clazz == null || ft.te_id == null) {
                continue;
            }
            ModLoader.registerTileEntity(ft.clazz, ft.te_id);
        }
    }
}