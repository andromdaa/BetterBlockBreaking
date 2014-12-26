package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;
import java.util.UUID;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.Entity;
import net.minecraft.server.v1_8_R1.EntityChicken;
import net.minecraft.server.v1_8_R1.EntityPlayer;
import net.minecraft.server.v1_8_R1.EnumPlayerDigType;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

public class BetterBlockBreaking extends JavaPlugin implements Listener {

    private static int blockDamageUpdateDelay = 20 * 20; // seconds * ticks
    private ProtocolManager protocolManager;

    public void onEnable() {
	Bukkit.getServer().getPluginManager().registerEvents(this, this);

    }

    public void onLoad() {
	protocolManager = ProtocolLibrary.getProtocolManager();
	protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
	    @SuppressWarnings("deprecation")
	    @Override
	    public void onPacketReceiving(PacketEvent event) {
		if (event.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {

		    PacketContainer packet = event.getPacket();
		    StructureModifier<EnumPlayerDigType> data = packet.getSpecificModifier(EnumPlayerDigType.class);
		    StructureModifier<BlockPosition> dataTemp = packet.getSpecificModifier(BlockPosition.class);

		    EnumPlayerDigType type = data.getValues().get(0);
		    Player p = event.getPlayer();
		    BlockPosition pos = dataTemp.getValues().get(0);

		    if (type == EnumPlayerDigType.START_DESTROY_BLOCK) {

			p.setMetadata("BlockBeginDestroy", new FixedMetadataValue(plugin, new Date()));
			if (p.hasMetadata("currentDamageTaskId"))
			    Bukkit.getScheduler().cancelTask(p.getMetadata("currentDamageTaskId").get(0).asInt());
			BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new ShowCurrentBlockDamageTask(plugin, p, pos), 0, 2);
			p.setMetadata("currentDamageTaskId", new FixedMetadataValue(plugin, task.getTaskId()));
		    } else if (type == EnumPlayerDigType.ABORT_DESTROY_BLOCK || type == EnumPlayerDigType.STOP_DESTROY_BLOCK) {

			if (p.hasMetadata("currentDamageTaskId"))
			    Bukkit.getScheduler().cancelTask(p.getMetadata("currentDamageTaskId").get(0).asInt());
			Date old = (Date) p.getMetadata("BlockBeginDestroy").get(0).value();
			Date now = new Date();
			long differenceMilliseconds = now.getTime() - old.getTime();
			p.removeMetadata("BlockBeginDestroy", plugin);
			((BetterBlockBreaking) plugin).SetBlockDamage(p, pos, differenceMilliseconds);
		    }
		}
	    }
	});
    }

    public void onDisable() {

    }

    public void SetBlockDamage(Player damager, BlockPosition pos, long totalMilliseconds) {
	WorldServer world = ((CraftWorld) damager.getWorld()).getHandle();
	EntityPlayer player = ((CraftPlayer) damager).getHandle();
	net.minecraft.server.v1_8_R1.Block block = world.getType(pos).getBlock();

	float i = totalMilliseconds / 20; // Magic value

	float f = 1000 * ((block.getDamage(player, world, pos) * (float) (i)) / 240);
	damageBlock(damager, new Location(damager.getWorld(), pos.getX(), pos.getY(), pos.getZ()).getBlock(), f);
    }

    @EventHandler
    public void onPlayerDestroyBlock(BlockBreakEvent event) {
	Block block = event.getBlock();
	WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
	BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
	cleanBlock(block, world, pos);
	BreakBlock(event.getBlock(), world, pos);
	event.setCancelled(true);
    }

    public void BreakBlock(Block block, WorldServer world, BlockPosition pos) {
	if (block.getType() != org.bukkit.Material.AIR) {
	    cleanBlock(block, world, pos);
	    block.getWorld().playSound(block.getLocation(), Sound.ZOMBIE_WOODBREAK, 2.0f, 1.0f);
	    // world.triggerEffect(1012, pos, 0);
	    block.breakNaturally();
	}
    }

    private void cleanBlock(Block block, WorldServer world, BlockPosition pos) {
	if (block.hasMetadata("updateBlockDamageTaskId")) {
	    int updateBlockDamageTaskId = block.getMetadata("updateBlockDamageTaskId").get(0).asInt();
	    Bukkit.getScheduler().cancelTask(updateBlockDamageTaskId);
	}

	// block.getWorld().playSound(block.getLocation(), Sound.ZOMBIE_WOOD, 1.0f, 1.0f);
	if (block.hasMetadata("monster")) {
	    UUID monsterUUID = (UUID) block.getMetadata("monster").get(0).value();
	    Entity toRemove = world.getEntity(monsterUUID);
	    if (block.hasMetadata("monsterId")) {
		((CraftServer) getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
			new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, -1));
	    }
	    if (toRemove != null)
		toRemove.die();
	}

	block.removeMetadata("damage", this);
	block.removeMetadata("monster", this);
	block.removeMetadata("monsterId", this);
	block.removeMetadata("updateBlockDamageTaskId", this);
    }

    @SuppressWarnings({ "deprecation" })
    private void damageBlock(Player p, Block block, float amount) {
	if (block != null) {
	    WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
	    BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());

	    float damage = (block.hasMetadata("damage") ? block.getMetadata("damage").get(0).asFloat() : 0);
	    damage += amount;
	    if (damage < 10) {
		block.setMetadata("damage", new FixedMetadataValue(this, damage));

		if (!block.hasMetadata("monster")) {
		    Entity monster = new EntityChicken(world);
		    world.addEntity(monster, SpawnReason.CUSTOM);
		    block.setMetadata("monster", new FixedMetadataValue(this, monster.getUniqueID()));
		    block.setMetadata("monsterId", new FixedMetadataValue(this, monster.getId()));
		}

		if (!block.hasMetadata("updateBlockDamageTaskId")) {
		    BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new ShowBlockDamageTask(this, block), blockDamageUpdateDelay, blockDamageUpdateDelay);
		    int updateBlockDamageTaskId = task.getTaskId();
		    block.setMetadata("updateBlockDamageTaskId", new FixedMetadataValue(this, updateBlockDamageTaskId));
		}

		((CraftServer) getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
			new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, -1));

		((CraftServer) getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
			new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, (int) damage));
	    } else {
		BreakBlock(block, world, pos);
	    }
	}
    }

}
