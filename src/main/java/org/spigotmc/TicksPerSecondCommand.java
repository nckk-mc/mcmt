package org.spigotmc;

import net.minecraft.server.MinecraftServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class TicksPerSecondCommand extends Command
{

    public TicksPerSecondCommand(String name)
    {
        super( name );
        this.description = "Gets the current ticks per second for the server";
        this.usageMessage = "/tps";
        this.setPermission( "bukkit.command.tps" );
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !testPermission( sender ) )
        {
            return true;
        }

        // Paper start - Further improve tick handling
        double[] tps = org.bukkit.Bukkit.getTPS();
        String[] tpsAvg = new String[tps.length];

        for ( int i = 0; i < tps.length; i++) {
            tpsAvg[i] = format( tps[i] );
        }
        sender.sendMessage( ChatColor.GOLD + "TPS Thread report: ");
        MinecraftServer server = MinecraftServer.getServer();
        for(int i = 0; i < server.tickingThreadCount; i++)
        {
            sender.sendMessage(ChatColor.GOLD + "Thread " + ChatColor.RED + "#" + Integer.toString(i) + " " + format(server.threadsTPS.get(i)));
        }
        // Paper end

        return true;
    }

    private static String format(double tps) // Paper - Made static
    {
        return ( ( tps > 18.0 ) ? ChatColor.GREEN : ( tps > 16.0 ) ? ChatColor.YELLOW : ChatColor.RED ).toString()
                + ( ( tps > 20.0 ) ? "*" : "" ) + Math.min( Math.round( tps * 100.0 ) / 100.0, 20.0 );
    }
}
