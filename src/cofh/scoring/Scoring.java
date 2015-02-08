package cofh.scoring;

import cofh.scoring.Scoring.ClientPacketHandler.Message;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import gnu.trove.map.hash.TObjectLongHashMap;

import io.netty.buffer.ByteBuf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;

import org.apache.logging.log4j.Logger;

@Mod(modid = "CoFHScoring", name = "Scoring", version = "0.0.1.0", dependencies = "")
public class Scoring {

	private static String sep = System.getProperty("line.separator");
	public static long playerScore = 0;
	Logger log;
	Configuration config;
	static TObjectLongHashMap<String> score = new TObjectLongHashMap<String>();
	static TObjectLongHashMap<Item> values = new TObjectLongHashMap<Item>(8, 0.5f, 0L);
	static ArrayList<String> bossEntities = new ArrayList<String>();
	static long serverTickTime, timeMod;
	static boolean complete;
	File scoreData;
	SimpleNetworkWrapper networkWrapper;

	static long calculateScore(String name) {

		long v = score.get(name);
		long time = timeMod <= 0 ? 0 : serverTickTime / timeMod;
		return v - time;
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {

		log = event.getModLog();
		config = new Configuration(event.getSuggestedConfigurationFile());

		String comment = "1 point is subtracted from a player's score every this many ticks (default: 10 minutes (20*60*10))";
		timeMod = config.get("general", "TimeCost", 20 * 60 * 10, comment + ". 0 disables.").getInt() & 0xFFFFFFFFL;

		networkWrapper = new SimpleNetworkWrapper("CoFH|Scoring");
		networkWrapper.registerMessage(ClientPacketHandler.class, Message.class, 0, Side.CLIENT);
	}

	@EventHandler
	public void loadComplete(FMLLoadCompleteEvent event) {

		config.get("item_values", "iron_block", config.get("item_values", "iron_ingot", 5).getInt() * 9);
		config.get("item_values", "gold_block", config.get("item_values", "gold_ingot", 2).getInt() * 9);
		config.get("item_values", "emerald_block", config.get("item_values", "emerald", 1).getInt() * 9);

		ConfigCategory droplist = config.getCategory("item_values");
		droplist.setComment("Entries in this category are used to give players points for destroying them\n\n" +
				"Format: I:\"<item name>\" = value");
		for (Entry<String, Property> e : droplist.entrySet()) {
			Item item = GameData.getItemRegistry().getObject(e.getKey());
			if (item != null)
				values.put(item, e.getValue().getInt());
			else
				log.warn("item_values entry %s not found", e.getKey());
		}

		String c = "A list of entity IDs that when killed will end this round of scoring.";
		for (String a : config.get("general", "RoundEnders", new String[] { "EnderDragon" }, c).getStringList()) {
			bossEntities.add(a);
		}
		config.save();

		if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
			MinecraftForge.EVENT_BUS.register(this);
			FMLCommonHandler.instance().bus().register(this);
		}
	}

	@EventHandler
	public void serverStarting(FMLServerStartedEvent event) {

		scoreData = new File(DimensionManager.getCurrentSaveRootDirectory(), "score-data.prop");
		if (scoreData.exists() && scoreData.isDirectory())
			throw new Error("score-data.prop is a directory!");
		score.clear();
		serverTickTime = 0;
		complete = false;
		try {
			scoreData.createNewFile();
			BufferedReader reader = new BufferedReader(new FileReader(scoreData));
			for (String data; (data = reader.readLine()) != null;) {
				int i = data.indexOf('=');
				if (i >= 0)
					score.put(data.substring(0, i), Long.parseLong(data.substring(i + 1), 16));
				else {
					switch (data.charAt(0)) {
					case 3:
						complete = true;
					case 2:
						serverTickTime = Long.parseLong(data.substring(1), 36);
						break;
					default:
					}
				}
			}
			reader.close();
		} catch (Throwable e) {
			log.error("Unable to load score data!", e);
			scoreData = null;
		}

		if (event != null && !complete && FMLCommonHandler.instance().getSide() != Side.CLIENT) {
			MinecraftForge.EVENT_BUS.register(this);
			FMLCommonHandler.instance().bus().register(this);
		}

	}

	@EventHandler
	public void serverStopped(FMLServerStoppedEvent event) {

		if (scoreData != null && scoreData.exists()) {
			try {
				FileWriter writer = new FileWriter(scoreData);
				writer.write(complete ? 3 : 2);
				writer.write(Long.toString(serverTickTime, 36));
				writer.write(sep);
				int i = 0;
				for (String user : score.keySet()) {
					writer.write(user);
					writer.write('=');
					writer.write(Long.toHexString(score.get(user)));
					writer.write(sep);
					if ((++i & 15) == 0)
						writer.flush();
				}
				writer.close();
			} catch (Throwable e) {
				log.error("Unable to save score data!", e);
			}
		}

		if (event != null && FMLCommonHandler.instance().getSide() != Side.CLIENT) {
			MinecraftForge.EVENT_BUS.unregister(this);
			FMLCommonHandler.instance().bus().unregister(this);
		}
	}

	@SubscribeEvent
	public void playerLoggedIn(PlayerLoggedInEvent evt) {

		networkWrapper.sendTo(new Message(1, complete ? 1 : 0), (EntityPlayerMP) evt.player);
	}

	@SubscribeEvent
	public void serverTick(ServerTickEvent evt) {

		if (!complete && evt.phase == Phase.END)
			if (++serverTickTime % 900 == 0)
				serverStopped(null);
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void openGui(GuiOpenEvent evt) {

		if (evt.gui instanceof GuiGameOver)
			evt.gui = new cofh.scoring.GuiGameOver();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void stopEquipment(LivingDeathEvent evt) {

		if (evt.entity instanceof EntityPlayerMP) {
			networkWrapper.sendTo(new Message(0, calculateScore(evt.entity.getCommandSenderName())), (EntityPlayerMP) evt.entity);
			return;
		}
		DamageSource src = evt.source;
		if (!complete && src != null && src.getEntity() instanceof EntityPlayer && src.getEntity().addedToChunk &&
				bossEntities.contains(EntityList.getEntityString(evt.entity))) {
			complete = true;
			EntityPlayer winner = (EntityPlayer) src.getEntity();
			String winnerName = winner.getCommandSenderName();
			ScorePlayerTeam team = winner.getWorldScoreboard().getPlayersTeam(winnerName);
			if (team != null) {
				for (String s : (Collection<String>)team.getMembershipCollection()) {
					score.adjustValue(s, score.get(s));
				}
			} else {
				score.adjustValue(winnerName, score.get(winnerName));
			}
			serverStopped(null);
			for (EntityPlayer player : (List<EntityPlayer>) MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
				networkWrapper.sendTo(new Message(1, 1), (EntityPlayerMP) player);
				player.addStat(StatList.deathsStat, -1);
				player.setHealth(0);
				player.onDeath(DamageSource.generic);
			}
		}
	}

	@SubscribeEvent
	public void itemBroken(PlayerDestroyItemEvent evt) {

		if (!complete && evt.original != null) {
			long value = values.get(evt.original.getItem());
			score.adjustOrPutValue(evt.entityPlayer.getCommandSenderName(), value, value);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void itemExpire(ItemExpireEvent evt) {

		EntityItem ent = evt.entityItem;
		String name = ent.func_145800_j();
		if (!complete && name != null) {
			long value = Math.min(0, values.get(ent.getEntityItem().getItem()) * ent.getEntityItem().stackSize);
			score.adjustOrPutValue(name, value, value);
		}
	}

	public static void itemDeath(EntityItem ent, DamageSource damage) {

		String name = ent.func_145800_j();
		if (!complete && name != null) {
			long value = values.get(ent.getEntityItem().getItem()) * ent.getEntityItem().stackSize;
			score.adjustOrPutValue(name, value, value);
		}
		return;
	}

	public static class ClientPacketHandler implements IMessageHandler<Message, IMessage> {

		@Override
		public IMessage onMessage(Message message, MessageContext ctx) {

			return null;
		}

		public static class Message implements IMessage {

			private static Charset UTF8 = Charset.forName("UTF-8");
			private long data;
			private byte[] player;
			private int type = 0;

			public Message() {

			}

			public Message(int type, long data) {

				this.type = type;
				this.data = data;
			}

			public Message(int type, String user, long data) {

				this.type = type;
				this.data = data;
				player = user.getBytes(UTF8);
			}

			@Override
			public void fromBytes(ByteBuf buf) {

				switch (buf.readInt()) {
				case 2:
					byte[] bytes = new byte[buf.readInt()];
					buf.readBytes(bytes);
					String player = new String(bytes, UTF8);
					long data = buf.readLong();
					break;
				case 0:
					playerScore = buf.readLong();
					break;
				case 1:
					complete = buf.readLong() != 0;
					break;
				}
			}

			@Override
			public void toBytes(ByteBuf buf) {

				buf.writeInt(type);
				switch (type) {
				case 2:
					buf.writeInt(player.length);
					buf.writeBytes(player);
				case 0:
				case 1:
					buf.writeLong(data);
				}
			}
		}

	}

}
