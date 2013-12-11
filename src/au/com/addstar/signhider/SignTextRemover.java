package au.com.addstar.signhider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;

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
	public SignTextRemover(Plugin plugin)
	{
		super(plugin, ConnectionSide.SERVER_SIDE, ListenerPriority.LOWEST, Packets.Server.UPDATE_SIGN, Packets.Server.MAP_CHUNK, Packets.Server.MAP_CHUNK_BULK, Packets.Server.MULTI_BLOCK_CHANGE);
	}
	
	private boolean canSeeSign(Player player, PacketContainer packet)
	{
		return SignHiderPlugin.canSee(player, packet.getIntegers().read(0), packet.getIntegers().read(1), packet.getIntegers().read(2), true);
	}
	
	private void removeTileEntities(RawChunk raw, Chunk chunk, Player player)
	{
		for(BlockState tile : chunk.getTileEntities())
		{
			if(tile instanceof Sign && !SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), false))
				raw.setBlockId(tile.getX() & 0xF, tile.getY(), tile.getZ() & 0xF, 0);
		}
	}
	
	private boolean cleanMapChunk(Player player, PacketContainer packet)
	{
		int x = packet.getIntegers().read(0);
		int z = packet.getIntegers().read(1);
		
		Chunk chunk = player.getWorld().getChunkAt(x,z);
		removeTileEntities(new RawChunk(packet.getByteArrays().read(1), packet.getIntegers().read(2), packet.getIntegers().read(3)), chunk, player);
		
		Deflater deflater = new Deflater(-1);
		deflater.setInput(packet.getByteArrays().read(1));
		deflater.finish();
		byte[] buffer = new byte[packet.getByteArrays().read(1).length];
		deflater.deflate(buffer);
		packet.getByteArrays().write(0, buffer);
		packet.getIntegers().write(4, buffer.length);
		deflater.end();
		
		return true;
	}
	
	protected boolean cleanBulkMapChunk(Player player, PacketContainer packet)
	{
		byte[][] buffers = packet.getSpecificModifier(byte[][].class).read(0);
		if(buffers == null)
			return true;

		int[] x = packet.getIntegerArrays().read(0);
		int[] z = packet.getIntegerArrays().read(1);
		int[] chunkData = packet.getIntegerArrays().read(2);
		int[] biomeData = packet.getIntegerArrays().read(3);

		for(int i = 0; i < x.length; ++i)
		{
			Chunk chunk = player.getWorld().getChunkAt(x[i],z[i]);
			removeTileEntities(new RawChunk(buffers[i], chunkData[i], biomeData[i]), chunk, player);
		}
		
		byte[] buildBuffer = new byte[0];
		int start = 0;
		for(int i = 0; i < x.length; ++i)
		{
			if(buildBuffer.length < start + buffers[i].length)
				buildBuffer = Arrays.copyOf(buildBuffer, start + buffers[i].length);
			
			System.arraycopy(buffers[i], 0, buildBuffer, start, buffers[i].length);
			start += buffers[i].length;
		}
		packet.getByteArrays().write(1, buildBuffer);
		
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
