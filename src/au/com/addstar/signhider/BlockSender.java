package au.com.addstar.signhider;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import net.minecraft.server.v1_8_R2.PacketPlayOutMultiBlockChange;
import net.minecraft.server.v1_8_R2.PacketPlayOutMultiBlockChange.MultiBlockChangeInfo;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import com.comphenix.protocol.PacketType.Play;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

public class BlockSender
{
	private static Class<?> mChatComponentType;
	private Player mPlayer;
	private HashMap<Chunk, OutputSet> mChunks;
	
	private LinkedList<PacketContainer> mTilePackets;
	
	public void begin(Player player)
	{
		mPlayer = player;
	
		mChunks = new HashMap<Chunk, OutputSet>();
		mTilePackets = new LinkedList<PacketContainer>();
	}
	
	@SuppressWarnings( "deprecation" )
	public void add(BlockVector location)
	{
		Block block = mPlayer.getWorld().getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		add(location, block.getTypeId(), block.getData());
	}
	public void add(BlockVector location, int material, int data)
	{
		add(location.getBlockX(), location.getBlockY(), location.getBlockZ(), material, data);
	}
	
	public void add(int x, int y, int z, int material, int data)
	{
		Validate.notNull(mPlayer);
		Validate.notNull(mPlayer.getWorld());
		
		Chunk chunk = mPlayer.getWorld().getChunkAt(x >> 4, z >> 4);
		
		OutputSet output = mChunks.get(chunk);
		
		if(output == null)
		{
			output = new OutputSet();
			mChunks.put(chunk, output);
		}
		
		short locPart = (short)((x & 0xF) << 12 | (z & 0xF) << 8 | (y & 0xFF));
		int dataPart = ((material & 0xFFF) | (data & 0xF) << 12);
		
		output.locations.add(locPart);
		output.ids.add(dataPart);
	}
	
	@SuppressWarnings( "unchecked" )
	public void setText(BlockVector location, String[] text)
	{
		if (mChatComponentType == null)
			mChatComponentType = WrappedChatComponent.fromText("").getHandleType();
		
		PacketContainer packet = new PacketContainer(Play.Server.UPDATE_SIGN);
		packet.getBlockPositionModifier().write(0, new BlockPosition(location));
		Object lines = Array.newInstance(mChatComponentType, 4);

		for (int i = 0; i < 4; ++i)
		{
			if (i < text.length)
				Array.set(lines, i, WrappedChatComponent.fromText(text[i]).getHandle());
			else
				Array.set(lines, i, WrappedChatComponent.fromText("").getHandle());
		}
		
		packet.getSpecificModifier((Class<Object>)lines.getClass()).write(0, lines);
		mTilePackets.add(packet);
	}
	
	public void end()
	{
		try
		{
			for(Entry<Chunk, OutputSet> entry : mChunks.entrySet())
			{
				PacketContainer packet = new PacketContainer(Play.Server.MULTI_BLOCK_CHANGE);
				
				packet.getChunkCoordIntPairs().write(0, new ChunkCoordIntPair(entry.getKey().getX(), entry.getKey().getZ()));
				MultiBlockChangeInfo[] changes = new MultiBlockChangeInfo[entry.getValue().ids.size()];
				for (int i = 0; i < changes.length; ++i)
					changes[i] = ((PacketPlayOutMultiBlockChange)packet.getHandle()).new MultiBlockChangeInfo(entry.getValue().locations.get(i), net.minecraft.server.v1_8_R2.Block.getByCombinedId(entry.getValue().ids.get(i)));

				packet.getSpecificModifier(MultiBlockChangeInfo[].class).write(0, changes);
				ProtocolLibrary.getProtocolManager().sendServerPacket(mPlayer, packet, false);
			}
			
			for(PacketContainer tilePacket : mTilePackets)
			{
				ProtocolLibrary.getProtocolManager().sendServerPacket(mPlayer, tilePacket, false);
			}
		}
		catch ( InvocationTargetException e )
		{
			e.printStackTrace();
		}
		
		mPlayer = null;
		mChunks.clear();
		mChunks = null;
	}
	
	public void endWithDelay()
	{
		final LinkedList<PacketContainer> packets = new LinkedList<PacketContainer>();
		final Player player = mPlayer; 
		
		for(Entry<Chunk, OutputSet> entry : mChunks.entrySet())
		{
			PacketContainer packet = new PacketContainer(Play.Server.MULTI_BLOCK_CHANGE);
			
			packet.getChunkCoordIntPairs().write(0, new ChunkCoordIntPair(entry.getKey().getX(), entry.getKey().getZ()));
			MultiBlockChangeInfo[] changes = new MultiBlockChangeInfo[entry.getValue().ids.size()];
			for (int i = 0; i < changes.length; ++i)
				changes[i] = ((PacketPlayOutMultiBlockChange)packet.getHandle()).new MultiBlockChangeInfo(entry.getValue().locations.get(i), net.minecraft.server.v1_8_R2.Block.getByCombinedId(entry.getValue().ids.get(i)));

			packet.getSpecificModifier(MultiBlockChangeInfo[].class).write(0, changes);
			
			packets.add(packet);
		}

		packets.addAll(mTilePackets);
		
		Bukkit.getScheduler().runTask(SignHiderPlugin.instance, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					for(PacketContainer packet : packets)
						ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false);
				}
				catch(InvocationTargetException e)
				{
				}
			}
		});
		
		mPlayer = null;
		mChunks.clear();
		mChunks = null;
	}
	
	private class OutputSet
	{
		public OutputSet()
		{
			locations = new ArrayList<Short>();
			ids = new ArrayList<Integer>();
		}
		
		public ArrayList<Short> locations;
		public ArrayList<Integer> ids;
	}
}
