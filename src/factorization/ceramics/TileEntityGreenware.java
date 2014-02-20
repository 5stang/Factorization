package factorization.ceramics;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3Pool;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeSubscribe;

import org.lwjgl.opengl.GL11;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Coord;
import factorization.api.DeltaCoord;
import factorization.api.Quaternion;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.common.FzConfig;
import factorization.common.ResourceType;
import factorization.notify.Notify;
import factorization.shared.BlockClass;
import factorization.shared.BlockRenderHelper;
import factorization.shared.Core;
import factorization.shared.FzUtil;
import factorization.shared.NetworkFactorization.MessageType;
import factorization.shared.TileEntityCommon;
import factorization.shared.TileEntityExtension;

public class TileEntityGreenware extends TileEntityCommon {
    public static int MAX_PARTS = 32;
    ForgeDirection front = ForgeDirection.UNKNOWN;
    byte rotation = 0;
    Quaternion rotation_quat = Quaternion.getRotationQuaternionRadians(0, ForgeDirection.UP);
    
    @Override
    public FactoryType getFactoryType() {
        return FactoryType.CERAMIC;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Ceramic;
    }

    public static class ClayLump {
        public byte minX, minY, minZ;
        public byte maxX, maxY, maxZ;

        public Block icon_id; // But only for blocks; no items
        public byte icon_md;
        public byte icon_side;

        public Quaternion quat;

        public int raw_color = -1;

        void write(ByteArrayDataOutput out) {
            out.writeByte(minX);
            out.writeByte(minY);
            out.writeByte(minZ);
            out.writeByte(maxX);
            out.writeByte(maxY);
            out.writeByte(maxZ);
            out.writeShort(Block.getIdFromBlock(icon_id));
            out.writeByte(icon_md);
            out.writeByte(icon_side);
            quat.write(out);
        }

        void write(NBTTagCompound tag) {
            tag.setByte("lx", minX);
            tag.setByte("ly", minY);
            tag.setByte("lz", minZ);
            tag.setByte("hx", maxX);
            tag.setByte("hy", maxY);
            tag.setByte("hz", maxZ);
            tag.setShort("icon_id", icon_id);
            tag.setByte("icon_md", icon_md);
            tag.setByte("icon_sd", icon_side);
            quat.writeToTag(tag, "r");
        }

        void write(ArrayList<Object> out) {
            out.add(minX);
            out.add(minY);
            out.add(minZ);
            out.add(maxX);
            out.add(maxY);
            out.add(maxZ);
            out.add(icon_id);
            out.add(icon_md);
            out.add(icon_side);
            out.add(quat);
        }

        ClayLump read(DataInput in) throws IOException {
            minX = in.readByte();
            minY = in.readByte();
            minZ = in.readByte();
            maxX = in.readByte();
            maxY = in.readByte();
            maxZ = in.readByte();
            icon_id = Block.getBlockById(in.readShort());
            icon_md = in.readByte();
            icon_side = in.readByte();
            quat = Quaternion.read(in);
            return this;
        }

        ClayLump read(NBTTagCompound tag) {
            minX = tag.getByte("lx");
            minY = tag.getByte("ly");
            minZ = tag.getByte("lz");
            maxX = tag.getByte("hx");
            maxY = tag.getByte("hy");
            maxZ = tag.getByte("hz");
            icon_id = tag.getShort("icon_id");
            icon_md = tag.getByte("icon_md");
            if (tag.hasKey("icon_sd")) {
                icon_side = tag.getByte("icon_sd");
            } else {
                icon_side = -1;
            }
            quat = Quaternion.loadFromTag(tag, "r");
            return this;
        }

        void offset(int dx, int dy, int dz) {
            minX += dx;
            maxX += dx;
            minY += dy;
            maxY += dy;
            minZ += dz;
            maxZ += dz;
        }

        ClayLump asDefault() {
            minX = minZ = 4;
            minY = 0;
            maxX = maxZ = 16 - 4;
            maxY = 10;
            offset(16, 16 + 1, 16);
            icon_id = (short) FzConfig.resource_id;
            icon_md = (byte) ResourceType.BISQUE.md;
            icon_side = -1;
            quat = new Quaternion();
            return this;
        }

        public void toBlockBounds(Block b) {
            b.setBlockBounds((minX - 16) / 16F, (minY - 16) / 16F, (minZ - 16) / 16F, (maxX - 16) / 16F, (maxY - 16) / 16F, (maxZ - 16) / 16F);
        }

        public void toRotatedBlockBounds(TileEntityGreenware gw, BlockRenderHelper b) {
            toBlockBounds(b);
            b.beginNoIIcons();
            b.rotateMiddle(quat);
            b.rotateCenter(gw.rotation_quat);
            b.setBlockBoundsBasedOnRotation();
            
            // TODO: This doesn't work! Lame!
            /*b.beginNoIIcons();
            b.rotateMiddle(quat);
            b.rotateCenter(gw.rotation_quat);
            b.setBlockBoundsBasedOnRotation();
            // */
        }

        public ClayLump copy() {
            ClayLump ret = new ClayLump();
            ret.minX = minX;
            ret.minY = minY;
            ret.minZ = minZ;
            ret.maxX = maxX;
            ret.maxY = maxY;
            ret.maxZ = maxZ;
            ret.icon_id = icon_id;
            ret.icon_md = icon_md;
            ret.quat = new Quaternion(quat);
            return ret;
        }


    }

    public ArrayList<ClayLump> parts = new ArrayList();
    public int lastTouched = 0;
    public int totalHeat = 0;
    boolean glazesApplied = false;
    private boolean partsValidated = false;

    public static int dryTime = 20 * 60 * 2; // 2 minutes
    public static int bisqueHeat = 1000, highfireHeat = bisqueHeat * 10;

    // Client-side only
    public boolean shouldRenderTesr = false;

    public static enum ClayState {
        WET("Wet Clay"), DRY("Bone-Dry Greenware"), BISQUED("Bisqued"), UNFIRED_GLAZED("Glazed Bisqueware"), HIGHFIRED("Highfire Glazed");
        public String english;

        ClayState(String en) {
            this.english = en;
        }
    };

    public TileEntityGreenware() {
    }

    public ClayState getState() {
        if (totalHeat > highfireHeat) {
            return ClayState.HIGHFIRED;
        }
        if (totalHeat > bisqueHeat) {
            if (glazesApplied) {
                return ClayState.UNFIRED_GLAZED;
            }
            return ClayState.BISQUED;
        }
        if (lastTouched > dryTime) {
            return ClayState.DRY;
        }
        return ClayState.WET;
    }

    public IIcon getIcon(ClayLump lump, int side) {
        //NOTE: This isn't what's actually used for rendering.
        switch (getState()) {
        case WET:
            return Blocks.clay.getBlockTextureFromSide(side);
        case DRY:
            return BlockIcons.ceramics$dry;
        case BISQUED:
        case UNFIRED_GLAZED:
            return BlockIcons.error;
        case HIGHFIRED:
            Item it = Items.itemsList[lump.icon_id];
            if (it == null) {
                return BlockIcons.error;
            }
            return it.getIconFromDamage(lump.icon_md);
        default:
            return BlockIcons.error;
        }
    }

    public void touch() {
        if (getState() == ClayState.WET) {
            lastTouched = 0;
        }
    }

    public boolean renderEfficient() {
        return getState() != ClayState.WET;
    }

    public boolean canEdit() {
        return getState() == ClayState.WET;
    }

    void initialize() {
        parts = new ArrayList<ClayLump>();
        parts.add(new ClayLump().asDefault());
        touch();
    }

    void writeParts(NBTTagCompound tag) {
        NBTTagList l = new NBTTagList();
        for (ClayLump lump : parts) {
            NBTTagCompound rc_tag = new NBTTagCompound();
            lump.write(rc_tag);
            l.appendTag(rc_tag);
        }
        tag.setTag("parts", l);
        tag.setInteger("touch", lastTouched);
        tag.setInteger("heat", totalHeat);
        tag.setBoolean("glazed", glazesApplied);
        tag.setByte("front", (byte)front.ordinal());
        tag.setByte("rot", rotation);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeParts(tag);
        if (parts.size() == 0) {
            getCoord().setId(0);
        }
    }

    public void loadParts(NBTTagCompound tag) {
        if (tag == null) {
            initialize();
            return;
        }
        NBTTagList partList = tag.getTagList("parts");
        if (partList == null) {
            initialize();
            return;
        }
        int tagCount = partList.tagCount();
        parts = new ArrayList<ClayLump>(tagCount);
        for (int i = 0; i < tagCount; i++) {
            NBTTagCompound rc_tag = (NBTTagCompound) partList.tagAt(i);
            parts.add(new ClayLump().read(rc_tag));
        }
        lastTouched = tag.getInteger("touch");
        totalHeat = tag.getInteger("heat");
        glazesApplied = tag.getBoolean("glazed");
        if (tag.hasKey("front")) {
            front = ForgeDirection.getOrientation(tag.getByte("front"));
            setRotation(tag.getByte("rot"));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        loadParts(tag);
    }
    
    public void setRotation(byte newRotation) {
        rotation = newRotation;
        rotation_quat = Quaternion.getRotationQuaternionRadians(Math.PI*newRotation/2, ForgeDirection.UP);
    }

    @Override
    public Packet getAuxillaryInfoPacket() {
        ArrayList<Object> args = new ArrayList(2 + parts.size() * 9);
        args.add(MessageType.SculptDescription);
        args.add(getState().ordinal());
        args.add((byte)front.ordinal());
        args.add(rotation);
        for (ClayLump lump : parts) {
            lump.write(args);
        }
        return getDescriptionPacketWith(args.toArray());
    }

    @Override
    public void onPlacedBy(EntityPlayer player, ItemStack is, int side) {
        super.onPlacedBy(player, is, side);
        NBTTagCompound tag = is.getTagCompound();
        loadParts(tag);
        ForgeDirection placement = ForgeDirection.getOrientation(FzUtil.determineFlatOrientation(player));
        if (tag == null || !tag.hasKey("front")) {
            front = placement;
            setRotation((byte) 0);
        } else if (placement.offsetY == 0 && placement != ForgeDirection.UNKNOWN) {
            front = ForgeDirection.getOrientation(tag.getByte("front"));
            if (front == ForgeDirection.UNKNOWN || front.offsetY != 0) {
                setRotation((byte) 0);
                front = placement;
            } else {
                ForgeDirection f = placement;
                byte r = 0;
                for (byte i = 0; i < 4; i++) {
                    if (f == front) {
                        r = i;
                        break;
                    }
                    f = f.getRotation(ForgeDirection.UP);
                }
                setRotation(r);
            }
        }
    }

    public ItemStack getItem() {
        ItemStack ret = Core.registry.greenware_item.copy();
        NBTTagCompound tag = new NBTTagCompound();
        writeParts(tag);
        tag.setByte("front", (byte)front.ordinal());
        ret.setTagCompound(tag);
        if (customName != null) {
            ret.setStackDisplayName(customName);
        }
        return ret;
    }

    private ClayState lastState = null;

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) {
            return;
        }
        if (!partsValidated) {
            partsValidated = true;
            Iterator<ClayLump> it = parts.iterator();
            while (it.hasNext()) {
                ClayLump lump = it.next();
                if (!isValidLump(lump)) {
                    if (parts.size() == 1) {
                        lump.asDefault();
                    } else {
                        it.remove();
                        FzUtil.spawnItemStack(getCoord(), new ItemStack(Items.clay));
                    }
                }
            }
        }
        if (getState() == ClayState.WET) {
            if (!worldObj.isRaining()) {
                lastTouched++;
            }
            if (totalHeat > 0) {
                totalHeat--;
                lastTouched++;
            }
        }
        if (getState() != lastState) {
            lastState = getState();
            broadcastMessage(null, MessageType.SculptState, lastState.ordinal());
        }
    }

    @Override
    public boolean activate(EntityPlayer player, ForgeDirection side) {
        ClayState state = getState();
        if (state == ClayState.WET) {
            touch();
        }
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null) {
            return false;
        }
        int heldId = held.getItem().itemID;
        boolean creative = player.capabilities.isCreativeMode;
        if (heldId == Items.bucketWater.itemID && state == ClayState.DRY) {
            lastTouched = 0;
            if (creative) {
                return true;
            }
            int ci = player.inventory.currentItem;
            player.inventory.mainInventory[ci] = new ItemStack(Items.bucketEmpty);
            return true;
        }
        if (heldId == Blocks.cloth.blockID) {
            lastTouched = dryTime + 1;
            return true;
        }
        if (held.getItem() != Items.clay || held.stackSize == 0) {
            return false;
        }
        if (state != ClayState.WET) {
            Notify.send(player, getCoord(), "Not wet");
            return false;
        }
        if (!creative) {
            held.stackSize--;
        }
        if (player.worldObj.isRemote) {
            // Let the server tell us the results
            return true;
        }
        if (parts.size() >= MAX_PARTS) {
            Notify.send(player, getCoord(), "Too complex");
            held.stackSize++;
            return false;
        }
        ClayLump toAdd = addLump();
        MovingObjectPosition hit = ItemSculptingTool.doRayTrace(player);
        if (hit == null || hit.subHit == -1) {
            return true;
        }
        ClayLump against = parts.get(hit.subHit);
        ClayLump extrusion = extrudeLump(against, hit.sideHit);
        if (isValidLump(extrusion)) {
            changeLump(parts.size() - 1, extrusion);
        } else {
            // TODO: Sometimes it fails when it shouldn't.
        }
        return true;
    }

    ClayLump addLump() {
        ClayLump ret = new ClayLump().asDefault();
        parts.add(ret);
        if (!worldObj.isRemote) {
            broadcastMessage(null, MessageType.SculptNew);
            touch();
        }
        return ret;
    }

    void removeLump(int id) {
        if (id < 0 || id >= parts.size()) {
            return;
        }
        parts.remove(id);
        if (!worldObj.isRemote) {
            broadcastMessage(null, MessageType.SculptRemove, id);
            touch();
        }
    }

    ClayLump extrudeLump(ClayLump against, int side) {
        ClayLump lump = against.copy();
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        BlockRenderHelper b = Core.registry.serverTraceHelper;
        against.toBlockBounds(b);
        int wX = lump.maxX - lump.minX;
        int wY = lump.maxY - lump.minY;
        int wZ = lump.maxZ - lump.minZ;
        lump.maxX += wX * dir.offsetX;
        lump.maxY += wY * dir.offsetY;
        lump.maxZ += wZ * dir.offsetZ;
        lump.minX += wX * dir.offsetX;
        lump.minY += wY * dir.offsetY;
        lump.minZ += wZ * dir.offsetZ;
        return lump;
    }

    public boolean isValidLump(ClayLump lump) {
        // check volume
        if (!(Core.cheat)) {
            int wX = lump.maxX - lump.minX;
            int wY = lump.maxY - lump.minY;
            int wZ = lump.maxZ - lump.minZ;
            int area = wX * wY * wZ;
            int max_area = 16 * 16 * 16 /* / 4 */;
            if (!FzConfig.stretchy_clay) {
                max_area /= 4;
            }
            if (area <= 0 || area > max_area) {
                return false;
            }
        }

        // check bounds
        final int B = 16 * 3;
        if (lump.minX < 0)
            return false;
        if (lump.minY < 0)
            return false;
        if (lump.minZ < 0)
            return false;
        if (lump.maxX > B)
            return false;
        if (lump.maxY > B)
            return false;
        if (lump.maxZ > B)
            return false;

        // check for free space (needs to be last, as it can mutate the world)
        BlockRenderHelper block = Core.registry.serverTraceHelper;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    AxisAlignedBB ab = AxisAlignedBB.getBoundingBox(xCoord + dx, yCoord + dy, zCoord + dz, xCoord + dx + 1, yCoord + dy + 1, zCoord + dz + 1);
                    Coord c = getCoord();
                    c.x += dx;
                    c.y += dy;
                    c.z += dz;
                    lump.toRotatedBlockBounds(this, block);
                    AxisAlignedBB in = block.getCollisionBoundingBoxFromPool(worldObj, xCoord, yCoord, zCoord);
                    if (ab.intersectsWith(in)) {
                        // This block needs to be an Extension, or this
                        if (c.isAir() || c.isReplacable()) {
                            c.setId(Core.registry.factory_block);
                            TileEntityExtension tex = new TileEntityExtension(this);
                            c.setTE(tex);
                            tex.getBlockClass().enforce(c);
                            continue;
                        }
                        TileEntity te = c.getTE();
                        if (te == this) {
                            continue;
                        }
                        if (te instanceof TileEntityExtension) {
                            TileEntityExtension tex = (TileEntityExtension) te;
                            if (tex.getParent() == this) {
                                continue;
                            }
                        }
                        // We used to not allow this. We just make a bit of noise instead.
                        // A notification will indicate that things will be a bit messed up here.
                        // FIXME: Let block collision boxes go outside the block (Notch hard-coded for fences)
                        Notify.send(null, c, "!");
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected void onRemove() {
        super.onRemove();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Coord c = getCoord().add(x, y, z);
                    TileEntityExtension tex = c.getTE(TileEntityExtension.class);
                    if (tex != null && tex.getParent() == this) {
                        c.setId(0);
                    }
                }
            }
        }
    }

    private void updateLump(int id, ClayLump lump) {
        if (id < 0 || id >= parts.size()) {
            return;
        }
        ClayLump old = parts.get(id);
        if (old.equals(lump)) {
            return;
        }
        parts.set(id, lump);
        touch();
        if (worldObj.isRemote) {
            return;
        }
    }

    private void shareLump(int id, ClayLump selection) {
        ArrayList<Object> toSend = new ArrayList();
        toSend.add(id);
        selection.write(toSend);
        broadcastMessage(null, MessageType.SculptMove, toSend.toArray());
    }

    void changeLump(int id, ClayLump newValue) {
        updateLump(id, newValue);
        shareLump(id, newValue);
    }

    private float getFloat(DataInput input) throws IOException {
        int r = (int) (input.readFloat() * 2);
        // XXX TODO: clip to within the 3x3 cube!
        return r / 2F;
    }

    @Override
    public boolean handleMessageFromServer(int messageType, DataInputStream input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        switch (messageType) {
        case MessageType.SculptDescription:
            readStateChange(input);
            front = ForgeDirection.getOrientation(input.readByte());
            setRotation(input.readByte());
            parts.clear();
            ArrayList<Object> args = new ArrayList();
            while (true) {
                try {
                    parts.add(new ClayLump().read(input));
                } catch (IOException e) {
                    break;
                }
            }
            shouldRenderTesr = getState() == ClayState.WET;
            break;
        case MessageType.SculptMove:
            updateLump(input.readInt(), new ClayLump().read(input));
            break;
        case MessageType.SculptNew:
            addLump();
            break;
        case MessageType.SculptRemove:
            removeLump(input.readInt());
            break;
        case MessageType.SculptState:
            readStateChange(input);
            break;
        default:
            return false;
        }
        if (renderEfficient()) {
            getCoord().redraw();
        }
        return true;
    }

    private void readStateChange(DataInput input) throws IOException {
        switch (ClayState.values()[input.readInt()]) {
        case WET:
            lastTouched = 0;
            break;
        case DRY:
            lastTouched = dryTime + 10;
            break;
        case BISQUED:
            totalHeat = bisqueHeat + 1;
            break;
        case UNFIRED_GLAZED:
            totalHeat = bisqueHeat + 2;
            glazesApplied = true;
            break;
        case HIGHFIRED:
            totalHeat = highfireHeat + 1;
            break;
        }
        getCoord().redraw();
    }

    private static final Vec3 zeroVec = Vec3.createVectorHelper(0, 0, 0);

    @Override
    protected boolean removeBlockByPlayer(EntityPlayer player) {
        if (player.worldObj.isRemote) {
            return false;
        }
        MovingObjectPosition hit = ItemSculptingTool.doRayTrace(player);
        if (hit == null || hit.subHit == -1 || parts.size() < 1) {
            return super.removeBlockByPlayer(player);
        }
        Coord here = getCoord();
        ClayState state = getState();
        // If it's solid, break it.
        // If we're sneaking & creative, break it
        boolean shouldDestroy = player.isSneaking() || parts.size() == 1;
        if (player.capabilities.isCreativeMode) {
            if (shouldDestroy) {
                return super.removeBlockByPlayer(player);
            } else {
                removeLump(hit.subHit);
                return true;
            }
        }
        shouldDestroy |= state != ClayState.WET;
        if (shouldDestroy) {
            FzUtil.spawnItemStack(here, getItem());
            here.setId(0);
        } else {
            removeLump(hit.subHit);
            FzUtil.spawnItemStack(here, new ItemStack(Items.clay));
        }
        return false;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(Vec3 startVec, Vec3 endVec) {
        BlockRenderHelper block;
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            block = Core.registry.clientTraceHelper;
        } else {
            block = Core.registry.serverTraceHelper;
        }
        // It's possible for the startVec to be embedded in a lump (causing it
        // to hit the opposite side), so we must move it farther away
        double dx = startVec.xCoord - endVec.xCoord;
        double dy = startVec.yCoord - endVec.yCoord;
        double dz = startVec.zCoord - endVec.zCoord;
        double scale = 5.2; // Diagonal of a 3³. (Was initially using scale = 2)
        // This isn't quite right; the dVector would properly be normalized here
        // & rescaled to the max diameter. But we can survive without it.
        // Unnormalized length of dVector is 6m in surviavl mode IIRC. This'll
        // be way longer than it needs to be.
        // Why is it + instead of -? Hmm.
        startVec.xCoord += dx * scale;
        startVec.yCoord += dy * scale;
        startVec.zCoord += dz * scale;
        MovingObjectPosition shortest = null;
        for (int i = 0; i < parts.size(); i++) {
            ClayLump lump = parts.get(i);
            lump.toRotatedBlockBounds(this, block);
            MovingObjectPosition mop = block.collisionRayTrace(worldObj, xCoord, yCoord, zCoord, startVec, endVec);
            Vec3Pool vp = worldObj.getWorldVec3Pool();
            if (mop != null) {
                mop.subHit = i;
                if (shortest == null) {
                    shortest = mop;
                } else {
                    Vec3 s = shortest.hitVec;
                    Vec3 m = mop.hitVec;
                    s = vp.getVecFromPool(s.xCoord, s.yCoord, s.zCoord);
                    m = vp.getVecFromPool(m.xCoord, m.yCoord, m.zCoord);
                    offsetVector(startVec, s);
                    offsetVector(startVec, m);
                    if (m.lengthVector() < s.lengthVector()) {
                        shortest = mop;
                    }
                }
            }
        }
        return shortest;
        // return super.collisionRayTrace(w, x, y, z, startVec, endVec);
    }

    private void offsetVector(Vec3 player, Vec3 v) {
        v.xCoord -= player.xCoord;
        v.yCoord -= player.yCoord;
        v.zCoord -= player.zCoord;
    }

    @ForgeSubscribe
    @SideOnly(Side.CLIENT)
    public void renderCeramicsSelection(DrawBlockHighlightEvent event) {
        if (event.target.subHit == -1) {
            return;
        }
        Coord c = new Coord(event.player.worldObj, event.target);
        TileEntityGreenware clay = c.getTE(TileEntityGreenware.class);
        if (clay == null) {
            return;
        }
        event.setCanceled(true);
        EntityPlayer player = event.player;
        double partial = event.partialTicks;
        ClayLump lump = clay.parts.get(event.target.subHit);
        BlockRenderHelper block = Core.registry.clientTraceHelper;
        lump.toRotatedBlockBounds(clay, block);
        double widen = 0.002;
        double oX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial;
        double oY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial;
        double oZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial;
        AxisAlignedBB bb = block.getSelectedBoundingBoxFromPool(c.w, c.x, c.y, c.z).expand(widen, widen, widen).getOffsetBoundingBox(-oX, -oY, -oZ);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        float r = 0xFF;
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0, 0, 0, 0.4F);
        // GL11.glColor4f(0x4D/r, 0x34/r, 0x7C/r, 0.8F); //#4D347C
        drawOutlinedBoundingBox(bb);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);

        // TODO: If the rotation tool is selected, may draw the axis?
        // Oooooh, we could also draw the offset position for *EVERY* tool...
    }

    // Copied. Private in RenderGlobal.drawOutlinedBoundingBox. For some stupid
    // pointless reason. Don't really feel like re-writing it to be public every
    // update or submitting an AT. (GL_LINES)
    private static void drawOutlinedBoundingBox(AxisAlignedBB aabb) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(3);
        tessellator.addVertex(aabb.minX, aabb.minY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.minY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.minY, aabb.maxZ);
        tessellator.addVertex(aabb.minX, aabb.minY, aabb.maxZ);
        tessellator.addVertex(aabb.minX, aabb.minY, aabb.minZ);
        tessellator.draw();
        tessellator.startDrawing(3);
        tessellator.addVertex(aabb.minX, aabb.maxY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.maxY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.maxY, aabb.maxZ);
        tessellator.addVertex(aabb.minX, aabb.maxY, aabb.maxZ);
        tessellator.addVertex(aabb.minX, aabb.maxY, aabb.minZ);
        tessellator.draw();
        tessellator.startDrawing(1);
        tessellator.addVertex(aabb.minX, aabb.minY, aabb.minZ);
        tessellator.addVertex(aabb.minX, aabb.maxY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.minY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.maxY, aabb.minZ);
        tessellator.addVertex(aabb.maxX, aabb.minY, aabb.maxZ);
        tessellator.addVertex(aabb.maxX, aabb.maxY, aabb.maxZ);
        tessellator.addVertex(aabb.minX, aabb.minY, aabb.maxZ);
        tessellator.addVertex(aabb.minX, aabb.maxY, aabb.maxZ);
        tessellator.draw();
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB bb = AxisAlignedBB.getAABBPool().getAABB(xCoord - 1, yCoord - 1, zCoord - 1, xCoord + 1, yCoord + 1, zCoord + 1);
        return bb;
    }

    @Override
    public void setBlockBounds(Block b) {
        super.setBlockBounds(b);
        // b.setBlockBounds(-1, -1, -1, 1, 1, 1);
    }

    @Override
    public boolean addCollisionBoxesToList(Block ignore, AxisAlignedBB aabb, List list, Entity entity) {
        boolean remote = (entity != null && entity.worldObj != null) ? entity.worldObj.isRemote : FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT;
        BlockRenderHelper block = remote ? Core.registry.clientTraceHelper : Core.registry.serverTraceHelper;
        ClayState state = getState();
        if (state == ClayState.WET) {
            block.setBlockBounds(0, 0, 0, 1, 1F / 8F, 1);
            AxisAlignedBB a = block.getCollisionBoundingBoxFromPool(worldObj, xCoord, yCoord, zCoord);
            if (aabb.intersectsWith(a)) {
                list.add(a);
            }
        }
        for (ClayLump lump : parts) {
            lump.toRotatedBlockBounds(this, block);
            AxisAlignedBB a = block.getCollisionBoundingBoxFromPool(worldObj, xCoord, yCoord, zCoord);
            if (aabb.intersectsWith(a)) {
                list.add(a);
            }
        }
        return true;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool() {
        return null;
    }

    @Override
    public boolean isBlockSolidOnSide(int side) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ForgeDirection dir) {
        if (parts.size() == 0) {
            return Blocks.clay.getBlockTextureFromSide(0);
        }
        return getIcon(parts.get(0), dir.ordinal());
    }
    
    @Override
    public ItemStack getDroppedBlock() {
        return getItem();
    }
}
