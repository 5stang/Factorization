package factorization.notify;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.google.common.base.Joiner;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.shared.Core;

@Mod(modid = NotifyImplementation.modId, name = NotifyImplementation.name, version = Core.version, dependencies = "required-after: " + Core.modId)
public class NotifyImplementation extends Notify {
    public static final String modId = Core.modId + ".notify";
    public static final String name = "Factorization Notification System";
    
    @SidedProxy(clientSide = "factorization.notify.RenderMessages", serverSide = "factorization.notify.RenderMessagesProxy")
    public static RenderMessagesProxy proxy;
    
    {
        Notify.instance = this;
        Core.loadBus(this);
    }
    
    @EventHandler
    public void setParent(FMLPreInitializationEvent event) {
        event.getModMetadata().parent = Core.modId;
    }
    
    @EventHandler
    public void registerServerCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {
            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if (!(sender instanceof TileEntity || sender instanceof Entity)) {
                    return;
                }
                EnumSet theStyle = EnumSet.noneOf(Style.class);
                ItemStack heldItem = null;
                if (sender instanceof EntityLivingBase) {
                    heldItem = ((EntityLivingBase) sender).getHeldItem();
                }
                ItemStack sendItem = null;
                for (int i = 0; i < args.length; i++) {
                    String s = args[i];
                    if (s.equalsIgnoreCase("--long")) {
                        theStyle.add(Style.LONG);
                    } else if (s.equalsIgnoreCase("--show-item") && heldItem != null) {
                        theStyle.add(Style.DRAWITEM);
                        sendItem = heldItem;
                    } else {
                        break;
                    }
                    args[i] = null;
                }
                String msg = Joiner.on(" ").skipNulls().join(args);
                msg = msg.replace("\\n", "\n");
                Notify.send(null, sender, theStyle, sendItem, "%s", msg);
            }
            
            @Override
            public String getCommandUsage(ICommandSender icommandsender) {
                return "/mutter [--long] [--show-item] [--refer-item] some text. Clears if empty";
            }
            
            @Override
            public String getCommandName() {
                return "mutter";
            }
            
            @Override
            public boolean canCommandSenderUseCommand(ICommandSender sender) {
                return sender instanceof Entity;
            }
            
            @Override
            public int getRequiredPermissionLevel() {
                return 0;
            }
            
            // o_ó eclipse has no trouble compiling without these two methods...
            public int compareTo(ICommand otherCmd) {
                return this.getCommandName().compareTo(otherCmd.getCommandName());
            }

            public int compareTo(Object obj) {
                return this.compareTo((ICommand)obj);
            }
        });
    }
    
    @Override
    protected void doSend(EntityPlayer player, Object where, EnumSet<Style> style, ItemStack item, String format, String[] args) {
        if (where == null) {
            return;
        }
        format = styleMessage(style, format);
        if ((player != null && player.worldObj.isRemote) || FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            proxy.addMessage(where, item, format, args);
        } else {
            Coord target = Coord.tryLoad(player != null ? player.worldObj : null, where);
            Core.network.broadcastPacket(player, target, Core.network.notifyPacket(where, item, format, args));
        }
    }
    
    public static void recieve(EntityPlayer player, Object where, ItemStack item, String styledFormat, String[] args) {
        if (where == null) {
            return;
        }
        proxy.addMessage(where, item, styledFormat, args);
    }
    
    String styleMessage(EnumSet<Style> style, String format) {
        if (style == null) {
            return "\n" + format;
        }
        String prefix = "";
        String sep = "";
        for (Style s : style) {
            prefix += sep + s.toString();
            sep = " ";
        }
        return prefix + "\n" + format;
    }
    
    static EnumSet<Style> loadStyle(String firstLine) {
        EnumSet<Style> ret = EnumSet.noneOf(Style.class);
        for (String s : firstLine.split(" ")) {
            try {
                ret.add(Style.valueOf(s));
            } catch (IllegalArgumentException e) {}
        }
        return ret;
    }
    
    static ArrayList<RecuringNotification> recuring_notifications = new ArrayList();
    @SubscribeEvent
    public void updateRecuringNotifications(ServerTickEvent event) {
        if (event.phase != Phase.END) return;
        for (Iterator<RecuringNotification> iterator = recuring_notifications.iterator(); iterator.hasNext();) {
            RecuringNotification rn = iterator.next();
            if (rn.isInvalid()) {
                iterator.remove();
                continue;
            }
            if (rn.updater.update(rn.first) == false) {
                iterator.remove();
            }
            rn.first = false;
        }
    }
    
    @Override
    protected void addRecuringNotification(RecuringNotification newRN) {
        for (Iterator<RecuringNotification> iterator = recuring_notifications.iterator(); iterator.hasNext();) {
            RecuringNotification rn = iterator.next();
            if (rn.where.equals(newRN.where) && (newRN.player == null || newRN.player == rn.player)) {
                iterator.remove();
            }
        }
        recuring_notifications.add(newRN);
    }
}
