package factorization.fzds;

import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeDirection;
import factorization.api.Coord;
import factorization.api.Quaternion;
import factorization.common.Core;

public class FZDSCommand extends CommandBase {
    private static DimensionSliceEntity currentWE = null;
    @Override
    public String getCommandName() {
        return "fzds";
    }
    
    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            boolean op = MinecraftServer.getServer().getConfigurationManager().areCommandsAllowed(player.username);
            boolean cr = player.capabilities.isCreativeMode;
            if (!(op || cr)) {
                sender.sendChatToPlayer("You must be op or in creative mode to use these commands");
                return;
            }
        }
        if (args.length == 0) {
            //TODO: Non-shitty command interface
            sender.sendChatToPlayer("Player: spawn (show #) grass (go [#=0]) (goc [#=0]) leave");
            sender.sendChatToPlayer("Selected: selection + - remove (s|r|v|w +|= x y z [w]) (rs|rw angle° direction)");
            sender.sendChatToPlayer("removeall force_cell_allocation_count rot?");
            return;
        }
        String cmd = args[0];
        if (sender instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) sender;
            /*if (cmd.equalsIgnoreCase("puppet")) {
                MinecraftServer ms = MinecraftServer.getServer();
                World w = player.worldObj;
                ItemInWorldManager iiwm = new ItemInWorldManager(w);
                PuppetPlayer puppet = new PuppetPlayer(ms, w, "neptunepink", iiwm);
                HammerNet.puppetPlayer(player, puppet);
                return;
            }*/
            if (cmd.equalsIgnoreCase("spawn")) {
                currentWE = Hammer.allocateSlice(player.worldObj);
                currentWE.setPosition((int)player.posX, (int)player.posY, (int)player.posZ);
                currentWE.worldObj.spawnEntityInWorld(currentWE);
                ((EntityPlayerMP) sender).addChatMessage("Created FZDS " + currentWE.cell);
                return;
            }
            if (cmd.equalsIgnoreCase("show")) {
                int cell = 0;
                if (args.length == 2) {
                    cell = Integer.valueOf(args[1]);
                }
                currentWE = Hammer.spawnSlice(player.worldObj, cell);
                currentWE.setPosition((int)player.posX, (int)player.posY, (int)player.posZ);
                currentWE.worldObj.spawnEntityInWorld(currentWE);
                ((EntityPlayerMP) sender).addChatMessage("Showing FZDS " + currentWE.cell);
                return;
            }
            if (cmd.equalsIgnoreCase("grass")) {
                new Coord(player).add(0, -1, 0).setId(Block.grass);
                return;
            }
            ServerConfigurationManager manager = MinecraftServer.getServerConfigurationManager(MinecraftServer.getServer());
            DSTeleporter tp = new DSTeleporter((WorldServer) player.worldObj);
            tp.destination = new Coord (player.worldObj, 0, 0, 0);
            if (cmd.equalsIgnoreCase("go") || cmd.equalsIgnoreCase("goc")) {
                World hammerWorld = player.worldObj;
                int destinationCell = 0;
                if (args.length == 2) {
                    destinationCell = Integer.parseInt(args[1]);
                } 
                if (cmd.equalsIgnoreCase("goc")) {
                    tp.destination = Hammer.getCellCenter(player.worldObj, destinationCell);
                } else {
                    tp.destination = Hammer.getCellLookout(player.worldObj, destinationCell);
                }
                if (DimensionManager.getWorld(Core.dimension_slice_dimid) != player.worldObj) {
                    manager.transferPlayerToDimension(player, Core.dimension_slice_dimid, tp);
                } else {
                    tp.destination.x--;
                    tp.destination.moveToTopBlock();
                    player.setPositionAndUpdate(tp.destination.x + 0.5, tp.destination.y, tp.destination.z + 0.5);
                }
                return;
            }
            if (cmd.equalsIgnoreCase("leave")) {
                World w = DimensionManager.getWorld(0);
                if (w != player.worldObj) {
                    ChunkCoordinates target = player.getBedLocation();
                    if (target == null) {
                        target = w.getSpawnPoint(); 
                    }
                    Vec3 v = Vec3.createVectorHelper(target.posX, target.posY + 1, target.posZ);
                    //HammerNet.transferPlayer(player, (DimensionSliceEntity)null, w, v);
                    if (target != null) {
                        tp.destination.set(target);
                    }
                    manager.transferPlayerToDimension(player, 0, tp);
                }
                return;
            }
            if (cmd.equalsIgnoreCase("tome")) {
                if (currentWE == null) {
                    player.addChatMessage("No selection");
                    return;
                }
                currentWE.posX = player.posX;
                currentWE.posY = player.posY;
                currentWE.posZ = player.posZ;
                return;
            }
            if (cmd.equalsIgnoreCase("snap")) {
                if (currentWE == null) {
                    player.addChatMessage("No selection");
                    return;
                }
                currentWE.posX = (int) currentWE.posX;
                currentWE.posY = (int) currentWE.posY;
                currentWE.posZ = (int) currentWE.posZ;
                return;
            }
            /*if (cmd.equals("kill_everything_except_me")) { //NORELEASE
                World w = player.worldObj;
                for (Entity e : (Iterable<Entity>)w.loadedEntityList) {
                    if (e == player) {
                        continue;
                    }
                    e.setDead();
                }
                return;
            }*/
        }
        if (cmd.equals("removeall")) {
            int i = 0;
            for (World w : MinecraftServer.getServer().worldServers) {
                for (Entity ent : (List<Entity>)w.loadedEntityList) {
                    if (ent instanceof DimensionSliceEntity) {
                        ent.setDead();
                        i++;
                    }
                }
            }
            sender.sendChatToPlayer("Removed " + i);
            return;
        }
        if (cmd.equals("selection")) {
            sender.sendChatToPlayer("> " + currentWE);
            return;
        }
        if (cmd.equals("rot?")) {
            sender.sendChatToPlayer("r = " + currentWE.rotation);
            sender.sendChatToPlayer("ω = " + currentWE.rotationalVelocity);
            return;
        }
        if (cmd.equals("+") || cmd.equals("-")) {
            boolean add = cmd.equals("+");
            Iterator<DimensionSliceEntity> it = Hammer.getSlices(MinecraftServer.getServer().worldServerForDimension(0)).iterator();
            DimensionSliceEntity first = null, prev = null, next = null, last = null;
            boolean found_current = false;
            if (!it.hasNext()) {
                sender.sendChatToPlayer("There are no DSEs loaded");
                currentWE = null;
                return;
            }
            while (it.hasNext()) {
                DimensionSliceEntity here = it.next();
                if (here.isDead) {
                    System.out.println(here + " was not removed");
                    it.remove();
                    continue;
                }
                last = here;
                if (first == null) {
                    first = last;
                }
                if (!found_current) {
                    prev = last;
                }
                if (found_current && next == null) {
                    next = last;
                }
                if (last == currentWE) {
                    found_current = true;
                }
            }
            if (first == null) {
                sender.sendChatToPlayer("There are no DSEs loaded");
                currentWE = null;
                return;
            }
            if (currentWE == null) {
                //initialize selection
                currentWE = add ? first : last;
            } else if (currentWE == last && add) {
                currentWE = first;
            } else if (currentWE == first && !add) {
                currentWE = last;
            } else {
                currentWE = add ? next : prev;
            }
            sender.sendChatToPlayer("> " + currentWE);
            return;
        }
        if (cmd.equalsIgnoreCase("remove")) {
            if (currentWE == null) {
                sender.sendChatToPlayer("No selection");
            } else {
                currentWE.setDead();
                currentWE = null;
                sender.sendChatToPlayer("Made dead");
            }
            return;
        }
        if (cmd.equals("force_cell_allocation_count")) {
            int newCount = Integer.parseInt(args[1]);
            Hammer.instance.hammerInfo.setAllocationCount(newCount);
            return;
        }
        /*if (cmd.equals("kill_most_entities")) { //NORELEASE
            for (World w : MinecraftServer.getServer().worldServers) {
                for (Entity e : (Iterable<Entity>)w.loadedEntityList) {
                    if (e instanceof EntityPlayer && !(e instanceof PuppetPlayer)) {
                        continue;
                    }
                    if (e instanceof DimensionSliceEntity || e instanceof IFzdsEntryControl) {
                        continue;
                    }
                    e.setDead();
                }
            }
            return;
        }*/
        if (cmd.equals("rs") || cmd.equals("rw")) {
            if (args.length != 3) {
                sender.sendChatToPlayer("Usage: /fzds rs angle direction");
                return;
            }
            if (currentWE == null) {
                sender.sendChatToPlayer("No selection");
                return;
            }
            double theta = Math.toRadians(Double.parseDouble(args[1]));
            ForgeDirection dir;
            try {
                dir = ForgeDirection.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                String msg = "Direction must be:";
                for (ForgeDirection d : ForgeDirection.values()) {
                    if (d == ForgeDirection.UNKNOWN) {
                        continue;
                    }
                    msg += " " + d;
                }
                sender.sendChatToPlayer(msg);
                return;
            }
            Quaternion toMod = cmd.equals("rs") ? currentWE.rotation : currentWE.rotationalVelocity;
            toMod.update(Quaternion.getRotationQuaternion(theta, dir));
            return;
        }
        if (cmd.equals("s") || cmd.equals("d") || cmd.equals("r") || cmd.equals("v") || cmd.equals("w")) {
            if (args.length != 5 && args.length != 6) {
                sender.sendChatToPlayer("Usage: /fzds s(displacement)|r(otation)|v(elocity)|w(r.velocity) =|+ [W=1; rotations] X Y Z");
                return;
            }
            //TODO NORELEASE: put w at the front. For insanity-prevention.
            int i = 0;
            double w = 1;
            if (args.length == 6) {
                w = Double.parseDouble(args[2]);
                i = 1;
            }
            double x = Double.parseDouble(args[2+i]);
            double y = Double.parseDouble(args[3+i]);
            double z = Double.parseDouble(args[4+i]);
            if (args.length == 6) {
                w = Double.parseDouble(args[5]);
            }
            char type = cmd.charAt(0);
            if (args[1].equals("+")) {
                if (type == 'd' || type == 's') {
                    currentWE.setPosition(currentWE.posX + x, currentWE.posY + y, currentWE.posZ + z);
                } else if (type == 'v') {
                    currentWE.addVelocity(x/20, y/20, z/20);
                } else if (type == 'r') {
                    currentWE.rotation.incrAdd(new Quaternion(w, x, y, z));
                } else if (type == 'w') {
                    currentWE.rotationalVelocity.incrAdd(new Quaternion(w, x, y, z));
                } else {
                    sender.sendChatToPlayer("Not a command?");
                }
            } else if (args[1].equals("=")) {
                if (type == 'd') {
                    currentWE.setPosition(x, y, z);
                } else if (type == 'v') {
                    currentWE.motionX = 0;
                    currentWE.motionY = 0;
                    currentWE.motionZ = 0;
                    currentWE.addVelocity(x/20, y/20, z/20);
                } else if (type == 'r') {
                    currentWE.rotation = (new Quaternion(w, x, y, z));
                } else if (type == 'w') {
                    Quaternion omega = (new Quaternion(w, x, y, z));
                    currentWE.rotationalVelocity = omega;
                } else {
                    sender.sendChatToPlayer("Not a command?");
                }
                currentWE.rotation.incrNormalize();
                currentWE.rotationalVelocity.incrNormalize();
            } else {
                sender.sendChatToPlayer("Are you setting or adding?");
            }
            return;
        }
        if (cmd.equals("dirty")) {
            currentWE.rotationalVelocity.w *= -1;
            currentWE.rotation.w *= -1;
            currentWE.rotation.w += 0.1;
            return;
        }
        sender.sendChatToPlayer("Not a command");
    }
    
    

}
