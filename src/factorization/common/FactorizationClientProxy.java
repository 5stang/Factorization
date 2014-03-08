package factorization.common;

import java.util.Random;
import java.util.logging.Level;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.api.IFactoryType;
import factorization.ceramics.BlockRenderGreenware;
import factorization.ceramics.ItemRenderGlazeBucket;
import factorization.ceramics.TileEntityGreenware;
import factorization.ceramics.TileEntityGreenwareRender;
import factorization.charge.BatteryItemRender;
import factorization.charge.BlockRenderBattery;
import factorization.charge.BlockRenderHeater;
import factorization.charge.BlockRenderLeydenJar;
import factorization.charge.BlockRenderMirrorStand;
import factorization.charge.BlockRenderSteamTurbine;
import factorization.charge.BlockRenderWire;
import factorization.charge.TileEntityHeater;
import factorization.charge.TileEntityHeaterRenderer;
import factorization.charge.TileEntityLeydenJar;
import factorization.charge.TileEntityLeydenJarRender;
import factorization.charge.TileEntitySteamTurbine;
import factorization.charge.TileEntitySteamTurbineRender;
import factorization.crafting.BlockRenderCompressionCrafter;
import factorization.crafting.BlockRenderMixer;
import factorization.crafting.ContainerMixer;
import factorization.crafting.GuiMixer;
import factorization.crafting.GuiStamper;
import factorization.crafting.TileEntityCompressionCrafter;
import factorization.crafting.TileEntityCompressionCrafterRenderer;
import factorization.crafting.TileEntityMixer;
import factorization.crafting.TileEntityMixerRenderer;
import factorization.darkiron.BlockDarkIronOre;
import factorization.darkiron.GlintRenderer;
import factorization.oreprocessing.BlockRenderCrystallizer;
import factorization.oreprocessing.BlockRenderGrinder;
import factorization.oreprocessing.ContainerCrystallizer;
import factorization.oreprocessing.ContainerGrinder;
import factorization.oreprocessing.ContainerSlagFurnace;
import factorization.oreprocessing.GuiCrystallizer;
import factorization.oreprocessing.GuiGrinder;
import factorization.oreprocessing.GuiSlag;
import factorization.oreprocessing.TileEntityCrystallizer;
import factorization.oreprocessing.TileEntityCrystallizerRender;
import factorization.oreprocessing.TileEntityGrinder;
import factorization.oreprocessing.TileEntityGrinderRender;
import factorization.oreprocessing.TileEntitySlagFurnace;
import factorization.servo.BlockRenderServoRail;
import factorization.servo.GuiParasieve;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.shared.BlockRenderDefault;
import factorization.shared.BlockRenderEmpty;
import factorization.shared.BlockRenderHelper;
import factorization.shared.Core;
import factorization.shared.EmptyRender;
import factorization.shared.FactorizationBlockRender;
import factorization.shared.FactorizationRender;
import factorization.shared.ItemRenderCapture;
import factorization.shared.TileEntityFactorization;
import factorization.sockets.BlockRenderSocketBase;
import factorization.sockets.SocketLacerator;
import factorization.sockets.TileEntitySocketRenderer;
import factorization.sockets.fanturpeller.SocketFanturpeller;
import factorization.weird.BlockRenderDayBarrel;
import factorization.weird.ContainerPocket;
import factorization.weird.DayBarrelItemRenderer;
import factorization.weird.GuiPocketTable;
import factorization.weird.TileEntityDayBarrel;
import factorization.weird.TileEntityDayBarrelRenderer;
import factorization.wrath.BlockRenderLamp;
import factorization.wrath.TileEntityWrathLamp;

public class FactorizationClientProxy extends FactorizationProxy {
    public FactorizationKeyHandler keyHandler = new FactorizationKeyHandler();
    public FactorizationClientProxy() {
        MinecraftForge.EVENT_BUS.register(this);
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
        
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityFactorization)) {
            return null;
        }
        TileEntityFactorization fac = (TileEntityFactorization) te;
        ContainerFactorization cont;
        if (ID == FactoryType.SLAGFURNACE.gui) {
            cont = new ContainerSlagFurnace(player, fac);
        } else if (ID == FactoryType.GRINDER.gui) {
            cont = new ContainerGrinder(player, fac);
        } else if (ID == FactoryType.MIXER.gui) {
            cont = new ContainerMixer(player, fac);
        } else if (ID == FactoryType.CRYSTALLIZER.gui) {
            cont = new ContainerCrystallizer(player, fac);
        } else {
            cont = new ContainerFactorization(player, fac);
        }
        GuiScreen gui = null;
        if (ID == FactoryType.STAMPER.gui) {
            gui = new GuiStamper(cont);
        }
        if (ID == FactoryType.SLAGFURNACE.gui) {
            gui = new GuiSlag(cont);
        }
        if (ID == FactoryType.GRINDER.gui) {
            gui = new GuiGrinder(cont);
        }
        if (ID == FactoryType.MIXER.gui) {
            gui = new GuiMixer((ContainerMixer)cont);
        }
        if (ID == FactoryType.CRYSTALLIZER.gui) {
            gui = new GuiCrystallizer(cont);
        }
        if (ID == FactoryType.PARASIEVE.gui) {
            gui = new GuiParasieve(cont);
        }
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

    int fireParticlesSpawned = 0;
    int fireParticlesMax = 5;

    @Override
    public void randomDisplayTickFor(World w, int x, int y, int z, Random rand) { //NORELEASE: This isn't necessary
        Coord here = new Coord(w, x, y, z);
        Block id = w.getBlock(x, y, z);
        int md = w.getBlockMetadata(x, y, z);
        if (id == Core.registry.factory_block) {
            TileEntity te = w.getTileEntity(x, y, z);
            if (!(te instanceof IFactoryType)) {
                return;
            }

            FactoryType ft = ((IFactoryType) te).getFactoryType();

            if (ft == FactoryType.SLAGFURNACE) {
                TileEntitySlagFurnace slag = (TileEntitySlagFurnace) te;
                if (slag.draw_active <= 0) {
                    return;
                }
//				if (!slag.isBurning()) {
//					return;
//				}
                int var6 = slag.facing_direction;
                float var7 = (float) x + 0.5F;
                float var8 = (float) y + 0.0F + rand.nextFloat() * 6.0F / 16.0F;
                float var9 = (float) z + 0.5F;
                float var10 = 0.52F;
                float var11 = rand.nextFloat() * 0.6F - 0.3F;

                if (var6 == 4) {
                    w.spawnParticle("smoke", (double) (var7 - var10), (double) var8, (double) (var9 + var11), 0.0D, 0.0D, 0.0D);
                    w.spawnParticle("flame", (double) (var7 - var10), (double) var8, (double) (var9 + var11), 0.0D, 0.0D, 0.0D);
                } else if (var6 == 5) {
                    w.spawnParticle("smoke", (double) (var7 + var10), (double) var8, (double) (var9 + var11), 0.0D, 0.0D, 0.0D);
                    w.spawnParticle("flame", (double) (var7 + var10), (double) var8, (double) (var9 + var11), 0.0D, 0.0D, 0.0D);
                } else if (var6 == 2) {
                    w.spawnParticle("smoke", (double) (var7 + var11), (double) var8, (double) (var9 - var10), 0.0D, 0.0D, 0.0D);
                    w.spawnParticle("flame", (double) (var7 + var11), (double) var8, (double) (var9 - var10), 0.0D, 0.0D, 0.0D);
                } else if (var6 == 3) {
                    w.spawnParticle("smoke", (double) (var7 + var11), (double) var8, (double) (var9 + var10), 0.0D, 0.0D, 0.0D);
                    w.spawnParticle("flame", (double) (var7 + var11), (double) var8, (double) (var9 + var10), 0.0D, 0.0D, 0.0D);
                }

            }
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

    @Override
    public void registerRenderers() {
        setTileEntityRendererDispatcher(TileEntityDayBarrel.class, new TileEntityDayBarrelRenderer());
        setTileEntityRendererDispatcher(TileEntityGreenware.class, new TileEntityGreenwareRender());
        if (FzConfig.renderTEs) {
            // This is entirely Azanor's fault.
            setTileEntityRendererDispatcher(TileEntityHeater.class, new TileEntityHeaterRenderer());
            setTileEntityRendererDispatcher(TileEntityGrinder.class, new TileEntityGrinderRender());
            setTileEntityRendererDispatcher(TileEntityMixer.class, new TileEntityMixerRenderer());
            setTileEntityRendererDispatcher(TileEntityCrystallizer.class, new TileEntityCrystallizerRender());
            setTileEntityRendererDispatcher(TileEntitySteamTurbine.class, new TileEntitySteamTurbineRender());
            setTileEntityRendererDispatcher(TileEntityLeydenJar.class, new TileEntityLeydenJarRender());
            setTileEntityRendererDispatcher(TileEntityCompressionCrafter.class, new TileEntityCompressionCrafterRenderer());
            setTileEntityRendererDispatcher(SocketLacerator.class, new TileEntitySocketRenderer());
            setTileEntityRendererDispatcher(SocketFanturpeller.class, new TileEntitySocketRenderer());
            // End section that is azanor's fault
        }

        RenderingRegistry.registerEntityRenderingHandler(TileEntityWrathLamp.RelightTask.class, new EmptyRender());
        RenderingRegistry.registerEntityRenderingHandler(ServoMotor.class, new RenderServoMotor());

        RenderingRegistry.registerBlockHandler(new FactorizationRender());
        BlockRenderBattery renderBattery = new BlockRenderBattery();
        BlockRenderDayBarrel renderBarrel = new BlockRenderDayBarrel();
        new BlockRenderLeydenJar();
        new BlockRenderDefault();
        new BlockRenderHeater();
        new BlockRenderLamp();
        new BlockRenderMirrorStand();
        new BlockRenderSteamTurbine();
        new BlockRenderWire();
        new BlockRenderGrinder();
        new BlockRenderMixer();
        new BlockRenderCrystallizer();
        new BlockRenderCompressionCrafter();
        new BlockRenderGreenware().setup();
        //NORELEASE new BlockRenderRocketEngine();
        new BlockRenderServoRail();
        for (FactoryType ft : new FactoryType[] {
                FactoryType.SOCKET_EMPTY,
                FactoryType.SOCKET_LACERATOR,
                FactoryType.SOCKET_ROBOTHAND,
                FactoryType.SOCKET_SHIFTER,
                FactoryType.SOCKET_FANTURPELLER,
                FactoryType.SOCKET_BLOWER,
                FactoryType.SOCKET_PUMP,
                FactoryType.SOCKET_POWERGEN,
                FactoryType.SOCKET_MIXER,
                FactoryType.SOCKET_BARE_MOTOR
        }) {
            new BlockRenderSocketBase(ft);
        }
        for (FactoryType ft : new FactoryType[] {
                FactoryType.STAMPER,
                FactoryType.PACKAGER,
                FactoryType.SLAGFURNACE,
                FactoryType.SOLARBOILER,
                FactoryType.PARASIEVE,
                FactoryType.CALIOMETRIC_BURNER
                }) {
            FactorizationBlockRender.setDefaultRender(ft);
        }
        new BlockRenderEmpty(FactoryType.EXTENDED);

        ItemRenderCapture capture = new ItemRenderCapture();
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(Core.registry.factory_block), capture);
        MinecraftForgeClient.registerItemRenderer(Core.registry.battery, new BatteryItemRender(renderBattery));
        MinecraftForgeClient.registerItemRenderer(Core.registry.glaze_bucket, new ItemRenderGlazeBucket());
        MinecraftForgeClient.registerItemRenderer(Core.registry.daybarrel, new DayBarrelItemRenderer(renderBarrel));
        setTileEntityRendererDispatcher(BlockDarkIronOre.Glint.class, new GlintRenderer());
    }
    
    @Override
    public void texturepackChanged() {
        TileEntityGrinderRender.remakeModel();
    }
    
    @Override
    public boolean BlockRenderHelper_has_texture(BlockRenderHelper block, int f) {
        if (block.textures == null) {
            return true;
        }
        return block.textures[f] != null;
    }
    
    @Override
    public void BlockRenderHelper_clear_texture(BlockRenderHelper block) {
        block.textures = null;
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
    
    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Post event) {
        int t = event.map.textureType;
        if (t == 0 /* terrain */) {
            Core.blockMissingIIcon = event.map.getAtlasSprite("this code for getting the missing IIcon brought to you by LexManos");
        } else if (t == 1 /* items */) {
            Core.itemMissingIIcon = event.map.getAtlasSprite("this code for getting the missing IIcon brought to you by Tahg");
        }
    }
}
