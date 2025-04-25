package org.dimasik.playerobfuscator;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class PlayerVisibilityManager {
    private final PlayerObfuscator plugin;
    private final PacketHandler packetHandler;
    private final WorldGuardHandler worldGuardHandler;
    private final Map<UUID, Set<UUID>> hiddenPlayersMap = new HashMap<>();

    public PlayerVisibilityManager(PlayerObfuscator plugin, PacketHandler packetHandler, WorldGuardHandler worldGuardHandler) {
        this.plugin = plugin;
        this.packetHandler = packetHandler;
        this.worldGuardHandler = worldGuardHandler;
    }

    public void checkVisibilityForAll() {
        Bukkit.getOnlinePlayers().forEach(viewer -> {
            Bukkit.getOnlinePlayers().forEach(target -> {
                if (viewer == target) return;

                if (!viewer.getWorld().equals(target.getWorld())) {
                    if (isHiddenFor(target, viewer)) {
                        try {
                            showPlayer(viewer, target);
                        } catch (InvocationTargetException e) {
                            plugin.getLogger().warning("Failed to show player: " + e.getMessage());
                        }
                    }
                    return;
                }

                new BukkitRunnable(){
                    @Override
                    public void run(){
                        if (isVisible(viewer, target)) {
                            try {
                                showPlayer(viewer, target);
                            } catch (InvocationTargetException e) {
                                plugin.getLogger().warning("Failed to show player: " + e.getMessage());
                            }
                        } else {
                            double distance = viewer.getLocation().distance(target.getLocation());
                            if(distance <= 50){
                                try {
                                    hidePlayer(viewer, target);
                                } catch (InvocationTargetException e) {
                                    plugin.getLogger().warning("Failed to hide player: " + e.getMessage());
                                }
                            }
                        }
                    }
                }.runTaskAsynchronously(plugin);
            });
        });
    }

    public boolean isHiddenFor(Player target, Player viewer) {
        Set<UUID> hiddenFor = hiddenPlayersMap.get(target.getUniqueId());
        return hiddenFor != null && hiddenFor.contains(viewer.getUniqueId());
    }

    public void hidePlayer(Player viewer, Player target) throws InvocationTargetException {
        if (!worldGuardHandler.canPvp(viewer.getLocation()) || !worldGuardHandler.canPvp(target.getLocation())) {
            return;
        }

        Set<UUID> hiddenFor = hiddenPlayersMap.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());

        if (hiddenFor.contains(viewer.getUniqueId())) {
            return;
        }

        hiddenFor.add(viewer.getUniqueId());

        if (target.hasPotionEffect(PotionEffectType.INVISIBILITY) || target.isSneaking()) {
            packetHandler.sendDestroyPacket(viewer, target);
            packetHandler.sendFakeTeleportPacket(viewer, target);
            packetHandler.sendPacketArmor(target, viewer, target.getInventory().getArmorContents());
        } else {
            packetHandler.sendPacketHandItems(target, viewer, true);
            ItemStack[] nulls = new ItemStack[4];
            Arrays.fill(nulls, new ItemStack(Material.AIR));
            packetHandler.sendPacketArmor(target, viewer, nulls);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target == null || viewer == null) return;
                    try {
                        packetHandler.sendPacketHandItems(target, viewer, true);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to send hand items: " + e.getMessage());
                    }
                }
            }.runTaskLater(plugin, 1);
        }
    }

    public void showPlayer(Player viewer, Player target) throws InvocationTargetException {
        if (target.getGameMode() == GameMode.SPECTATOR || isVanished(target)) return;

        Set<UUID> hiddenFor = hiddenPlayersMap.get(target.getUniqueId());
        if (hiddenFor == null || !hiddenFor.contains(viewer.getUniqueId())) return;

        hiddenFor.remove(viewer.getUniqueId());
        if (hiddenFor.isEmpty()) {
            hiddenPlayersMap.remove(target.getUniqueId());
        }

        packetHandler.sendPlayerInfoPacket(viewer, target, true);
        packetHandler.sendSpawnPacket(viewer, target);
        packetHandler.sendPacketArmor(target, viewer, target.getInventory().getArmorContents());
        packetHandler.sendPacketHandItems(target, viewer, false);
        packetHandler.sendHeadRotationPacket(viewer, target);
        packetHandler.sendInvisibilityPacket(viewer, target, target.hasPotionEffect(PotionEffectType.INVISIBILITY));
    }

    private boolean isVisible(Player viewer, Player target) {
        if (viewer.getGameMode() == GameMode.SPECTATOR) return true;

        if (viewer.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            if (viewer.getLocation().toVector().distance(target.getLocation().toVector()) >= 5) {
                return false;
            }
        }

        if (!worldGuardHandler.canPvp(viewer.getLocation()) || !worldGuardHandler.canPvp(target.getLocation())) {
            return true;
        }

        if (isVanished(target)) return false;

        double distance = viewer.getLocation().distance(target.getLocation());
        if (distance >= 50) return false;
        if (distance <= 2.0) return true;
        if (target.hasPotionEffect(PotionEffectType.GLOWING)) return true;

        Location viewerLocation = viewer.getEyeLocation();
        Location targetLocation = target.getLocation();
        Vector toTarget = targetLocation.toVector().subtract(viewerLocation.toVector()).normalize();
        Vector viewerDirection = viewerLocation.getDirection().normalize();
        double angle = Math.toDegrees(toTarget.angle(viewerDirection));
        if (angle > 75) return false;

        return checkLineOfSight(viewer, target);
    }

    private boolean checkLineOfSight(Player viewer, Player target) {
        Location viewerLocation = viewer.getEyeLocation();
        double halfWidth = target.getWidth() / 2.0;
        double height = target.getHeight();

        for (double i = 0; i < height; i += 0.1) {
            for (double j = 0; j < halfWidth; j += halfWidth / 10) {
                if (checkSingleLine(viewer, viewerLocation, target.getLocation().add(halfWidth - j, i, halfWidth))) return true;
                if (checkSingleLine(viewer, viewerLocation, target.getLocation().add(-halfWidth, i, halfWidth - j))) return true;
                if (checkSingleLine(viewer, viewerLocation, target.getLocation().add(halfWidth, i, -halfWidth + j))) return true;
                if (checkSingleLine(viewer, viewerLocation, target.getLocation().add(-halfWidth + j, i, -halfWidth))) return true;
            }
        }
        return false;
    }

    private boolean checkSingleLine(Player viewer, Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        RayTraceResult rayTraceResult = viewer.getWorld().rayTraceBlocks(
                from,
                direction,
                direction.length(),
                FluidCollisionMode.NEVER,
                true
        );
        return rayTraceResult == null || rayTraceResult.getHitBlock() == null || isTransparent(rayTraceResult.getHitBlock().getType());
    }

    private boolean isVanished(Player player) {
        return Bukkit.getPluginManager().isPluginEnabled("Essentials") &&
                PlaceholderAPI.setPlaceholders(player, "%essentials_vanished%").equalsIgnoreCase("yes");
    }

    private boolean isTransparent(Material material) {
        String materialName = material.name().toUpperCase();
        return material.isTransparent() ||
                materialName.contains("GLASS") ||
                materialName.equals("IRON_BARS") ||
                materialName.equals("ICE") ||
                materialName.contains("LEAVES") ||
                materialName.contains("FENCE") ||
                materialName.equals("CAKE") ||
                materialName.equals("DIRT_PATH") ||
                materialName.equals("DRAGON_EGG") ||
                materialName.contains("SLAB") ||
                materialName.contains("STAIRS") ||
                materialName.contains("CARPET") ||
                materialName.contains("BANNER");
    }

    public void cleanup() {
        Bukkit.getOnlinePlayers().forEach(viewer -> {
            Bukkit.getOnlinePlayers().forEach(target -> {
                if (isHiddenFor(target, viewer)) {
                    try {
                        showPlayer(viewer, target);
                    } catch (InvocationTargetException e) {
                        plugin.getLogger().warning("Failed to show player during cleanup: " + e.getMessage());
                    }
                }
            });
        });
        hiddenPlayersMap.clear();
    }
}