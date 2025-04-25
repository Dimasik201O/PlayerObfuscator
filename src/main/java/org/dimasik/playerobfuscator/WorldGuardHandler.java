package org.dimasik.playerobfuscator;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;

public class WorldGuardHandler {
    private final JavaPlugin plugin;

    public WorldGuardHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canPvp(Location location) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            return true;
        }

        ProtectedRegion highestPriorityRegion = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location)).getRegions().stream()
                .max(Comparator.comparingInt(ProtectedRegion::getPriority))
                .orElse(null);

        if (highestPriorityRegion == null) {
            return true;
        }

        StateFlag flag = (StateFlag) WorldGuard.getInstance().getFlagRegistry().get("pvp");
        if (flag == null) {
            return true;
        }

        StateFlag.State flagState = highestPriorityRegion.getFlag(flag);
        return flagState == null || flagState == StateFlag.State.ALLOW;
    }
}