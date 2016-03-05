package factorization.api.energy;

/**
 * <p>An {@link IWorker} is a thing that can accept {@link WorkUnit}s.</p>
 * <p>The energy net might not be prompt in handing the worker another WorkUnit when the worker needs it. For this reason,
 * some kinds of workers may want to buffer a single WorkUnit so that they can immediately continue working after
 * finishing a task.</p>
 * <p/>
 * This energy API does <b>NOT</b> need to be exhaustively implemented, neither by workers nor by energy nets.
 * (Eg, battery block items are not obliged to be chargable, and wires are not obliged to spark power over to adjacent
 * entities.)
 *
 * @param <T> An {@link IContext}, such as {@link ContextBlock}, {@link ContextEntity}, {@link ContextItemStack},
 *           or {@link ContextTileEntity}.
 */
public interface IWorker<T extends IContext> {
    /**
     * Call this when the IWorker has been removed from the world.
     * This need not be the same IContext object as used with {@link IWorker#construct(IContext)}, but it must
     * have been constructed with the same arguments.
     * <p/>
     * If the units that an IWorker needs changes, then invalidate() followed by {@link IWorker#construct(IContext)}.
     */
    public static void invalidate(IContext context) {
        WorkerBoss.invalidateWorker(context);
    }

    /**
     * Call this when the IWorker has been constructed. This is safe to do with TileEntities.
     */
    public static void construct(IContext context) {
        WorkerBoss.addWorker(context);
    }

    /**
     * Call this when the IWorker needs power.
     */
    public static void requestPower(IContext context) {
        WorkerBoss.needsPower(context);
    }

    enum Accepted {
        /**
         * The worker can not accept the unit because it is incompatible.
         */
        NEVER,

        /**
         * The worker could accept the unit, but has a full buffer or something.
         */
        LATER,

        /**
         * The worker has room in its buffer for the unit.
         */
        NOW
    }

    /**
     * Receive a {@link WorkUnit} and get one unit of work done.
     * <p/>
     * Power may be given without simulate being checked first.
     * For this reason, WorkUnits should be discarded if there is nowhere for them to fit.
     * <p/>
     * Returning something other than NEVER may imply unit-specific ramifications; for example, rotational power may
     * want you to send a packet to the client to keep your gears rendering in sync with the driver. Such details can be
     * made accessible to the Worker via a custom WorkUnit class.
     *
     * @param context  The object containing information about what 'this' is and how it is being accessed. Often ignorable.
     * @param unit     The {@link WorkUnit}. You'll want to compare its category field to an {@link EnergyCategory} instance.
     * @param simulate if simulate is true, then this is a querying if the IWorker can handle the unit.
     * @return
     *      NEVER if the worker can't handle the unit.
     *      LATER if the worker can generally use it, but not right now.
     *      NOW if the worker can use the unit immediately,
     *      or NOW if simulate is true.
     */
    Accepted accept(T context, WorkUnit unit, boolean simulate);
}
