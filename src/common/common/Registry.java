package factorization.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;

import net.minecraft.src.Block;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IInventory;
import net.minecraft.src.IRecipe;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MapColor;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProviderHell;
import net.minecraft.src.forge.ICraftingHandler;
import net.minecraft.src.forge.IOreHandler;
import net.minecraft.src.forge.IPickupHandler;
import net.minecraft.src.forge.MinecraftForge;
import net.minecraft.src.forge.oredict.OreDictionary;
import factorization.api.IActOnCraft;

public class Registry implements IOreHandler, IPickupHandler, ICraftingHandler {
    static public final int MechaKeyCount = 3;

    public ItemFactorization item_factorization;
    public ItemBlockResource item_resource;
    public BlockFactorization factory_block;
    public BlockLightAir lightair_block;
    public BlockResource resource_block;

    public ItemStack cutter_item, router_item, maker_item, stamper_item, packager_item,
            barrel_item,
            queue_item, lamp_item, air_item, sentrydemon_item,
            slagfurnace_item, battery_item, solar_turbine_item, heater_item, mirror_item_hidden,
            leadwire_item;
    public ItemStack silver_ore_item, silver_block_item, lead_block_item,
            dark_iron_block_item, mechaworkshop_item;
    public ItemStack is_factory, is_lamp, is_lightair;
    public ItemCraft item_craft;
    public ItemBagOfHolding bag_of_holding;
    public ItemPocketTable pocket_table;
    public ItemDemon tiny_demon, bound_tiny_demon;
    public ItemWandOfCooling wand_of_cooling;
    public ItemCraftingComponent diamond_shard;
    public IRecipe diamond_shard_recipe;
    public ItemStack diamond_shard_packet;
    public IRecipe boh_upgrade_recipe;
    public ItemWrathIgniter wrath_igniter;
    public ItemCraftingComponent silver_ingot, lead_ingot, dark_iron;
    public ItemCraftingComponent mecha_chasis;
    public MechaArmor mecha_head, mecha_chest, mecha_leg, mecha_foot;
    public MechaBuoyantBarrel mecha_buoyant_barrel;
    public MechaCobblestoneDrive mecha_cobble_drive;
    public MechaMountedPiston mecha_mounted_piston;
    public ItemMachineUpgrade router_item_filter, router_machine_filter, router_speed,
            router_thorough, router_throughput;
    public ItemStack fake_is;
    public ItemCraftingComponent acid, magnet, insulated_coil, motor, fan;
    public ItemChargeMeter charge_meter;
    public ItemMirror mirror;

    public Material materialMachine = new Material(MapColor.ironColor);

    void makeBlocks() {
        factory_block = new BlockFactorization(Core.factory_block_id);
        lightair_block = new BlockLightAir(Core.lightair_id);
        resource_block = new BlockResource(Core.resource_id);
        is_factory = new ItemStack(factory_block);
        is_lightair = new ItemStack(lightair_block);

        ModLoader.registerBlock(factory_block, ItemFactorization.class);
        ModLoader.registerBlock(lightair_block);
        ModLoader.registerBlock(resource_block, ItemBlockResource.class);
    }

    void registerSimpleTileEntities() {
        FactoryType.registerTileEntities();
        //TileEntity renderers are registered in the client's mod_Factorization

        ModLoader.registerEntityID(TileEntityWrathLamp.RelightTask.class, "factory_relight_task", Core.entity_relight_task_id);
    }

    private void addName(Object what, String name) {
        Core.instance.addName(what, name);
    }

    HashSet<Integer> added_ids = new HashSet();

    public int itemID(String name, int default_id) {
        int id = Integer.parseInt(Core.instance.config.getOrCreateIntProperty(name, "item", default_id).value);
        if (added_ids.contains(default_id)) {
            throw new RuntimeException("Default ID already used: " + default_id);
        }
        if (Item.itemsList[id] != null) {
            throw new RuntimeException("Item ID conflict: " + id + " is already taken by "
                    + Item.itemsList[id] + "; tried to use it for Factorization " + name);
        }
        added_ids.add(default_id);
        return id;
    }

    void makeRecipes() {
        mecha_head.setSlotCount(5);
        mecha_chest.setSlotCount(8);
        mecha_leg.setSlotCount(6);
        mecha_foot.setSlotCount(4);

        // *** blocks ***
        //item_factorization = new ItemFactorization();
        item_factorization = (ItemFactorization) Item.itemsList[Core.factory_block_id];
        item_resource = (ItemBlockResource) Item.itemsList[resource_block.blockID];

        //BlockFactorization stuff
        router_item = FactoryType.ROUTER.itemStack("Router");
        cutter_item = FactoryType.CUTTER.itemStack("Stack Cutter");
        barrel_item = FactoryType.BARREL.itemStack("Barrel");
        queue_item = FactoryType.QUEUE.itemStack("Queue");
        maker_item = FactoryType.MAKER.itemStack("Craftpacket Maker");
        stamper_item = FactoryType.STAMPER.itemStack("Craftpacket Stamper");
        lamp_item = FactoryType.LAMP.itemStack("Wrathlamp");
        packager_item = FactoryType.PACKAGER.itemStack("Packager");
        sentrydemon_item = FactoryType.SENTRYDEMON.itemStack("Sentry Demon");
        slagfurnace_item = FactoryType.SLAGFURNACE.itemStack("Slag Furnace");
        battery_item = FactoryType.BATTERY.itemStack("Battery");
        solar_turbine_item = FactoryType.SOLARTURBINE.itemStack("Solar Turbine");
        heater_item = FactoryType.HEATER.itemStack("Furnace Heater");
        mirror_item_hidden = FactoryType.MIRROR.itemStack("Reflective Mirror");
        leadwire_item = FactoryType.LEADWIRE.itemStack("Lead Wire");


        //BlockResource stuff
        silver_ore_item = ResourceType.SILVERORE.itemStack("Silver Ore");
        silver_block_item = ResourceType.SILVERBLOCK.itemStack("Block of Silver");
        lead_block_item = ResourceType.LEADBLOCK.itemStack("Block of Lead");
        dark_iron_block_item = ResourceType.DARKIRONBLOCK.itemStack("Block of Dark Iron");
        mechaworkshop_item = ResourceType.MECHAMODDER.itemStack("Mecha-Modder");

        //Factorization Items
        //Dark iron
        dark_iron = new ItemCraftingComponent(itemID("darkIron", 9008), "item.darkiron", 3 * 16 + 2);
        addName(dark_iron, "Dark Iron Ingot");
        ModLoader.addShapelessRecipe(new ItemStack(dark_iron, 4), dark_iron_block_item);
        ModLoader.addRecipe(dark_iron_block_item,
                "II",
                "II",
                'I', dark_iron);

        // Craft packet
        item_craft = new ItemCraft(itemID("itemCraftId", 9000));
        addName(item_craft, "Craftpacket");

        // Bag of holding
        bag_of_holding = new ItemBagOfHolding(itemID("bagOfHolding", 9001));
        ItemStack BOH = new ItemStack(bag_of_holding, 1);
        //bag_of_holding.init(BOH); // don't want to show the 'contains 3 columns' bit
        addName(bag_of_holding, "Bag of Holding");
        ModLoader.addRecipe(BOH,
                "LOL",
                "ILI",
                " I ",
                'I', dark_iron,
                'O', Item.enderPearl,
                'L', Item.leather); // LOL!
        ModLoader.addShapelessRecipe(BOH, BOH, dark_iron, Item.enderPearl, Item.leather);
        boh_upgrade_recipe = FactorizationUtil.createShapelessRecipe(BOH, BOH, dark_iron, Item.enderPearl, Item.leather);
        // Pocket Crafting Table (pocket table)
        pocket_table = new ItemPocketTable(itemID("pocketCraftingTable", 9002));
        addName(pocket_table, "Pocket Crafting Table");
        ModLoader.addRecipe(new ItemStack(pocket_table),
                "# ",
                " |",
                '#', Block.workbench,
                '|', Item.stick);

        // tiny demons
        bound_tiny_demon = new ItemDemon(itemID("boundTinyDemon", 9003));
        tiny_demon = new ItemDemon(itemID("tinyDemon", 9004));
        addName(bound_tiny_demon, "Bound Tiny Demon");
        addName(tiny_demon, "Tiny Demon");
        ModLoader.addShapelessRecipe(new ItemStack(bound_tiny_demon), tiny_demon, Item.silk);

        // wand of cooling
        wand_of_cooling = new ItemWandOfCooling(itemID("wandOfCooling", 9005));
        addName(wand_of_cooling, "Wand of Cooling");
        ModLoader.addRecipe(new ItemStack(wand_of_cooling),
                " OD",
                " FO",
                "I  ",
                'O', Block.obsidian,
                'D', bound_tiny_demon,
                'F', Item.flintAndSteel,
                'I', Item.ingotIron);
        ModLoader.addRecipe(new ItemStack(wand_of_cooling),
                "DO ",
                "OF ",
                "  I",
                'O', Block.obsidian,
                'D', bound_tiny_demon,
                'F', Item.flintAndSteel,
                'I', Item.ingotIron);

        // diamond shard
        diamond_shard = new ItemCraftingComponent(itemID("diamondShard", 9006), "item.diamondshard", 3 * 16);
        addName(diamond_shard, "Diamond Shard");
        diamond_shard_recipe = FactorizationUtil.createShapedRecipe(new ItemStack(diamond_shard, 12),
                "OTO",
                "TDT",
                "OTO",
                'O', Block.obsidian,
                'T', Block.tnt,
                'D', Block.blockDiamond);
        ItemCraft.addStamperRecipe(diamond_shard_recipe);
        diamond_shard_packet = new ItemStack(item_craft);
        diamond_shard_packet.setItemDamage(0xFF);
        for (int i : new int[] { 0, 2, 6, 8 }) {
            item_craft.addItem(diamond_shard_packet, i, new ItemStack(Block.obsidian));
        }
        for (int i : new int[] { 1, 3, 5, 7 }) {
            item_craft.addItem(diamond_shard_packet, i, new ItemStack(Block.tnt));
        }
        item_craft.addItem(diamond_shard_packet, 4, new ItemStack(Block.blockDiamond));
        ModLoader.addRecipe(diamond_shard_packet,
                "OTO",
                "TDT",
                "OTO",
                'O', Block.obsidian,
                'T', Block.tnt,
                'D', Block.blockDiamond);


        //wrathfire igniter
        wrath_igniter = new ItemWrathIgniter(itemID("wrathIgniter", 9007));
        addName(wrath_igniter, "Wrath Igniter");
        ModLoader.addRecipe(new ItemStack(wrath_igniter),
                "D ",
                " B",
                'D', diamond_shard,
                'B', Block.netherBrick);
        ModLoader.addRecipe(new ItemStack(wrath_igniter),
                "D ",
                " B",
                'D', Item.diamond,
                'B', Block.netherBrick);

        //Resources
        lead_ingot = new ItemCraftingComponent(itemID("leadIngot", 9014), "Lead Ingot", 16 * 3 + 3);
        silver_ingot = new ItemCraftingComponent(itemID("silverIngot", 9015), "Silver Ingot", 16 * 3 + 4);
        ModLoader.addRecipe(new ItemStack(lead_ingot, 9), "#", '#', lead_block_item);
        ModLoader.addRecipe(new ItemStack(silver_ingot, 9), "#", '#', silver_block_item);
        MinecraftForge.registerOre("oreSilver", silver_ore_item);
        MinecraftForge.registerOre("ingotSilver", new ItemStack(silver_ingot));
        MinecraftForge.registerOre("ingotLead", new ItemStack(lead_ingot));
        addName(lead_ingot, "Lead Ingot");
        addName(silver_ingot, "Silver Ingot");
        FurnaceRecipes.smelting().addSmelting(resource_block.blockID, 0 /* MD for silver */, new ItemStack(silver_ingot));

        //mecha armor
        mecha_chasis = new ItemCraftingComponent(itemID("mechaChasis", 9009), "item.mechachasis", 5);
        addName(mecha_chasis, "Mecha-Chassis");
        //mecha_ITEMS created in make_recipes_side()
        //Mecha-armor uses up to Item ID 9013.
        addName(mecha_head, "Mecha-Helmet");
        addName(mecha_chest, "Mecha-Chestplate");
        addName(mecha_leg, "Mecha-Leggings");
        addName(mecha_foot, "Mecha-Boots");
        ModLoader.addRecipe(new ItemStack(mecha_chasis),
                "III",
                "InI",
                "III",
                'I', Item.ingotIron,
                'n', Item.goldNugget
                );
        ModLoader.addRecipe(new ItemStack(mecha_head),
                "###",
                "# #",
                '#', mecha_chasis
                );
        ModLoader.addRecipe(new ItemStack(mecha_chest),
                "# #",
                "###",
                "###",
                '#', mecha_chasis
                );
        ModLoader.addRecipe(new ItemStack(mecha_leg),
                "###",
                "# #",
                "# #",
                '#', mecha_chasis
                );
        ModLoader.addRecipe(new ItemStack(mecha_foot),
                "# #",
                "# #",
                '#', mecha_chasis
                );
        //mecha armor upgrades
        mecha_buoyant_barrel = new MechaBuoyantBarrel(itemID("mechaBouyantBarrel", 9021));
        mecha_cobble_drive = new MechaCobblestoneDrive(itemID("mechaCobbleDrive", 9022));
        mecha_mounted_piston = new MechaMountedPiston(itemID("mechaMountedPiston", 9023));
        addName(mecha_buoyant_barrel, "Mecha-Upgrade: Buoyant Barrel");
        addName(mecha_cobble_drive, "Mecha-Upgrade: Cobblestone Drive");
        addName(mecha_mounted_piston, "Mecha-Upgrade: Mounted Piston");

        ModLoader.addRecipe(new ItemStack(mecha_buoyant_barrel),
                "W_W",
                "PBP",
                "WVW",
                'W', Block.planks,
                '_', Block.pressurePlatePlanks,
                'P', Block.pistonBase,
                'B', barrel_item,
                'V', Item.boat
                );

        ItemStack is_cobble_drive = new ItemStack(mecha_cobble_drive);
        ModLoader.addRecipe(is_cobble_drive,
                "OPO",
                "WTL",
                "OOO",
                'O', Block.obsidian,
                'P', Block.pistonBase,
                'W', Item.bucketWater,
                'T', Item.pickaxeSteel,
                'L', Item.bucketLava
                );
        ModLoader.addRecipe(is_cobble_drive,
                "OPO",
                "LTW",
                "OOO",
                'O', Block.obsidian,
                'P', Block.pistonBase,
                'W', Item.bucketWater,
                'T', Item.pickaxeSteel,
                'L', Item.bucketLava
                );
        ModLoader.addRecipe(new ItemStack(mecha_mounted_piston),
                "CNC",
                "LSL",
                "CCC",
                'C', Block.cobblestone,
                'S', Block.pistonStickyBase,
                'N', Block.pistonBase,
                'L', Block.lever
                );

        // BlockFactorization recipes
        // router
        ModLoader.addRecipe(router_item,
                "MMM",
                "oIO",
                "MMM",
                'M', dark_iron,
                'I', Item.egg,
                'o', Item.enderPearl,
                'O', Item.eyeOfEnder);
        ModLoader.addRecipe(router_item,
                "MMM",
                "OIo",
                "MMM",
                'M', dark_iron,
                'I', Item.egg,
                'o', Item.enderPearl,
                'O', Item.eyeOfEnder);
        // router upgrades
        router_item_filter = new ItemMachineUpgrade(itemID("routerItemFilter", 9016), "Router Upgrade: Item Filter", FactoryType.ROUTER, 0);
        router_machine_filter = new ItemMachineUpgrade(itemID("routerMachineFilter", 9017), "Router Upgrade: Machine Filter", FactoryType.ROUTER, 1);
        router_speed = new ItemMachineUpgrade(itemID("routerSpeed", 9018), "Router Upgrade: Speed Boost", FactoryType.ROUTER, 2);
        router_thorough = new ItemMachineUpgrade(itemID("routerThorough", 9019), "Router Upgrade: Thoroughness", FactoryType.ROUTER, 3);
        router_throughput = new ItemMachineUpgrade(itemID("routerThroughput", 9020), "Router Upgrade: Bandwidth", FactoryType.ROUTER, 4);
        ModLoader.addRecipe(new ItemStack(router_item_filter),
                "ITI",
                "GDG",
                "ICI",
                'I', dark_iron,
                'T', Block.torchRedstoneActive,
                'D', bound_tiny_demon,
                'G', Item.ingotGold,
                'C', Block.chest);

        for (ItemStack ingotSilver : OreDictionary.getOres("ingotSilver")) {
            ModLoader.addRecipe(new ItemStack(router_machine_filter),
                    "ITI",
                    "SDS",
                    "IBI",
                    'I', dark_iron,
                    'T', Block.torchRedstoneActive,
                    'D', bound_tiny_demon,
                    'S', ingotSilver,
                    'B', Item.book);
        }
        ModLoader.addRecipe(new ItemStack(router_speed),
                "ISI",
                "SCS",
                "ISI",
                'I', dark_iron,
                'S', Item.sugar,
                'C', Item.cake);
        ModLoader.addRecipe(new ItemStack(router_thorough),
                "ISI",
                "SSS",
                "ISI",
                'I', dark_iron,
                'S', Block.slowSand);
        ModLoader.addRecipe(new ItemStack(router_throughput),
                "IBI",
                "B!B",
                "IBI",
                'I', dark_iron,
                'B', Item.blazePowder,
                '!', Item.egg);

        // Cutter
        //TODO: Remove the cutter
        //		ModLoader.addRecipe(cutter_item,
        //				"> P",
        //				" > ",
        //				"> P",
        //				'>', Item.shears,
        //				'P', Block.pistonBase);

        // Barrel
        ModLoader.addRecipe(barrel_item,
                "W-W",
                "W W",
                "WWW",
                'W', Block.wood,
                '-', new ItemStack(Block.stairSingle, 1, 2));

        // Queue
        //		ModLoader.addRecipe(queue_item,
        //				" -P",
        //				" C_",
        //				"  P",
        //				'-', Block.pressurePlatePlanks,
        //				'P', Block.pistonBase,
        //				'_', new ItemStack(Block.stairSingle, 1, 0),
        //				'C', Block.chest);

        // Craft maker
        ModLoader.addRecipe(maker_item,
                "#p#",
                "# #",
                "#C#",
                '#', Block.cobblestone,
                'p', Block.pistonBase,
                'C', Block.workbench);
        fake_is = maker_item;

        // Craft stamper
        ModLoader.addRecipe(stamper_item,
                "#p#",
                "III",
                "#C#",
                '#', Block.cobblestone,
                'p', Block.pistonBase,
                'I', Item.ingotIron,
                'C', Block.workbench);

        //Packager
        ModLoader.addRecipe(packager_item,
                "#M#",
                "# #",
                "#S#",
                '#', Block.cobblestone,
                'M', maker_item,
                'S', stamper_item);

        // Wrath lamp
        for (ItemStack ingotLead : OreDictionary.getOres("ingotLead")) {
        ModLoader.addRecipe(lamp_item,
                "LIL",
                "GWG",
                "LIL",
                'I', dark_iron,
                    'L', ingotLead,
                'G', Block.thinGlass,
                'W', new ItemStack(wrath_igniter, 1, -1));
        }

        //sentry demon
        ModLoader.addRecipe(sentrydemon_item,
                "###",
                "#D#",
                "###",
                '#', Block.fenceIron,
                'D', bound_tiny_demon);

        //Slag furnace
        ModLoader.addRecipe(slagfurnace_item,
                "CFC",
                "C C",
                "CFC",
                'C', Block.cobblestone,
                'F', Block.stoneOvenIdle
                );
        createOreProcessingPath(new ItemStack(Block.oreIron), new ItemStack(Item.ingotIron));
        createOreProcessingPath(new ItemStack(Block.oreGold), new ItemStack(Item.ingotGold));
        for (Block redstone : Arrays.asList(Block.oreRedstone, Block.oreRedstoneGlowing)) {
            TileEntitySlagFurnace.SlagRecipes.register(redstone, 5.8F, Item.redstone, 0.2F, Block.stone);
            //most ores give 0.4F stone, but redstone is dense.
            //mining redstone normally gives 4 to 6 ore. 5.8F should get you a slightly better yield.
        }

        //mecha-workshop
        ModLoader.addRecipe(mechaworkshop_item,
                "MCM",
                "i i",
                "i i",
                'C', Block.workbench,
                'M', mecha_chasis,
                'i', Item.ingotIron
                );

        //electricity crafting components
        acid = new ItemAcidBottle(itemID("acid", 9024), "Sulfuric Acid", 16 * 3 + 5);
        magnet = new ItemCraftingComponent(itemID("magnet", 9025), "Magnet", 16 * 3 + 6);
        insulated_coil = new ItemCraftingComponent(itemID("coil", 9026), "Insulated Coil", 16 * 3 + 7);
        motor = new ItemCraftingComponent(itemID("motor", 9027), "Motor", 16 * 3 + 8);
        fan = new ItemCraftingComponent(itemID("fan", 9028), "Fan", 16 * 3 + 9);
        charge_meter = new ItemChargeMeter(itemID("chargemeter", 9029));
        mirror = new ItemMirror(itemID("mirror", 9030));

        ModLoader.addShapelessRecipe(new ItemStack(acid), Item.gunpowder, Item.gunpowder, Item.coal, Item.potion);
        
        ModLoader.addRecipe(new ItemStack(fan),
                "I I",
                " I ",
                "I I",
                'I', Item.ingotIron);
        
        ModLoader.addRecipe(solar_turbine_item,
                "###",
                "#F#",
                "#M#",
                '#', Block.glass,
                'F', fan,
                'M', motor);
        ModLoader.addRecipe(new ItemStack(charge_meter),
                "WSW",
                "W/W",
                "LIL",
                'W', Block.planks,
                'S', Item.sign,
                '/', Item.stick,
                'L', lead_ingot,
                'I', Item.ingotIron
                );
        ModLoader.addRecipe(battery_item,
                "ILI",
                "LAL",
                "ILI",
                'I', Item.ingotIron,
                'L', lead_ingot,
                'A', acid);
        ModLoader.addRecipe(heater_item,
                "CCC",
                "L L",
                "CCC",
                'C', insulated_coil,
                'L', lead_ingot);
        ModLoader.addRecipe(new ItemStack(insulated_coil),
                "LLL",
                "LCL",
                "LLL",
                'L', lead_ingot,
                'C', Block.blockClay);
        ModLoader.addRecipe(new ItemStack(motor),
                "CIC",
                "CMC",
                "LIL",
                'C', insulated_coil,
                'M', magnet,
                'L', lead_ingot,
                'I', Item.ingotIron);
        for (ItemStack silver : OreDictionary.getOres("ingotSilver")) {
            ModLoader.addRecipe(new ItemStack(mirror),
                    "SSS",
                    "S#S",
                    "SSS",
                    'S', silver,
                    '#', Block.thinGlass);
        }
        ItemStack with_8 = leadwire_item.copy();
        with_8.stackSize = 8;
        ModLoader.addRecipe(with_8,
                "   ",
                "LLL",
                "LLL",
                'L', lead_ingot);
    }

    void createOreProcessingPath(ItemStack ore, ItemStack ingot) {
        //Smelt: 1.0
        //Slag: 1.2
        //Grind -> Smelt: 1.4
        //Grind -> Slag -> Smelt: 1.6
        //Grind -> Wash -> Smelt: 1.8
        //Grind -> Wash -> Slag -> Smelt: 2.0
        //Grind -> Wash -> Slag -> Crystallize -> Smelt: 3.0
        //Crystallization will be a very slow & intensive & (energy) expensive.

        TileEntitySlagFurnace.SlagRecipes.register(ore, 1.2F, ingot, 0.4F, Block.stone);
        //TODO: Make all the machines for the upgrade path...

    }

    void addDictOres() {
        for (String oreClass : Arrays.asList("oreCopper", "oreTin")) {
            ItemStack bestOre = null;
            ItemStack bestIngot = null;
            if (MinecraftForge.getOreClass(oreClass) == null) {
                continue;
            }
            for (ItemStack ore : MinecraftForge.getOreClass(oreClass)) {
                ItemStack smeltsTo = FurnaceRecipes.smelting().getSmeltingResult(ore);
                if (smeltsTo == null) {
                    continue;
                }
                if (bestOre == null || ore.getItemDamage() != 0) {
                    bestOre = ore;
                    bestIngot = smeltsTo;
                }
            }
            if (bestIngot == null) {
                continue;
            }
            for (ItemStack ore : MinecraftForge.getOreClass(oreClass)) {
                createOreProcessingPath(ore, bestIngot);
            }

        }
    }

    @Override
    public void registerOre(String oreClass, ItemStack ore) {
        if (oreClass.equals("ingotLead")) {
            //NOTE: I think it'd be cool to have the recipe for lead blocks be 5x5 instead of 3x3...
            ModLoader.addRecipe(lead_block_item, "###", "###", "###", '#', ore);
            return;
        }
        if (oreClass.equals("ingotSilver")) {
            ModLoader.addRecipe(silver_block_item, "###", "###", "###", '#', ore);
            return;
        }
        if (oreClass.equals("oreSilver")) {
            TileEntitySlagFurnace.SlagRecipes.register(ore, 0.9F, new ItemStack(silver_ingot), 1.4F, new ItemStack(lead_ingot));
            return;
        }
    }

    public void setToolEffectiveness() {
        for (String tool : new String[] { "pickaxe", "axe", "shovel" }) {
            MinecraftForge.removeBlockEffectiveness(factory_block, tool);
            MinecraftForge.removeBlockEffectiveness(resource_block, tool);
        }
        BlockClass.DarkIron.harvest("pickaxe", 2);
        BlockClass.Barrel.harvest("axe", 1);
        BlockClass.Machine.harvest("pickaxe", 0);
        BlockClass.Cage.harvest("pickaxe", 1);
        MinecraftForge.setBlockHarvestLevel(resource_block, "pickaxe", 2);
    }

    public void onTickPlayer(EntityPlayer player) {
        MechaArmor.onTickPlayer(player);
        if (!Core.instance.isCannonical(player.worldObj)) {
            //we need mecha-armor to tick on the client. >_>
            return;
        }
        // If in a GUI and holding a demon, BITE!
        // Otherwise, if not in a GUI and holding in hand... BITE!
        if (player.inventory.getItemStack() != null) {
            tiny_demon.playerHolding(player.inventory.getItemStack(), player);
        } else {
            tiny_demon.playerHolding(player.inventory.getCurrentItem(), player);
        }
    }

    int spawn_delay = 0;
    boolean reg_ore = false;

    public void onTickWorld(World world) {
        if (!reg_ore) {
            //XXX TODO: Move this somewhere proper; after all our favorite mods have loaded
            addDictOres();
            reg_ore = true;
        }
        //NOTE: This might bug out if worlds don't tick at the same rate or something! Or if they're in different threads!
        //(Like THAT would ever happen, ah ha ha ha ha ha ha ha ha ha ha ha ha ha.)
        TileEntityWrathLamp.handleAirUpdates();
        TileEntityWrathFire.updateCount = 0;
        if (world.worldProvider instanceof WorldProviderHell) {
            spawn_delay -= 1;
            if (spawn_delay <= 0) {
                try {
                    ItemDemon.spawnDemons(world);
                } catch (ConcurrentModificationException e) {
                    //This can happen while loading?
                    System.err.print("This error occured while attempting to spawn demons: ");
                    e.printStackTrace();
                }
                spawn_delay = 20 * 60;
            }
        }
        TileEntityWatchDemon.worldTick(world);
    }

    @Override
    public boolean onItemPickup(EntityPlayer player, EntityItem item) {
        if (item == null || item.item == null || item.item.stackSize == 0) {
            return true;
        }
        if (player.isDead) {
            return true;
        }
        ItemStack is = item.item;
        InventoryPlayer inv = player.inventory;
        // If the item would take a new slot in our inventory, look for bags of
        // holding to put it into
        int remaining_size = is.stackSize;
        int free_slots = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack here = inv.getStackInSlot(i);
            if (here == null) {
                free_slots += 1;
                continue;
            }
            if (is.isItemEqual(here)) {
                int free = here.getMaxStackSize() - here.stackSize;
                remaining_size -= free;
                if (remaining_size <= 0) {
                    break;
                }
            }
        }
        if (remaining_size > 0) {
            // find the BOHs
            ArrayList<ItemStack> bags = new ArrayList<ItemStack>();
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack here = inv.getStackInSlot(i);
                if (here != null && here.getItem() == bag_of_holding) {
                    bags.add(here);
                }
            }
            // For each row
            boolean success = false;
            for (ItemStack bag : bags) {
                if (is.stackSize < 0) {
                    break;
                }
                success = bag_of_holding.insertItem(bag, is);
            }
            if (success) {
                Sound.bagSlurp.playAt(player);
            }
        }
        Core.instance.pokePocketCrafting();
        tiny_demon.bitePlayer(is, player, true);
        return true;
    }

    @Override
    public void onTakenFromCrafting(EntityPlayer player, ItemStack stack, IInventory craftMatrix) {
        for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
            ItemStack here = craftMatrix.getStackInSlot(i);
            if (here == null) {
                continue;
            }
            Item item = here.getItem();
            if (item instanceof IActOnCraft) {
                ((IActOnCraft) item).onCraft(here, craftMatrix, i, stack, player);
            }
        }

        if (stack.getItem() == item_craft && stack.getItemDamage() == diamond_shard_packet.getItemDamage()) {
            stack.setTagCompound((NBTTagCompound) diamond_shard_packet.getTagCompound().copy());
            stack.setItemDamage(1);
        }
    }
}
