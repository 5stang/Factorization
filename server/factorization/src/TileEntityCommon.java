package factorization.src;

import net.minecraft.src.Packet;
import net.minecraft.src.TileEntity;
import factorization.src.NetworkFactorization.MessageType;

public abstract class TileEntityCommon extends TileEntity implements ICoord, IFactoryType {
	//@Override -- can't override due to MY GOD ITS THE CLIENTS FAULT THIS TIME
	public Packet getDescriptionPacket() {
		Packet p = Core.network.messagePacket(getCoord(), MessageType.FactoryType, getFactoryType().md, getExtraInfo(), getExtraInfo2());
		p.isChunkDataPacket = true;
		return p;
	}

	byte getExtraInfo() {
		return 0;
	}

	byte getExtraInfo2() {
		return 0;
	}

	void useExtraInfo(byte b) {
	}

	void useExtraInfo2(byte b) {
	}

	Packet getDescriptionPacketWith(Object... args) {
		Object[] suffix = new Object[args.length + 3];
		suffix[0] = getFactoryType().md;
		suffix[1] = getExtraInfo();
		suffix[2] = getExtraInfo2();
		for (int i = 0; i < args.length; i++) {
			suffix[i + 3] = args[i];
		}
		Packet p = Core.network.messagePacket(getCoord(), MessageType.FactoryType, suffix);
		p.isChunkDataPacket = true;
		return p;
	}

	@Override
	public Coord getCoord() {
		return new Coord(this);
	}
}
