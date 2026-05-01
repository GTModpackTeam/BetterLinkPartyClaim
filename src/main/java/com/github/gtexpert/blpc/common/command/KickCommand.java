package com.github.gtexpert.blpc.common.command;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.github.gtexpert.blpc.common.party.PartyRole;

public class KickCommand extends CommandBase {

    @Override
    public @NotNull String getName() {
        return "kick";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc admin kick <partyName> <player>";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        if (args.length != 2) {
            throw new CommandException("/blpc admin kick <partyName> <player>");
        }
        Party party = BLPCCommandHelper.findPartyByName(args[0]);
        if (party == null) {
            throw new CommandException("Party not found: " + args[0]);
        }

        UUID targetUUID = resolveMemberUUID(server, party, args[1]);
        if (targetUUID == null) {
            throw new CommandException("Player is not a member of this party: " + args[1]);
        }
        if (party.getRole(targetUUID) == PartyRole.OWNER) {
            throw new CommandException("Cannot kick the party owner. Use /blpc admin move-owner first.");
        }

        String targetName = ListCommand.resolveName(server, party, targetUUID);
        ChunkManagerData.getInstance().releaseAllClaims(targetUUID, sender.getEntityWorld());
        party.removeMember(targetUUID);
        PartyManagerData.getInstance().setBQuLinked(targetUUID, false);
        PartyProviderRegistry.get().syncToAll();
        BLPCSaveHandler.INSTANCE.markDirty();

        EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(targetUUID);
        if (target != null) {
            ModNetwork.INSTANCE.sendTo(
                    new MessagePartyEventNotify(MessagePartyEventNotify.KICKED, targetName, ""),
                    target);
        }
        sender.sendMessage(
                new TextComponentTranslation("command.blpc.kick.success", targetName, party.getName()));
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
                List<String> names = party.getMembers().keySet().stream()
                        .filter(uuid -> party.getRole(uuid) != PartyRole.OWNER)
                        .map(uuid -> ListCommand.resolveName(server, party, uuid))
                        .collect(Collectors.toList());
                return getListOfStringsMatchingLastWord(args, names);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Nullable
    private static UUID resolveMemberUUID(MinecraftServer server, Party party, String name) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null && party.isMember(online.getUniqueID())) {
            return online.getUniqueID();
        }
        for (UUID uuid : party.getMembers().keySet()) {
            String resolved = ListCommand.resolveName(server, party, uuid);
            if (resolved.equalsIgnoreCase(name)) return uuid;
        }
        return null;
    }
}
