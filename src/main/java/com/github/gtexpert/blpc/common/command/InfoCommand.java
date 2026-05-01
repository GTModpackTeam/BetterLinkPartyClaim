package com.github.gtexpert.blpc.common.command;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;
import com.github.gtexpert.blpc.common.party.Party;

public class InfoCommand extends CommandBase {

    @Override
    public @NotNull String getName() {
        return "info";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc info <partyName>";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        if (args.length != 1) {
            throw new CommandException("/blpc info <partyName>");
        }
        Party party = BLPCCommandHelper.findPartyByName(args[0]);
        if (party == null) {
            throw new CommandException("Party not found: " + args[0]);
        }

        UUID owner = party.getOwner();
        String ownerName = owner != null ? ListCommand.resolveName(server, party, owner) : "-";
        int claimCount = countClaims(party);

        sender.sendMessage(new TextComponentTranslation("command.blpc.info.header", party.getName()));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.id",
                party.getPartyId().toString()));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.owner", ownerName));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.members", party.getMembers().size()));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.claims", claimCount));

        for (var entry : party.getMembers().entrySet()) {
            String name = ListCommand.resolveName(server, party, entry.getKey());
            sender.sendMessage(new TextComponentString(String.format("  %s- %s%s %s(%s)%s",
                    TextFormatting.GRAY, TextFormatting.RESET, name,
                    TextFormatting.DARK_GRAY, ListCommand.roleLabel(entry.getValue()), TextFormatting.RESET)));
        }
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
        return 0;
    }

    @Override
    public boolean checkPermission(@NotNull MinecraftServer server, @NotNull ICommandSender sender) {
        return true;
    }

    private static int countClaims(Party party) {
        int count = 0;
        for (ClaimedChunkData claim : ChunkManagerData.getInstance().getAllClaims()) {
            if (party.isMember(claim.ownerUUID)) count++;
        }
        return count;
    }
}
