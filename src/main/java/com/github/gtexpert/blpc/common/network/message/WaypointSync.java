package com.github.gtexpert.blpc.common.network.message;

import java.util.UUID;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import io.netty.buffer.ByteBuf;

/**
 * S→C: A single party-shared waypoint was added, updated, or removed.
 * Handler lives in {@code client.network.WaypointSyncClientHandler}.
 */
public class WaypointSync implements IMessage {

    private int action;
    private String waypointId;
    private UUID ownerUUID;
    private String name;
    private int dimension;
    private int x, y, z;
    private int color;

    public WaypointSync() {}

    public static WaypointSync addOrUpdate(String waypointId, UUID ownerUUID, String name, int dimension, int x,
                                           int y, int z, int color) {
        WaypointSync msg = new WaypointSync();
        msg.action = WaypointAction.ACTION_ADD_OR_UPDATE;
        msg.waypointId = waypointId;
        msg.ownerUUID = ownerUUID;
        msg.name = name;
        msg.dimension = dimension;
        msg.x = x;
        msg.y = y;
        msg.z = z;
        msg.color = color;
        return msg;
    }

    public static WaypointSync remove(String waypointId) {
        WaypointSync msg = new WaypointSync();
        msg.action = WaypointAction.ACTION_REMOVE;
        msg.waypointId = waypointId;
        return msg;
    }

    public int getAction() {
        return action;
    }

    public String getWaypointId() {
        return waypointId;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getName() {
        return name;
    }

    public int getDimension() {
        return dimension;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getColor() {
        return color;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        waypointId = ByteBufUtils.readUTF8String(buf);
        if (action == WaypointAction.ACTION_ADD_OR_UPDATE) {
            ownerUUID = new UUID(buf.readLong(), buf.readLong());
            name = ByteBufUtils.readUTF8String(buf);
            dimension = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            color = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, waypointId);
        if (action == WaypointAction.ACTION_ADD_OR_UPDATE) {
            buf.writeLong(ownerUUID.getMostSignificantBits());
            buf.writeLong(ownerUUID.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, name);
            buf.writeInt(dimension);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(color);
        }
    }
}
