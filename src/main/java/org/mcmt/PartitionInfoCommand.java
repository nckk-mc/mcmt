package org.mcmt;

import net.minecraft.server.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;

public class PartitionInfoCommand extends Command
{
    public PartitionInfoCommand(String name)
    {
        super( name );
        this.description = "Returns debug information about the current partition you are in";
        this.usageMessage = "/partition";
        this.setPermission( "bukkit.command.tps" );
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !testPermission( sender ) )
        {
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player)sender;
            Location location = player.getLocation();
            BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            ChunkCoordIntPair coordinates = new ChunkCoordIntPair(position);

            sender.sendMessage( ChatColor.GOLD + "Partition debug info:");
            for (WorldServer world : MinecraftServer.getServer().getWorlds()) {
                List<Partition> partitions = world.getPartitionManager().getPartitions();
                for (int i=0; i<partitions.size(); i++) {
                    Partition partition = partitions.get(i);

                    if (partition.isInRadius(coordinates, 1)) {
                        sender.sendMessage(ChatColor.GOLD + "You are in partition #" + i);
                        return true;
                    }
                }
            }

            sender.sendMessage(ChatColor.RED + "Sorry, we didn't seem to find you in any of the partitions X: " + coordinates.x + " Z: " + coordinates.z);
        }

        return false;
    }
}
