package org.dimasik.playerobfuscator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class EventListener implements Listener {
    private final PlayerVisibilityManager visibilityManager;

    public EventListener(PlayerObfuscator plugin, PlayerVisibilityManager visibilityManager) {
        this.visibilityManager = visibilityManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }

    @EventHandler
    public void onTp(PlayerTeleportEvent event) {
        new BukkitRunnable(){
            @Override
            public void run(){
                visibilityManager.checkVisibilityForAll();
            }
        }.runTaskAsynchronously(PlayerObfuscator.getInstance());
    }
}