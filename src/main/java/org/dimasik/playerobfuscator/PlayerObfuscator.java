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
    private final Map<UUID, Set<UUID>> hiddenPlayersMap = new HashMap<>(); // Карта для хранения скрытых игроков

    private boolean isWorldGuardInitialized = false; // Флаг для отслеживания инициализации WorldGuard

    @Override
    public void onEnable() {
        // Инициализируем ProtocolLib
        protocolManager = ProtocolLibrary.getProtocolManager();

        // Регистрируем события
        Bukkit.getPluginManager().registerEvents(this, this);

        // Запускаем задачу, которая будет проверять видимость игроков каждые 5 тиков
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().size() == 0) {
                    return;
                }
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (viewer == target) continue; // Игрок не должен скрывать себя

                        // Игнорируем игроков из других миров
                        if (!viewer.getWorld().equals(target.getWorld())) {
                            if (isHiddenFor(target, viewer)) {
                                showPlayer(viewer, target); // Показываем игрока, если он в другом мире
                            }
                            continue;
                        }

                        if (!isVisible(viewer, target)) {
                            // Скрываем игрока, если он не виден
                            hidePlayer(viewer, target);
                        } else {
                            // Показываем игрока, если он виден
                            showPlayer(viewer, target);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L); // Проверка каждые 5 тиков
    }

    @Override
    public void onDisable() {
        // При отключении плагина показываем всех игроков всем
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (isHiddenFor(target, viewer)) {
                    showPlayer(viewer, target); // Показываем игрока, если он был скрыт
                }
            }
        }
        hiddenPlayersMap.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Инициализируем WorldGuard только при первом входе игрока
        if (!isWorldGuardInitialized) {
            initializeWorldGuard();
            isWorldGuardInitialized = true; // Устанавливаем флаг, чтобы больше не инициализировать
        }
    }

    /**
     * Инициализирует WorldGuard.
     */
    private void initializeWorldGuard() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("WorldGuard не найден! Некоторые функции будут недоступны.");
            return;
        }

        getLogger().info("WorldGuard успешно инициализирован!");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // При разрушении блока проверяем видимость всех игроков для всех зрителей
        checkVisibilityForAll();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // При установке блока проверяем видимость всех игроков для всех зрителей
        checkVisibilityForAll();
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // При изменении блока сущностью (например, эндермен) проверяем видимость
        checkVisibilityForAll();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // При движении игрока проверяем видимость всех игроков для всех зрителей
        checkVisibilityForAll();
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        // При изменении режима игры проверяем видимость всех игроков для этого игрока
        checkVisibilityForAll();
    }

    /**
     * Проверяет видимость всех игроков для всех зрителей.
     */
    private void checkVisibilityForAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer == target) continue; // Игрок не должен скрывать себя

                // Игнорируем игроков из других миров
                if (!viewer.getWorld().equals(target.getWorld())) {
                    if (isHiddenFor(target, viewer)) {
                        showPlayer(viewer, target); // Показываем игрока, если он в другом мире
                    }
                    continue;
                }

                if (isVisible(viewer, target)) {
                    // Показываем игрока, если он стал видимым
                    showPlayer(viewer, target);
                } else {
                    // Скрываем игрока, если он не виден
                    hidePlayer(viewer, target);
                }
            }
        }
    }

    /**
     * Проверяет, скрыт ли target для viewer.
     *
     * @param target Игрок, которого нужно проверить.
     * @param viewer Игрок, для которого проверяется видимость.
     * @return true, если target скрыт для viewer, иначе false.
     */
    private boolean isHiddenFor(Player target, Player viewer) {
        Set<UUID> hiddenFor = hiddenPlayersMap.get(target.getUniqueId());
        return hiddenFor != null && hiddenFor.contains(viewer.getUniqueId());
    }

    /**
     * Скрывает игрока визуально, но оставляет его в таб-листе.
     *
     * @param viewer Игрок, который видит изменения.
     * @param target Игрок, которого нужно скрыть.
     */
    private void hidePlayer(Player viewer, Player target) {
        if(!canPvp(viewer.getLocation()) || !canPvp(target.getLocation())){
            return;
        }

        // Получаем множество зрителей, для которых скрыт target
        Set<UUID> hiddenFor = hiddenPlayersMap.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());

        // Если target уже скрыт для viewer, ничего не делаем
        if (hiddenFor.contains(viewer.getUniqueId())) {
            return;
        }

        // Добавляем viewer в множество зрителей, для которых скрыт target
        hiddenFor.add(viewer.getUniqueId());

        // Отправляем пакет EntityDestroy, чтобы скрыть игрока
        PacketContainer destroyPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntegerArrays().write(0, new int[]{target.getEntityId()});

        try {
            protocolManager.sendServerPacket(viewer, destroyPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Отправляем пакет EntityTeleport, чтобы создать иллюзию телепортации
        PacketContainer teleportPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_TELEPORT);
        teleportPacket.getIntegers().write(0, target.getEntityId()); // ID сущности
        teleportPacket.getDoubles().write(0, 0.0); // X (далеко за пределы видимости)
        teleportPacket.getDoubles().write(1, -1000.0); // Y (далеко за пределы видимости)
        teleportPacket.getDoubles().write(2, 0.0); // Z (далеко за пределы видимости)
        teleportPacket.getBytes().write(0, (byte) 0); // Yaw
        teleportPacket.getBytes().write(1, (byte) 0); // Pitch

        try {
            protocolManager.sendServerPacket(viewer, teleportPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Обновляем броню для скрытия
        sendPacketArmor(target, viewer, target.getInventory().getArmorContents());
    }

    /**
     * Показывает игрока визуально.
     *
     * @param viewer Игрок, который видит изменения.
     * @param target Игрок, которого нужно показать.
     */
    private void showPlayer(Player viewer, Player target) {
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (isVanished(target)) {
            return;
        }

        // Получаем множество зрителей, для которых скрыт target
        Set<UUID> hiddenFor = hiddenPlayersMap.get(target.getUniqueId());

        // Если target не скрыт для viewer, ничего не делаем
        if (hiddenFor == null || !hiddenFor.contains(viewer.getUniqueId())) {
            return;
        }

        // Удаляем viewer из множества зрителей, для которых скрыт target
        hiddenFor.remove(viewer.getUniqueId());

        // Если множество пусто, удаляем запись из карты
        if (hiddenFor.isEmpty()) {
            hiddenPlayersMap.remove(target.getUniqueId());
        }

        // Удаляем игрока из списка видимых
        sendPlayerInfoPacket(viewer, target, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);

        // Добавляем игрока снова с обновленными данными
        sendPlayerInfoPacket(viewer, target, EnumWrappers.PlayerInfoAction.ADD_PLAYER);

        // Отправляем пакет NamedEntitySpawn, чтобы показать игрока
        PacketContainer spawnPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawnPacket.getIntegers().write(0, target.getEntityId());
        spawnPacket.getUUIDs().write(0, target.getUniqueId());
        spawnPacket.getDoubles().write(0, target.getLocation().getX());
        spawnPacket.getDoubles().write(1, target.getLocation().getY());
        spawnPacket.getDoubles().write(2, target.getLocation().getZ());

        // Корректно преобразуем yaw и pitch в байты
        float yaw = target.getLocation().getYaw();
        float pitch = target.getLocation().getPitch();
        spawnPacket.getBytes().write(0, (byte) ((yaw * 256.0F) / 360.0F)); // Преобразуем yaw в байт
        spawnPacket.getBytes().write(1, (byte) ((pitch * 256.0F) / 360.0F)); // Преобразуем pitch в байт

        try {
            protocolManager.sendServerPacket(viewer, spawnPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Обновляем броню для показа
        sendPacketArmor(target, viewer, target.getInventory().getArmorContents());

        // Обновляем предметы в руках
        sendPacketHandItems(target, viewer);
    }

    /**
     * Отправляет пакет PlayerInfo для обновления данных игрока.
     *
     * @param viewer Игрок, который видит изменения.
     * @param target Игрок, чьи данные обновляются.
     * @param action Действие (ADD_PLAYER или REMOVE_PLAYER).
     */
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
    }/**
     * Отправляет пакеты для обновления предметов в руках игрока.
     *
     * @param target    Игрок, чьи предметы обновляются.
     * @param receiver  Игрок, который видит изменения.
     */
    private void sendPacketHandItems(Player target, Player receiver) {
        try {
            // Создаем список пар (слот, предмет)
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> slotStackPairs = new ArrayList<>();

            // Заполняем список парами для основной и дополнительной руки
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, target.getInventory().getItemInMainHand())); // Основная рука
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, target.getInventory().getItemInOffHand())); // Дополнительная рука

            // Создаем пакет
            PacketContainer handItemsPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_EQUIPMENT);
            handItemsPacket.getIntegers().write(0, target.getEntityId()); // ID сущности

            // Записываем список пар в пакет
            handItemsPacket.getSlotStackPairLists().write(0, slotStackPairs);

            // Отправляем пакет
            protocolManager.sendServerPacket(receiver, handItemsPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Отправляет пакеты для обновления брони игрока.
     *
     * @param target    Игрок, чья броня обновляется.
     * @param receiver  Игрок, который видит изменения.
     * @param armor     Массив предметов брони (шлем, нагрудник, поножи, ботинки).
     */
    private void sendPacketArmor(Player target, Player receiver, ItemStack[] armor) {
        try {
            // Создаем список пар (слот, предмет)
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> slotStackPairs = new ArrayList<>();

            // Заполняем список парами
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, armor[3])); // Шлем (HEAD)
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, armor[2])); // Нагрудник (CHEST)
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, armor[1])); // Поножи (LEGS)
            slotStackPairs.add(new Pair<>(EnumWrappers.ItemSlot.FEET, armor[0])); // Ботинки (FEET)

            // Создаем пакет
            PacketContainer armorPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_EQUIPMENT);
            armorPacket.getIntegers().write(0, target.getEntityId()); // ID сущности

            // Записываем список пар в пакет
            armorPacket.getSlotStackPairLists().write(0, slotStackPairs);

            // Отправляем пакет
            protocolManager.sendServerPacket(receiver, armorPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean canPvp(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        Collection<ProtectedRegion> regions = query.getApplicableRegions(BukkitAdapter.adapt(location)).getRegions();

        // Проверяем флаг "mantiitems-enable" в высшем регионе
        ProtectedRegion highestPriorityRegion = null;
        for (ProtectedRegion region : regions) {
            if (highestPriorityRegion == null || region.getPriority() > highestPriorityRegion.getPriority()) {
                highestPriorityRegion = region;
            }
        }

        if (highestPriorityRegion == null) {
            return true;
        }

        // Получаем флаг и проверяем его значение
        StateFlag flag = (StateFlag) WorldGuard.getInstance().getFlagRegistry().get("pvp");
        if (flag == null) {
            return true; // Если флаг не найден, считаем, что использование разрешено
        }

        StateFlag.State flagState = highestPriorityRegion.getFlag(flag);
        return flagState == null || flagState == StateFlag.State.ALLOW;
    }

    /**
     * Проверяет, виден ли target для viewer.
     *
     * @param viewer Игрок, который смотрит.
     * @param target Игрок, которого нужно проверить на видимость.
     * @return true, если target виден для viewer, иначе false.
     */
    private boolean isVisible(Player viewer, Player target) {
        // Если зритель находится в режиме наблюдателя, он видит всех игроков
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

        // Если целевой игрок скрыт с помощью vanish (CMI), он не виден
        if (isVanished(target)) {
            return false;
        }

        // Проверка расстояния между игроками
        double distance = viewer.getLocation().distance(target.getLocation());
        if (distance <= 1.0) {
            return true; // Если расстояние <= 1 блок, игрок виден
        }

        if(target.hasPotionEffect(PotionEffectType.GLOWING)){
            return true;
        }

        // Проверка угла между направлением взгляда зрителя и вектором к целевому игроку
        Location viewerLocation = viewer.getEyeLocation();
        Location targetLocation = target.getLocation();

        // Вектор от зрителя к целевому игроку
        Vector toTarget = targetLocation.toVector().subtract(viewerLocation.toVector()).normalize();

        // Вектор направления взгляда зрителя
        Vector viewerDirection = viewerLocation.getDirection().normalize();

        // Вычисляем угол между векторами
        double angle = Math.toDegrees(toTarget.angle(viewerDirection));

        // Если угол больше 90 градусов, целевой игрок не виден
        if (angle > 90) {
            return false;
        }

        // Остальная логика проверки видимости (например, через блоки)
        double halfWidth = target.getWidth() / 2.0;
        double height = target.getHeight();

        Location[] hitboxCorners = {
                target.getLocation().add(halfWidth, 0, halfWidth),
                target.getLocation().add(-halfWidth, 0, halfWidth),
                target.getLocation().add(halfWidth, 0, -halfWidth),
                target.getLocation().add(-halfWidth, 0, -halfWidth),
        };

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

    /**
     * Проверяет, скрыт ли игрок с помощью vanish через CMI.
     *
     * @param player Игрок, которого нужно проверить.
     * @return true, если игрок скрыт, иначе false.
     */
    private boolean isVanished(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
            return CMIUser.getUser(player).isVanished();
        }
        return false;
    }

    /**
     * Проверяет, является ли материал полупрозрачным.
     *
     * @param material Материал блока.
     * @return true, если материал полупрозрачный, иначе false.
     */
    private boolean isTransparent(Material material) {
        String materialName = material.name().toUpperCase();

        // Проверяем, содержит ли название материала ключевые слова, связанные с полупрозрачными блоками
        return materialName.contains("GLASS") ||
                materialName.equals("IRON_BARS") || // Железные решетки
                materialName.equals("ICE") || // Лава
                materialName.contains("LEAVES") || // Лава
                materialName.contains("FENCE") || // Лава
                materialName.equals("CAKE") || // Лава
                materialName.equals("DIRT_PATH") || // Лава
                materialName.equals("DRAGON_EGG") || // Лава
                material.isTransparent(); // Другие полупрозрачные блоки
    }
}