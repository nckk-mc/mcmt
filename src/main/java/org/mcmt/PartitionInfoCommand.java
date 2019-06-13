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
                sender.sendMessage( ChatColor.DARK_GREEN + "World " + world.getWorld().getName());
                List<Partition> partitions = world.getPartitionManager().getPartitions();
                for (int i=0; i<partitions.size(); i++) {
                    Partition partition = partitions.get(i);
                    sender.sendMessage(ChatColor.BLUE + "Partition #" + i);
                    int chunkCount = partition.chunks.size();
                    if (chunkCount > 0) {
                        sender.sendMessage(ChatColor.LIGHT_PURPLE + " - Chunks: " + chunkCount + " @ " + partition.getChunkBoundingBox());
                    }
                    int entityCount = partition.entities.size();
                    if (entityCount > 0) {
                        sender.sendMessage(ChatColor.LIGHT_PURPLE + " - Entities: " + entityCount + " @ " + partition.getEntityBoundingBox());
                    }
                    int blockCount = partition.blockTickListServer.getNextTickList().size();
                    if (blockCount > 0) {
                        sender.sendMessage(ChatColor.LIGHT_PURPLE + " - Blocks: " + blockCount + " @ " + partition.getBlockTickBoundingBox());
                    }
                    int fluidCount = partition.fluidTickListServer.getNextTickList().size();
                    if (fluidCount > 0) {
                        sender.sendMessage(ChatColor.LIGHT_PURPLE + " - Fluids: " + fluidCount + " @ " + partition.getFluidTickBoundingBox());
                    }
                }
            }
        }

        return false;
    }
}
