package com.github.gtexpert.bquclaim.chunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.github.gtexpert.bquclaim.BQuClaim;

public class TicketManager implements LoadingCallback {

    private static final Map<String, Ticket> activeTickets = new HashMap<>();

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket ticket : tickets) {
            int cx = ticket.getModData().getInteger("cx");
            int cz = ticket.getModData().getInteger("cz");
            forceChunk(world, cx, cz, ticket);
        }
    }

    public static void forceChunk(World world, int cx, int cz, Ticket ticket) {
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(BQuClaim.INSTANCE, world, ForgeChunkManager.Type.NORMAL);
        }
        if (ticket != null) {
            ticket.getModData().setInteger("cx", cx);
            ticket.getModData().setInteger("cz", cz);
            ForgeChunkManager.forceChunk(ticket, new ChunkPos(cx, cz));
            activeTickets.put(ticketKey(world, cx, cz), ticket);
        }
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
