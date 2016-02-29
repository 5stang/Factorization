package factorization.flat;

import factorization.api.Coord;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public final class FlatFaceAir extends FlatFace {
    public static final FlatFace INSTANCE = new FlatFaceAir();

    @Override
    @Nullable
    public IFlatModel getModel(Coord at, EnumFacing side) {
        return null;
    }

    @Override
    public void loadModels(IModelMaker uhm) {
    }

    @Override
    public boolean isReplaceable(Coord at, EnumFacing side) {
        return true;
    }

    @Override
    public boolean isNull() {
        return true;
    }
}
