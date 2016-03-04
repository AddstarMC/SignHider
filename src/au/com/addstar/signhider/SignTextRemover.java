package au.com.addstar.signhider;

import java.util.ArrayList;

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
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;

public class SignTextRemover extends PacketAdapter
{
	private BlockSender mSender = new BlockSender();
	
	public SignTextRemover(Plugin plugin)
	{
		super(plugin, ListenerPriority.LOWEST, PacketType.Play.Server.UPDATE_SIGN, PacketType.Play.Server.MAP_CHUNK, PacketType.Play.Server.MULTI_BLOCK_CHANGE);
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
		
		synchronized(mSender)
		{
			mSender.begin(player);
			if(player.getWorld().isChunkLoaded(x, z))
			{
				Chunk chunk = player.getWorld().getChunkAt(x, z);
				for(BlockState tile : chunk.getTileEntities())
				{
					if(tile instanceof Sign && !SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), false))
						mSender.add(tile.getX(), tile.getY(), tile.getZ(), Material.AIR, 0);
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
	
	private boolean cleanMultiChange(Player player, PacketContainer packet)
	{
		MultiBlockChangeInfo[] changes = packet.getMultiBlockChangeInfoArrays().read(0);
		
		ArrayList<MultiBlockChangeInfo> newChanges = new ArrayList<MultiBlockChangeInfo>();

		for(int i = 0; i < changes.length; ++i)
		{
			if (changes[i].getData().getType() != Material.SIGN_POST && changes[i].getData().getType() != Material.WALL_SIGN)
				newChanges.add(changes[i]);
			else
			{
				if (SignHiderPlugin.canSee(player, changes[i].getAbsoluteX(), changes[i].getY(), changes[i].getAbsoluteZ(), false))
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
		else if(event.getPacketType() == Play.Server.MULTI_BLOCK_CHANGE)
		{
			if(!cleanMultiChange(event.getPlayer(), event.getPacket()))
				event.setCancelled(true);
		}
	}
}
