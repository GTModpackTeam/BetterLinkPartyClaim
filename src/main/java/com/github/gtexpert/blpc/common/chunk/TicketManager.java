package com.github.gtexpert.blpc.common.chunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.github.gtexpert.blpc.BLPCMod;
import com.github.gtexpert.blpc.common.ModLog;

public class TicketManager implements LoadingCallback {

    private static final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket ticket : tickets) {
            int cx = ticket.getModData().getInteger("cx");
            int cz = ticket.getModData().getInteger("cz");
            forceChunk(world, cx, cz, ticket);
        }
    }

    public static boolean forceChunk(World world, int cx, int cz, Ticket ticket) {
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(BLPCMod.INSTANCE, world, ForgeChunkManager.Type.NORMAL);
        }
        if (ticket != null) {
            ticket.getModData().setInteger("cx", cx);
            ticket.getModData().setInteger("cz", cz);
            ForgeChunkManager.forceChunk(ticket, new ChunkPos(cx, cz));
            activeTickets.put(ticketKey(world, cx, cz), ticket);
            return true;
        }
        ModLog.IO.warn("Failed to acquire chunk loading ticket for chunk ({}, {})", cx, cz);
        return false;
    }

    public static void reset() {
        activeTickets.clear();
    }

    public static void unforceChunk(World world, int cx, int cz) {
        Ticket ticket = activeTickets.remove(ticketKey(world, cx, cz));
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
        }
    }

    private static String ticketKey(World world, int cx, int cz) {
        return world.provider.getDimension() + "," + cx + "," + cz;
    }
}
