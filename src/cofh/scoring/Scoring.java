package cofh.scoring;

import cofh.scoring.Scoring.ClientPacketHandler.Message;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
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
import java.util.Map.Entry;

import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
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

	public static long playerScore = 0;
	Logger log;
	Configuration config;
	static TObjectLongHashMap<String> score = new TObjectLongHashMap<String>();
	static TObjectLongHashMap<Item> values = new TObjectLongHashMap<Item>(8, 0.5f, 0L);
	File scoreData;
	SimpleNetworkWrapper networkWrapper;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {

		log = event.getModLog();
		config = new Configuration(event.getSuggestedConfigurationFile());

		networkWrapper = new SimpleNetworkWrapper("CoFH|Scoring");
		networkWrapper.registerMessage(ClientPacketHandler.class, Message.class, 0, Side.CLIENT);
	}

	@EventHandler
	public void loadComplete(FMLLoadCompleteEvent event) {

		MinecraftForge.EVENT_BUS.register(this);

		config.get("item_values", "iron_ingot", 5);
		config.get("item_values", "gold_ingot", 2);
		config.get("item_values", "emerald", 1);

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
		config.save();
	}

	@EventHandler
	public void serverStarting(FMLServerStartedEvent event) {

		scoreData = new File(DimensionManager.getCurrentSaveRootDirectory(), "score-data.prop");
		if (scoreData.exists() && scoreData.isDirectory())
			throw new Error("score-data.prop is a directory!");
		score.clear();
		try {
			scoreData.createNewFile();
			BufferedReader reader = new BufferedReader(new FileReader(scoreData));
			for (String data; (data = reader.readLine()) != null;) {
				int i = data.indexOf('=');
				if (i >= 0)
					score.put(data.substring(0, i), Long.parseLong(data.substring(i + 1), 16));
			}
			reader.close();
		} catch (Throwable e) {
			log.error("Unable to load score data!", e);
		}

	}

	@EventHandler
	public void serverStopped(FMLServerStoppedEvent event) {

		if (scoreData != null && scoreData.exists()) {
			try {
				FileWriter writer = new FileWriter(scoreData);
				for (String user : score.keySet()) {
					writer.write(user);
					writer.write('=');
					writer.write(Long.toHexString(score.get(user)));
					writer.write("\r\n");
				}
				writer.close();
			} catch (Throwable e) {
				log.error("Unable to save score data!", e);
			}
		}
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
			networkWrapper.sendTo(new Message(score.get(evt.entity.getCommandSenderName())), (EntityPlayerMP) evt.entity);
		}
	}

	@SubscribeEvent
	public void itemBroken(PlayerDestroyItemEvent evt) {

		if (evt.original != null) {
			long value = values.get(evt.original.getItem());
			score.adjustOrPutValue(evt.entityPlayer.getCommandSenderName(), value, value);
		}
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void itemExpire(ItemExpireEvent evt) {

		EntityItem ent = evt.entityItem;
		String name = ent.func_145800_j();
		if (name != null) {
			long value = Math.min(0, values.get(ent.getEntityItem().getItem()) * ent.getEntityItem().stackSize);
			score.adjustOrPutValue(name, value, value);
		}
	}

	public static void itemDeath(EntityItem ent, DamageSource damage) {

		String name = ent.func_145800_j();
		if (name != null) {
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

			public long data;

			public Message() {

			}

			public Message(long data) {

				this.data = data;
			}

			@Override
			public void fromBytes(ByteBuf buf) {

				playerScore = buf.readLong();
			}

			@Override
			public void toBytes(ByteBuf buf) {

				buf.writeLong(data);
			}
		}

	}

}
