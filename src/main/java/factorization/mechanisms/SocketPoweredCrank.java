package factorization.mechanisms;

import factorization.api.*;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.fzds.interfaces.IDeltaChunk;
import factorization.servo.ServoMotor;
import factorization.shared.Core;
import factorization.shared.EntityReference;
import factorization.shared.FactorizationBlockRender;
import factorization.sockets.ISocketHolder;
import factorization.sockets.SocketBareMotor;
import factorization.sockets.TileEntitySocketBase;
import factorization.util.NumUtil;
import factorization.util.SpaceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public class SocketPoweredCrank extends TileEntitySocketBase implements IChargeConductor {
    private Charge charge = new Charge(this);
    final EntityReference<IDeltaChunk> hookedIdc = new EntityReference<IDeltaChunk>();
    Vec3 hookLocation = SpaceUtil.newVec();

    static final float sprocketRadius = 8F / 16F;


    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SOCKET_POWERED_CRANK;
    }

    @Override
    public ItemStack getCreatingItem() {
        return Core.registry.dark_iron_sprocket;
    }

    @Override
    public FactoryType getParentFactoryType() {
        return FactoryType.SOCKET_BARE_MOTOR;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        charge = data.as(Share.PRIVATE, "charge").put(charge);
        data.as(Share.VISIBLE, "hookedEntity");
        hookedIdc.serialize(prefix, data);
        hookLocation = data.as(Share.VISIBLE, "hookLocation").putVec3(hookLocation);
        if (data.isReader() && chainDraw != null) {
            chainDraw.release();
        }
        return this;
    }

    @Override
    public Charge getCharge() {
        return charge;
    }

    @Override
    public String getInfo() {
        return hookedIdc.trackingEntity() ? "Chained" : "Not connected";
    }

    @Override
    public void setWorldObj(World world) {
        super.setWorldObj(world);
        hookedIdc.setWorld(world);
    }

    @Override
    public void updateEntity() {
        charge.update();
        super.updateEntity();
    }

    @Override
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) {
        if (worldObj.isRemote) {
            updateHookDrawPos(socket == null ? this : socket);
        }
    }

    public void setChain(IDeltaChunk idc, Vec3 hookLocation) {
        if (hookedIdc.trackingEntity()) {
            getCoord().spawnItem(Core.registry.darkIronChain);
        }
        hookedIdc.trackEntity(idc);
        this.hookLocation = hookLocation;
        getCoord().syncTE();
    }

    public boolean isChained() {
        return hookedIdc.trackingEntity();
    }

    @Override
    public void uninstall() {
        if (!isChained()) return;
        new Coord(this).spawnItem(Core.registry.darkIronChain);
    }

    @Override
    public void renderStatic(ServoMotor motor, Tessellator tess) {
        SocketBareMotor sbm = (SocketBareMotor) FactoryType.SOCKET_BARE_MOTOR.getRepresentative();
        getCoord().setAsTileEntityLocation(sbm);
        sbm.facing = facing;
        sbm.renderStatic(motor, tess);
        sbm.setWorldObj(null); // Don't leak the world (TODO: Unset all worldObj for all representitives when the world unload?)
    }


    ChainLink chainDraw;
    float chainLen, prevChainLen;
    double chainDelta = 0;
    boolean soundActive;
    byte spinSign = +1;

    void updateHookDrawPos(ISocketHolder socket) {
        IDeltaChunk idc = hookedIdc.getEntity();
        if (idc == null) return;
        boolean first = false;
        if (chainDraw == null) {
            chainDraw = ChainRender.instance.add();
            first = true;
        }
        Vec3 realHookLocation = idc.shadow2real(hookLocation);
        Vec3 selfPos = socket.getPos();
        Vec3 point = SpaceUtil.fromDirection(facing);
        Vec3 chainVec = SpaceUtil.subtract(realHookLocation, selfPos);
        Vec3 right = SpaceUtil.scale(point.crossProduct(chainVec).normalize(), sprocketRadius);
        spinSign = (byte) (SpaceUtil.sum(right) > 0 ? +1 : -1);
        SpaceUtil.incrAdd(selfPos, right);
        chainDraw.update(selfPos, realHookLocation);
        float len = (float) SpaceUtil.lineDistance(selfPos, realHookLocation);
        if (first) {
            chainLen = prevChainLen = len;
        } else {
            chainDelta += len - prevChainLen;
            prevChainLen = chainLen;
            chainLen = len;
        }
        if (soundActive) {
            chainDelta /= 2;
            if (Math.abs(chainDelta) < 0.0001) chainDelta = 0;
            return;
        }

        double min = 0.15;
        byte direction = 0;
        if (chainDelta < -min) {
            direction = -1;
        } else if (chainDelta > min) {
            direction = +1;
        } else {
            return;
        }
        Minecraft.getMinecraft().getSoundHandler().playSound(new WinchSound(direction, this));
    }

    @Override
    public Vec3 getPos() {
        double d = 0.5;
        return Vec3.createVectorHelper(xCoord + d, yCoord + d, zCoord + d);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (chainDraw != null) {
            chainDraw.release();
            chainDraw = null;
        }
    }

    @Override
    public void renderTesr(ServoMotor motor, float partial) {
        super.renderTesr(motor, partial);
        float sprocketTheta = 0;
        if (chainDraw != null) {
            float len = NumUtil.interp(prevChainLen, chainLen, partial);
            sprocketTheta = len / sprocketRadius;
        }


        float d = 0.5F;
        GL11.glTranslatef(d, d, d);
        Quaternion.fromOrientation(FzOrientation.fromDirection(facing.getOpposite())).glRotate();
        GL11.glTranslatef(0, -5F/64F, 0);
        GL11.glScalef(1, 2.5F, 1);


        GL11.glRotated(Math.toDegrees(sprocketTheta), 0, 1, 0);
        //TileEntityGrinderRender.renderGrindHead();
        GL11.glRotatef(90, 1, 0, 0);
        GL11.glTranslatef(-0.5F, -0.5F, -0F);
        FactorizationBlockRender.renderItemIIcon(getCreatingItem().getItem().getIconFromDamage(0));
    }
}
