package factorization.sockets.fanturpeller;

import java.io.DataInputStream;
import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Icon;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.IFluidHandler;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import factorization.api.Charge;
import factorization.api.Coord;
import factorization.api.FzOrientation;
import factorization.api.IChargeConductor;
import factorization.api.Quaternion;
import factorization.api.datahelpers.DataHelper;
import factorization.api.datahelpers.IDataSerializable;
import factorization.api.datahelpers.Share;
import factorization.common.BlockIcons;
import factorization.common.FactoryType;
import factorization.servo.ServoMotor;
import factorization.shared.BlockRenderHelper;
import factorization.shared.Core;
import factorization.shared.FactorizationBlockRender;
import factorization.shared.FzUtil;
import factorization.shared.NetworkFactorization.MessageType;
import factorization.sockets.ISocketHolder;
import factorization.sockets.TileEntitySocketBase;

public class SocketFanturpeller extends TileEntitySocketBase implements IChargeConductor {
    Charge charge = new Charge(this);
    boolean isSucking = false;
    byte target_speed = 1;
    float fanω, lastfanω; // because I can.

    transient float fanRotation, prevFanRotation;

    @Override
    public IDataSerializable serialize(String prefix, DataHelper data) throws IOException {
        charge = data.as(Share.PRIVATE, "charge").put(charge);
        isSucking = data.as(Share.MUTABLE, "suck").put(isSucking);
        target_speed = data.as(Share.MUTABLE, "target_speed").putByte(target_speed);
        if (target_speed < 0) target_speed = 0;
        if (target_speed > 3) target_speed = 3;
        fanω = data.as(Share.VISIBLE, "fanw").putFloat(fanω);
        return this;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SOCKET_FANTURPELLER;
    }
    
    @Override
    public ItemStack getCreatingItem() {
        return new ItemStack(Core.registry.fan);
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
    public String getInfo() {
        return null;
    }

    @Override
    public Charge getCharge() {
        return charge;
    }

    @Override
    public void updateEntity() {
        charge.update();
        super.updateEntity();
    }
    
    boolean pickFanAction(Coord coord, boolean powered, boolean onServo) {
        /*
         * Possible actions: - move liquids (IFluidContainers/world). Source and
         * destination must be able to hold liquids (or have liquids spilled
         * into them, in the case of air) - suck in entities; or blow out items
         * and entities. Front must be clear. Back may have an inventory. - mix
         * shapeless recipes. Must have an inventory, front and back. - generate
         * power. Must have a redstone signal, and front must be clear, and back
         * must contain a gas as a block or as an inventory
         */
        final Coord front = coord.add(facing);
        final Coord back = coord.add(facing.getOpposite());

        if (!front.blockExists() || !back.blockExists()) {
            return false;
        }

        final Coord source = isSucking ? front : back;
        final Coord destination = isSucking ? back : front;

        // BEGIN MACRO-GENERATED CODE
        // The real source for this is in fanmacro.py, which should be located in the same folder as this file.
        boolean need_PumpLiquids = false;
        boolean need_GeneratePower = false;
        boolean need_BlowEntities = false;
        boolean need_MixCrafting = false;
        if (!powered && !onServo && (isLiquid(source) || hasTank(source)) && (isLiquid(destination) || hasTank(destination) || isClear(destination))) {
            need_PumpLiquids = true;
        }
        if (powered && (isLiquid(source) || hasTank(source)) && isClear(destination)) {
            need_GeneratePower = true;
        }
        if (isClear(front) && (isClear(back) || hasInv(back))) {
            need_BlowEntities = true;
        }
        if (hasInv(front) && hasInv(back)) {
            need_MixCrafting = true;
        }
        if (need_PumpLiquids && this instanceof PumpLiquids) return false;
        if (need_GeneratePower && this instanceof GeneratePower) return false;
        if (need_BlowEntities && this instanceof BlowEntities) return false;
        if (need_MixCrafting && this instanceof MixCrafting) return false;
        if (need_PumpLiquids) {
            replaceWith(new PumpLiquids());
            return true;
        }
        if (need_GeneratePower) {
            replaceWith(new GeneratePower());
            return true;
        }
        if (need_BlowEntities) {
            replaceWith(new BlowEntities());
            return true;
        }
        if (need_MixCrafting) {
            replaceWith(new MixCrafting());
            return true;
        }
        // END MACRO-GENERATED CODE
        return false;
    }

    boolean isLiquid(Coord at) {
        final Block block = at.getBlock();
        if (block == Block.waterStill || block == Block.waterMoving || block == Block.lavaStill || block == Block.lavaMoving) {
            return at.getMd() == 0;
        }
        return block instanceof IFluidBlock;
    }

    boolean hasTank(Coord at) {
        return at.getTE(IFluidHandler.class) != null;
    }

    boolean hasInv(Coord at) {
        return at.getTE(IInventory.class) != null;
    }

    boolean isClear(Coord at) {
        return at.isReplacable() && !isLiquid(at);
    }
    
    @Override
    protected void replaceWith(TileEntitySocketBase baseReplacement) {
        if (baseReplacement instanceof SocketFanturpeller) {
            SocketFanturpeller replacement = (SocketFanturpeller) baseReplacement;
            if (!isSafeToDiscard()) {
                if (replacement instanceof BufferedFanturpeller && this instanceof BufferedFanturpeller) {
                    BufferedFanturpeller old = (BufferedFanturpeller) this;
                    BufferedFanturpeller rep = (BufferedFanturpeller) replacement;
                    rep.buffer = old.buffer;
                } else {
                    return;
                }
            }
            replacement.isSucking = isSucking;
            replacement.target_speed = target_speed;
            replacement.fanω = fanω;
            replacement.fanRotation = fanRotation;
            replacement.prevFanRotation = prevFanRotation;
            replacement.charge = charge;
        }
        super.replaceWith(baseReplacement);
    }

    transient boolean needActionCheck = true;
    @Override
    public void neighborChanged() {
        needActionCheck = true;
    }
    
    float getTargetSpeed() {
        if (!shouldFeedJuice()) return 0;
        return target_speed*10;
    }
    
    boolean shouldDoWork() {
        if (target_speed == 0) return false;
        int direction = (isSucking ? -1 : 1);
        float ts = getTargetSpeed();
        if (Math.signum(fanω) != direction) return false;
        float ω = Math.abs(fanω);
        if (ω >= ts) return true;
        return false;
        /*if (ts > ω + 10) return false;
        return (ts - ω)/10.0F > rand.nextFloat(); */
    }
    
    int getRequiredCharge() {
        return 0;
    }
    
    @Override
    public final void genericUpdate(ISocketHolder socket, Coord coord, boolean powered) {
        boolean neighbor_changed = false;
        if (needActionCheck && !worldObj.isRemote) {
            needActionCheck = false;
            if (pickFanAction(coord, powered, socket != this)) {
                return;
            }
            neighbor_changed = true;
        }
        prevFanRotation = fanRotation;
        fanturpellerUpdate(socket, coord, powered, neighbor_changed);
        if (!worldObj.isRemote) {
            final int need = getRequiredCharge();
            if (need > 0) {
                final float ts = getTargetSpeed() * (isSucking ? -1 : 1);
                if (charge.deplete(need) < need) {
                    fanω *= 0.9;
                } else if (Math.abs(fanω) > Math.abs(ts)) { // we've been switched to a slower speed
                    fanω = (fanω*9 + ts)/10;
                    if (Math.abs(fanω) < Math.abs(ts)) {
                        fanω = ts;
                    }
                } else if (Math.abs(fanω) < Math.abs(ts)) {
                    fanω += Math.signum(ts);
                    if (fanω > Math.abs(ts)) {
                        fanω = ts;
                    }
                }
            }
            
            if (FzUtil.significantChange(fanω, lastfanω)) {
                broadcastMessage(null, MessageType.FanturpellerSpeed, fanω);
            }
            lastfanω = fanω;
        }
        fanRotation += fanω;
    }
    
    protected boolean shouldFeedJuice() {
        return false;
    }
    
    protected void fanturpellerUpdate(ISocketHolder socket, Coord coord, boolean powered, boolean neighbor_changed) {
        fanω *= 0.95F;
    }
    
    protected boolean isSafeToDiscard() {
        return true;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(ServoMotor motor, Tessellator tess) {
        if (motor != null) {
            TextureManager tex = Minecraft.getMinecraft().renderEngine;
            tex.bindTexture(Core.blockAtlas);
        }
        BlockRenderHelper block = BlockRenderHelper.instance;
        Quaternion rotation = Quaternion.fromOrientation(FzOrientation.fromDirection(facing.getOpposite()));
        {
            Icon metal = BlockIcons.motor_texture;
            float d = 4.0F / 16.0F;
            float yd = -d + 0.003F;
    
            block.useTextures(metal, null,
                    metal, metal,
                    metal, metal);
            float yoffset = 5F/16F;
            float sd = motor == null ? 0 : 2F/16F;
            block.setBlockBounds(d, d + yd + yoffset + 2F/16F + sd, d, 1 - d, 1 - (d + 0F/16F) + yd + yoffset, 1 - d);
            block.begin();
            block.rotateCenter(rotation);
            block.renderRotated(tess, xCoord, yCoord, zCoord);
        }
        
        Icon glass = Block.glass.getIcon(0, 0);
        Icon hole = Block.fenceIron.getIcon(0, 0);
        block.useTextures(hole, null,
                glass, glass,
                glass, glass);
        if (motor == null) {
            block.setBlockBounds(0, 0, 0, 1, 12F/16F, 1);
        } else {
            float s = 1.5F/16F;
            block.setBlockBounds(s, 0, s, 1 - s, 12F/16F, 1 - s);
        }
        block.begin();
        block.rotateCenter(rotation);
        block.renderRotated(tess, xCoord, yCoord, zCoord);
    }
    
    protected float scaleRotation(float rotation) {
        return rotation;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void renderTesr(ServoMotor motor, float partial) {
        float d = 0.5F;
        GL11.glTranslatef(d, d, d);
        Quaternion.fromOrientation(FzOrientation.fromDirection(facing.getOpposite())).glRotate();
        float turn = scaleRotation(FzUtil.interp(prevFanRotation, fanRotation, partial));
        GL11.glRotatef(turn, 0, 1, 0);
        float sd = motor == null ? -2F/16F : 3F/16F;
        GL11.glTranslatef(0, sd, 0);
        
        
        float s = 12F/16F;
        if (motor != null) {
            s = 10F/16F;
        }
        GL11.glScalef(s, 1, s);
        //TileEntityGrinderRender.renderGrindHead();
        GL11.glRotatef(90, 1, 0, 0);
        GL11.glTranslatef(-0.5F, -0.5F, 0);
        FactorizationBlockRender.renderItemIcon(Core.registry.fan.getIconFromDamage(0));
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public boolean handleMessageFromServer(int messageType, DataInputStream input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        if (messageType == MessageType.FanturpellerSpeed) {
            fanω = input.readFloat();
            return true;
        }
        return false;
    }
    
    @Override
    public Packet getDescriptionPacket() {
        return getDescriptionPacketWith(MessageType.FanturpellerSpeed, fanω);
    }
}
