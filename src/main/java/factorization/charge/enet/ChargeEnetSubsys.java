package factorization.charge.enet;

import com.google.common.collect.Maps;
import factorization.algos.FastBag;
import factorization.api.energy.EnergyCategory;
import factorization.api.energy.IContext;
import factorization.api.energy.IEnergyNet;
import factorization.api.energy.WorkUnit;
import factorization.flat.api.Flat;
import factorization.flat.api.FlatCoord;
import factorization.flat.api.FlatFace;
import factorization.shared.Core;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ChargeEnetSubsys implements IEnergyNet {
    public static final ChargeEnetSubsys instance = new ChargeEnetSubsys();

    public static final ResourceLocation FZ_CHARGE = new ResourceLocation("factorization:charge");
    public static final WorkUnit CHARGE = WorkUnit.get(EnergyCategory.ELECTRIC, FZ_CHARGE);
    public WireCharge wire0;
    public WireLeader wireLeader;

    public void setup() {
        Core.loadBus(this);
        IEnergyNet.register(this);
        wire0 = new WireCharge();
        Flat.registerStatic(new ResourceLocation("factorization:charge/wire"), wire0);
        wireLeader = new WireLeader();
        Flat.registerDynamic(new ResourceLocation("factorization:charge/wire_leader"), wireLeader);
    }

    @Override
    public boolean canHandlePower(WorkUnit unit) {
        return CHARGE.equals(unit);
    }

    @Override
    public boolean propagatePower(IContext generator, WorkUnit unit) {
        return false;
    }

    @Override
    public void workerAdded(IContext context, WorkUnit unit) {

    }

    @Override
    public void workerDestroyed(IContext context) {

    }

    @Override
    public void workerNeedsPower(IContext context) {

    }


    final Map<World, List<FlatCoord>> tickList = Maps.newConcurrentMap();
    public void registerLeader(FlatCoord at) {
        List<FlatCoord> list = tickList.get(at.at.w);
        if (list == null) {
            list = FastBag.create();
            tickList.put(at.at.w, list);
        }
        list.add(at);
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        List<FlatCoord> tickables = tickList.get(event.world);
        if (tickables == null) return;
        for (Iterator<FlatCoord> iter = tickables.iterator(); iter.hasNext(); ) {
            FlatCoord fc = iter.next();
            if (!fc.exists()) {
                iter.remove();
                continue;
            }
            FlatFace face = fc.get();
            if (face.getSpecies() != WireLeader.SPECIES || !(face instanceof WireLeader)) {
                iter.remove();
                continue;
            }
            ((WireLeader) face).tick(fc);
        }
    }
}
