package factorization.fzds;

import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.fzds.api.IDeltaChunk;
import factorization.shared.FzUtil;
import gnu.trove.set.hash.THashSet;

public class DeltaChunk {
    static DeltaChunkMap getSlices(World w) {
        if (w == null) {
            if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
                return Hammer.clientSlices;
            } else {
                return Hammer.serverSlices;
            }
        }
        return w.isRemote ? Hammer.clientSlices : Hammer.serverSlices;
    }
    
    public static IDeltaChunk[] getSlicesContainingPoint(Coord at) {
        return getSlices(at.w).get(at.getChunk());
    }
    
    static boolean addSlice(IDeltaChunk dse) {
        return getSlices(dse.worldObj).add(dse);
    }
    
    static Set<IDeltaChunk> getSlicesInRange(World w, int lx, int ly, int lz, int hx, int hy, int hz) {
        THashSet<IDeltaChunk> found_deltachunks = new THashSet<IDeltaChunk>(10);
        World sliceWorld = DeltaChunk.getWorld(w);
        DeltaChunkMap map = DeltaChunk.getSlices(w);
        IDeltaChunk last_found = null;
        for (int x = lx; x <= hx; x += 16) {
            for (int z = lz; z <= hz; z += 16) {
                Chunk hereChunk = sliceWorld.getChunkFromBlockCoords(x, z);
                IDeltaChunk new_idcs[] = map.get(hereChunk);
                for (IDeltaChunk idc : new_idcs) {
                    if (idc == last_found) continue;
                    found_deltachunks.add(idc);
                    last_found = idc;
                }
            }
        }
        return found_deltachunks;
    }
    
    public static World getClientShadowWorld() {
        World ret = Hammer.worldClient;
        if (ret == null) {
            Hammer.proxy.createClientShadowWorld();
            return Hammer.worldClient;
        }
        return ret;
    }
    
    public static World getServerShadowWorld() {
        return DimensionManager.getWorld(Hammer.dimensionID);
    }
    
    public static World getClientRealWorld() {
        return Hammer.proxy.getClientRealWorld();
    }
    
    /***
     * @return the thread-appropriate shadow world
     */
    public static World getWorld(World realWorld) {
        boolean remote = realWorld == null ? FMLCommonHandler.instance().getEffectiveSide().isClient() : realWorld.isRemote;
        return remote ? getClientShadowWorld() : getServerShadowWorld();
    }
    
    public static IDeltaChunk allocateSlice(World spawnWorld, int channel, DeltaCoord size) {
        Coord base = Hammer.hammerInfo.takeCell(channel, size);
        return new DimensionSliceEntity(spawnWorld, base, base.add(size));
    }
    
    public static IDeltaChunk findClosest(Entity target, Coord pos) {
        if (target == null) {
            return null;
        }
        World real_world = target.worldObj;
        IDeltaChunk closest = null;
        double dist = Double.POSITIVE_INFINITY;
        for (IDeltaChunk here : DeltaChunk.getSlicesContainingPoint(pos)) {
            if (here.worldObj != real_world && !pos.inside(here.getCorner(), here.getFarCorner())) {
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
        IDeltaChunk closest = findClosest(player, new Coord(player.worldObj, x, y, z));
        if (closest == null) {
            return null;
        }
        buffer.xCoord = x;
        buffer.yCoord = y;
        buffer.zCoord = z;
        Vec3 ret = closest.shadow2real(buffer);
        return ret;
    }
    
    public static interface AreaMap {
        void fillDse(DseDestination destination);
    }
    
    public static interface DseDestination {
        void include(Coord c);
    }
    
    private static Coord shadow = new Coord(null, 0, 0, 0);
    
    public static IDeltaChunk makeSlice(int channel, final Coord min, final Coord max, AreaMap mapper, final boolean wipeSrc) {
        DeltaCoord size = max.difference(min);
        final IDeltaChunk dse = allocateSlice(min.w, channel, size);
        Vec3 vrm = min.centerVec(max);
        dse.posX = (int)vrm.xCoord;
        dse.posY = (int)vrm.yCoord;
        dse.posZ = (int)vrm.zCoord;
        mapper.fillDse(new DseDestination() {@Override
        public void include(Coord real) {
            shadow.set(real);
            dse.real2shadow(shadow);
            TransferLib.move(real, shadow, false, true);
        }});
        mapper.fillDse(new DseDestination() {@Override
        public void include(Coord real) {
            if (wipeSrc) {
                TransferLib.rawErase(real);
            }
            shadow.set(real);
            dse.real2shadow(shadow);
            shadow.markBlockForUpdate();
        }});
        return dse;
    }
    
    public static IDeltaChunk construct(final Coord min, final Coord max) {
        return new DimensionSliceEntity(getServerShadowWorld(), min, max);
    }
    
    public static void paste(IDeltaChunk selected, boolean overwriteDestination) {
        Coord a = new Coord(DeltaChunk.getServerShadowWorld(), 0, 0, 0);
        Coord b = a.copy();
        Vec3 vShadowMin = Vec3.createVectorHelper(0, 0, 0);
        Vec3 vShadowMax = Vec3.createVectorHelper(0, 0, 0);
        selected.getCorner().setAsVector(vShadowMin);
        selected.getFarCorner().setAsVector(vShadowMax);
        a.set(vShadowMin);
        b.set(vShadowMax);
        DeltaCoord dc = b.difference(a);
        Coord dest = new Coord(selected);
        Coord c = new Coord(a.w, 0, 0, 0);
        
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        boolean first = true;
        
        for (int x = a.x; x <= b.x; x++) {
            for (int y = a.y; y <= b.y; y++) {
                for (int z = a.z; z <= b.z; z++) {
                    c.set(a.w, x, y, z);
                    dest.set(c);
                    selected.shadow2real(dest);
                    TransferLib.move(c, dest, false, overwriteDestination);
                    if (first) {
                        minX = maxX = x;
                        minY = maxY = y;
                        minZ = maxZ = z;
                        first = false;
                    } else {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        dest.w.markBlockRangeForRenderUpdate(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    public static void clear(IDeltaChunk selected) {
        Coord a = new Coord(DeltaChunk.getServerShadowWorld(), 0, 0, 0);
        Coord b = a.copy();
        Vec3 vShadowMin = Vec3.createVectorHelper(0, 0, 0);
        Vec3 vShadowMax = Vec3.createVectorHelper(0, 0, 0);
        selected.getCorner().setAsVector(vShadowMin);
        selected.getFarCorner().setAsVector(vShadowMax);
        a.set(vShadowMin);
        b.set(vShadowMax);
        
        Coord c = new Coord(a.w, 0, 0, 0);
        for (int x = a.x; x < b.x; x++) {
            for (int y = a.y; y < b.y; y++) {
                for (int z = a.z; z < b.z; z++) {
                    c.set(a.w, x, y, z);
                    selected.shadow2real(c);
                    c.markBlockForUpdate();
                }
            }
        }
    }
    
}
