package au.com.addstar.signhider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.google.common.base.Throwables;

public class SignHiderPlugin extends JavaPlugin
{
	private ProtocolManager mManager;
	
	public static int chunkRange = 2;
	public static int signRange = 400;
	public static int textRange = 100;
	
	public static int updateFreq = 4;
	
	public static SignHiderPlugin instance;
	
	private static List<String> disabledWorldNames;
	private static WeakHashMap<World, Void> disabledWorlds = new WeakHashMap<World, Void>();
	
	public static boolean isEnabledInWorld(World world)
	{
		return !disabledWorlds.containsKey(world);
	}
	
	public static boolean canSee(Player player, int x, int y, int z, boolean text)
	{
		Location loc = player.getLocation();
		int dist = ((loc.getBlockX() - x) * (loc.getBlockX() - x)) + ((loc.getBlockY() - y) * (loc.getBlockY() - y)) + ((loc.getBlockZ() - z) * (loc.getBlockZ() - z));
		
		if(text)
			return dist < SignHiderPlugin.textRange;
		else
			return dist < SignHiderPlugin.signRange;
	}
	
	private static Class<Object> mChunkCoordClass;
	private static Constructor<Object> mChunkCoordConstructor;
	private static Field mChunkCoordX;
	private static Field mChunkCoordZ;
	
	public static int[] getChunkCoord(PacketContainer packet)
	{
		Object obj = packet.getSpecificModifier(mChunkCoordClass).read(0);
		
		try
		{
			return new int[] {(Integer)mChunkCoordX.get(obj), (Integer)mChunkCoordZ.get(obj)}; 
		}
		catch(Exception e)
		{
			Throwables.propagateIfPossible(e);
			throw new RuntimeException(e);
		}
	}
	
	public static void setChunkCoord(PacketContainer packet, int x, int z)
	{
		try
		{
			Object obj = mChunkCoordConstructor.newInstance(x, z);
			packet.getSpecificModifier(mChunkCoordClass).write(0, obj);
		}
		catch(Exception e)
		{
			Throwables.propagateIfPossible(e);
			throw new RuntimeException(e);
		}
	}
	
	private void loadConfig()
	{
		saveDefaultConfig();
		
		FileConfiguration config = getConfig();
		
		disabledWorldNames = config.getStringList("disabledWorlds");
		
		disabledWorlds.clear();
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
		
		chunkRange = (sign >> 4) + 1;
		signRange = sign * sign;
		textRange = text * text;
	}
	
	@SuppressWarnings( "unchecked" )
	@Override
	public void onEnable()
	{
		instance = this;
		try
		{
			mChunkCoordClass = (Class<Object>)Class.forName("net.minecraft.server.v1_7_R1.ChunkCoordIntPair");
			mChunkCoordX = mChunkCoordClass.getField("x");
			mChunkCoordZ = mChunkCoordClass.getField("z");
			mChunkCoordConstructor = mChunkCoordClass.getConstructor(int.class, int.class);
		}
		catch(Exception e)
		{
			Throwables.propagateIfPossible(e);
			throw new RuntimeException(e);
		}
		
		loadConfig();
		
		mManager = ProtocolLibrary.getProtocolManager();
		if(Bukkit.getPluginManager().isPluginEnabled("Orebfuscator"))
			mManager.addPacketListener(new OrebfuscatorSignRemover(this));
		else
			mManager.addPacketListener(new SignTextRemover(this));
		
		Bukkit.getPluginManager().registerEvents(new PlayerUpdater(), this);
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
}
