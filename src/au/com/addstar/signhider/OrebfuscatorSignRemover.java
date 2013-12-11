package au.com.addstar.signhider;

import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.events.PacketContainer;

public class OrebfuscatorSignRemover extends SignTextRemover
{
	private BlockSender mSender = new BlockSender();
	
	public OrebfuscatorSignRemover( Plugin plugin )
	{
		super(plugin);
	}
	
	@Override
	protected boolean cleanBulkMapChunk( Player player, PacketContainer packet )
	{
		byte[][] buffers = packet.getSpecificModifier(byte[][].class).read(0);
		
		if(buffers != null)
			return super.cleanBulkMapChunk(player, packet);
		
		int[] x = packet.getIntegerArrays().read(0);
		int[] z = packet.getIntegerArrays().read(1);
		
		mSender.begin(player);
		for(int i = 0; i < x.length; ++i)
		{
			Chunk chunk = player.getWorld().getChunkAt(x[i], z[i]);
			for(BlockState tile : chunk.getTileEntities())
			{
				if(tile instanceof Sign && !SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), false))
					mSender.add(tile.getX(), tile.getY(), tile.getZ(), 0, 0);
			}
		}

		mSender.endWithDelay();

		return true;
	}

}
