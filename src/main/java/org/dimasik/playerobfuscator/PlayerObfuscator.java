package org.dimasik.playerobfuscator;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;
import com.Zrips.CMI.Containers.CMIVanish;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class PlayerObfuscator extends JavaPlugin implements Listener {

    private ProtocolManager protocolManager;
    private final Map<UUID, Set<UUID>> hiddenPlayersMap = new HashMap<>();

    private boolean isWorldGuardInitialized = false;

    @Override
    public void onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager();

        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().size() == 0) {
                    return;
                }
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (viewer == target) continue;

                        if (!viewer.getWorld().equals(target.getWorld())) {
                            if (isHiddenFor(target, viewer)) {
                                showPlayer(viewer, target);
                            }
                            continue;
                        }

                        if (!isVisible(viewer, target)) {
                            hidePlayer(viewer, target);
                        } else {
                            showPlayer(viewer, target);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public void onDisable() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (isHiddenFor(target, viewer)) {
                    showPlayer(viewer, target);
                }
            }
        }
        hiddenPlayersMap.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isWorldGuardInitialized) {
            initializeWorldGuard();
            isWorldGuardInitialized = true;
        }
    }
    private void initializeWorldGuard() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("WorldGuard не найден! Некоторые функции будут недоступны.");
            return;
        }

        getLogger().info("WorldGuard успешно инициализирован!");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        checkVisibilityForAll();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        checkVisibilityForAll();
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        checkVisibilityForAll();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        checkVisibilityForAll();
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        checkVisibilityForAll();
    }
    private void checkVisibilityForAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer == target) continue;

                if (!viewer.getWorld().equals(target.getWorld())) {
                    if (isHiddenFor(target, viewer)) {
                        showPlayer(viewer, target);
                    }
                    continue;
                }

                if (isVisible(viewer, target)) {
                    showPlayer(viewer, target);
                } else {
                    hidePlayer(viewer, target);
                }
            }
        }
    }
    private boolean isHiddenFor(Player target, Player viewer) {
        Set<UUID> hiddenFor = hiddenPlayersMap.get(target.getUniqueId());
        return hiddenFor != null && hiddenFor.contains(viewer.getUniqueId());
    }

    private void hidePlayer(Player viewer, Player target) {
        if(!canPvp(viewer.getLocation()) || !canPvp(target.getLocation())){
            return;
        }

        Set<UUID> hiddenFor = hiddenPlayersMap.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());

        if (hiddenFor.contains(viewer.getUniqueId())) {
            return;
        }

        hiddenFor.add(viewer.getUniqueId());

        PacketContainer destroyPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntegerArrays().write(0, new int[]{target.getEntityId()});

        try {
            protocolManager.sendServerPacket(viewer, destroyPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        PacketContainer teleportPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_TELEPORT);
        teleportPacket.getIntegers().write(0, target.getEntityId());
        teleportPacket.getDoubles().write(0, 0.0);
        teleportPacket.getDoubles().write(1, -1000.0);
        teleportPacket.getDoubles().write(2, 0.0);
        teleportPacket.getBytes().write(0, (byte) 0);
        teleportPacket.getBytes().write(1, (byte) 0);

        try {
            protocolManager.sendServerPacket(viewer, teleportPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendPacketArmor(target, viewer, target.getInventory().getArmorContents());
    }
    private void showPlayer(Player viewer, Player target) {
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (isVanished(target)) {
            return;
        }

        Set<UUID> hiddenFor = hiddenPlayersMap.get(target.getUniqueId());

        if (hiddenFor == null || !hiddenFor.contains(viewer.getUniqueId())) {
            return;
        }

        hiddenFor.remove(viewer.getUniqueId());

        if (hiddenFor.isEmpty()) {
            hiddenPlayersMap.remove(target.getUniqueId());
        }

        sendPlayerInfoPacket(viewer, target, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);

        sendPlayerInfoPacket(viewer, target, EnumWrappers.PlayerInfoAction.ADD_PLAYER);

        PacketContainer spawnPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawnPacket.getIntegers().write(0, target.getEntityId());
        spawnPacket.getUUIDs().write(0, target.getUniqueId());
        spawnPacket.getDoubles().write(0, target.getLocation().getX());
        spawnPacket.getDoubles().write(1, target.getLocation().getY());
        spawnPacket.getDoubles().write(2, target.getLocation().getZ());

        float yaw = target.getLocation().getYaw();
        float pitch = target.getLocation().getPitch();
        spawnPacket.getBytes().write(0, (byte) ((yaw * 256.0F) / 360.0F));
        spawnPacket.getBytes().write(1, (byte) ((pitch * 256.0F) / 360.0F));

        try {
            protocolManager.sendServerPacket(viewer, spawnPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendPacketArmor(target, viewer, target.getInventory().getArmorContents());

        sendPacketHandItems(target, viewer);
    }
    private void sendPlayerInfoPacket(Player viewer, Player target, EnumWrappers.PlayerInfoAction action) {
        PacketContainer playerInfoPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.PLAYER_INFO);
        playerInfoPacket.getPlayerInfoAction().write(0, action);

        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(target);
        playerInfoPacket.getPlayerInfoDataLists().write(0, Collections.singletonList(
                new PlayerInfoData(
                        profile,
                        0,
                        EnumWrappers.NativeGameMode.SURVIVAL,
                        WrappedChatComponent.fromText(target.getName())
                )
        ));

        try {
            protocolManager.sendServerPacket(viewer, playerInfoPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacketHandItems(Player target, Player receiver) {
        try {
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> slotStackPairs = new ArrayList<>();

            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, target.getInventory().getItemInMainHand()));
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, target.getInventory().getItemInOffHand()));

            PacketContainer handItemsPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_EQUIPMENT);
            handItemsPacket.getIntegers().write(0, target.getEntityId());

            handItemsPacket.getSlotStackPairLists().write(0, slotStackPairs);

            protocolManager.sendServerPacket(receiver, handItemsPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacketArmor(Player target, Player receiver, ItemStack[] armor) {
        try {
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> slotStackPairs = new ArrayList<>();

            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, armor[3]));
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, armor[2]));
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, armor[1]));
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.FEET, armor[0]));

            PacketContainer armorPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_EQUIPMENT);
            armorPacket.getIntegers().write(0, target.getEntityId());

            armorPacket.getSlotStackPairLists().write(0, slotStackPairs);

            protocolManager.sendServerPacket(receiver, armorPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean canPvp(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        Collection<ProtectedRegion> regions = query.getApplicableRegions(BukkitAdapter.adapt(location)).getRegions();

        ProtectedRegion highestPriorityRegion = null;
        for (ProtectedRegion region : regions) {
            if (highestPriorityRegion == null || region.getPriority() > highestPriorityRegion.getPriority()) {
                highestPriorityRegion = region;
            }
        }

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

    private boolean isVisible(Player viewer, Player target) {
        if (viewer.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }

        if(viewer.hasPotionEffect(PotionEffectType.BLINDNESS)){
            if(viewer.getLocation().toVector().distance(target.getLocation().toVector()) >= 5){
                return false;
            }
        }

        if(!canPvp(viewer.getLocation()) || !canPvp(target.getLocation())){
            return true;
        }

        if (isVanished(target)) {
            return false;
        }

        if(viewer.getLocation().toVector().distance(target.getLocation().toVector()) >= 50){
            return false;
        }

        double distance = viewer.getLocation().distance(target.getLocation());
        if (distance <= 1.0) {
            return true;
        }

        if(target.hasPotionEffect(PotionEffectType.GLOWING)){
            return true;
        }

        Location viewerLocation = viewer.getEyeLocation();
        Location targetLocation = target.getLocation();

        Vector toTarget = targetLocation.toVector().subtract(viewerLocation.toVector()).normalize();

        Vector viewerDirection = viewerLocation.getDirection().normalize();

        double angle = Math.toDegrees(toTarget.angle(viewerDirection));

        if (angle > 90) {
            return false;
        }

        double halfWidth = target.getWidth() / 2.0;
        double height = target.getHeight();

        Location[] hitboxCorners = {};

        List<Location> locations = new ArrayList<>();
        for(double i = 0; i < height; i += 0.05){
            for (double j = 0; j < halfWidth; j += halfWidth / 20){
                locations.add(target.getLocation().add(halfWidth - j, i, halfWidth));
                locations.add(target.getLocation().add(-halfWidth, i, halfWidth - j));
                locations.add(target.getLocation().add(halfWidth, i, -halfWidth + j));
                locations.add(target.getLocation().add(-halfWidth + j, i, -halfWidth));
            }
        }

        hitboxCorners = locations.toArray(new Location[0]);

        for (Location corner : hitboxCorners) {
            Vector direction = corner.toVector().subtract(viewerLocation.toVector());
            RayTraceResult rayTraceResult = viewer.getWorld().rayTraceBlocks(
                    viewerLocation,
                    direction,
                    direction.length(),
                    FluidCollisionMode.NEVER,
                    true
            );

            if (rayTraceResult == null || rayTraceResult.getHitBlock() == null) {
                return true;
            }

            Material blockType = rayTraceResult.getHitBlock().getType();
            if (isTransparent(blockType)) {
                return true;
            }
        }

        return false;
    }

    private boolean isVanished(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
            return CMIUser.getUser(player).isVanished();
        }
        return false;
    }

    private boolean isTransparent(Material material) {
        String materialName = material.name().toUpperCase();

        return materialName.contains("GLASS") ||
                materialName.equals("IRON_BARS") ||
                materialName.equals("ICE") ||
                materialName.contains("LEAVES") ||
                materialName.contains("FENCE") ||
                materialName.equals("CAKE") ||
                materialName.equals("DIRT_PATH") ||
                materialName.equals("DRAGON_EGG") ||
                material.isTransparent();
    }
}