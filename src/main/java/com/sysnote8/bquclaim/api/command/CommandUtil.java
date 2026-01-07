package com.sysnote8.bquclaim.api.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class CommandUtil {
    public static void sendMessage(ICommandSender sender, String... messages) {
        for (String msg : messages) {
            sender.sendMessage(new TextComponentString(msg));
        }
    }

    public static void sendMessage(ICommandSender sender, ITextComponent... components) {
        for (ITextComponent component : components) {
            sender.sendMessage(component);
        }
    }
}
