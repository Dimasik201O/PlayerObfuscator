package org.dimasik.playerobfuscator;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Collections;

public class PacketHandler {
    private final PlayerObfuscator plugin;
    private final ProtocolManager protocolManager;

    public PacketHandler(PlayerObfuscator plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void sendDestroyPacket(Player viewer, Player target) {
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityDestroy(target.getEntityId()));
    }

    public void sendFakeTeleportPacket(Player viewer, Player target) {
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
    }

    public void sendPlayerInfoPacket(Player viewer, Player target, boolean add) {
        EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(
                add ? PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER :
                        PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER,
                entityPlayer);
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
    }

    public void sendSpawnPacket(Player viewer, Player target) {
        EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
        PacketPlayOutNamedEntitySpawn packet = new PacketPlayOutNamedEntitySpawn(entityPlayer);
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
    }

    public void sendPacketArmor(Player target, Player receiver, ItemStack[] armor) {
        List<Pair<EnumItemSlot, net.minecraft.server.v1_16_R3.ItemStack>> items = List.of(
                Pair.of(EnumItemSlot.HEAD, org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(armor[3])),
                Pair.of(EnumItemSlot.CHEST, org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(armor[2])),
                Pair.of(EnumItemSlot.LEGS, org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(armor[1])),
                Pair.of(EnumItemSlot.FEET, org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(armor[0]))
        );
        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(
                target.getEntityId(),
                items
        );
        ((CraftPlayer) receiver).getHandle().playerConnection.sendPacket(packet);
    }

    public void sendPacketHandItems(Player target, Player receiver, boolean empty) {
        List<Pair<EnumItemSlot, net.minecraft.server.v1_16_R3.ItemStack>> items = List.of(
                Pair.of(EnumItemSlot.MAINHAND,
                        org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(
                                empty ? new ItemStack(Material.AIR) : target.getInventory().getItemInMainHand())),
                Pair.of(EnumItemSlot.OFFHAND,
                        org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack.asNMSCopy(
                                empty ? new ItemStack(Material.AIR) : target.getInventory().getItemInOffHand()))
        );
        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(
                target.getEntityId(),
                items
        );
        ((CraftPlayer) receiver).getHandle().playerConnection.sendPacket(packet);
    }

    public void sendHeadRotationPacket(Player viewer, Player target) {
        PacketPlayOutEntityHeadRotation headPacket = new PacketPlayOutEntityHeadRotation(
                ((CraftPlayer) target).getHandle(),
                (byte) ((target.getLocation().getYaw() % 360.0F) * 256.0F / 360.0F)
        );
        PacketPlayOutEntity.PacketPlayOutEntityLook lookPacket = new PacketPlayOutEntity.PacketPlayOutEntityLook(
                target.getEntityId(),
                (byte) ((target.getLocation().getYaw() % 360.0F) * 256.0F / 360.0F),
                (byte) ((target.getLocation().getPitch() % 360.0F) * 256.0F / 360.0F),
                true
        );
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(headPacket);
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(lookPacket);
    }

    public void sendInvisibilityPacket(Player viewer, Player target, boolean invis) {
        EntityPlayer entityPlayer = ((CraftPlayer) target).getHandle();
        DataWatcher watcher = entityPlayer.getDataWatcher();

        byte flags = watcher.get(DataWatcherRegistry.a.a(0));

        if (invis) {
            flags |= 0x20;
        } else {
            flags &= ~0x20;
        }

        watcher.set(DataWatcherRegistry.a.a(0), flags);

        PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(
                target.getEntityId(),
                watcher,
                true
        );
        ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
    }
}