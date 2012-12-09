package factorization.common.astro;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.src.Block;
import net.minecraft.src.ChunkProviderServer;
import net.minecraft.src.CommandBase;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.ICommandSender;
import net.minecraft.src.Packet11PlayerPosition;
import net.minecraft.src.ServerConfigurationManager;
import net.minecraft.src.Teleporter;
import net.minecraft.src.World;
import net.minecraft.src.WorldServer;
import net.minecraftforge.common.DimensionManager;
import factorization.api.Coord;
import factorization.common.Core;

public class FZDSCommand extends CommandBase {
    private static DimensionSliceEntity currentWE = null;
    @Override
    public String getCommandName() {
        return "fzds";
    }
    
    class DSTeleporter extends Teleporter {
        public DSTeleporter(WorldServer par1WorldServer) {
            super(par1WorldServer);
        }
        
        Coord destination;
        @Override
        public void placeInPortal(Entity player, double par2, double par4, double par6, float par8) {
            destination.x--;
            destination.moveToTopBlock();
            if (player.worldObj == DimensionManager.getWorld(Core.dimension_slice_dimid)) {
                destination.y = Math.min(HammerChunkProvider.wallHeight, destination.y);
            }
            destination.setAsEntityLocation(player);
            Coord below = new Coord(player);
            below = below.add(0, -3, 0);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Coord platform = below.add(dx, 0, dz);
                    if (platform.isAir()) {
                        platform.setId(Block.stone);
                    }
                }
            }
        }
    }
    
    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendChatToPlayer("Sub-commands: spawn remove go leave removeall (only removeall works from console)");
            return;
        }
        String cmd = args[0];
        if (cmd.equalsIgnoreCase("test")) {
            Core.proxy.setClientWorld(HammerManager.getClientWorld());
            return;
        }
        if (sender instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) sender;
            if (cmd.equalsIgnoreCase("spawn")) {
                currentWE = Core.hammerManager.allocateSlice(player.worldObj);
                currentWE.setPosition(player.posX, player.posY, player.posZ);
                currentWE.worldObj.spawnEntityInWorld(currentWE);
                PacketProxyingPlayer ppp = new PacketProxyingPlayer(player, currentWE);
                currentWE.hammerCell.setAsEntityLocation(ppp);
                ppp.posY += 4;
                ppp.worldObj.spawnEntityInWorld(ppp);
            }
            if (cmd.equalsIgnoreCase("remove")) {
                for (Entity ent : (List<Entity>)player.worldObj.loadedEntityList) {
                    if (ent instanceof DimensionSliceEntity) {
                        ent.setDead();
                    }
                }
            }
            ServerConfigurationManager manager = MinecraftServer.getServerConfigurationManager(MinecraftServer.getServer());
            DSTeleporter tp = new DSTeleporter((WorldServer) player.worldObj);
            tp.destination = new Coord (player.worldObj, 0, 0, 0);
            if (cmd.equalsIgnoreCase("go")) {
                World hammerWorld = player.worldObj;
                int destinationCell = 0;
                if (args.length == 2) {
                    destinationCell = Integer.parseInt(args[1]);
                }
                tp.destination = HammerChunkProvider.getCellStart(hammerWorld, destinationCell); 
                if (DimensionManager.getWorld(Core.dimension_slice_dimid) != player.worldObj) {
                    manager.transferPlayerToDimension(player, Core.dimension_slice_dimid, tp);
                } else {
                    tp.destination.x--;
                    tp.destination.moveToTopBlock();
                    player.setPositionAndUpdate(tp.destination.x + 0.5, tp.destination.y, tp.destination.z + 0.5);
                }
            }
            if (cmd.equalsIgnoreCase("leave")) {
                if (DimensionManager.getWorld(0) != player.worldObj) {
                    manager.transferPlayerToDimension(player, 0, tp);
                }
            }
        }
        if (cmd.equals("removeall")) {
            for (World w : MinecraftServer.getServer().worldServers) {
                for (Entity ent : (List<Entity>)w.loadedEntityList) {
                    if (ent instanceof DimensionSliceEntity) {
                        ent.setDead();
                    }
                }
            }
        }
    }
    
    

}
