package au.com.addstar.signhider;

import java.util.HashSet;
import java.util.Iterator;
import java.util.WeakHashMap;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BlockVector;

import au.com.addstar.signhider.SignHiderPlugin.Setting;

public class PlayerUpdater implements Listener
{
	private WeakHashMap<Player, HashSet<BlockVector>> mActiveSigns = new WeakHashMap<Player, HashSet<BlockVector>>();
	private WeakHashMap<Player, HashSet<BlockVector>> mActiveText = new WeakHashMap<Player, HashSet<BlockVector>>();
	
	private BlockSender mSender = new BlockSender();
	private Location mLoc = new Location(null, 0, 0, 0);
	
	@EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
	private void onPlayerMove(PlayerMoveEvent event)
	{
		if(!SignHiderPlugin.isEnabledInWorld(event.getPlayer().getWorld()))
			return;
		
		if(!mActiveSigns.containsKey(event.getPlayer()) || (event.getFrom().getBlockX() / SignHiderPlugin.updateFreq != event.getTo().getBlockX() / SignHiderPlugin.updateFreq) ||
				(event.getFrom().getBlockZ() / SignHiderPlugin.updateFreq != event.getTo().getBlockZ() / SignHiderPlugin.updateFreq) ||
				(event.getFrom().getBlockY() / SignHiderPlugin.updateFreq != event.getTo().getBlockY() / SignHiderPlugin.updateFreq))
				onPlayerMove(event.getPlayer());
	}
	
	@EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
	private void onChangeWorld(PlayerChangedWorldEvent event)
	{
		mActiveSigns.remove(event.getPlayer());
		mActiveText.remove(event.getPlayer());
	}
	
	@SuppressWarnings( "deprecation" )
	public synchronized void onPlayerMove(Player player)
	{
		HashSet<BlockVector> nearby = mActiveSigns.get(player);
		HashSet<BlockVector> nearbyText = mActiveText.get(player);
		
		if(nearby == null)
		{
			nearby = new HashSet<BlockVector>();
			nearbyText = new HashSet<BlockVector>();
			mActiveSigns.put(player, nearby);
			mActiveText.put(player, nearbyText);
		}
		
		mSender.begin(player);
		
		// Eliminate too far away ones
		Iterator<BlockVector> it = nearby.iterator();
		while(it.hasNext())
		{
			BlockVector loc = it.next();
			Material type = player.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getType();
			
			if(!SignHiderPlugin.canSee(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false))
			{
				if(type == Material.SIGN_POST || type == Material.WALL_SIGN)
					mSender.add(loc, 0, 0);

				it.remove();
			}
			else 
			{
				if(!SignHiderPlugin.canSee(player, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), true))
				{
					if(type == Material.SIGN_POST || type == Material.WALL_SIGN)
						mSender.setText(loc, new String[] {"","","",""});
					
					nearbyText.remove(loc);
				}
				else if(!nearbyText.contains(loc))
				{
					// Set the text
					BlockState state = player.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getState();
					if(state instanceof Sign)
					{
						mSender.setText(loc, ((Sign) state).getLines());
						nearbyText.add(loc);
					}
					else
						// Not a sign anymore
						it.remove();
				}
			}
		}
	
		player.getLocation(mLoc);
		Setting settings = SignHiderPlugin.getWorldSettings(player.getWorld());
		
		// Find new ones
		for(int x = (mLoc.getBlockX() >> 4) - settings.chunkRange; x <= (mLoc.getBlockX() >> 4) + settings.chunkRange; ++x)
		{
			for(int z = (mLoc.getBlockZ() >> 4) - settings.chunkRange; z <= (mLoc.getBlockZ() >> 4) + settings.chunkRange; ++z)
			{
				Chunk chunk = player.getWorld().getChunkAt(x, z);
				for(BlockState tile : chunk.getTileEntities())
				{
					if(!(tile instanceof Sign))
						continue;
					
					if(SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), false))
					{
						BlockVector loc = new BlockVector(tile.getX(), tile.getY(), tile.getZ());
						if(nearby.add(loc))
						{
							mSender.add(loc, tile.getTypeId(), tile.getData().getData());
							
							if(SignHiderPlugin.canSee(player, tile.getX(), tile.getY(), tile.getZ(), true))
							{
								mSender.setText(loc, ((Sign)tile).getLines());
								nearbyText.add(loc);
							}
						}
					}
				}
			}
		}
		
		
		mSender.end();
	}
}
