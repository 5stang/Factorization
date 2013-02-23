package factorization.fzds;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.Mod.ServerStopping;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.common.Core;
import factorization.common.WeakSet;

@Mod(modid = Hammer.modId, name = Hammer.name, version = Core.version, dependencies = "required-after: " + Core.modId)
@NetworkMod(clientSideRequired = true, tinyPacketHandler = HammerNet.class)
public class Hammer {
    static final String lore = "Anvil's Hammer";
    
    
    public static final String modId = Core.modId + ".dimensionalSlice";
    public static final String name = "Factorization Dimensional Slices";
    public static Hammer instance; //@Instance seems to give the parent?
    @SidedProxy(clientSide = "factorization.fzds.HammerClientProxy", serverSide = "factorization.fzds.HammerProxy")
    public static HammerProxy proxy;
    public static boolean enabled;
    public static int dimensionID;
    public static World worldClient = null; //This is actually a WorldClient that is actually HammerClientProxy.HammerWorldClient
    public static double DSE_ChunkUpdateRangeSquared = Math.pow(16*8, 2); //This is actually set when the server starts
    
    private static Set<DimensionSliceEntity> serverSlices = new WeakSet(), clientSlices = new WeakSet();
    static Set<DimensionSliceEntity> getSlices(World w) {
        if (w == null) {
            if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
                return clientSlices;
            } else {
                return serverSlices;
            }
        }
        return w.isRemote ? clientSlices : serverSlices;
    }
    
    public Hammer() {
        Hammer.instance = this;
    }
    
    public static World getClientShadowWorld() {
        return worldClient;
    }
    
    public static World getServerShadowWorld() {
        return DimensionManager.getWorld(dimensionID);
    }
    
    public static World getClientRealWorld() {
        return proxy.getClientRealWorld();
    }
    
    /***
     * @return the thread-appropriate shadow world
     */
    public static World getWorld(World realWorld) {
        if (realWorld == null) {
            return FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT ? getClientShadowWorld() : getServerShadowWorld();
        }
        return realWorld.isRemote ? getClientShadowWorld() : getServerShadowWorld();
    }
    
    static Coord getCornerForId(World realWorld, int cellId) {
        int cellSize = (cellWidth + wallWidth)*16;
        return new Coord(getWorld(realWorld), cellSize*cellId, 0, 0);
    }
    
    static int getIdFromCoord(Coord c) {
        //You're not allowed to write getIdFromXCoordinate
        if (c.x < 0 || c.z < 0 || c.z > cellWidth*16) {
            return -1;
        }
        int depth_per_cell = (cellWidth + wallWidth)*16;
        return c.x / depth_per_cell;
    }
    
    @Deprecated
    public static Chunk[] getChunks(DimensionSliceEntity dse) {
        Coord corner = dse.getCorner();
        int i = 0;
        for (int dx = 0; dx < cellWidth; dx++) {
            for (int dz = 0; dz < cellWidth; dz++) {
                hChunks[i++] = corner.w.getChunkFromBlockCoords(corner.x + 16*dx, corner.z + 16*dz);
            }
        }
        return hChunks;
    }
    
    public static DimensionSliceEntity allocateSlice(World spawnWorld) {
        return spawnSlice(spawnWorld, hammerInfo.takeCellId());
    }
    
    public static DimensionSliceEntity spawnSlice(World spawnWorld, int cellId) {
        return new DimensionSliceEntity(spawnWorld, cellId);
    }
    
    private final static EnumSet<TickType> serverTicks = EnumSet.of(TickType.SERVER);
    final static HammerInfo hammerInfo = new HammerInfo();
    //each cell is a few chunks wide, with chunks of bedrock between.
    static final int cellWidth = 3; //TODO: Make this more generic; we don't want limitations on size.
    static final int cellHeight = cellWidth;
    static final int wallWidth = 16;
    static final int wallHeight = 16*8; //16*8 is the minimum or something. (For the Chunk constructor that we're using.) I'd rather go with 16*4. Meh.
    
    private static Chunk[] hChunks = new Chunk[cellWidth*cellWidth];
    
    @PreInit
    public void setup(FMLPreInitializationEvent event) {
        event.getModMetadata().parent = Core.modId;
        enabled = Core.enable_dimension_slice;
        if (!enabled) {
            return;
        }
        
        EntityRegistry.registerModEntity(DimensionSliceEntity.class, "fzds", 1, this, 64, 1, true);
        EntityRegistry.registerModEntity(DseCollider.class, "fzdsC", 2, this, 64, 80000, false);
        
        //Create the hammer dimension
        dimensionID = Core.dimension_slice_dimid;
        DimensionManager.registerProviderType(dimensionID, HammerWorldProvider.class, true);
        DimensionManager.registerDimension(dimensionID, dimensionID);
        
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            //When the client logs in or out, we need to do something to the shadow world
            proxy.clientInit();
            NetworkRegistry.instance().registerConnectionHandler(new IConnectionHandler() {
                @Override
                public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager) { }

                @Override
                public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager) { return null; }
                
                @Override
                public void connectionOpened(NetHandler netClientHandler, MinecraftServer server, INetworkManager manager) { }
                
                @Override
                public void connectionOpened(NetHandler netClientHandler, String server, int port, INetworkManager manager) { }
                
                @Override
                public void connectionClosed(INetworkManager manager) {
                    Hammer.proxy.clientLogout(manager);
                }
                
                @Override
                public void clientLoggedIn(NetHandler clientHandler, INetworkManager manager, Packet1Login login) {
                    Hammer.proxy.clientLogin(clientHandler, manager, login);
                }
            });
            
            TickRegistry.registerTickHandler(new ITickHandler() {
                EnumSet<TickType> clientTick = EnumSet.of(TickType.CLIENT);
                @Override
                public EnumSet<TickType> ticks() {
                    return clientTick;
                }
                
                @Override
                public void tickStart(EnumSet<TickType> type, Object... tickData) {
                    proxy.checkForWorldChange();
                }
                
                @Override
                public void tickEnd(EnumSet<TickType> type, Object... tickData) {
                    proxy.runShadowTick();
                }
                
                @Override
                public String getLabel() {
                    return "FZ Hammer client tick";
                }
            }, Side.CLIENT);
        }
        
        //This sets up saving how many IDs we've used
        TickRegistry.registerScheduledTickHandler(new IScheduledTickHandler() {
            @Override
            public EnumSet<TickType> ticks() { return serverTicks; }
            
            @Override
            public String getLabel() {
                return "FZDS save info";
            }
            
            @Override
            public int nextTickSpacing() {
                return 5*20;
            }
            
            @Override
            public void tickStart(EnumSet<TickType> type, Object... tickData) { }
            
            @Override
            public void tickEnd(EnumSet<TickType> type, Object... tickData) {
                hammerInfo.saveCellAllocations();
            }
        }, Side.SERVER);
        MinecraftForge.EVENT_BUS.register(hammerInfo);
    }
    
    @ServerStarting
    public void setMainServerThread(FMLServerStartingEvent event) {
        if (!enabled) {
            return;
        }
        event.registerServerCommand(new FZDSCommand());
        DimensionManager.initDimension(dimensionID);
        assert DimensionManager.shouldLoadSpawn(dimensionID);
        World hammerWorld = DimensionManager.getWorld(dimensionID);
        hammerWorld.addWorldAccess(new IWorldAccess() {
            //Should lets DSEs know that they need to update their area when a block is changed 
            @Override public void spawnParticle(String var1, double var2, double var4, double var6, double var8, double var10, double var12) { }
            @Override public void releaseEntitySkin(Entity var1) { }
            @Override public void playSound(String var1, double var2, double var4, double var6, float var8, float var9) { }
            @Override public void playRecord(String var1, int var2, int var3, int var4) { }
            @Override public void playAuxSFX(EntityPlayer var1, int var2, int var3, int var4, int var5, int var6) { }
            @Override public void obtainEntitySkin(Entity var1) { }
            
            @Override
            public void markBlockRangeForRenderUpdate(int lx, int ly, int lz, int hx, int hy, int hz) {
                markBlocksForUpdate(lx, ly, lz, hx, hy, hz);
            }
            
            @Override
            public void markBlockForUpdate(int x, int y, int z) {
                markBlocksForUpdate(x, y, z, x, y, z);
            }
            
            @Override
            public void markBlockForRenderUpdate(int x, int y, int z) {
                markBlocksForUpdate(x, y, z, x, y, z);
            }
            
            Coord center = new Coord(Hammer.getClientShadowWorld(), 0, 0, 0);
            void markBlocksForUpdate(int lx, int ly, int lz, int hx, int hy, int hz) {
                //Sorry, it could probably be a bit more efficient.
                World shadow = Hammer.getServerShadowWorld();
                center.set(shadow, (lx + hx)/2, (ly + hy)/2, (lz + hz)/2);
                int cellId = Hammer.getIdFromCoord(center);
                if (cellId < 0) {
                    return;
                }
                Iterator<DimensionSliceEntity> it = Hammer.getSlices(shadow).iterator();
                while (it.hasNext()) {
                    DimensionSliceEntity dse = it.next();
                    if (dse.isDead) {
                        it.remove(); //should be handled now. Keeping it anyways.
                        continue;
                    }
                    if (dse.cell == cellId && dse.worldObj == shadow) {
                        dse.blocksChanged(lx, ly, lz);
                        dse.blocksChanged(hx, hy, hz);
                    }
                }
            }
            
            @Override public void func_85102_a(EntityPlayer var1, String var2, double var3, double var5, double var7, float var9, float var10) { }
            @Override public void destroyBlockPartially(int var1, int var2, int var3, int var4, int var5) { }
            @Override public void broadcastSound(int var1, int var2, int var3, int var4, int var5) { }
        });
        int view_distance = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
        //the undeobfed method comes after "isPlayerWatchingChunk", also in uses of ServerConfigurationManager.getViewDistance()
        //It returns how many blocks are visible.
        DSE_ChunkUpdateRangeSquared = Math.pow(PlayerManager.func_72686_a(view_distance) + 16*cellWidth, 2);
    }
    
    @ServerStopping
    public void saveInfo(FMLServerStoppingEvent event) {
        hammerInfo.saveCellAllocations();
        serverSlices.clear();
        clientSlices.clear();
    }
    
    public static DimensionSliceEntity findClosest(Entity target, int cellId) {
        if (target == null) {
            return null;
        }
        DimensionSliceEntity closest = null;
        double dist = Double.POSITIVE_INFINITY;
        World real_world = getClientRealWorld();
        
        for (DimensionSliceEntity here : Hammer.getSlices(target.worldObj)) {
            if (here.worldObj != real_world && here.cell != cellId) {
                continue;
            }
            if (closest == null) {
                closest = here;
                continue;
            }
            double here_dist = target.getDistanceSqToEntity(here);
            if (here_dist < dist) {
                dist = here_dist;
                closest = here;
            }
        }
        return closest;
    }
    
    private static Vec3 buffer = Vec3.createVectorHelper(0, 0, 0);
    
    public static Vec3 shadow2nearestReal(Entity player, double x, double y, double z) {
        //The JVM sometimes segfaults in this function.
        int correct_cell_id = Hammer.getIdFromCoord(Coord.of(x, y, z));
        DimensionSliceEntity closest = Hammer.findClosest(player, correct_cell_id);
        if (closest == null) {
            return null;
        }
        buffer.xCoord = x;
        buffer.yCoord = y;
        buffer.zCoord = z;
        Vec3 ret = closest.shadow2real(buffer);
        return ret;
    }
    
    public static Vec3 ent2vec(Entity ent) {
        return ent.worldObj.getWorldVec3Pool().getVecFromPool(ent.posX, ent.posY, ent.posZ);
    }
    
    @PostInit
    public void modsLoaded(FMLPostInitializationEvent event) {
        double desired_radius = 16/2;
        if (Core.force_max_entity_radius >= 0 && Core.force_max_entity_radius < desired_radius) {
            desired_radius = Core.force_max_entity_radius;
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
    
    public static interface AreaMap {
        void fillDse(DseDestination destination);
    }
    
    public static interface DseDestination {
        void include(Coord c);
    }
    
    private static Coord shadow = new Coord(null, 0, 0, 0);
    
    public static DimensionSliceEntity makeSlice(final Coord min, final Coord max, AreaMap mapper) {
        final DimensionSliceEntity dse = Hammer.allocateSlice(min.w);
        Vec3 vrm = min.centerVec(max);
        dse.posX = vrm.xCoord;
        dse.posY = vrm.yCoord;
        dse.posZ = vrm.zCoord;
        mapper.fillDse(new DseDestination() {public void include(Coord real) {
            shadow.set(real);
            dse.real2shadow(shadow);
            TransferLib.move(real, shadow);
        }});
        mapper.fillDse(new DseDestination() {public void include(Coord real) {
            shadow.set(real);
            dse.real2shadow(shadow);
            shadow.markBlockForUpdate();
        }});
        return dse;
    }
}
