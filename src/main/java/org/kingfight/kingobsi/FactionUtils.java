package org.kingfight.kingobsi;

import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Set;

public class FactionUtils {
    public static Set<Player> getOnlineMembersOfFaction(Faction faction) {
        Set<Player> onlineMembers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (FPlayers.getInstance().getByPlayer(player).getFaction() == faction) {
                onlineMembers.add(player);
            }
        }
        return onlineMembers;
    }

    public static boolean isPlayerInFaction(Player player) {
        return FPlayers.getInstance().getByPlayer(player).hasFaction();
    }

    public static Faction getFactionByPlayer(Player player) {
        return FPlayers.getInstance().getByPlayer(player).getFaction();
    }

    public static boolean isPlayerInThisFaction(Player player, Faction faction) {
        return FPlayers.getInstance().getByPlayer(player).getFaction() == faction;
    }

    public static void sendMessageToOnlineMembersOfFaction(Faction faction, String message) {
        Set<Player> onlineMembers = getOnlineMembersOfFaction(faction);
        for (Player onlinePlayer : onlineMembers) {
            onlinePlayer.sendMessage(message);
        }
    }
}
