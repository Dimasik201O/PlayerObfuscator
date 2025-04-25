package org.dimasik.playerobfuscator;

import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerObfuscator extends JavaPlugin {
    private PlayerVisibilityManager visibilityManager;
    private PacketHandler packetHandler;
    private WorldGuardHandler worldGuardHandler;
    private static PlayerObfuscator instance;

    public static PlayerObfuscator getInstance(){
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.worldGuardHandler = new WorldGuardHandler(this);
        this.packetHandler = new PacketHandler(this);
        this.visibilityManager = new PlayerVisibilityManager(this, packetHandler, worldGuardHandler);

        new EventListener(this, visibilityManager);
        new VisibilityTask(this, visibilityManager).runTaskTimerAsynchronously(this, 0L, 2L);
    }

    @Override
    public void onDisable() {
        visibilityManager.cleanup();
    }
}