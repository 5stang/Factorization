package factorization.fzds;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.fzds.api.IDeltaChunk;
import factorization.shared.Core;
import factorization.shared.EmptyRender;
import factorization.shared.FzUtil;

public class HammerClientProxy extends HammerProxy {
    public HammerClientProxy() {
        RenderingRegistry.registerEntityRenderingHandler(DseCollider.class, new EmptyRender());
        RenderDimensionSliceEntity rwe = new RenderDimensionSliceEntity();
        RenderingRegistry.registerEntityRenderingHandler(DimensionSliceEntity.class, rwe);
        Core.loadBus(rwe);
    }
    
    //These two classes below make it easy to see in a debugger.
    public static class HammerChunkProviderClient extends ChunkProviderClient {
        public HammerChunkProviderClient(World par1World) {
            super(par1World);
        }
    }
    
    public static class HammerWorldClient extends WorldClient {
        public HammerWorldClient(NetHandlerPlayClient par1NetClientHandler, WorldSettings par2WorldSettings, int par3, EnumDifficulty par4, Profiler par5Profiler) {
            super(par1NetClientHandler, par2WorldSettings, par3, par4, par5Profiler);
        }
        
        @Override
        public void playSoundAtEntity(Entity par1Entity, String par2Str, float par3, float par4) {
            super.playSoundAtEntity(par1Entity, par2Str, par3, par4);
        }
        
        public void clearAccesses() {
            worldAccesses.clear();
        }
        
        public void shadowTick() {
            clientChunkProvider.unloadQueuedChunks();
        }
    }
    
    @Override
    public World getClientRealWorld() {
        if (real_world == null) {
            return Minecraft.getMinecraft().theWorld;
        }
        return real_world;
    }
    
    private static World lastWorld = null;
    void checkForWorldChange() {
        WorldClient currentWorld = Minecraft.getMinecraft().theWorld;
        if (currentWorld != lastWorld) {
            lastWorld = currentWorld;
            if (lastWorld == null) {
                return;
            }
            if (Hammer.worldClient != null) {
                ((HammerWorldClient)Hammer.worldClient).clearAccesses();
                Hammer.worldClient.addWorldAccess(new ShadowRenderGlobal(currentWorld));
            }
        }
    }
    
    @SubscribeEvent
    public void tick(ClientTickEvent event) {
        if (event.phase == Phase.START) {
            checkForWorldChange(); // Is there an event for this?
            runShadowTick();
        }
    }
    
    @Override
    public void createClientShadowWorld() {
        final Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || mc.thePlayer == null) {
            Hammer.worldClient = null;
            send_queue = null;
            fake_player = null;
            return;
        }
        send_queue = mc.thePlayer.sendQueue;
        WorldInfo wi = world.getWorldInfo();
        Hammer.worldClient = new HammerWorldClient(send_queue,
                new WorldSettings(wi),
                Hammer.dimensionID,
                world.difficultySetting,
                Core.proxy.getProfiler());
        Hammer.worldClient.addWorldAccess(new ShadowRenderGlobal(mc.theWorld));
    }
    
    
    @SubscribeEvent
    public void onClientLogout(ClientDisconnectionFromServerEvent event) {
        //TODO: what else we can do here to cleanup?
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) {
            return;
        }
        if (Hammer.worldClient != null) {
            ((HammerWorldClient)Hammer.worldClient).clearAccesses();
        }
        Hammer.worldClient = null;
        send_queue = null;
        fake_player = null;
    }
    
    private static NetHandlerPlayClient send_queue;
    
    private void setSendQueueWorld(WorldClient wc) {
        send_queue.clientWorldController = wc;
    }
    
    private void setWorldAndPlayer(WorldClient wc, EntityClientPlayerMP player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (wc == null) {
            Core.logSevere("Setting client world to null. PREPARE FOR IMPACT.");
        }
        //For logic
        mc.theWorld = wc;
        mc.thePlayer = player;
        mc.thePlayer.worldObj = wc;
        setSendQueueWorld(wc);
        
        //For rendering
        mc.renderViewEntity = player; //TODO NOTE: This make mess up in third person!
        if (TileEntityRendererDispatcher.instance.field_147550_f != null) {
            TileEntityRendererDispatcher.instance.field_147550_f = wc;
        }
        if (RenderManager.instance.worldObj != null) {
            RenderManager.instance.worldObj = wc;
        }
        mc.renderGlobal.theWorld = wc;
    }
    
    private EntityClientPlayerMP real_player = null;
    private WorldClient real_world = null;
    private EntityClientPlayerMP fake_player = null;
    
    @Override
    public void setShadowWorld() {
        //System.out.println("Setting world");
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient w = (WorldClient) DeltaChunk.getClientShadowWorld();
        assert w != null;
        if (real_player != null || real_world != null) {
            Core.logSevere("Tried to switch to Shadow world, but we're already in the shadow world");
            return;
        }
        if (real_player == null) {
            real_player = mc.thePlayer;
            if (real_player == null) {
                Core.logSevere("Swapping out to hammer world, but thePlayer is null");
            }
        }
        if (real_world == null) {
            real_world = mc.theWorld;
            if (real_world == null) {
                Core.logSevere("Swapping out to hammer world, but theWorld is null");
            }
        }
        real_player.worldObj = w;
        if (fake_player == null || w != fake_player.worldObj) {
            fake_player = new EntityClientPlayerMP(
                    mc,
                    mc.theWorld /* why is this real world? */,
                    mc.getSession(), real_player.sendQueue /* not sure about this one. */,
                    real_player.getStatFileWriter());
        }
        setWorldAndPlayer((WorldClient) w, fake_player);
    }
    
    @Override
    public void restoreRealWorld() {
        //System.out.println("Restoring world");
        setWorldAndPlayer(real_world, real_player);
        real_world = null;
        real_player = null;
    }
    
    @Override
    public boolean isInShadowWorld() {
        return real_world != null;
    }
    
    void runShadowTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isGamePaused()) {
            return;
        }
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return;
        }
        HammerWorldClient w = (HammerWorldClient) DeltaChunk.getClientShadowWorld();
        if (w == null) {
            return;
        }
        int range = 10;
        AxisAlignedBB nearby = player.boundingBox.expand(range, range, range);
        Iterable<IDeltaChunk> nearbyChunks = mc.theWorld.getEntitiesWithinAABB(IDeltaChunk.class, nearby);
        setShadowWorld();
        Core.profileStart("FZDStick");
        try {
            //Inspired by Minecraft.runTick()
            w.updateEntities();
            Vec3 playerPos = Vec3.createVectorHelper(player.posX, player.posY, player.posZ);
            for (IDeltaChunk idc : nearbyChunks) {
                Vec3 center = idc.real2shadow(playerPos);
                w.doVoidFogParticles((int) center.xCoord, (int) center.yCoord, (int) center.zCoord);
            }
            w.shadowTick();
        } finally {
            Core.profileEnd();
            restoreRealWorld();
        }
        
    }
    
    @Override
    public void clientInit() {
        
    }
    
    MovingObjectPosition shadowSelected = null;
    DseRayTarget rayTarget = null;
    AxisAlignedBB selectionBlockBounds = null;
    
    @SubscribeEvent
    public void renderSelection(DrawBlockHighlightEvent event) {
        //System.out.println(event.target.hitVec);
        if (!(event.target.entityHit instanceof DseRayTarget)) {
            return;
        }
        if (shadowSelected == null) {
            return;
        }
        if (shadowSelected.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        if (event.isCanceled()) {
            return;
        }
        EntityPlayer player = event.player;
        RenderGlobal rg = event.context;
        ItemStack is = event.currentItem;
        float partialTicks = event.partialTicks;
        DimensionSliceEntity dse = rayTarget.parent;
        Coord here = null;
        if (selectionBlockBounds != null && shadowSelected.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            here = new Coord(DeltaChunk.getClientShadowWorld(), shadowSelected.blockX, shadowSelected.blockY, shadowSelected.blockZ);
            here.getBlock().setBlockBounds(
                    (float)(selectionBlockBounds.minX - here.x), (float)(selectionBlockBounds.minY - here.y), (float)(selectionBlockBounds.minZ - here.z),
                    (float)(selectionBlockBounds.maxX - here.x), (float)(selectionBlockBounds.maxY - here.y), (float)(selectionBlockBounds.maxZ - here.z)
                );
        }
        //GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glPushMatrix();
        setShadowWorld();
        try {
            //TODO: Rotation transform
            Coord corner = dse.getCenter();
            GL11.glTranslatef(-corner.x, -corner.y, -corner.z);
            GL11.glTranslatef((float)(+dse.posX), (float)(+dse.posY), (float)(+dse.posZ));
            
            //Could glPushAttr for the mask. Nah.
            GL11.glColorMask(true, true, false, true);
            if (!ForgeHooksClient.onDrawBlockHighlight(rg, player, shadowSelected, shadowSelected.subHit, is, partialTicks)) {
                event.context.drawSelectionBox(player, shadowSelected, 0, partialTicks);
            }
        } finally {
            GL11.glColorMask(true, true, true, true);
            restoreRealWorld();
            GL11.glPopMatrix();
            //GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
        //shadowSelected = null;
    }
    
    @Override
    void updateRayPosition(DseRayTarget ray) {
        if (ray.parent.centerOffset == null) {
            return;
        }
        //mc.renderViewEntity.rayTrace(reachDistance, partialTicks) Just this function would work if we didn't care about entities.
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        double origX = player.posX;
        double origY = player.posY;
        double origZ = player.posZ;
        Vec3 shadowPos = ray.parent.real2shadow(Vec3.createVectorHelper(origX, origY, origZ));
        MovingObjectPosition origMouseOver = mc.objectMouseOver;
        //Entity origPointed = mc.entityRenderer.pointedEntity;
        //It's private! It's used in one function! Why is this even a field?
        
        try {
            AxisAlignedBB bb;
            Hammer.proxy.setShadowWorld();
            try {
                mc.thePlayer.posX = shadowPos.xCoord;
                mc.thePlayer.posY = shadowPos.yCoord;
                mc.thePlayer.posZ = shadowPos.zCoord;
                //TODO: Need to rotate the player if the DSE has rotated
                mc.thePlayer.rotationPitch = player.rotationPitch;
                mc.thePlayer.rotationYaw = player.rotationYaw;
                
                mc.entityRenderer.getMouseOver(1F);
                shadowSelected = mc.objectMouseOver;
                if (shadowSelected == null) {
                    rayTarget = null;
                    return;
                }
                switch (shadowSelected.typeOfHit) {
                case ENTITY:
                    bb = shadowSelected.entityHit.boundingBox;
                    selectionBlockBounds = null;
                    break;
                case BLOCK:
                    Coord hit = new Coord(DeltaChunk.getClientShadowWorld(), shadowSelected.blockX, shadowSelected.blockY, shadowSelected.blockZ);
                    Block block = hit.getBlock();
                    bb = block.getSelectedBoundingBoxFromPool(hit.w, hit.x, hit.y, hit.z);
                    selectionBlockBounds = bb;
                    break;
                default: return;
                }
            } finally {
                Hammer.proxy.restoreRealWorld();
            }
            //TODO: Rotations!
            if (bb == null) {
                System.out.println("NORELEASE?");
            }
            Vec3 min = ray.parent.shadow2real(FzUtil.getMin(bb));
            Vec3 max = ray.parent.shadow2real(FzUtil.getMax(bb));
            FzUtil.setMin(ray.boundingBox, min);
            FzUtil.setMax(ray.boundingBox, max);
            rayTarget = ray;
        } finally {
            mc.objectMouseOver = origMouseOver;
            //mc.entityRenderer.pointedEntity = origPointed;
        }
    }
    
    @Override
    MovingObjectPosition getShadowHit() {
        return shadowSelected;
    }
    
    @Override
    void mineBlock(final MovingObjectPosition mop) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityClientPlayerMP player = mc.thePlayer;
        final PlayerControllerMP origController = mc.playerController;
        
        /*
        mc.playerController = new PlayerControllerMP(mc, player.sendQueue) {
            void pushWrapper() {
                setShadowWorld();
            }
            
            void popWrapper() {
                restoreRealWorld();
            }
            
            void sendDigPacket(Packet toWrap) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                try {
                    dos.write(toWrap.getPacketId());
                    toWrap.writePacketData(dos);
                } catch (IOException e) {
                    e.printStackTrace(); //Isn't there some guava thing for this?
                }
                Packet toSend = PacketDispatcher.getTinyPacket(Hammer.instance, HammerNet.HammerNetType.digPacket, baos.toByteArray());
                System.out.println("SEND: " + toWrap); //NORELEASE
                PacketDispatcher.sendPacketToServer(toSend);
                
            }
            
            void resetController() {
                mc.playerController = origController;
                System.out.println("Resetting controller"); //NORELEASE
            }
            
            //NOTE: Incomplete, because java sucks. Are there any other functions we'll need?
            
            @Override
            public void resetBlockRemoving() {
                System.out.println("HCP.PCMP.resetBlockRemoving"); //NORELEASE
                sendDigPacket(new Packet14BlockDig(1, mop.blockX, mop.blockY, mop.blockZ, mop.sideHit));
                resetController();
            }
            
            @Override
            public void clickBlock(int x, int y, int z, int side) {
                System.out.println("HCP.PCMP.clickBlock"); //NORELEASE
                //Unlike vanilla's, this should only be called during the mining. Not at the start. Not at the cancellation. Not when we switch to a different one. (HOPEFULLY)
                pushWrapper();
                try {
                    clickBlock_implementation(x, y, z, side);
                } finally {
                    popWrapper();
                }
            }
            
            public void clickBlock_implementation(int x, int y, int z, int side) {
                //Very terribly copied from clickBlock with this change: sendDigPacket instead of using addToSendQueue
                //And also adding resetController call after the block gets broken
                if (!this.currentGameType.isAdventure() || mc.thePlayer.isCurrentToolAdventureModeExempt(x, y, z))
                {
                    if (this.currentGameType.isCreative())
                    {
                        sendDigPacket(new Packet14BlockDig(0, x, y, z, side));
                        clickBlockCreative(this.mc, this, x, y, z, side);
                        this.blockHitDelay = 5;
                    }
                    else if (!this.isHittingBlock || !this.sameToolAndBlock(x, y, z)) // sameToolAndBlock sameToolAndBlock
                    {
                        if (this.isHittingBlock)
                        {
                            sendDigPacket(new Packet14BlockDig(1, this.currentBlockX, this.currentBlockY, this.currentblockZ, side));
                        }

                        sendDigPacket(new Packet14BlockDig(0, x, y, z, side));
                        Block i1 = this.mc.theWorld.getBlock(x, y, z);

                        if (this.curBlockDamageMP == 0.0F)
                        {
                            i1.onBlockClicked(this.mc.theWorld, x, y, z, this.mc.thePlayer);
                        }

                        if (i1.getPlayerRelativeBlockHardness(this.mc.thePlayer, this.mc.thePlayer.worldObj, x, y, z) >= 1.0F)
                        {
                            this.onPlayerDestroyBlock(x, y, z, side);
                            resetController();
                        }
                        else
                        {
                            this.isHittingBlock = true;
                            this.currentBlockX = x;
                            this.currentBlockY = y;
                            this.currentblockZ = z;
                            this.currentItemHittingBlock = this.mc.thePlayer.getHeldItem();
                            this.curBlockDamageMP = 0.0F;
                            this.stepSoundTickCounter = 0.0F;
                            this.mc.theWorld.destroyBlockInWorldPartially(this.mc.thePlayer.getEntityId(), this.currentBlockX, this.currentBlockY, this.currentblockZ, (int)(this.curBlockDamageMP * 10.0F) - 1);
                        }
                    }
                }
            }
            
            
            
            @Override
            public void attackEntity(EntityPlayer par1EntityPlayer, Entity par2Entity) {
                //likely will need stuff in here
                // TODO Auto-generated method stub
                System.out.println("HCP.PCMP.attackEntity"); //NORELEASE
                super.attackEntity(par1EntityPlayer, par2Entity);
            }
        };
        mc.playerController.clickBlock(mop.blockX, mop.blockY, mop.blockZ, mop.sideHit);
        */
    }
}
