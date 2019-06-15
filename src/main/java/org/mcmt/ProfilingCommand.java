package org.mcmt;

import net.minecraft.server.*;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class ProfilingCommand extends Command
{
    public ProfilingCommand(String name)
    {
        super( name );
        this.description = "Returns debug information about the current partition you are in";
        this.usageMessage = "/profiling <start|stop> <world> <partition>";
        this.setPermission( "bukkit.command.tps" );
    }

    static int tryParseOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !testPermission( sender ) ) {
            return true;
        }

        if (args == null || args.length != 3) {
            sender.sendMessage( ChatColor.RED + "Usage: /profiling <start|stop> <world> <partition_id>");
            return true;
        }

        String action = args[0].toLowerCase();

        WorldServer world = null;
        Iterator<WorldServer> iterator = MinecraftServer.getServer().getWorlds().iterator();
        while(iterator.hasNext()) {
            if (world == null || iterator.next().getWorld().getName().equalsIgnoreCase(args[1])) {
                world = iterator.next();
            }
        }

        int index = tryParseOrZero(args[2]);

        if (action.equalsIgnoreCase("start")) {
            List<Partition> partitions = world.getPartitionManager().getPartitions();
            if (partitions.size() > index) {
                sender.sendMessage( ChatColor.GREEN + "MCMT | Starting profiling partition #" + index);

                Partition partition = partitions.get(index);
                partition.startProfiling();
            }
        } else {
            List<Partition> partitions = world.getPartitionManager().getPartitions();
            if (partitions.size() > index) {
                sender.sendMessage( ChatColor.GREEN + "MCMT | Stopped profiling partition #" + index);

                Partition partition = partitions.get(index);
                Partition.Profile parent = partition.stopProfiling();

                long sessionTime = parent.getElapsed();
                long workingTime = parent.getChildren().stream().findFirst().get().getElapsed();

                report(sender, parent, 0);

                sender.sendMessage( ChatColor.GOLD + "MCMT | IDLE: " + (sessionTime-workingTime)/1_000_000 + "ms");
                sender.sendMessage( ChatColor.GOLD + "MCMT | TOTAL: " + sessionTime/1_000_000 + "ms");
            }
        }

        return false;
    }
    private void report(CommandSender sender, Partition.Profile parent, int indent) {
        sender.sendMessage( ChatColor.GOLD + "MCMT | " + StringUtils.repeat("  |", indent) + "  |-" + parent.getKey() + ": " + parent.getElapsed()/1_000_000 + "ms");
        for (Partition.Profile profile : parent.getChildren()) {
            report(sender, profile, indent + 1);
        }
    }
}
