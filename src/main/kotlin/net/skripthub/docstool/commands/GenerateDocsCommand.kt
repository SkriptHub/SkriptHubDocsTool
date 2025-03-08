package net.skripthub.docstool.commands


import net.skripthub.docstool.Core
import net.skripthub.docstool.documentation.BuildDocs
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class GenerateDocsCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        sender.sendMessage("[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
                + ChatColor.RESET + "] " + ChatColor.LIGHT_PURPLE + "Generating Docs")

        BuildDocs(Core.plugin, sender).load()

        return false
    }
}