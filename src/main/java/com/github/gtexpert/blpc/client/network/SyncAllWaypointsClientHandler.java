package com.github.gtexpert.blpc.client.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.common.network.message.SyncAllWaypoints;
import com.github.gtexpert.blpc.common.waypoint.ClientWaypointCache;
import com.github.gtexpert.blpc.common.waypoint.PartyWaypointData;

/** Client-side handler for the full party-waypoint sync sent on login. */
@SideOnly(Side.CLIENT)
public final class SyncAllWaypointsClientHandler extends MainThreadMessageHandler<SyncAllWaypoints> {

    @Override
    protected void handleOnMainThread(SyncAllWaypoints msg) {
        NBTTagCompound data = msg.getData();
        NBTTagList list = data.getTagList("waypoints", Constants.NBT.TAG_COMPOUND);
        List<PartyWaypointData> waypoints = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            PartyWaypointData d = PartyWaypointData.fromNBT(list.getCompoundTagAt(i));
            if (d == null) continue;
            waypoints.add(d);
        }
        ClientWaypointCache.loadAll(waypoints);
    }
}
