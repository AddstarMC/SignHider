package au.com.addstar.signhider;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import com.comphenix.protocol.PacketType.Play;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;

public class BlockSender
{
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
		int dataPart = ((material & 0xFFF) << 4 | (data & 0xF));
		
		try
		{
			output.output.writeShort(locPart);
			output.output.writeShort(dataPart);
			output.locations.add(locPart);
			output.ids.add(dataPart);
			
			++output.blockCount;
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void setText(BlockVector location, String[] text)
	{
		PacketContainer packet = new PacketContainer(Play.Server.UPDATE_SIGN);
		packet.getIntegers().write(0, location.getBlockX());
		packet.getIntegers().write(1, location.getBlockY());
		packet.getIntegers().write(2, location.getBlockZ());
		
		for(int i = 0; i < text.length; ++i)
		{
			if(text[i].length() > 15)
				text[i] = text[i].substring(0, 15);
		}
		packet.getStringArrays().write(0, text);
		
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
				packet.getIntegers().write(0, entry.getValue().blockCount);
				packet.getByteArrays().write(0, entry.getValue().stream.toByteArray());
				packet.getIntegerArrays().write(0, entry.getValue().getBlockIds());
				packet.getSpecificModifier(short[].class).write(0, entry.getValue().getLocations());
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
			packet.getIntegers().write(0, entry.getValue().blockCount);
			packet.getByteArrays().write(0, entry.getValue().stream.toByteArray());
			packet.getIntegerArrays().write(0, entry.getValue().getBlockIds());
			packet.getSpecificModifier(short[].class).write(0, entry.getValue().getLocations());
			
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
			blockCount = 0;
			stream = new ByteArrayOutputStream();
			output = new DataOutputStream(stream);
			locations = new ArrayList<Short>();
			ids = new ArrayList<Integer>();
		}
		
		public int blockCount;
		public DataOutputStream output;
		public ByteArrayOutputStream stream;
		
		// For 1.8
		public ArrayList<Short> locations;
		public ArrayList<Integer> ids;
		
		public short[] getLocations()
		{
			short[] locs = new short[locations.size()];
			for(int i = 0; i < locations.size(); ++i)
				locs[i] = locations.get(i);
			return locs;
		}
		
		public int[] getBlockIds()
		{
			int[] blockIds = new int[ids.size()];
			for(int i = 0; i < ids.size(); ++i)
				blockIds[i] = ids.get(i);
			return blockIds;
		}
	}
}
