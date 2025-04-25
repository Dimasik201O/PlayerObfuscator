package org.dimasik.playerobfuscator;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class VisibilityTask extends BukkitRunnable {
    private final PlayerVisibilityManager visibilityManager;

    public VisibilityTask(PlayerObfuscator plugin, PlayerVisibilityManager visibilityManager) {
        this.visibilityManager = visibilityManager;
    }

    @Override
    public void run() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        visibilityManager.checkVisibilityForAll();
    }
}