package org.kingfight.kingobsi.handlers;

import org.kingfight.kingobsi.kingobsiplugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand implements CommandExecutor {


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player) || sender.hasPermission("kingobsi.reload")) {

            if (args.length == 1) {

                kingobsiplugin.instance.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Config successfully reloaded!");

            } else {

                sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/kingobsi reload");

            }

        } else {

            sender.sendMessage(ChatColor.RED + "You don't have permission to this!");

        }

        return true;
    }

}
