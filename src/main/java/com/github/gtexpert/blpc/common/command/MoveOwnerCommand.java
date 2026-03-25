package com.github.gtexpert.blpc.common.command;

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

import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;

public class MoveOwnerCommand extends CommandBase {

    @Override
    public @NotNull String getName() {
        return "move-owner";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "commands.blpc.usage";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        if (args.length != 2) {
            throw new CommandException("move-owner <partyId> <newOwner>");
        }

        int parsedPartyId;
        try {
            parsedPartyId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new CommandException("Invalid party id");
        }

        EntityPlayerMP newOwner = server.getPlayerList().getPlayerByUsername(args[1]);
        if (newOwner == null) {
            throw new CommandException("Owner player not found");
        }

        Party party = PartyManagerData.getInstance().getParty(parsedPartyId);
        if (party == null) {
            throw new CommandException("Party not found");
        }

        UUID oldOwner = party.getOwner();
        if (oldOwner != null) {
            party.setRole(oldOwner, PartyRole.MEMBER);
        }

        party.setRole(newOwner.getUniqueID(), PartyRole.OWNER);
        sender.sendMessage(
                new TextComponentTranslation("command.blpc.move_owner.success", parsedPartyId, newOwner.getName()));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public @NotNull List<String> getTabCompletions(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                                                   String @NotNull [] args,
                                                   @Nullable BlockPos targetPos) {
        return super.getTabCompletions(server, sender, args, targetPos);
    }
}
