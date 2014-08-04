package factorization.colossi;

import java.util.Random;

import net.minecraft.block.Block;
import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.shared.Core;

public class BlobBuilder {
    static final byte AIR = 0, SUSTAINER = -1, WALL = -2, POISON = -3;
    
    byte[] cells, nextTick;
    final byte[] buffer1, buffer2;
    
    final Coord start, end;
    final DeltaCoord size;
    
    public byte neighbor_checks = 5;
    public byte min_neighbors = 2;
    public double birth_threshold = 4;
    public double death_threshold = 0;
    public double wall_healing = -1.0;
    public double sustainer_healing = 2.0;
    
    
    
    BlobBuilder(Coord start, Coord end) {
        start = start.copy();
        end = end.copy();
        Coord.sort(start, end);
        this.start = start;
        this.end = end;
        size = end.difference(start);
        cells = buffer1 = new byte[size.x * size.y * size.z];
        nextTick = buffer2 = new byte[size.x * size.y * size.z];
    }
    
    void swap() {
        if (cells == buffer1) {
            cells = buffer2;
            nextTick = buffer1;
        } else {
            cells = buffer1;
            nextTick = buffer2;
        }
    }
    
    int index(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0) return -1;
        if (x > size.x || y > size.y || z > size.z) return -1;
        return x + y * size.x + z * size.x * size.y;
    }
    
    byte classify(Coord at) {
        if (at.isAir()) return AIR;
        Block b = at.getBlock();
        if (b == Core.registry.colossal_block) {
            int md = at.getMd();
            if (md == ColossalBlock.MD_ARM || md == ColossalBlock.MD_LEG || md == ColossalBlock.MD_BODY || md == ColossalBlock.MD_BODY_CRACKED) {
                return SUSTAINER;
            }
            if (md == ColossalBlock.MD_MASK) {
                return POISON;
            }
        }
        return WALL;
    }
    
    public void populateCellsFromWorld() {
        Coord at = start.copy();
        for (int x = start.x; x <= end.x; x++) {
            at.x = x;
            for (int y = start.y; y <= end.y; y++) {
                at.y = y;
                for (int z = start.z; z <= end.z; z++) {
                    at.z = z;
                    int index = index(x - start.x, y - start.y, z - start.z);
                    if (index == -1) continue;
                    cells[index] = classify(at);
                }
            }
        }
    }
    
    public void sprinkleSeeds(Random rand, double ratio) {
        int count = (int) (size.x * size.y * size.z * ratio);
        for (int i = 0; i < count; i++) {
            int index = rand.nextInt(cells.length);
            if (cells[index] == 0) {
                cells[index] = 1;
            }
        }
    }
    
    public void saveCellsToWorld(Block stone) {
        Coord at = start.copy();
        for (int x = start.x; x <= end.x; x++) {
            at.x = x;
            for (int y = start.y; y <= end.y; y++) {
                at.y = y;
                for (int z = start.z; z <= end.z; z++) {
                    at.z = z;
                    int index = index(x - start.x, y - start.y, z - start.z);
                    if (index == -1) continue;
                    if (cells[index] > 0) {
                        at.setId(stone);
                    }
                }
            }
        }
    }
    
    public void simulateTick(Random rand) {
        for (int x = start.x; x <= end.x; x++) {
            for (int y = start.y; y <= end.y; y++) {
                for (int z = start.z; z <= end.z; z++) {
                    tickCell(rand, x, y, z);
                }
            }
        }
        swap();
    }
    
    byte get(int x, int y, int z) {
        int i = index(x, y, z);
        if (i == -1) return POISON;
        return cells[i];
    }
    
    void set(int x, int y, int z, byte new_val) {
        int i = index(x, y, z);
        if (i == -1) return;
        cells[i] = new_val;
    }

    int dGauss(Random rand) {
        int r = (int) Math.round(rand.nextGaussian() / 2);
        if (r > 4) return 4;
        if (r < -4) return -4;
        return r;
    }
    
    private void tickCell(Random rand, int x, int y, int z) {
        byte here = get(x, y, z);
        switch (here) {
        case WALL: return;
        case SUSTAINER: return;
        case POISON: return;
        case AIR:
            if (calculateLivliness(rand, x, y, z) > birth_threshold) {
                set(x, y, z, (byte) 1);
            }
            break;
        default:
            if (calculateLivliness(rand, x, y, z) < death_threshold) {
                set(x, y, z, (byte) 0);
            }
            break;
        }
    }
    
    double calculateLivliness(Random rand, int x, int y, int z) {
        double life = 0;
        byte neighbors = 0;
        for (byte i = 0; i < neighbor_checks; i++) {
            int dx = x + dGauss(rand);
            int dy = y + dGauss(rand);
            int dz = z + dGauss(rand);
            switch (get(dx, dy, dz)) {
            case AIR: break;
            case POISON:
                life -= sustainer_healing * 1.5;
                break;
            case WALL:
                life += wall_healing;
                break;
            case SUSTAINER:
                life += sustainer_healing;
                neighbors++;
                break;
            default:
                neighbors++;
                break;
            }
        }
        if (neighbors < min_neighbors) {
            return -1;
        }
        return life + (get(x, y, z) > 0 ? 5 : 0);
    }
    
}
