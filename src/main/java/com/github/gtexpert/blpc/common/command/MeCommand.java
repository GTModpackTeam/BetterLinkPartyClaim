package com.github.gtexpert.blpc.common.command;

import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

public class MeCommand extends CommandBase {

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
        Party party = PartyManagerData.getInstance().getPartyByPlayer(player.getUniqueID());
        if (party == null) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.me.no_party"));
            return;
        }

        UUID owner = party.getOwner();
        String ownerName = owner != null ? ListCommand.resolveName(server, party, owner) : "-";

        sender.sendMessage(new TextComponentTranslation("command.blpc.info.header", party.getName()));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.owner", ownerName));
        sender.sendMessage(new TextComponentTranslation("command.blpc.me.your_role",
                ListCommand.roleLabel(party.getRole(player.getUniqueID()))));
        sender.sendMessage(new TextComponentTranslation("command.blpc.info.members", party.getMembers().size()));

        for (var entry : party.getMembers().entrySet()) {
            String name = ListCommand.resolveName(server, party, entry.getKey());
            boolean online = server.getPlayerList().getPlayerByUUID(entry.getKey()) != null;
            String dot = online ? TextFormatting.GREEN + "●" : TextFormatting.DARK_GRAY + "○";
            sender.sendMessage(new TextComponentString(String.format("  %s%s %s %s(%s)%s",
                    dot, TextFormatting.RESET, name,
                    TextFormatting.DARK_GRAY, ListCommand.roleLabel(entry.getValue()), TextFormatting.RESET)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(@NotNull MinecraftServer server, @NotNull ICommandSender sender) {
        return true;
    }
}
