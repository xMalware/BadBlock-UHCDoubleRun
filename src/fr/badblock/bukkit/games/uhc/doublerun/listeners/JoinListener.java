package fr.badblock.bukkit.games.uhc.doublerun.listeners;

import java.util.HashSet;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ocelot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import fr.badblock.bukkit.games.uhc.doublerun.PluginUHC;
import fr.badblock.bukkit.games.uhc.doublerun.players.UHCScoreboard;
import fr.badblock.bukkit.games.uhc.doublerun.runnables.BossBarRunnable;
import fr.badblock.bukkit.games.uhc.doublerun.runnables.PreStartRunnable;
import fr.badblock.bukkit.games.uhc.doublerun.runnables.StartRunnable;
import fr.badblock.gameapi.BadListener;
import fr.badblock.gameapi.GameAPI;
import fr.badblock.gameapi.events.api.PlayerLoadedEvent;
import fr.badblock.gameapi.events.api.SpectatorJoinEvent;
import fr.badblock.gameapi.players.BadblockPlayer;
import fr.badblock.gameapi.players.BadblockPlayer.BadblockMode;
import fr.badblock.gameapi.players.BadblockTeam;
import fr.badblock.gameapi.utils.BukkitUtils;
import fr.badblock.gameapi.utils.i18n.TranslatableString;
import fr.badblock.gameapi.utils.itemstack.ItemAction;
import fr.badblock.gameapi.utils.itemstack.ItemEvent;
import fr.badblock.gameapi.utils.itemstack.ItemStackExtra.ItemPlaces;
import fr.badblock.gameapi.utils.selections.CuboidSelection;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.EntityPlayer;

public class JoinListener extends BadListener {
	private Random random = new Random();

	@EventHandler
	public void onSpectatorJoin(SpectatorJoinEvent e){
		e.getPlayer().teleport(PluginUHC.getInstance().getDefaultLoc());

		new UHCScoreboard(e.getPlayer());
	}


	@EventHandler
	public void onJoin(PlayerJoinEvent e)
	{
		e.setJoinMessage(null);
	}

	@EventHandler
	public void onLoaded(PlayerLoadedEvent e){
		if(inGame()){
			return;
		}

		BadblockPlayer player = (BadblockPlayer) e.getPlayer();
		if (!player.getBadblockMode().equals(BadblockMode.SPECTATOR) && !inGame()) {

			EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
			HashSet<Chunk> chunk = new HashSet<>();
			Location spawn = PluginUHC.getInstance().getConfiguration().spawn.getHandle();
			entityPlayer.chunkCoordIntPairQueue.add(new ChunkCoordIntPair(spawn.getChunk().getX(), spawn.getChunk().getZ()));
			Location lbo = spawn.clone().add(-120, 0, 120);
			Location lbz = spawn.clone().add(120, 0, -120);
			CuboidSelection sl = new CuboidSelection(lbo, lbz);

			for (Block b : sl.getBlocks())
			{
				if (chunk.contains(b.getChunk()))
				{
					continue;
				}

				chunk.add(b.getChunk());
			}

			for (Chunk c : chunk)
			{
				entityPlayer.chunkCoordIntPairQueue.add(new ChunkCoordIntPair(c.getX(), c.getZ()));
			}

			new BossBarRunnable(player.getUniqueId()).runTaskTimer(GameAPI.getAPI(), 0, 20L);

			player.setGameMode(GameMode.SURVIVAL);
			player.teleport(spawn);
			player.sendTimings(0, 80, 20);
			player.sendTranslatedTabHeader(new TranslatableString("uhcspeed.tab.header"), new TranslatableString("uhcspeed.tab.footer"));

			ItemStack item = GameAPI.getAPI().createItemStackFactory()
					.type(Material.ARROW)
					.doWithI18n(player.getPlayerData().getLocale())
					.displayName(new TranslatableString("uhcspeed.kitty.displayname"))
					.lore(new TranslatableString("uhcspeed.kitty.lore"))
					.asExtra(1)
					.listenAs(new ItemEvent() {
						long cooldown = 0;

						@Override
						public boolean call(ItemAction action, BadblockPlayer player) {
							if(cooldown > System.currentTimeMillis()){
								return true;
							}

							cooldown = System.currentTimeMillis() + 1000L;


							if(action == ItemAction.RIGHT_CLICK_AIR || action == ItemAction.RIGHT_CLICK_BLOCK){
								sendKitty(player);
							}

							return true;
						}
					}, ItemPlaces.HOTBAR_CLICKABLE).getHandler();

			new BukkitRunnable() {
				@Override
				public void run() {
					player.getInventory().addItem(item);
				}
			}.runTaskLater(GameAPI.getAPI(), 15L);

			String display = player.getTabGroupPrefix().getAsLine(player) + player.getName();
			BukkitUtils.getAllPlayers().forEach(plo ->
			{
				plo.sendTranslatedMessage("uhcspeed.joined", display, Bukkit.getOnlinePlayers().size(), PluginUHC.getInstance().getMaxPlayers());
				plo.playSound(Sound.CLICK);
			});
		}

		if (player.getCustomObjective() == null)
			new UHCScoreboard(player);

		PreStartRunnable.doJob();
		StartRunnable.joinNotify(Bukkit.getOnlinePlayers().size(), PluginUHC.getInstance().getMaxPlayers());

	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e){
		e.setQuitMessage(null);
	}

	private void sendKitty(BadblockPlayer player){
		final Ocelot ocelot = player.getWorld().spawn(player.getEyeLocation(), Ocelot.class);

		if (ocelot == null) {
			return;
		}

		int i = random.nextInt(Ocelot.Type.values().length);

		ocelot.setCatType(Ocelot.Type.values()[i]);
		ocelot.setTamed(true);
		ocelot.setBaby();
		ocelot.setVelocity(player.getEyeLocation().getDirection().multiply(2));

		new BukkitRunnable(){
			@Override
			public void run() {
				Location loc = ocelot.getLocation();
				ocelot.remove();
				loc.getWorld().createExplosion(loc, 0.0F); 

				changeWithRadius(player.getTeam(), loc.getBlock(), 2);

				for(Entity e : player.getNearbyEntities(2, 2, 2)){
					Vector vector = new Vector(
							e.getLocation().getX() - player.getLocation().getX(),
							0,
							e.getLocation().getZ() - player.getLocation().getZ()
							);

					vector.normalize();
					vector.multiply(2.0d);

					vector.setY(0.8);

					e.setVelocity(vector);
				}

			}
		}.runTaskLater(GameAPI.getAPI(), 20L);
	}

	@SuppressWarnings("deprecation")
	private void changeWithRadius(BadblockTeam team, Block block, int radius){
		DyeColor[] colors = DyeColor.values();
		CuboidSelection selec = PluginUHC.getInstance().getConfiguration().spawnZone.getHandle();

		for(int x=-radius;x<=radius;x++){
			for(int y=-radius;y<=radius;y++){
				for(int z=-radius;z<=radius;z++){
					Block b = block.getWorld().getBlockAt(block.getX() + x, block.getY() + y, block.getZ() + z);

					if(!selec.isInSelection(b))
						continue;

					if(b.getType() == Material.STONE || b.getType() == Material.COBBLESTONE){
						b.setType(Material.STAINED_CLAY);
					} else if(b.getType() == Material.GRASS || b.getType() == Material.DIRT){
						b.setType(Material.STAINED_GLASS);
					}

					if(b.getType() == Material.STAINED_GLASS || b.getType() == Material.WOOL || b.getType() == Material.STAINED_CLAY){
						DyeColor color = team != null ? team.getDyeColor() : colors[ new Random().nextInt(colors.length) ];
						b.setData(color.getWoolData());
					}
				}
			}
		}
	}
}
