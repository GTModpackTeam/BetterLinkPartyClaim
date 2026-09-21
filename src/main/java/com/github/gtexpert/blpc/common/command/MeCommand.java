package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.api.party.Party;

public class MeCommand extends PlayerCommand {

    @Override
    public @NotNull String getName() {
        return "me";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc me";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        Party party = BLPCCommandHelper.resolveParty(player);
        if (party == null) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.me.no_party"));
            return;
        }

        String ownerName = BLPCCommandHelper.resolveOwnerName(server, party);

        sender.sendMessage(new TextComponentTranslation("command.blpc.info.header", party.getName()));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.owner", ownerName));
        sender.sendMessage(new TextComponentTranslation("command.blpc.me.your_role",
                ListCommand.roleLabel(party.getRole(player.getUniqueID()))));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.members", party.getMembers().size()));

        for (var entry : party.getMembers().entrySet()) {
            String name = BLPCCommandHelper.resolveName(server, party, entry.getKey());
            boolean online = server.getPlayerList().getPlayerByUUID(entry.getKey()) != null;
            String dot = online ? TextFormatting.GREEN + "●" : TextFormatting.DARK_GRAY + "○";
            sender.sendMessage(new TextComponentString(String.format("  %s%s %s %s(%s)%s",
                    dot, TextFormatting.RESET, name,
                    TextFormatting.DARK_GRAY, ListCommand.roleLabel(entry.getValue()), TextFormatting.RESET)));
        }
    }
}
