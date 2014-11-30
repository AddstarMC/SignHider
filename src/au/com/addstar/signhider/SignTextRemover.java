package au.com.addstar.signhider;

import java.util.ArrayList;

import net.minecraft.server.v1_8_R1.Block;
import net.minecraft.server.v1_8_R1.ChunkMap;
import net.minecraft.server.v1_8_R1.IBlockData;
import net.minecraft.server.v1_8_R1.MultiBlockChangeInfo;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Play;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;

public class SignTextRemover extends PacketAdapter
{
	private BlockSender mSender = new BlockSender();
	
	public SignTextRemover(Plugin plugin)
	{
		super(plugin, ListenerPriority.LOWEST, PacketType.Play.Server.UPDATE_SIGN, PacketType.Play.Server.MAP_CHUNK, PacketType.Play.Server.MAP_CHUNK_BULK, PacketType.Play.Server.MULTI_BLOCK_CHANGE);
	}
	
	private boolean canSeeSign(Player player, PacketContainer packet)
	{
		BlockPosition pos = packet.getBlockPositionModifier().read(0);
		return SignHiderPlugin.canSee(player, pos.getX(), pos.getY(), pos.getZ(), true);
	}
	
	private boolean cleanMapChunk(Player player, PacketContainer packet)
	{
		int x = packet.getIntegers().read(0);
		int z = packet.getIntegers().read(1);
		
		boolean full = packet.getBooleans().read(0);
		
		ChunkMap data = packet.getSpecificModifier(ChunkMap.class).read(0);
		
		// Chunk unload packet
		if (full && data.b == 0)
			return true;
		
		synchronized(mSender)
		{
			mSender.begin(player);
			if(player.getWorld().isChunkLoaded(x, z))
			{
				Chunk chunk = player.getWorld().getChunkAt(x, z);
				for(BlockState tile : chunk.getTileEntities())
				{
					if(tile instanceof Sign && !SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), false))
						mSender.add(tile.getX(), tile.getY(), tile.getZ(), 0, 0);
				}
			}
	
			mSender.endWithDelay();
		}
		
		return true;
	}
	
	protected boolean cleanBulkMapChunk(Player player, PacketContainer packet)
	{
		if(player == null)
			return true;
		
		int[] x = packet.getIntegerArrays().read(0);
		int[] z = packet.getIntegerArrays().read(1);
		
		synchronized(mSender)
		{
			mSender.begin(player);
			for(int i = 0; i < x.length; ++i)
			{
				if(player.getWorld().isChunkLoaded(x[i], z[i]))
				{
					Chunk chunk = player.getWorld().getChunkAt(x[i], z[i]);
					for(BlockState tile : chunk.getTileEntities())
					{
						if(tile instanceof Sign && !SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), false))
							mSender.add(tile.getX(), tile.getY(), tile.getZ(), 0, 0);
					}
				}
			}
			mSender.endWithDelay();
		}

		return true;
	}
	
	public short[] toShortArray(ArrayList<Short> list)
	{
		short[] array = new short[list.size()];
		for(int i = 0; i < list.size(); ++i)
			array[i] = list.get(i);
		return array;
	}
	
	public int[] toIntArray(ArrayList<Integer> list)
	{
		int[] array = new int[list.size()];
		for(int i = 0; i < list.size(); ++i)
			array[i] = list.get(i);
		return array;
	}
	
	@SuppressWarnings( "deprecation" )
	private boolean cleanMultiChange(Player player, PacketContainer packet)
	{
		ChunkCoordIntPair coord = packet.getChunkCoordIntPairs().read(0);
		MultiBlockChangeInfo[] changes = packet.getSpecificModifier(MultiBlockChangeInfo[].class).read(0);
		
		ArrayList<MultiBlockChangeInfo> newChanges = new ArrayList<MultiBlockChangeInfo>();
		
		int chunkX = coord.getChunkX() * 16;
		int chunkZ = coord.getChunkZ() * 16;

		for(int i = 0; i < changes.length; ++i)
		{
			short loc = changes[i].b();
			IBlockData block = changes[i].c();
			
			int blockId = Block.getId(block.getBlock());
			if(Material.getMaterial(blockId) != Material.SIGN_POST && Material.getMaterial(blockId) != Material.WALL_SIGN)
				newChanges.add(changes[i]);
			else
			{
				if(SignHiderPlugin.canSee(player, (loc >> 12) & 0xF + chunkX, loc & 0xFF, (loc >> 8) & 0xF + chunkZ, false))
					newChanges.add(changes[i]);
			}
		}
		
		if(newChanges.isEmpty())
			return false;
		else if(newChanges.size() != changes.length)
		{
			changes = new MultiBlockChangeInfo[newChanges.size()];
			for (int i = 0; i < newChanges.size(); ++i)
				changes[i] = newChanges.get(i);
			
			packet.getSpecificModifier(MultiBlockChangeInfo[].class).write(0, changes);
		}
		return true;
	}
	
	@Override
	public void onPacketSending( PacketEvent event )
	{
		if(!SignHiderPlugin.isEnabledInWorld(event.getPlayer().getWorld()))
			return;
		
		if(event.getPacketType() == Play.Server.UPDATE_SIGN) 
		{
			if(!canSeeSign(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
		}
		else if(event.getPacketType() == Play.Server.MAP_CHUNK)
		{
			if(!cleanMapChunk(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
		}
		else if(event.getPacketType() == Play.Server.MAP_CHUNK_BULK)
		{
			if(!cleanBulkMapChunk(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
		}
		else if(event.getPacketType() == Play.Server.MULTI_BLOCK_CHANGE)
		{
			if(!cleanMultiChange(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
		}
	}
}
