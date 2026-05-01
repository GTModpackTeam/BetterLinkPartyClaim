package com.github.gtexpert.blpc.common.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.network.MessagePartyEventNotify;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

public class DisbandCommand extends CommandBase {

    @Override
    public @NotNull String getName() {
        return "disband";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc admin disband <partyName>";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        if (args.length != 1) {
            throw new CommandException("/blpc admin disband <partyName>");
        }
        Party party = BLPCCommandHelper.findPartyByName(args[0]);
        if (party == null) {
            throw new CommandException("Party not found: " + args[0]);
        }

        String partyName = party.getName();
        List<UUID> members = new ArrayList<>(party.getMemberUUIDs());
        ChunkManagerData chunks = ChunkManagerData.getInstance();
        for (UUID memberId : members) {
            chunks.releaseAllClaims(memberId, sender.getEntityWorld());
        }

        PartyManagerData pm = PartyManagerData.getInstance();
        pm.removeParty(party.getPartyId());
        for (UUID memberId : members) {
            pm.setBQuLinked(memberId, false);
        }
        PartyProviderRegistry.get().syncToAll();
        BLPCSaveHandler.INSTANCE.markDirty();

        for (UUID memberId : members) {
            EntityPlayerMP member = server.getPlayerList().getPlayerByUUID(memberId);
            if (member != null) {
                ModNetwork.INSTANCE.sendTo(
                        new MessagePartyEventNotify(MessagePartyEventNotify.DISBANDED, "", ""),
                        member);
            }
        }
        sender.sendMessage(new TextComponentTranslation("command.blpc.disband.success", partyName));
    }

    @Override
    public @NotNull List<String> getTabCompletions(@NotNull MinecraftServer server,
                                                   @NotNull ICommandSender sender,
                                                   String @NotNull [] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, BLPCCommandHelper.allPartyNames());
        }
        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }
}
