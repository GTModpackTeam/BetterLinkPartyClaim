package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;

public class ClaimsCommand extends PlayerCommand {

    @Override
    public @NotNull String getName() {
        return "claims";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc claims";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ChunkManagerData chunks = ChunkManagerData.getInstance();
        int personalClaims = chunks.getClaimsByOwner(player.getUniqueID()).size();
        int personalForce = (int) chunks.getClaimsByOwner(player.getUniqueID()).stream()
                .filter(c -> c.isForceLoaded).count();

        Party party = BLPCCommandHelper.resolveParty(player);
        if (party == null) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.claims.personal",
                    personalClaims, personalForce));
            return;
        }

        int partyClaims = 0;
        int partyForce = 0;
        for (ClaimedChunkData claim : chunks.getAllClaims()) {
            if (party.isMember(claim.ownerUUID)) {
                partyClaims++;
                if (claim.isForceLoaded) partyForce++;
            }
        }

        sender.sendMessage(new TextComponentTranslation("command.blpc.claims.party_header", party.getName()));
        sender.sendMessage(new TextComponentTranslation("command.blpc.claims.party_total",
                partyClaims, party.sumClaimLimit(ModConfig.claims.maxClaimsPerPlayer)));
        sender.sendMessage(new TextComponentTranslation("command.blpc.claims.party_force",
                partyForce, party.sumForceLoadLimit(ModConfig.claims.maxForceLoadsPerPlayer)));
        sender.sendMessage(new TextComponentTranslation("command.blpc.claims.your_share",
                personalClaims, personalForce));
    }
}
