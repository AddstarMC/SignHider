package au.com.addstar.signhider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

public class SignTextRemover extends PacketAdapter
{
	private BlockSender mSender = new BlockSender();
	
	public SignTextRemover(Plugin plugin)
	{
		super(plugin, ConnectionSide.SERVER_SIDE, ListenerPriority.LOWEST, Packets.Server.UPDATE_SIGN, Packets.Server.MAP_CHUNK, Packets.Server.MAP_CHUNK_BULK, Packets.Server.MULTI_BLOCK_CHANGE);
	}
	
	private boolean canSeeSign(Player player, PacketContainer packet)
	{
		return SignHiderPlugin.canSee(player, packet.getIntegers().read(0), packet.getIntegers().read(1), packet.getIntegers().read(2), true);
	}
	
	private boolean cleanMapChunk(Player player, PacketContainer packet)
	{
		int x = packet.getIntegers().read(0);
		int z = packet.getIntegers().read(1);
		
		int minY = packet.getIntegers().read(2);
		int maxY = packet.getIntegers().read(3);
		if (minY == maxY && minY == 0)
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
	
	@SuppressWarnings( "deprecation" )
	private boolean cleanMultiChange(Player player, PacketContainer packet)
	{
		int[] coord = SignHiderPlugin.getChunkCoord(packet);
		byte[] data = packet.getByteArrays().read(0);
		int count = packet.getIntegers().read(0);
		
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(stream);
		int newCount = 0;
		
		int chunkX = coord[0] * 16;
		int chunkZ = coord[1] * 16;

		try
		{
			for(int i = 0; i < count; ++i)
			{
				short loc = input.readShort();
				short id = input.readShort();
				
				int blockId = (id >> 4) & 4095;
				
				if(Material.getMaterial(blockId) != Material.SIGN_POST && Material.getMaterial(blockId) != Material.WALL_SIGN)
				{
					output.writeShort(loc);
					output.writeShort(id);
					++newCount;
				}
				else
				{
					if(SignHiderPlugin.canSee(player, (loc >> 12) & 0xF + chunkX, loc & 0xFF, (loc >> 8) & 0xF + chunkZ, false))
					{
						output.writeShort(loc);
						output.writeShort(id);
						++newCount;
					}
				}
			}
			
			if(newCount == 0)
				return false;
			else if(newCount != count)
			{
				packet.getIntegers().write(0, newCount);
				packet.getByteArrays().write(0, stream.toByteArray());
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		return true;
	}
	
	@Override
	public void onPacketSending( PacketEvent event )
	{
		if(!SignHiderPlugin.isEnabledInWorld(event.getPlayer().getWorld()))
			return;
		
		switch(event.getPacketID())
		{
		case Packets.Server.UPDATE_SIGN:
			if(!canSeeSign(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
			break;
		case Packets.Server.MAP_CHUNK:
			if(!cleanMapChunk(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
			break;
		case Packets.Server.MAP_CHUNK_BULK:
			if(!cleanBulkMapChunk(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
			break;
		case Packets.Server.MULTI_BLOCK_CHANGE:
			if(!cleanMultiChange(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
			break;
		}
	}
}
