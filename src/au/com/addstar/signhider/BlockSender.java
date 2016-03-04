package au.com.addstar.signhider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import com.comphenix.protocol.PacketType.Play;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

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
		add(location, block.getType(), block.getData());
	}
	public void add(BlockVector location, Material material, int data)
	{
		add(location.getBlockX(), location.getBlockY(), location.getBlockZ(), material, data);
	}
	
	public void add(int x, int y, int z, Material material, int data)
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
		
		output.locations.add(new Location(mPlayer.getWorld(), x, y, z));
		output.ids.add(WrappedBlockData.createData(material, data));
	}
	
	public void setText(BlockVector location, String[] text)
	{
		PacketContainer packet = new PacketContainer(Play.Server.UPDATE_SIGN);
		packet.getBlockPositionModifier().write(0, new BlockPosition(location));
		WrappedChatComponent[] lines = new WrappedChatComponent[4];

		for (int i = 0; i < 4; ++i)
		{
			if (i < text.length)
				lines[i] = WrappedChatComponent.fromText(text[i]);
			else
				lines[i] = WrappedChatComponent.fromText("");
		}
		
		packet.getChatComponentArrays().write(0, lines);
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
					changes[i] = new MultiBlockChangeInfo(entry.getValue().locations.get(i), entry.getValue().ids.get(i));

				packet.getMultiBlockChangeInfoArrays().write(0, changes);
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
				changes[i] = new MultiBlockChangeInfo(entry.getValue().locations.get(i), entry.getValue().ids.get(i));

			packet.getMultiBlockChangeInfoArrays().write(0, changes);
			
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
			locations = new ArrayList<Location>();
			ids = new ArrayList<WrappedBlockData>();
		}
		
		public ArrayList<Location> locations;
		public ArrayList<WrappedBlockData> ids;
	}
}
