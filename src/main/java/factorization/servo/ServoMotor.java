package factorization.servo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.IChargeConductor;
import factorization.api.IEntityMessage;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.DataInNBT;
import factorization.api.datahelpers.DataInPacket;
import factorization.api.datahelpers.DataInPacketClientEdited;
import factorization.api.datahelpers.DataOutNBT;
import factorization.api.datahelpers.DataOutPacket;
import factorization.api.datahelpers.Share;
import factorization.common.FactoryType;
import factorization.shared.Core;
import factorization.shared.FzNetDispatch;
import factorization.shared.FzUtil;
import factorization.shared.FzUtil.FzInv;
import factorization.shared.NetworkFactorization.MessageType;
import factorization.shared.Sound;
import factorization.shared.TileEntityCommon;
import factorization.sockets.GuiDataConfig;
import factorization.sockets.ISocketHolder;
import factorization.sockets.SocketEmpty;
import factorization.sockets.TileEntitySocketBase;

public class ServoMotor extends Entity implements IEntityAdditionalSpawnData, IEntityMessage, IInventory, ISocketHolder {
    public final MotionHandler motionHandler = new MotionHandler(this);
    public Executioner executioner = new Executioner(this);
    public TileEntitySocketBase socket = new SocketEmpty();
    public boolean isSocketActive = false;
    public boolean isSocketPulsed = false;
    
    ItemStack[] inv = new ItemStack[1], inv_last_sent = new ItemStack[inv.length];
    
    public ServoMotor(World world) {
        super(world);
        setSize(1, 1);
        isImmuneToFire = true;
    }
    
    /**
     * You <b>must</b> call this method instead of using worldObj.spawnEntityInWorld!
     */
    public void spawnServoMotor() {
        motionHandler.beforeSpawn();
        worldObj.spawnEntityInWorld(this);
    }

    public void syncWithSpawnPacket() {
        if (worldObj.isRemote) return;
        Packet p = FMLNetworkHandler.getEntitySpawningPacket(this);
        FzNetDispatch.addPacketFrom(p, this);
    }
    
    
    
    // Serialization
    
    @Override
    protected void readEntityFromNBT(NBTTagCompound nbttagcompound) {
        try {
            putData(new DataInNBT(nbttagcompound));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void writeEntityToNBT(NBTTagCompound nbttagcompound) {
        try {
            putData(new DataOutNBT(nbttagcompound));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void readSpawnData(ByteBuf data) {
        try {
            putData(new DataInPacket(new ByteBufInputStream(data), Side.CLIENT));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace(); //Hrm! Why? (I mean, besides the obvious.)
        }
    }

    @Override
    public void writeSpawnData(ByteBuf data) {
        try {
            putData(new DataOutPacket(new ByteBufOutputStream(data), Side.SERVER));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace(); //Hrm! Why? (I mean, besides the obvious.)
        }
    }

    void putData(DataHelper data) throws IOException {
        executioner.putData(data);
        motionHandler.putData(data);
        
        final byte invSize = data.as(Share.VISIBLE, "inv#").putByte((byte) inv.length);
        resizeInventory(invSize);
        for (int i = 0; i < invSize; i++) {
            ItemStack is = inv[i] == null ? EMPTY_ITEM : inv[i];
            is = data.as(Share.VISIBLE, "inv" + i).putItemStack(is);
            if (is == null) {
                inv[i] = is;
            } else {
                inv[i] = is.getItem() == null ? null : is;
            }
        }
        data.as(Share.VISIBLE, "sock");
        if (data.isReader()) {
            NBTTagCompound tag = data.putTag(new NBTTagCompound());
            TileEntity te = TileEntity.createAndLoadEntity(tag);
            if (te instanceof TileEntitySocketBase) {
                socket = (TileEntitySocketBase) te;
            } else {
                socket = new SocketEmpty();
            }
        } else {
            NBTTagCompound output = new NBTTagCompound();
            socket.writeToNBT(output);
            data.putTag(output);
        }
        isSocketActive = data.as(Share.VISIBLE, "sockon").putBoolean(isSocketActive);
        isSocketPulsed = data.as(Share.VISIBLE, "sockpl").putBoolean(isSocketPulsed);
    }
    
    
    
    
    // Networking
    
    void broadcast(MessageType message_type, Object... msg) {
        FMLProxyPacket p = Core.network.entityPacket(this, message_type, msg);
        Core.network.broadcastPacket(null, getCurrentPos(), p);
    }
    
    public void broadcastBriefUpdate() {
        Coord a = getCurrentPos();
        Coord b = getNextPos();
        broadcast(MessageType.servo_brief, (byte) motionHandler.orientation.ordinal(), motionHandler.speed_b,
                a.x, a.y, a.z,
                b.x, b.y, b.z,
                motionHandler.pos_progress);
    }
    
    @Override
    public boolean handleMessageFromClient(MessageType messageType, DataInput input) throws IOException {
        if (messageType == MessageType.DataHelperEditOnEntity) {
            DataInPacketClientEdited di = new DataInPacketClientEdited(input);
            socket.serialize("", di);
            markDirty();
            return true;
        }
        return false;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public boolean handleMessageFromServer(MessageType messageType, DataInput input) throws IOException {
        switch (messageType) {
        case OpenDataHelperGuiOnEntity:
            if (!worldObj.isRemote) {
                return false;
            } else {
                DataInPacket dip = new DataInPacket(input, Side.CLIENT);
                socket.serialize("", dip);
                Minecraft.getMinecraft().displayGuiScreen(new GuiDataConfig(socket, this));
            }
            return true;
        case servo_item:
            while (true) {
                byte index = input.readByte();
                if (index < 0) {
                    break;
                }
                inv[index] = FzUtil.readStack(input);
            }
            return true;
        case servo_brief:
            Coord a = getCurrentPos();
            Coord b = getNextPos();
            FzOrientation no = FzOrientation.getOrientation(input.readByte());
            if (no != motionHandler.prevOrientation) {
                motionHandler.orientation = no;
            }
            motionHandler.speed_b = input.readByte();
            a.x = input.readInt();
            a.y = input.readInt();
            a.z = input.readInt();
            b.x = input.readInt();
            b.y = input.readInt();
            b.z = input.readInt();
            motionHandler.pos_progress = input.readFloat();
            if (motionHandler.speed_b > 0) {
                motionHandler.stopped = false;
            }
            return true;
        case servo_complete:
            try {
                DataHelper data = new DataInPacket(input, Side.CLIENT);
                putData(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        case servo_stopped:
            motionHandler.stopped = input.readBoolean();
            return true;
        case TileEntityMessageOnEntity:
            MessageType subMsg = MessageType.read(input);
            return socket.handleMessageFromServer(subMsg, input);
        default:
            return socket.handleMessageFromServer(messageType, input);
        }
    }
    
    
    
    
    
    
    // Main logic
    
    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        if (isDead) {
            return;
        }
        if (worldObj.isRemote) {
            motionHandler.updateServoMotion();
            executioner.tick();
        } else {
            byte orig_speed = motionHandler.speed_b;
            FzOrientation orig_or = motionHandler.orientation;
            motionHandler.updateServoMotion();
            executioner.tick();
            if (orig_speed != motionHandler.speed_b || orig_or != motionHandler.orientation) {
                broadcastBriefUpdate();
                //NOTE: Could be spammy. Speed might be too important to not send tho.
            }
            
            if (executioner.stacks_changed) {
                try {
                    executioner.stacks_changed = false;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    Core.network.prefixEntityPacket(dos, this, MessageType.servo_complete);
                    DataHelper data = new DataOutPacket(dos, Side.SERVER);
                    putData(data);
                    FMLProxyPacket toSend = Core.network.entityPacket(baos);
                    Core.network.broadcastPacket(null, getCurrentPos(), toSend);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    void updateSocket() {
        Coord here = getCurrentPos();
        here.setAsTileEntityLocation(socket);
        socket.facing = motionHandler.orientation.top;
        socket.genericUpdate(this, here, isSocketActive ^ isSocketPulsed);
        isSocketPulsed = false;
    }
    
    void onEnterNewBlock() {
        if (worldObj.isRemote) {
            return;
        }
        socket.onEnterNewBlock();
        motionHandler.onEnterNewBlock();
        TileEntityServoRail rail = getCurrentPos().getTE(TileEntityServoRail.class);
        if (rail != null && rail.decoration != null) {
            if (rail.decoration.preMotorHit(this)) {
                return;
            }
        }
        executioner.onEnterNewBlock(rail);
    }
    
    
    
    
    
    // Utility functions for client code
    
    public ServoStack getArgStack() {
        return executioner.getArgStack();
    }
    
    public ServoStack getInstructionsStack() {
        return executioner.getInstructionStack();
    }
    
    public void putError(Object error) {
        executioner.putError(error);
    }

    public Coord getCurrentPos() {
        return motionHandler.pos_prev;
    }
    
    public Coord getNextPos() {
        return motionHandler.pos_next;
    }

    public FzOrientation getOrientation() {
        return motionHandler.orientation;
    }
    
    public void setOrientation(FzOrientation orientation) {
        motionHandler.orientation = orientation;
    }
    
    public void changeOrientation(ForgeDirection fd) {
        motionHandler.changeOrientation(fd);
    }
    
    public void setNextDirection(ForgeDirection direction) {
        motionHandler.nextDirection = direction;
    }
    
    public void setTargetSpeed(byte newTarget) {
        motionHandler.setTargetSpeed((byte) (newTarget - 1));
    }
    
    public byte getTargetSpeed() {
        return (byte) (motionHandler.target_speed_index + 1);
    }
    
    public void penalizeSpeed() {
        motionHandler.penalizeSpeed();
    }
    
    public void setStopped(boolean stop) {
        motionHandler.setStopped(stop);
    }
    
    public boolean isStopped() {
        return motionHandler.stopped;
    }
    
    
    
    
    
    
    
    
    // Entity behavior
    
    @Override
    protected void entityInit() { }
    
    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (worldObj.isRemote) return true;
        executioner.stacks_changed = true;
        ItemStack is = FzUtil.normalize(player.getHeldItem());
        if (is == null) {
            return false;
        }
        Item item = is.getItem();
        if (item instanceof ItemServoRailWidget) {
            ServoComponent sc = ServoComponent.fromItem(is);
            if (player.isSneaking()) {
                if (!sc.onClick(player, this)) {
                    return false;
                }
                ItemStack updated = sc.toItem();
                is.setItemDamage(updated.getItemDamage());
                is.setTagCompound(updated.getTagCompound());
                return true;
            } else {
                if (sc instanceof Decorator) {
                    Decorator dec = (Decorator) sc;
                    dec.motorHit(this);
                    return true;
                }
            }
        }
        if (socket == null) return false;
        if (socket.activateOnServo(player, this)) return false;
        for (FactoryType ft : FactoryType.values()) {
            TileEntityCommon tec = ft.getRepresentative();
            if (tec == null) continue;
            if (!(tec instanceof TileEntitySocketBase)) continue;
            TileEntitySocketBase rep = (TileEntitySocketBase) tec;
            ItemStack creator = rep.getCreatingItem();
            if (creator != null && FzUtil.couldMerge(is, creator)) {
                if (rep.getParentFactoryType() != socket.getFactoryType()) {
                    rep.mentionPrereq(this);
                    return false;
                }
                TileEntityCommon upgrade = ft.makeTileEntity();
                if (upgrade != null) {
                    socket = (TileEntitySocketBase) upgrade;
                    if (!player.capabilities.isCreativeMode) is.stackSize--;
                    Sound.servoInstall.playAt(new Coord(this));
                    socket.installedOnServo(this);
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean attackEntityFrom(DamageSource damageSource, float damage) {
        if (damageSource instanceof EntityDamageSourceIndirect) {
            return false;
        }
        Entity src = damageSource.getSourceOfDamage();
        if (!(src instanceof EntityPlayer)) {
            return false;
        }
        if (isDead) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) src;
        despawn(player.capabilities.isCreativeMode);
        return true;
    }
    
    void despawn(boolean creativeModeHit) {
        if (worldObj.isRemote) {
            return;
        }
        setDead();
        if (creativeModeHit) {
            return;
        }
        ArrayList<ItemStack> toDrop = new ArrayList();
        toDrop.add(new ItemStack(Core.registry.servo_placer));
        for (ItemStack is : inv) {
            toDrop.add(is);
        }
        if (socket != null) {
            socket.uninstall();
            FactoryType ft = socket.getFactoryType();
            while (ft != null) {
                TileEntitySocketBase sb = (TileEntitySocketBase) ft.getRepresentative();
                final ItemStack is = sb.getCreatingItem();
                if (is != null) toDrop.add(is.copy());
                ft = sb.getParentFactoryType();
            }
        }
        dropItemStacks(toDrop);
    }
    
    public void dropItemStacks(Iterable<ItemStack> toDrop) {
        for (ItemStack is : toDrop) {
            FzUtil.spawnItemStack(this, is);
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int three) {
        // The servers sends crappy packets. TODO: Sure would be nice if it didn't send those packets...
    }

    @Override
    public void setPosition(double x, double y, double z) {
        // super.setPosition(x, y, z); //Super does some stupid shit to the bounding box.
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        double dp = 1;
        this.boundingBox.setBounds(x, y, z, x + dp, y + dp, z + dp);
        /*
        double neg_size = -0.25;
        double pos_size = 0.75;
        double height = 2F/16F;
        double dy = 0.5;
        this.boundingBox.setBounds(x - neg_size, dy + y - height, z - neg_size, x + pos_size, dy + y + height, z + pos_size);
        */
    }

    @Override
    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        super.setPositionAndRotation(x, y, z, yaw, pitch);
    }
    
    public void resizeInventory(int newSize) {
        if (newSize == inv.length) return;
        ItemStack[] origInv = inv;
        int min = Math.min(newSize, origInv.length);
        inv = new ItemStack[newSize];
        for (int i = 0; i < min; i++) {
            inv[i] = origInv[i];
            origInv[i] = null;
        }
        for (ItemStack is : origInv) {
            if (is != null) {
                getCurrentPos().spawnItem(is);
            }
        }
        inv_last_sent = new ItemStack[newSize];
    }

    
    
    
    
    // IInventory implementation
    
    @Override
    public int getSizeInventory() {
        return inv.length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return inv[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int j) {
        ItemStack ret = inv[i].splitStack(j);
        inv[i] = FzUtil.normalize(inv[i]);
        return ret;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack) {
        inv[i] = itemstack;
    }

    @Override
    public String getInventoryName() {
        return "Servo Motor Inventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    private static final ItemStack EMPTY_ITEM = new ItemStack((Item)null);
    
    @Override
    public void markDirty() {
        ArrayList<Object> toSend = new ArrayList(inv.length*2);
        for (byte i = 0; i < inv.length; i++) {
            if (FzUtil.identical(inv[i], inv_last_sent[i])) {
                continue;
            }
            toSend.add(i);
            toSend.add(inv[i] == null ? EMPTY_ITEM : inv[i]);
            inv_last_sent[i] = inv[i];
        }
        if (toSend.isEmpty()) {
            return;
        }
        toSend.add(-1);
        broadcast(MessageType.servo_item, toSend.toArray());
        getCurrentPos().getChunk().setChunkModified();
        getNextPos().getChunk().setChunkModified();
    }
    
    @Override
    public boolean isUseableByPlayer(EntityPlayer entityplayer) {
        return false;
    }

    @Override
    public void openInventory() { }

    @Override
    public void closeInventory() { }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        return true;
    }

    
    
    
    
    
    // ISocketHolder implementation
    
    private final ArrayList<MovingObjectPosition> ret = new ArrayList<MovingObjectPosition>();
    
    ArrayList<MovingObjectPosition> rayTrace() {
        ret.clear();
        final Coord c = getCurrentPos();
        final ForgeDirection top = motionHandler.orientation.top;
        final ForgeDirection face = motionHandler.orientation.facing;
        final ForgeDirection right = face.getRotation(top);
        
        AxisAlignedBB ab = AxisAlignedBB.getAABBPool().getAABB(
                c.x + top.offsetX, c.y + top.offsetY, c.z + top.offsetZ,  
                c.x + 1 + top.offsetX, c.y + 1 + top.offsetY, c.z + 1 + top.offsetZ);
        for (Entity entity : (Iterable<Entity>)worldObj.getEntitiesWithinAABBExcludingEntity(this, ab)) {
            if (!entity.canBeCollidedWith()) {
                continue;
            }
            ret.add(new MovingObjectPosition(entity));
        }
        
        nullVec.xCoord = nullVec.yCoord = nullVec.zCoord = 0;
        Coord targetBlock = c.add(top);
        mopBlock(ret, targetBlock, top.getOpposite()); //nose-to-nose with the servo
        mopBlock(ret, targetBlock.add(top), top.getOpposite()); //a block away
        mopBlock(ret, targetBlock.add(top.getOpposite()), top);
        if (ret.size() == 0) {
            mopBlock(ret, targetBlock.add(face), face.getOpposite()); //running forward
            mopBlock(ret, targetBlock.add(face.getOpposite()), face); //running backward
            if (ret.size() == 0) {
                mopBlock(ret, targetBlock.add(right), right.getOpposite()); //to the servo's right
                mopBlock(ret, targetBlock.add(right.getOpposite()), right); //to the servo's left
            }
        }
        return ret;
    }
    
    private static final Vec3 nullVec = Vec3.createVectorHelper(0, 0, 0);
    void mopBlock(ArrayList<MovingObjectPosition> list, Coord target, ForgeDirection side) {
        if (target.isAir()) {
            return;
        }
        list.add(target.createMop(side, nullVec));
    }
    
    @Override
    public boolean dumpBuffer(List<ItemStack> buffer) {
        if (buffer.isEmpty()) {
            return false;
        }
        FzInv me = FzUtil.openInventory(this, false);
        ItemStack got = buffer.get(0);
        if (got == null) {
            buffer.remove(0);
            return true;
        }
        ItemStack res = me.push(buffer.get(0));
        if (res == null) {
            buffer.remove(0);
        } else {
            buffer.set(0, res);
        }
        return true;
    }
    
    @Override
    public boolean extractCharge(int amount) {
        IChargeConductor wire = getCurrentPos().getTE(IChargeConductor.class);
        if (wire == null) {
            return false;
        }
        return wire.getCharge().tryTake(amount) >= amount;
    }
    
    @Override
    public void sendMessage(MessageType msgType, Object... msg) {
        Object[] buff = new Object[msg.length + 1];
        System.arraycopy(msg, 0, buff, 1, msg.length);
        buff[0] = msgType;
        FMLProxyPacket toSend = Core.network.entityPacket(this, MessageType.TileEntityMessageOnEntity, buff);
        Core.network.broadcastPacket(null, getCurrentPos(), toSend); 
    }
    
}
