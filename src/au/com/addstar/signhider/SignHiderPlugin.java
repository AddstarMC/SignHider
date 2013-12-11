package au.com.addstar.signhider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
	
	@SuppressWarnings( "unchecked" )
	@Override
	public void onEnable()
	{
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
		
		mManager = ProtocolLibrary.getProtocolManager();
		mManager.addPacketListener(new SignTextRemover(this));
		
		Bukkit.getPluginManager().registerEvents(new PlayerUpdater(), this);
	}
	
	@Override
	public void onDisable()
	{
		mManager.removePacketListeners(this);
	}
}
