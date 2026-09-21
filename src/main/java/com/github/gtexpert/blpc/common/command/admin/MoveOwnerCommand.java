package com.github.gtexpert.blpc.common.command.admin;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.command.BLPCCommandHelper;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

public class MoveOwnerCommand extends AdminSubCommand {

    @Override
    public @NotNull String getName() {
        return "move-owner";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc admin move-owner <partyName> <newOwner>";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        if (args.length != 2) {
            throw new CommandException("/blpc admin move-owner <partyName> <newOwner>");
        }

        Party party = BLPCCommandHelper.requirePartyByName(args[0]);

        EntityPlayerMP newOwner = server.getPlayerList().getPlayerByUsername(args[1]);
        if (newOwner == null) {
            throw new CommandException("Owner player not found: " + args[1]);
        }

        if (!party.isMember(newOwner.getUniqueID())) {
            throw new CommandException("Player is not a member of this party");
        }

        PartyManagerData.getInstance().setRole(party.getPartyId(), newOwner.getUniqueID(), PartyRole.OWNER);
        PartyProviderRegistry.get().syncToAll();
        BLPCSaveHandler.INSTANCE.markDirty();
        sender.sendMessage(
                new TextComponentTranslation("command.blpc.move_owner.success", party.getName(), newOwner.getName()));
    }

    @Override
    public @NotNull List<String> getTabCompletions(@NotNull MinecraftServer server,
                                                   @NotNull ICommandSender sender,
                                                   String @NotNull [] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, BLPCCommandHelper.allPartyNames());
        }
        if (args.length == 2) {
            Party party = BLPCCommandHelper.findPartyByName(args[0]);
            if (party != null) {
                List<String> memberNames = party.getMembers().keySet().stream()
                        .map(uuid -> server.getPlayerList().getPlayerByUUID(uuid))
                        .filter(p -> p != null && party.getRole(p.getUniqueID()) != PartyRole.OWNER)
                        .map(EntityPlayerMP::getName)
                        .collect(Collectors.toList());
                return getListOfStringsMatchingLastWord(args, memberNames);
            }
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
