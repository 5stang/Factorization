package factorization.fzds;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import factorization.common.Core;

import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.WorldEvent;

public class HammerInfo {
    private int allocated_cells = 0;
    private int unsaved_allocations = 0;
    
    @ForgeSubscribe
    public void handleWorldLoad(WorldEvent.Load event) {
        if (DimensionManager.getWorld(Hammer.dimensionID) == event.world) {
            loadCellAllocations();
        }
    }

    int takeCellId() {
        //save the first time, and every 30 seconds (if there's been a change...)
        if (unsaved_allocations++ == 0) {
            saveCellAllocations();
        }
        return allocated_cells++;
    }
    
    private File getInfoFile() {
        World baseWorld = DimensionManager.getWorld(0);
        File saveDir = new File("saves", baseWorld.getSaveHandler().getSaveDirectoryName());
        saveDir = saveDir.getAbsoluteFile();
        return new File(saveDir, "fzds");
    }
    
    public void loadCellAllocations() {
        File infoFile = getInfoFile();
        if (!infoFile.exists()) {
            Core.logInfo("No FZDS info file");
            return;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(infoFile);
            DataInputStream ios = new DataInputStream(fis);
            allocated_cells = ios.readInt();
        } catch (Exception e) {
            Core.logWarning("Unable to load FZDS info");
            e.printStackTrace();
        } finally {
            try {
                fis.close();
            } catch (IOException e) {
                e.printStackTrace(); //lern2raii
            }
        }
    }
    
    public void saveCellAllocations() {
        FileOutputStream fos = null;
        try {
            File infoFile = getInfoFile();
            if (!infoFile.exists()) {
                infoFile.createNewFile();
            }
            fos = new FileOutputStream(infoFile);
            
            DataOutputStream dos = new DataOutputStream(fos);
            dos.writeInt(allocated_cells);
            dos.flush();
        } catch (Exception e) {
            Core.logWarning("Unable to save FZDS info");
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace(); //lern2raii
                }
            }
        }
        
    }
    
}
