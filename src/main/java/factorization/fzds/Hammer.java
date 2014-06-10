package factorization.fzds;

import java.io.File;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldManager;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.relauncher.Side;
import factorization.common.FzConfig;
import factorization.fzds.api.IDeltaChunk;
import factorization.fzds.network.FzdsPacketRegistry;
import factorization.shared.Core;

@Mod(modid = Hammer.modId, name = Hammer.name, version = Core.version, dependencies = "required-after: " + Core.modId)
public class Hammer {
    final String[] Lore = new String[] {
            "At twilight's end, the shadow's crossed,",
            "A new world birthed, the elder lost.",
            "Yet on the morn we wake to find",
            "That mem'ry left so far behind.",
            "To deafened ears we ask, unseen,",
            "“Which is life and which the dream?”" // -- Aaron Diaz, http://dresdencodak.com/2006/03/02/zhuangzi/
    };
    
    
    public static final String modId = Core.modId + ".dimensionalSlice";
    public static final String name = "Factorization Dimensional Slices";
    public static Hammer instance; //@Instance seems to give the parent?
    @SidedProxy(clientSide = "factorization.fzds.HammerClientProxy", serverSide = "factorization.fzds.HammerProxy")
    public static HammerProxy proxy;
    public static boolean enabled;
    public static int dimensionID;
    public static World worldClient = null; //This is actually a WorldClient that is actually HammerClientProxy.HammerWorldClient
    public static double DSE_ChunkUpdateRangeSquared = Math.pow(16*8, 2); //This is actually set when the server starts
    public static int fzds_command_channel = 0;
    public static int max_fzds_grab_area = 16*16*80*4;
    
    static Set<IDeltaChunk> serverSlices = new WeakSet(), clientSlices = new WeakSet();
    
    public Hammer() {
        Hammer.instance = this;
        Core.loadBus(this);
    }
    
    final static HammerInfo hammerInfo = new HammerInfo();
    static final int channelWidth = 16*50;
    
    @EventHandler
    public void setup(FMLPreInitializationEvent event) {
        event.getModMetadata().parent = Core.modId;
        enabled = FzConfig.enable_dimension_slice;
        if (!enabled) {
            return;
        }
        
        EntityRegistry.registerModEntity(DimensionSliceEntity.class, "fzds", 1, this, 64, 1, true);
        EntityRegistry.registerModEntity(DseCollider.class, "fzdsC", 2, this, 64, 80000, false);
        
        //Create the hammer dimension
        dimensionID = FzConfig.dimension_slice_dimid;
        DimensionManager.registerProviderType(dimensionID, HammerWorldProvider.class, true);
        DimensionManager.registerDimension(dimensionID, dimensionID);
        File base = event.getSuggestedConfigurationFile().getParentFile();
        hammerInfo.setConfigFile(new File(base, "hammerChannels.cfg"));
        fzds_command_channel = hammerInfo.makeChannelFor(this, "cmd", fzds_command_channel, -1, "This channel is used for Slices created using the /fzds command");
        FzdsPacketRegistry.init();
        
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            //When the client logs in or out, we need to do something to the shadow world
            proxy.clientInit();
        }
        
        //This sets up saving how many IDs we've used
        Core.loadBus(hammerInfo);
        Core.loadBus(proxy);
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new DseRayTarget.ClickHandler());
        }
    }
    
    @EventHandler
    public void serverStartup(FMLServerStartingEvent event) {
        if (!enabled) {
            return;
        }
        event.registerServerCommand(new FZDSCommand());
        NORELEASE_initDimension(dimensionID);
        if (!DimensionManager.shouldLoadSpawn(dimensionID)) {
            throw new RuntimeException("hammerWorld is not loaded");
        }
        World hammerWorld = DimensionManager.getWorld(dimensionID);
        hammerWorld.addWorldAccess(new ShadowWorldAccess());
        int view_distance = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
        //the undeobfed method comes after "isPlayerWatchingChunk", also in uses of ServerConfigurationManager.getViewDistance()
        //It returns how many blocks are visible.
        DSE_ChunkUpdateRangeSquared = Math.pow(PlayerManager.getFurthestViewableBlock(view_distance) + 16*2, 2);
    }
    

    public static void NORELEASE_initDimension(int dim) {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null)
        {
            throw new RuntimeException("Cannot Hotload Dim: Overworld is not Loaded!");
        }
        try
        {
            DimensionManager.getProviderType(dim);
        }
        catch (Exception e)
        {
            System.err.println("Cannot Hotload Dim: " + e.getMessage());
            return; // If a provider hasn't been registered then we can't hotload the dim
        }
        MinecraftServer mcServer = overworld.func_73046_m();
        ISaveHandler savehandler = overworld.getSaveHandler();
        WorldSettings worldSettings = new WorldSettings(overworld.getWorldInfo());

        WorldServer world = new HammerWorldMulti(mcServer, savehandler, overworld.getWorldInfo().getWorldName(), dim, worldSettings, overworld, mcServer.theProfiler);
        world.addWorldAccess(new WorldManager(mcServer, world));
        MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
        if (!mcServer.isSinglePlayer())
        {
            world.getWorldInfo().setGameType(mcServer.getGameType());
        }

        mcServer.func_147139_a(mcServer.func_147135_j());
    }
    
    static class HammerWorldMulti extends WorldServerMulti {
        public HammerWorldMulti(MinecraftServer p_i45283_1_, ISaveHandler p_i45283_2_, String p_i45283_3_, int p_i45283_4_, WorldSettings p_i45283_5_, WorldServer p_i45283_6_, Profiler p_i45283_7_) {
            super(p_i45283_1_, p_i45283_2_, p_i45283_3_, p_i45283_4_, p_i45283_5_, p_i45283_6_, p_i45283_7_);
        }
        
        @Override
        public void tick() {
            super.tick();
        }
        
        @Override
        public void updateEntities() {
            super.updateEntities();
        }
        
    }
    
    @EventHandler
    public void saveInfo(FMLServerStoppingEvent event) {
        hammerInfo.saveCellAllocations();
        serverSlices.clear();
        clientSlices.clear();
    }
    
    public static Vec3 ent2vec(Entity ent) {
        return ent.worldObj.getWorldVec3Pool().getVecFromPool(ent.posX, ent.posY, ent.posZ);
    }
    
    @EventHandler
    public void modsLoaded(FMLPostInitializationEvent event) {
        double desired_radius = 16/2;
        if (FzConfig.force_max_entity_radius >= 0 && FzConfig.force_max_entity_radius < desired_radius) {
            desired_radius = FzConfig.force_max_entity_radius;
            Core.logFine("Using %f as FZDS's maximum entity radius; this could cause failure to collide with FZDS entities");
        }
        if (World.MAX_ENTITY_RADIUS < desired_radius) {
            Core.logFine("Enlarging World.MAX_ENTITY_RADIUS from %f to %f", World.MAX_ENTITY_RADIUS, desired_radius);
            Core.logFine("Please let the author know if this causes problems.");
            World.MAX_ENTITY_RADIUS = desired_radius;
        } else {
            Core.logFine("World.MAX_ENTITY_RADIUS was already set to %f, which is large enough for our purposes (%f)", World.MAX_ENTITY_RADIUS, desired_radius);
        }
    }
}
