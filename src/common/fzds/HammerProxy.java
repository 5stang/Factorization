package factorization.fzds;

import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.world.World;

public class HammerProxy {
    public void clientLogin(NetHandler clientHandler, INetworkManager manager, Packet1Login login) { }
    
    public void clientLogout(INetworkManager manager) { }
    
    public World getClientRealWorld() { return null; }

    public void setClientWorld(World w) { }

    public void restoreClientWorld() { }

    public boolean isInShadowWorld() { return false; }
    
    public void clientInit() { }

    public void runShadowTick() { }
}
