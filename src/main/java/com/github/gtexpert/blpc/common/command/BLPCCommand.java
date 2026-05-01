package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

public class BLPCCommand extends CommandTreeBase {

    public BLPCCommand() {
        addSubcommand(new ListCommand());
        addSubcommand(new InfoCommand());
        addSubcommand(new MeCommand());
        addSubcommand(new HereCommand());
        addSubcommand(new ClaimsCommand());
        addSubcommand(new InvitesCommand());
        addSubcommand(new AcceptCommand());
        addSubcommand(new DeclineCommand());
        addSubcommand(new LeaveCommand());
        addSubcommand(new AdminCommand());
    }

    @Override
    public @NotNull String getName() {
        return "blpc";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc <list|info|me|here|claims|invites|accept|decline|leave|admin>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
