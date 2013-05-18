package factorization.client;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.TickType;
import factorization.api.Coord;
import factorization.api.IFactoryType;
import factorization.client.gui.*;
import factorization.client.render.*;
import factorization.common.*;
import factorization.common.servo.BlockRenderServoRail;
import factorization.common.servo.RenderServoMotor;
import factorization.common.servo.ServoMotor;

public class FactorizationClientProxy extends FactorizationProxy {
    @Override
    public void broadcastTranslate(EntityPlayer who, String... msg) {
        String format = msg[0];
        String params[] = new String[msg.length - 1];
        System.arraycopy(msg, 1, params, 0, msg.length - 1);
        try {
            who.addChatMessage(String.format(format, (Object[]) params));
        } catch (IllegalFormatException e) {
        }
    }

    @Override
    public EntityPlayer getPlayer(NetHandler handler) {
        return Minecraft.getMinecraft().thePlayer;
    }

    @Override
    public Profiler getProfiler() {
        return Minecraft.getMinecraft().mcProfiler;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == FactoryType.POCKETCRAFTGUI.gui) {
            return new GuiPocketTable(new ContainerPocket(player));
        }

        if (ID == FactoryType.EXOTABLEGUICONFIG.gui) {
            return new GuiExoConfig(new ContainerExoModder(player, new Coord(world, x, y, z)));
        }

        TileEntity te = world.getBlockTileEntity(x, y, z);
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
        if (ID == FactoryType.ROUTER.gui) {
            gui = new GuiRouter(cont);
        }
        if (ID == FactoryType.MAKER.gui) {
            gui = new GuiMaker(cont);
        }
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
            gui = new GuiMixer(cont);
        }
        if (ID == FactoryType.CRYSTALLIZER.gui) {
            gui = new GuiCrystallizer(cont);
        }

        cont.addSlotsForGui(fac, player.inventory);
        return gui;
    }

    //CLIENT
    /*
    @Override
    public void addNameDirect(String localId, String translation) {
        LanguageRegistry.instance().addStringLocalization(localId, "en_US", translation);
    }*/

    @Override
    public String translateItemStack(ItemStack is) {
        if (is == null) {
            return "null";
        }
        return is.getItem().getItemDisplayName(is);
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
    public void randomDisplayTickFor(World w, int x, int y, int z, Random rand) {
        Coord here = new Coord(w, x, y, z);
        int id = w.getBlockId(x, y, z);
        int md = w.getBlockMetadata(x, y, z);
        if (id == Core.registry.factory_block.blockID) {
            TileEntity te = w.getBlockTileEntity(x, y, z);
            if (!(te instanceof IFactoryType)) {
                return;
            }

            FactoryType ft = ((IFactoryType) te).getFactoryType();

            if (ft == FactoryType.LAMP) {
                for (int i = 0; i < 3; i++) {
                    double X = x + 0.4 + rand.nextFloat() * 0.2;
                    double Z = z + 0.4 + rand.nextFloat() * 0.2;
                    EntityWrathFlameFX flame = new EntityWrathFlameFX(w,
                            X, y + 0.2 + rand.nextFloat() * 0.1, Z,
                            0.001 - rand.nextFloat() * 0.002, 0.01, 0.001 - rand.nextFloat() * 0.002);
                    Minecraft.getMinecraft().effectRenderer.addEffect(flame);
                }
            }
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
        if (id == Core.registry.lightair_block.blockID) {
            if (md == BlockLightAir.fire_md) {
                int to_spawn = 1;
                EntityPlayer player = Core.proxy.getClientPlayer();
                boolean force = false;
                boolean big = true;
                if (player != null) {
                    int dx = (int) (player.posX) - x, dy = (int) (player.posY) - y, dz = (int) (player.posZ) - z;
                    int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (dist < 4) {
                        to_spawn = 8;
                        force = true;
                        big = false;
                    }
                    else if (dist <= 12) {
                        to_spawn = 4;
                        force = true;
                    }
                    else if (dist <= 16) {
                        to_spawn = 1;
                    }
                }

                //				if (to_spawn == 1) {
                //					if (rand.nextFloat() > 0.2) {
                //						return;
                //					}
                //				}
                if (fireParticlesSpawned >= fireParticlesMax) {
                    if (!force) {
                        return;
                    }
                    else {
                        to_spawn /= 4;
                    }
                }
                if (!force) {
                    fireParticlesSpawned += to_spawn;
                }
                for (int i = 0; i < to_spawn; i++) {
                    double X = x + .05 + rand.nextFloat() * .95;
                    double Z = z + .05 + rand.nextFloat() * .95;
                    EntityWrathFlameFX flame = new EntityWrathFlameFX(w,
                            X, y + rand.nextFloat() * 0.25, Z,
                            (rand.nextFloat() - 0.5) * 0.02, 0.05 + rand.nextFloat() * 0.04, (rand.nextFloat() - 0.5) * 0.02);
                    if (big) {
                        flame.setScale(4);
                    }
                    Minecraft.getMinecraft().effectRenderer.addEffect(flame);
                }
            }
        }
    }

    @Override
    public void playSoundFX(String src, float volume, float pitch) {
        Minecraft.getMinecraft().sndManager.playSoundFX(src, volume, pitch);
    }

    @Override
    public EntityPlayer getClientPlayer() {
        return Minecraft.getMinecraft().thePlayer;
    }

    public static KeyBinding bag_swap_key = new KeyBinding("FZ Bag of Holding", org.lwjgl.input.Keyboard.KEY_GRAVE);
    public static KeyBinding pocket_key = new KeyBinding("FZ Pocket Crafting Table", org.lwjgl.input.Keyboard.KEY_C);
    public static KeyBinding exoKeys[] = new KeyBinding[Registry.ExoKeyCount];

    private static class CommandKeySet extends KeyHandler {
        Map<KeyBinding, Command> map;

        static CommandKeySet create(Object... args) {
            KeyBinding bindings[] = new KeyBinding[args.length / 2];
            boolean repeatings[] = new boolean[args.length / 2];
            Map<KeyBinding, Command> map = new HashMap();
            for (int i = 0; i < args.length; i += 2) {
                KeyBinding key = (KeyBinding) args[i];
                Command cmd = (Command) args[i + 1];
                map.put(key, cmd);
                bindings[i / 2] = key;
                repeatings[i / 2] = false;
            }
            CommandKeySet ret = new CommandKeySet(bindings, repeatings);
            ret.map = map;
            return ret;
        }

        private CommandKeySet(KeyBinding[] keyBindings, boolean[] repeatings) {
            super(keyBindings, repeatings);
        }

        @Override
        public EnumSet<TickType> ticks() {
            return EnumSet.of(TickType.CLIENT);
        }

        @Override
        public String getLabel() {
            return "CommandKeys";
        }

        @Override
        public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd,
                boolean isRepeat) {
            if (tickEnd) {
                return;
            }
            GuiScreen gui = Minecraft.getMinecraft().currentScreen;
            if (gui != null) {
                return;
            }
            Command command = map.get(kb);
            EntityPlayer player = Core.proxy.getClientPlayer();
            if (player == null) {
                return;
            }
            if (player.isSneaking()) {
                command = command.reverse;
            }
            command.call(Core.proxy.getClientPlayer());
        }

        @Override
        public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {
        }
    }

    private Map<KeyBinding, Byte> exoIDmap = new HashMap();

    public class ExoKeySet extends KeyHandler {
        public ExoKeySet(KeyBinding[] keyBindings, boolean[] repeatings) {
            super(keyBindings, repeatings);
        }

        @Override
        public EnumSet<TickType> ticks() {
            return EnumSet.of(TickType.CLIENT);
        }

        @Override
        public String getLabel() {
            return "ExoKeys";
        }

        @Override
        public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
            if (tickEnd) {
                return;
            }
            if (kb.keyCode == 0) {
                return;
            }
            GuiScreen gui = Minecraft.getMinecraft().currentScreen;
            if (gui != null) {
                return;
            }
            Command.exoKeyOn.call(getClientPlayer(), exoIDmap.get(kb));
        }

        @Override
        public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {
            GuiScreen gui = Minecraft.getMinecraft().currentScreen;
            if (gui != null) {
                return;
            }
            if (kb.keyCode == 0) {
                return;
            }
            Command.exoKeyOff.call(getClientPlayer(), exoIDmap.get(kb));
        }

    }

    @Override
    public void registerKeys() {
        //we use numpad keys as our default, unless they're being used for movement (implying a left-handed user, using mouse w/ right hand)
        //If that's the case, we use our original keybindings.
        int defaults_main[] = new int[] { Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3 };
        int defaults_alt[] = new int[] { Keyboard.KEY_R, Keyboard.KEY_F, Keyboard.KEY_V };
        int defaults[] = defaults_main;
        GameSettings gs = Minecraft.getMinecraft().gameSettings;
        int conflict_check[] = new int[] { gs.keyBindForward.keyCode, gs.keyBindLeft.keyCode, gs.keyBindRight.keyCode, gs.keyBindBack.keyCode };
        outer: for (int vanilla_kc : conflict_check) {
            for (int default_kc : defaults_main) {
                if (default_kc == vanilla_kc) {
                    if (default_kc == vanilla_kc) {
                        defaults = defaults_alt;
                        break outer;
                    }
                }
            }
        }
        
        
        for (byte i = 0; i < exoKeys.length; i++) {
            exoKeys[i] = new KeyBinding("FZ Exo" + (i + 1), defaults[i]);
            exoIDmap.put(exoKeys[i], i);
        }
        KeyBindingRegistry.registerKeyBinding(CommandKeySet.create(
                bag_swap_key, Command.bagShuffle,
                pocket_key, Command.craftOpen));
        KeyBindingRegistry.registerKeyBinding(new ExoKeySet(exoKeys, new boolean[exoKeys.length]));
    }

    private void setTileEntityRenderer(Class clazz, TileEntitySpecialRenderer r) {
        ClientRegistry.bindTileEntitySpecialRenderer(clazz, r);
    }

    @Override
    public void registerRenderers() {
        if (Core.render_barrel_item || Core.render_barrel_text) {
            setTileEntityRenderer(TileEntityBarrel.class, new TileEntityBarrelRenderer(Core.render_barrel_item, Core.render_barrel_text));
        }
        setTileEntityRenderer(TileEntityGreenware.class, new TileEntityGreenwareRender());
        if (Core.renderTEs) {
            // This is entirely Azanor's fault.
            setTileEntityRenderer(TileEntityHeater.class, new TileEntityHeaterRenderer());
            //setTileEntityRenderer(TileEntityMirror.class, new TileEntityMirrorRenderer());
            setTileEntityRenderer(TileEntityGrinder.class, new TileEntityGrinderRender());
            setTileEntityRenderer(TileEntityMixer.class, new TileEntityMixerRenderer());
            setTileEntityRenderer(TileEntityCrystallizer.class, new TileEntityCrystallizerRender());
            setTileEntityRenderer(TileEntitySteamTurbine.class, new TileEntitySteamTurbineRender());
            setTileEntityRenderer(TileEntityLeydenJar.class, new TileEntityLeydenJarRender());
            // End section that is azanor's fault
        }

        RenderingRegistry.registerEntityRenderingHandler(TileEntityWrathLamp.RelightTask.class, new EmptyRender());
        RenderingRegistry.registerEntityRenderingHandler(ServoMotor.class, new RenderServoMotor());

        RenderingRegistry.registerBlockHandler(new FactorizationRender());
        BlockRenderBattery renderBattery = new BlockRenderBattery();
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
        new BlockRenderGreenware().setup();
        new BlockRenderRocketEngine();
        new BlockRenderServoRail();
        for (FactoryType ft : new FactoryType[] { FactoryType.ROUTER, FactoryType.MAKER, FactoryType.STAMPER, FactoryType.BARREL, FactoryType.PACKAGER, FactoryType.SLAGFURNACE, FactoryType.SOLARBOILER }) {
            FactorizationBlockRender.setDefaultRender(ft);
        }
        new BlockRenderEmpty(FactoryType.EXTENDED);

        ItemRenderCapture capture = new ItemRenderCapture();
        MinecraftForgeClient.registerItemRenderer(Core.registry.battery.itemID, new BatteryItemRender(renderBattery)); //TODO: Do battery rendering using the capture!
        MinecraftForgeClient.registerItemRenderer(Core.registry.factory_block.blockID, capture);
        MinecraftForgeClient.registerItemRenderer(Core.registry.glaze_bucket.itemID, new ItemRenderGlazeBucket());
        MinecraftForge.EVENT_BUS.register(new FactorizationNotify());
        
        if (Minecraft.getMinecraft().session.username.equals("neptunepink")) {
            Core.FZLogger.setLevel(Level.FINE);
        }
    }
    
    @Override
    public String getExoKeyBrief(int keyindex) {
        int key = FactorizationClientProxy.exoKeys[keyindex - 1].keyCode;
        return GameSettings.getKeyDisplayString(key);
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
        return GameSettings.getKeyDisplayString(pocket_key.keyCode);
    }
}
