package factorization.sockets;

import static org.lwjgl.opengl.GL11.*;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import factorization.shared.*;
import factorization.util.FzUtil;
import factorization.util.InvUtil;
import factorization.util.ItemUtil;
import factorization.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.IChargeConductor;
import factorization.api.datahelpers.DataInNBT;
import factorization.api.datahelpers.DataInPacket;
import factorization.api.datahelpers.DataInPacketClientEdited;
import factorization.api.datahelpers.DataOutNBT;
import factorization.api.datahelpers.DataOutPacket;
import factorization.api.datahelpers.IDataSerializable;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.common.FzConfig;
import factorization.notify.Notice;
import factorization.servo.LoggerDataHelper;
import factorization.servo.RenderServoMotor;
import factorization.servo.ServoMotor;
import factorization.util.InvUtil.FzInv;
import factorization.shared.NetworkFactorization.MessageType;

public abstract class TileEntitySocketBase extends TileEntityCommon implements ISocketHolder, IDataSerializable {
    /*
     * Some notes for when we get these moving on servos:
     * 		These vars need to be set: worldObj, [xyz]Coord, facing
     * 		Some things might call this's ISocketHolder methods rather than the passed in ISocketHolder's methods
     */
    public ForgeDirection facing = ForgeDirection.UP;

    @Override
    public final BlockClass getBlockClass() {
        return BlockClass.Socket;
    }

    @Override
    public final byte getExtraInfo() {
        return (byte) facing.ordinal();
    }

    @Override
    public final void useExtraInfo(byte b) {
        facing = ForgeDirection.getOrientation(b);
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side, float hitX, float hitY, float hitZ) {
        super.onPlacedBy(player, is, side, hitX, hitY, hitZ);
        facing = ForgeDirection.getOrientation(side);
    }
    
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        facing = ForgeDirection.getOrientation(tag.getByte("fc"));
        try {
            serialize("", new DataInNBT(tag));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("fc", (byte) facing.ordinal());
        try {
            serialize("", new DataOutNBT(tag));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * @return false if clean; true if there was something wrong
     */
    public boolean sanitize(ServoMotor motor) {
        LoggerDataHelper dh = new LoggerDataHelper(motor);
        try {
            serialize("", dh);
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }
        return dh.hadError;
    }
    
    protected Iterable<Entity> getEntities(ISocketHolder socket, Coord c, ForgeDirection top, int d) {
        Entity ent = null;
        if (socket instanceof Entity) {
            ent = (Entity) socket;
        }
        int one = 1;
        AxisAlignedBB ab = AxisAlignedBB.getBoundingBox(
                c.x + top.offsetX, c.y + top.offsetY, c.z + top.offsetZ,  
                c.x + one + top.offsetX, c.y + one + top.offsetY, c.z + one + top.offsetZ);
        if (d != 0) {
            ab.minX -= d;
            ab.minY -= d;
            ab.minZ -= d;
            ab.maxX += d;
            ab.maxY += d;
            ab.maxZ += d;
            
            if (top.offsetX + top.offsetY + top.offsetZ > 1) {
                ab.minX += d*top.offsetX;
                ab.minY += d*top.offsetY;
                ab.minZ += d*top.offsetZ;
            } else {
                ab.maxX -= d*top.offsetX;
                ab.maxY -= d*top.offsetY;
                ab.maxZ -= d*top.offsetZ;
            }
        }
        return (Iterable<Entity>)worldObj.getEntitiesWithinAABBExcludingEntity(ent, ab);
    }
    
    protected final boolean rayTrace(final ISocketHolder socket, final Coord coord, final FzOrientation orientation, final boolean powered, final boolean lookAround, final boolean onlyFirst) {
        final ForgeDirection top = orientation.top;
        final ForgeDirection face = orientation.facing;
        final ForgeDirection right = face.getRotation(top);
        
        for (Entity entity : getEntities(socket, coord, top, 0)) {
            if (entity == socket) {
                continue;
            }
            if (handleRay(socket, new MovingObjectPosition(entity), false, powered)) {
                return true;
            }
        }
        
        nullVec.xCoord = nullVec.yCoord = nullVec.zCoord = 0;
        Coord targetBlock = coord.add(top);
        if (mopBlock(targetBlock, top.getOpposite(), socket, false, powered)) return true; //nose-to-nose with the servo
        if (onlyFirst) return false;
        if (mopBlock(targetBlock.add(top), top.getOpposite(), socket, false, powered)) return true; //a block away
        if (mopBlock(coord, top, socket, true, powered)) return true;
        if (!lookAround) return false;
        if (mopBlock(targetBlock.add(face), face.getOpposite(), socket, false, powered)) return true; //running forward
        if (mopBlock(targetBlock.add(face.getOpposite()), face, socket, false, powered)) return true; //running backward
        if (mopBlock(targetBlock.add(right), right.getOpposite(), socket, false, powered)) return true; //to the servo's right
        if (mopBlock(targetBlock.add(right.getOpposite()), right, socket, false, powered)) return true; //to the servo's left
        return false;
    }
    
    private static final Vec3 nullVec = Vec3.createVectorHelper(0, 0, 0);
    boolean mopBlock(Coord target, ForgeDirection side, ISocketHolder socket, boolean mopIsThis, boolean powered) {
        nullVec.xCoord = xCoord + side.offsetX;
        nullVec.yCoord = yCoord + side.offsetY;
        nullVec.zCoord = zCoord + side.offsetZ;
        
        return handleRay(socket, target.createMop(side, nullVec), mopIsThis, powered);
    }
    
    @Override
    public final ForgeDirection[] getValidRotations() {
        return full_rotation_array;
    }
    
    @Override
    public final boolean rotate(ForgeDirection axis) {
        if (getClass() != SocketEmpty.class) {
            return false;
        }
        if (axis == facing) {
            return false;
        }
        facing = axis;
        return true;
    }
    
    @Override
    public boolean isBlockSolidOnSide(int side) {
        return side == facing.getOpposite().ordinal();
    }
    
    @Override
    public final void sendMessage(MessageType msgType, Object ...msg) {
        broadcastMessage(null, msgType, msg);
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ForgeDirection dir) {
        if (dir == facing || dir.getOpposite() == facing) {
            return BlockIcons.socket$face;
        }
        return BlockIcons.socket$side;
    }
    
    protected boolean isBlockPowered() {
        if (FzConfig.sockets_ignore_front_redstone) {
            for (ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
                if (fd == facing) continue;
                if (worldObj.getIndirectPowerLevelTo(xCoord + fd.offsetX, yCoord + fd.offsetY, zCoord + fd.offsetZ, fd.ordinal()) > 0) return true;
            }
            return false;
        } else {
            return worldObj.getBlockPowerInput(xCoord, yCoord, zCoord) > 0;
        }
    }
    
    @Override
    public boolean dumpBuffer(List<ItemStack> buffer) {
        if (buffer.size() == 0) {
            return false;
        }
        ItemStack is = buffer.get(0);
        if (is == null) {
            buffer.remove(0);
            return true;
        }
        Coord here = getCoord();
        here.adjust(facing.getOpposite());
        IInventory invTe = here.getTE(IInventory.class);
        if (invTe == null) {
            return true;
        }
        FzInv inv = InvUtil.openInventory(invTe, facing);
        if (inv == null) {
            return true;
        }
        int origSize = is.stackSize;
        int newSize = 0;
        if (inv.push(is) == null) {
            buffer.remove(0);
        } else {
            newSize = is.stackSize;
        }
        if (origSize != newSize) {
            markDirty();
        }
        return !buffer.isEmpty();
    }
    
    @Override
    public void updateEntity() {
        genericUpdate(this, getCoord(), isBlockPowered());
    }
    
    @Override
    protected void onRemove() {
        super.onRemove();
        uninstall();
        FactoryType ft = getFactoryType();
        Coord at = getCoord();
        while (ft != null) {
            TileEntitySocketBase sb = (TileEntitySocketBase) ft.getRepresentative();
            ItemStack is = sb.getCreatingItem();
            if (is != null) {
                at.spawnItem(ItemStack.copyItemStack(is));
            }
            ft = sb.getParentFactoryType();
        }
    }
    
    @Override
    public ItemStack getPickedBlock() {
        if (this instanceof SocketEmpty) {
            return FactoryType.SOCKET_EMPTY.itemStack();
        }
        ItemStack is = getCreatingItem();
        return is == null ? null : ItemStack.copyItemStack(is);
    }
    
    @Override
    public ItemStack getDroppedBlock() {
        return FactoryType.SOCKET_EMPTY.itemStack();
    }
    
    private static float[] pitch = new float[] {-90, 90, 0, 0, 0, 0, 0};
    private static float[] yaw = new float[] {0, 0, 180, 0, 90, -90, 0};
    
    protected EntityPlayer getFakePlayer() {
        EntityPlayer player = PlayerUtil.makePlayer(getCoord(), "socket");
        player.worldObj = worldObj;
        player.prevPosX = player.posX = xCoord + 0.5 + facing.offsetX;
        player.prevPosY = player.posY = yCoord + 0.5 - player.getEyeHeight() + facing.offsetY;
        player.prevPosZ = player.posZ = zCoord + 0.5 + facing.offsetZ;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            player.inventory.mainInventory[i] = null;
        }
        
        int i = facing.ordinal();
        player.rotationPitch = player.prevRotationPitch = pitch[i];
        player.rotationYaw = player.prevRotationYaw = yaw[i];
        player.limbSwingAmount = 0;
        
        return player;
    }
    
    protected IInventory getBackingInventory(ISocketHolder socket) {
        if (socket == this) {
            TileEntity te = worldObj.getTileEntity(xCoord - facing.offsetX,yCoord - facing.offsetY,zCoord - facing.offsetZ);
            if (te instanceof IInventory) {
                return (IInventory) te;
            }
            return null;
        } else if (socket instanceof IInventory) {
            return (IInventory) socket;
        }
        return null;
    }
    
    @Override
    public boolean extractCharge(int amount) {
        if (this instanceof IChargeConductor) {
            IChargeConductor cc = (IChargeConductor) this;
            return cc.getCharge().tryTake(amount) >= amount;
        }
        return false;
    }
    
    //Overridable code
    
    public void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) { }
    
    @Override
    public abstract FactoryType getFactoryType();
    public abstract ItemStack getCreatingItem();
    public abstract FactoryType getParentFactoryType();
    
    @Override
    public abstract boolean canUpdate();
    
    @SideOnly(Side.CLIENT)
    @Override
    public boolean handleMessageFromServer(MessageType messageType, DataInput input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == MessageType.OpenDataHelperGui) {
            if (!worldObj.isRemote) {
                return false;
            }
            DataInPacket dip = new DataInPacket(input, Side.CLIENT);
            serialize("", dip);
            Minecraft.getMinecraft().displayGuiScreen(new GuiDataConfig(this));
            return true;
        }
        return false;
    }
    
    @Override
    public boolean handleMessageFromClient(MessageType messageType, DataInput input) throws IOException {
        if (super.handleMessageFromClient(messageType, input)) {
            return true;
        }
        if (messageType == MessageType.DataHelperEdit) {
            DataInPacketClientEdited di = new DataInPacketClientEdited(input);
            this.serialize("", di);
            markDirty();
            return true;
        }
        return false;
    }
    
    /**
     * return true if mop-searching should stop
     */
    public boolean handleRay(ISocketHolder socket, MovingObjectPosition mop, boolean mopIsThis, boolean powered) {
        return true;
    }
    
    /**
     * Called when the socket is removed from a servo motor
     */
    public void uninstall() { }
    
    @Override
    public boolean activate(EntityPlayer player, ForgeDirection side) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() == Core.registry.logicMatrixProgrammer) {
            if (!getFactoryType().hasGui) {
                if (getClass() == SocketEmpty.class) {
                    facing = FzUtil.shiftEnum(facing, ForgeDirection.VALID_DIRECTIONS, 1);
                    if (worldObj.isRemote) {
                        new Coord(this).redraw();
                    }
                    return true;
                }
                return false;
            }
            if (worldObj.isRemote) {
                return true;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            DataOutPacket dop = new DataOutPacket(dos, Side.SERVER);
            try {
                Coord coord = getCoord();
                Core.network.prefixTePacket(dos, coord, MessageType.OpenDataHelperGui);
                serialize("", dop);
                Core.network.broadcastPacket(player, coord, FzNetDispatch.generate(baos));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        } else if (held != null) {
            boolean isValidItem = false;
            if (ItemUtil.identical(getCreatingItem(), held)) return false;
            for (FactoryType ft : FactoryType.values()) {
                TileEntityCommon tec = ft.getRepresentative();
                if (tec == null) continue;
                if (!(tec instanceof TileEntitySocketBase)) continue;
                TileEntitySocketBase rep = (TileEntitySocketBase) tec;
                final ItemStack creator = rep.getCreatingItem();
                if (creator != null && ItemUtil.couldMerge(held, creator)) {
                    isValidItem = true;
                    if (worldObj.isRemote) {
                        break;
                    }
                    if (rep.getParentFactoryType() != getFactoryType()) {
                        rep.mentionPrereq(this, player);
                        return false;
                    }
                    TileEntityCommon upgrade = ft.makeTileEntity();
                    if (upgrade == null) continue;
                    
                    replaceWith((TileEntitySocketBase) upgrade, this);
                    if (!player.capabilities.isCreativeMode) held.stackSize--;
                    Sound.socketInstall.playAt(this);
                    return true;
                }
            }
            if (isValidItem) {
                return false;
            }
        }
        return false;
    }
    
    public void mentionPrereq(ISocketHolder holder, EntityPlayer player) {
        FactoryType pft = getParentFactoryType();
        if (pft == null) return;
        TileEntityCommon tec = pft.getRepresentative();
        if (!(tec instanceof TileEntitySocketBase)) return;
        ItemStack is = ((TileEntitySocketBase) tec).getCreatingItem();
        if (is == null) return;
        String msg = "Needs {ITEM_NAME}";
        new Notice(holder, msg).withItem(is).send(player);
    }
    
    protected void replaceWith(TileEntitySocketBase replacement, ISocketHolder socket) {
        invalidate();
        replacement.facing = facing;
        if (socket == this) {
            Coord at = getCoord();
            at.setTE(replacement);
            replacement.getBlockClass().enforce(at);
            at.markBlockForUpdate();
            Core.network.broadcastPacket(null, at, replacement.getDescriptionPacket());
        } else if (socket instanceof ServoMotor) {
            ServoMotor motor = (ServoMotor) socket;
            motor.socket = replacement;
            motor.syncWithSpawnPacket();
        }
    }
    
    public boolean activateOnServo(EntityPlayer player, ServoMotor motor) {
        if (getWorldObj() == null /* wtf? */ || getWorldObj().isRemote) {
            return false;
        }
        ItemStack held = player.getHeldItem();
        if (held == null) {
            return false;
        }
        if (held.getItem() != Core.registry.logicMatrixProgrammer) {
            return false;
        }
        if (!getFactoryType().hasGui) {
            return false;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        DataOutPacket dop = new DataOutPacket(dos, Side.SERVER);
        try {
            Coord coord = getCoord();
            Core.network.prefixEntityPacket(dos, motor, MessageType.OpenDataHelperGuiOnEntity);
            serialize("", dop);
            Core.network.broadcastPacket(player, coord, Core.network.entityPacket(baos));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public void onEnterNewBlock() { }
    
    @SideOnly(Side.CLIENT)
    public void renderTesr(ServoMotor motor, float partial) {}
    
    @SideOnly(Side.CLIENT)
    public void renderStatic(ServoMotor motor, Tessellator tess) {}
    
    @SideOnly(Side.CLIENT)
    public void renderInServo(ServoMotor motor, float partial) {
        float s = 12F/16F;
        GL11.glScalef(s, s, s);
        float d = -0.5F;
        float y = -2F/16F;
        GL11.glTranslatef(d, y, d);
        
        GL11.glDisable(GL_LIGHTING);
        GL11.glPushMatrix();
        renderTesr(motor, partial);
        GL11.glPopMatrix();
        
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        renderStatic(motor, tess);
        tess.draw();
        GL11.glTranslatef(-d, -y, -d);
        GL11.glEnable(GL_LIGHTING);
    }
    
    @SideOnly(Side.CLIENT)
    public void renderItemOnServo(RenderServoMotor render, ServoMotor motor, ItemStack is, float partial) {
        GL11.glPushMatrix();
        GL11.glTranslatef(6.5F/16F, 4.5F/16F, 0);
        GL11.glRotatef(90, 0, 1, 0);
        render.renderItem(is);
        GL11.glPopMatrix();
    }

    public void installedOnServo(ServoMotor servoMotor) { }
}
