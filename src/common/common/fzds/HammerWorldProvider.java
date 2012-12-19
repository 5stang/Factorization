package factorization.common.fzds;

import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Vec3;
import net.minecraft.src.WorldProvider;

public class HammerWorldProvider extends WorldProvider {
    HammerChunkProvider chunkProvider = null;
    
    @Override
    public String getDimensionName() {
        return "FZHammer";
    }
    
    @Override
    public IChunkProvider createChunkGenerator() {
        if (chunkProvider == null) {
            chunkProvider = new HammerChunkProvider(worldObj);
        }
        return chunkProvider;
    }
    
    @Override
    public String getWelcomeMessage() {
        return "Entering FZ Hammerspace";
    }
    
    @Override
    public String getDepartMessage() {
        return "Leaving FZ Hammerspace";
    }
    
    @Override
    public boolean canRespawnHere() {
        return false;
    }
    
    @Override
    public boolean isSurfaceWorld() {
        return false;
    }
    
    @Override
    public ChunkCoordinates getEntrancePortalLocation() {
        int cellCenter = chunkProvider.cellWidth*16/2;
        return new ChunkCoordinates(cellCenter, 64, cellCenter);
    }
}
