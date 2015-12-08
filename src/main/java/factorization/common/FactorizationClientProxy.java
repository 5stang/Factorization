package factorization.common;

import factorization.api.Coord;
import factorization.artifact.ContainerForge;
import factorization.artifact.GuiArtifactForge;
import factorization.artifact.RenderBrokenArtifact;
import factorization.beauty.*;
import factorization.ceramics.ItemRenderGlazeBucket;
import factorization.ceramics.TileEntityGreenware;
import factorization.ceramics.TileEntityGreenwareRender;
import factorization.charge.TileEntityHeater;
import factorization.charge.TileEntityHeaterRenderer;
import factorization.charge.TileEntityLeydenJar;
import factorization.charge.TileEntityLeydenJarRender;
import factorization.citizen.EntityCitizen;
import factorization.citizen.RenderCitizen;
import factorization.colossi.ColossusController;
import factorization.colossi.ColossusControllerRenderer;
import factorization.crafting.TileEntityCompressionCrafter;
import factorization.crafting.TileEntityCompressionCrafterRenderer;
import factorization.darkiron.BlockDarkIronOre;
import factorization.darkiron.GlintRenderer;
import factorization.mechanics.SocketPoweredCrank;
import factorization.mechanics.TileEntityHinge;
import factorization.mechanics.TileEntityHingeRenderer;
import factorization.oreprocessing.*;
import factorization.redstone.GuiParasieve;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.servo.stepper.RenderStepperEngine;
import factorization.servo.stepper.StepperEngine;
import factorization.shared.Core;
import factorization.shared.EmptyRender;
import factorization.shared.TileEntityFactorization;
import factorization.sockets.SocketLacerator;
import factorization.sockets.SocketScissors;
import factorization.sockets.TileEntitySocketRenderer;
import factorization.sockets.fanturpeller.SocketFanturpeller;
import factorization.twistedblock.TwistedRender;
import factorization.utiligoo.GooRenderer;
import factorization.weird.*;
import factorization.weird.poster.EntityPoster;
import factorization.weird.poster.RenderPoster;
import factorization.wrath.TileEntityWrathLamp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public class FactorizationClientProxy extends FactorizationProxy {
    public FactorizationKeyHandler keyHandler = new FactorizationKeyHandler();

    public FactorizationClientProxy() {
        Core.loadBus(this);
    }

    @Override
    public Profiler getProfiler() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            return Minecraft.getMinecraft().mcProfiler;
        } else {
            return super.getProfiler();
        }
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == FactoryType.POCKETCRAFTGUI.gui) {
            return new GuiPocketTable(new ContainerPocket(player));
        }
        Coord at = new Coord(world, x, y, z);
        if (ID == FactoryType.ARTIFACTFORGEGUI.gui) {
            return new GuiArtifactForge(new ContainerForge(at, player));
        }
        
        TileEntity te = world.getTileEntity(at.toBlockPos());
        if (!(te instanceof TileEntityFactorization)) {
            return null;
        }
        TileEntityFactorization fac = (TileEntityFactorization) te;
        ContainerFactorization cont;
        if (ID == FactoryType.SLAGFURNACE.gui) {
            cont = new ContainerSlagFurnace(player, fac);
        } else if (ID == FactoryType.CRYSTALLIZER.gui) {
            cont = new ContainerCrystallizer(player, fac);
        } else {
            cont = new ContainerFactorization(player, fac);
        }
        GuiScreen gui = null;
        if (ID == FactoryType.SLAGFURNACE.gui) {
            gui = new GuiSlag(cont);
        }
        if (ID == FactoryType.CRYSTALLIZER.gui) {
            gui = new GuiCrystallizer(cont);
        }
        if (ID == FactoryType.PARASIEVE.gui) {
            gui = new GuiParasieve(cont);
        }
        if (gui == null) return null;
        cont.addSlotsForGui(fac, player.inventory);
        return gui;
    }
    
    @Override
    public void pokePocketCrafting() {
        // If the player has a pocket crafting table open, have it update
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen instanceof GuiPocketTable) {
            GuiPocketTable gui = (GuiPocketTable) minecraft.currentScreen;
            gui.containerPocket.updateCraft();
        }
    }

    @Override
    public void playSoundFX(String src, float volume, float pitch) {
        ISound sound = new PositionedSoundRecord(new ResourceLocation(src), volume, pitch, 0, 0, 0);
        Minecraft.getMinecraft().getSoundHandler().playSound(sound);
    }

    @Override
    public EntityPlayer getClientPlayer() {
        return Minecraft.getMinecraft().thePlayer;
    }





    private void setTileEntityRendererDispatcher(Class clazz, TileEntitySpecialRenderer r) {
        ClientRegistry.bindTileEntitySpecialRenderer(clazz, r);
    }

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Override
    public void registerRenderers() {
        setTileEntityRendererDispatcher(TileEntityDayBarrel.class, new TileEntityDayBarrelRenderer());
        setTileEntityRendererDispatcher(TileEntityGreenware.class, new TileEntityGreenwareRender());
        if (FzConfig.renderTEs) {
            setTileEntityRendererDispatcher(TileEntityHeater.class, new TileEntityHeaterRenderer());
            setTileEntityRendererDispatcher(TileEntityCrystallizer.class, new TileEntityCrystallizerRender());
            setTileEntityRendererDispatcher(TileEntityLeydenJar.class, new TileEntityLeydenJarRender());
            setTileEntityRendererDispatcher(TileEntityCompressionCrafter.class, new TileEntityCompressionCrafterRenderer());
            setTileEntityRendererDispatcher(SocketScissors.class, new TileEntitySocketRenderer());
            setTileEntityRendererDispatcher(SocketLacerator.class, new TileEntitySocketRenderer());
            setTileEntityRendererDispatcher(SocketFanturpeller.class, new TileEntitySocketRenderer());
            setTileEntityRendererDispatcher(TileEntityHinge.class, new TileEntityHingeRenderer());
            setTileEntityRendererDispatcher(SocketPoweredCrank.class, new TileEntitySocketRenderer());
            setTileEntityRendererDispatcher(TileEntitySteamShaft.class, new TileEntitySteamShaftRenderer());
            setTileEntityRendererDispatcher(TileEntityShaft.class, new TileEntityShaftRenderer());
            setTileEntityRendererDispatcher(TileEntityBiblioGen.class, new TileEntityBiblioGenRenderer());
        }

        RenderingRegistry.registerEntityRenderingHandler(TileEntityWrathLamp.RelightTask.class, new EmptyRender());
        RenderingRegistry.registerEntityRenderingHandler(ServoMotor.class, new RenderServoMotor());
        RenderingRegistry.registerEntityRenderingHandler(StepperEngine.class, new RenderStepperEngine());
        RenderingRegistry.registerEntityRenderingHandler(ColossusController.class, new ColossusControllerRenderer());
        RenderingRegistry.registerEntityRenderingHandler(EntityPoster.class, new RenderPoster());
        RenderingRegistry.registerEntityRenderingHandler(EntityCitizen.class, new RenderCitizen());
        RenderingRegistry.registerEntityRenderingHandler(EntityMinecartDayBarrel.class, new RenderMinecartDayBarrel());
        RenderingRegistry.registerEntityRenderingHandler(EntityLeafBomb.class, new RenderSnowball(Core.registry.leafBomb, 0));

        ItemRenderCapture capture = new ItemRenderCapture();
        MinecraftForgeClient.registerItemRenderer(Core.registry.glaze_bucket, new ItemRenderGlazeBucket());
        MinecraftForgeClient.registerItemRenderer(Core.registry.daybarrel, new DayBarrelItemRenderer(renderBarrel));
        MinecraftForgeClient.registerItemRenderer(Core.registry.twistedBlock, new TwistedRender());
        MinecraftForgeClient.registerItemRenderer(Core.registry.brokenTool, new RenderBrokenArtifact());
        setTileEntityRendererDispatcher(BlockDarkIronOre.Glint.class, new GlintRenderer());
        Core.loadBus(GooRenderer.INSTANCE);
    }

    @Override
    public String getPocketCraftingTableKey() {
        return GameSettings.getKeyDisplayString(FactorizationKeyHandler.pocket_key.getKeyCode());
    }
    
    @Override
    public boolean isClientHoldingShift() {
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.CLIENT) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        return org.lwjgl.input.Keyboard.isKeyDown(42 /* sneak */);
        //return !mc.gameSettings.keyBindSneak.pressed;
    }
    
    @Override
    public void afterLoad() {
        Core.logInfo("Reloading game settings");
        Minecraft.getMinecraft().gameSettings.loadOptions();
    }
    
    @Override
    public void sendBlockClickPacket() {
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition mop = mc.objectMouseOver;
        new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK /* NORELEASE: was 0, is this correct? This is for barrels. */, mop.getBlockPos(), mop.sideHit);
    }
}
