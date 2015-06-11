package factorization.api.crafting;

import java.util.ArrayList;
import java.util.HashMap;

public final class CraftingManagerGeneric<MachineType> {
    private static final HashMap<Class, CraftingManagerGeneric> systems = new HashMap<Class, CraftingManagerGeneric>();

    public static <M> CraftingManagerGeneric<M> get(Class<M> klass) {
        CraftingManagerGeneric<M> ret = systems.get(klass);
        if (ret != null) return ret;
        systems.put(klass, ret = new CraftingManagerGeneric<M>(klass));
        return ret;
    }

    public final ArrayList<IVexatiousCrafting<MachineType>> list = new ArrayList<IVexatiousCrafting<MachineType>>();

    public CraftingManagerGeneric(Class<MachineType> machineClass) {
        systems.put(machineClass, this);
    }

    public IVexatiousCrafting<MachineType> find(MachineType machine) {
        for (IVexatiousCrafting<MachineType> recipe : list) {
            if (recipe.matches(machine)) return recipe;
        }
        return null;
    }

    public void add(IVexatiousCrafting<MachineType> recipe) {
        list.add(recipe);
    }
}
