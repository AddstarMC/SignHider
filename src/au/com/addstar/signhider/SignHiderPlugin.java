package au.com.addstar.signhider;

import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

public class SignHiderPlugin extends JavaPlugin implements Listener
{
	private ProtocolManager mManager;
	
	private static Setting mDefaultSetting;
	private static HashMap<String, Setting> mWorldSettings = new HashMap<String, Setting>();
	
	
	public static int updateFreq = 4;
	
	public static SignHiderPlugin instance;
	
	private static boolean invertWorlds;
	private static List<String> disabledWorldNames;
	private static WeakHashMap<World, Void> disabledWorlds = new WeakHashMap<World, Void>();
	
	public static boolean isEnabledInWorld(World world)
	{
		return (invertWorlds ? disabledWorlds.containsKey(world) : !disabledWorlds.containsKey(world));
	}
	
	public static Setting getWorldSettings(World world)
	{
		Setting setting = mWorldSettings.get(world.getName().toLowerCase());
		if(setting == null)
			setting = mDefaultSetting;
		
		return setting;
	}
	
	public static boolean canSee(Player player, int x, int y, int z, boolean text)
	{
		Location loc = player.getLocation();
		int dist = ((loc.getBlockX() - x) * (loc.getBlockX() - x)) + ((loc.getBlockY() - y) * (loc.getBlockY() - y)) + ((loc.getBlockZ() - z) * (loc.getBlockZ() - z));
		Setting setting = getWorldSettings(player.getWorld());
		
		if(text)
			return dist < setting.textRange;
		else
			return dist < setting.signRange;
	}
	
	private void loadConfig()
	{
		saveDefaultConfig();
		
		reloadConfig();
		FileConfiguration config = getConfig();
		
		disabledWorldNames = config.getStringList("disabledWorlds");
		invertWorlds = config.getBoolean("invert", false);
		
		disabledWorlds.clear();
		mWorldSettings.clear();
		for(String name : disabledWorldNames)
		{
			World world = Bukkit.getWorld(name);
			if(world != null)
				disabledWorlds.put(world, null);
		}
		
		int sign = config.getInt("maxSignRange", 20);
		int text = config.getInt("textRange", 10);
		
		if(text >= sign)
			text = sign - 1;
		
		mDefaultSetting = new Setting();
		
		mDefaultSetting.chunkRange = (sign >> 4) + 1;
		mDefaultSetting.signRange = sign * sign;
		mDefaultSetting.textRange = text * text;
		
		// Load world settings
		if(config.isConfigurationSection("worldSettings"))
		{
			ConfigurationSection worlds = config.getConfigurationSection("worldSettings");
			for(String worldName : worlds.getKeys(false))
			{
				ConfigurationSection world = worlds.getConfigurationSection(worldName);
				
				Setting settings = new Setting();
				sign = world.getInt("maxSignRange", 20);
				text = world.getInt("textRange", 10);
				
				if(text >= sign)
					text = sign - 1;
				
				settings.chunkRange = (sign >> 4) + 1;
				settings.signRange = sign * sign;
				settings.textRange = text * text;
				mWorldSettings.put(worldName.toLowerCase(), settings);
			}
		}
	}
	
	@Override
	public void onEnable()
	{
		getDataFolder().mkdirs();
		instance = this;
		
		loadConfig();
		
		mManager = ProtocolLibrary.getProtocolManager();
		mManager.addPacketListener(new SignTextRemover(this));
		
		Bukkit.getPluginManager().registerEvents(new PlayerUpdater(), this);
		Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable()
	{
		mManager.removePacketListeners(this);
	}
	
	@Override
	public boolean onCommand( CommandSender sender, Command command, String label, String[] args )
	{
		if(command.getName().equals("signhider"))
		{
			if(args.length != 1)
				return false;
			
			if(args[0].equalsIgnoreCase("reload"))
			{
				loadConfig();
				sender.sendMessage(ChatColor.GREEN + "Config reloaded");
				return true;
			}
		}
		
		return false;
	}
	
	@EventHandler
	private void onWorldLoad(WorldLoadEvent event)
	{
		if(disabledWorldNames.contains(event.getWorld().getName()))
			disabledWorlds.put(event.getWorld(), null);
	}
	
	public static class Setting
	{
		public int chunkRange = 2;
		public int signRange = 400;
		public int textRange = 100;
	}
}
