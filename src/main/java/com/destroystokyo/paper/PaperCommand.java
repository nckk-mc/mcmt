package com.destroystokyo.paper;

import com.google.common.base.Functions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.server.*;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class PaperCommand extends Command {

    public PaperCommand(String name) {
        super(name);
        this.description = "Paper related commands";
        this.usageMessage = "/paper [heap | entity | reload | version | debug]";
        this.setPermission("bukkit.command.paper");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException {
        if (args.length <= 1)
            return getListMatchingLast(args, "heap", "entity", "reload", "version", "debug");

        switch (args[0].toLowerCase(Locale.ENGLISH))
        {
            case "entity":
                if (args.length == 2)
                    return getListMatchingLast(args, "help", "list");
                if (args.length == 3)
                    return getListMatchingLast(args, EntityTypes.getEntityNameList().stream().map(MinecraftKey::toString).sorted().toArray(String[]::new));
                break;
            case "debug":
                if (args.length == 2) {
                    return getListMatchingLast(args, "help", "chunks");
                }
        }
        return Collections.emptyList();
    }

    // Code from Mojang - copyright them
    public static List<String> getListMatchingLast(String[] args, String... matches) {
        return getListMatchingLast(args, (Collection) Arrays.asList(matches));
    }

    public static boolean matches(String s, String s1) {
        return s1.regionMatches(true, 0, s, 0, s.length());
    }

    public static List<String> getListMatchingLast(String[] strings, Collection<?> collection) {
        String last = strings[strings.length - 1];
        ArrayList<String> results = Lists.newArrayList();

        if (!collection.isEmpty()) {
            Iterator iterator = Iterables.transform(collection, Functions.toStringFunction()).iterator();

            while (iterator.hasNext()) {
                String s1 = (String) iterator.next();

                if (matches(last, s1)) {
                    results.add(s1);
                }
            }

            if (results.isEmpty()) {
                iterator = collection.iterator();

                while (iterator.hasNext()) {
                    Object object = iterator.next();

                    if (object instanceof MinecraftKey && matches(last, ((MinecraftKey) object).getKey())) {
                        results.add(String.valueOf(object));
                    }
                }
            }
        }

        return results;
    }
    // end copy stuff

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        switch (args[0].toLowerCase(Locale.ENGLISH))  {
            case "heap":
                dumpHeap(sender);
                break;
            case "entity":
                listEntities(sender, args);
                break;
            case "reload":
                doReload(sender);
                break;
            case "debug":
                doDebug(sender, args);
                break;
            case "ver":
            case "version":
                Command ver = org.bukkit.Bukkit.getServer().getCommandMap().getCommand("version");
                if (ver != null) {
                    ver.execute(sender, commandLabel, new String[0]);
                    break;
                }
                // else - fall through to default
            default:
                sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
                return false;
        }

        return true;
    }

    private void doDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Use /paper debug [chunks] help for more information on a specific command");
            return;
        }

        String debugType = args[1].toLowerCase(Locale.ENGLISH);
        switch (debugType) {
            case "chunks":
                if (args.length >= 3 && args[2].toLowerCase(Locale.ENGLISH).equals("help")) {
                    sender.sendMessage(ChatColor.RED + "Use /paper debug chunks to dump loaded chunk information to a file");
                    break;
                }
                File file = new File(new File(new File("."), "debug"),
                    "chunks-" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now()) + ".txt");
                sender.sendMessage(ChatColor.GREEN + "Writing chunk information dump to " + file.toString());
                try {
                    MCUtil.dumpChunks(file);
                    sender.sendMessage(ChatColor.GREEN + "Successfully written chunk information!");
                } catch (Throwable thr) {
                    MinecraftServer.LOGGER.warn("Failed to dump chunk information to file " + file.toString(), thr);
                    sender.sendMessage(ChatColor.RED + "Failed to dump chunk information, see console");
                }

                break;
            case "help":
                // fall through to default
            default:
                sender.sendMessage(ChatColor.RED + "Use /paper debug [chunks] help for more information on a specific command");
                return;
        }
    }

    /*
     * Ported from MinecraftForge - author: LexManos <LexManos@gmail.com> - License: LGPLv2.1
     */
    private void listEntities(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].toLowerCase(Locale.ENGLISH).equals("help")) {
            sender.sendMessage(ChatColor.RED + "Use /paper entity [list] help for more information on a specific command.");
            return;
        }

        switch (args[1].toLowerCase(Locale.ENGLISH)) {
            case "list":
                String filter = "*";
                if (args.length > 2) {
                    if (args[2].toLowerCase(Locale.ENGLISH).equals("help")) {
                        sender.sendMessage(ChatColor.RED + "Use /paper entity list [filter] [worldName] to get entity info that matches the optional filter.");
                        return;
                    }
                    filter = args[2];
                }
                final String cleanfilter = filter.replace("?", ".?").replace("*", ".*?");
                Set<MinecraftKey> names = EntityTypes.getEntityNameList().stream()
                        .filter(n -> n.toString().matches(cleanfilter))
                        .collect(Collectors.toSet());

                if (names.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Invalid filter, does not match any entities. Use /paper entity list for a proper list");
                    return;
                }

                String worldName;
                if (args.length > 3) {
                    worldName = args[3];
                } else if (sender instanceof Player) {
                    worldName = ((Player) sender).getWorld().getName();
                } else {
                    sender.sendMessage(ChatColor.RED + "Please specify the name of a world");
                    sender.sendMessage(ChatColor.RED + "To do so without a filter, specify '*' as the filter");
                    return;
                }

                Map<MinecraftKey, MutablePair<Integer, Map<ChunkCoordIntPair, Integer>>> list = Maps.newHashMap();
                World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld == null) {
                    sender.sendMessage(ChatColor.RED + "Could not load world for " + worldName + ". Please select a valid world.");
                    return;
                }
                WorldServer world = ((CraftWorld) Bukkit.getWorld(worldName)).getHandle();

                Collection<Entity> entities = world.entitiesById.values();
                entities.forEach(e -> {
                    MinecraftKey key = e.getMinecraftKey();
                    if (e.shouldBeRemoved) return; // Paper

                    MutablePair<Integer, Map<ChunkCoordIntPair, Integer>> info = list.computeIfAbsent(key, k -> MutablePair.of(0, Maps.newHashMap()));
                    ChunkCoordIntPair chunk = new ChunkCoordIntPair(e.getChunkX(), e.getChunkZ());
                    info.left++;
                    info.right.put(chunk, info.right.getOrDefault(chunk, 0) + 1);
                });

                if (names.size() == 1) {
                    MinecraftKey name = names.iterator().next();
                    Pair<Integer, Map<ChunkCoordIntPair, Integer>> info = list.get(name);
                    if (info == null) {
                        sender.sendMessage(ChatColor.RED + "No entities found.");
                        return;
                    }
                    sender.sendMessage("Entity: " + name + " Total: " + info.getLeft());
                    info.getRight().entrySet().stream()
                            .sorted((a, b) -> !a.getValue().equals(b.getValue()) ? b.getValue() - a.getValue() : a.getKey().toString().compareTo(b.getKey().toString()))
                            .limit(10).forEach(e -> sender.sendMessage("  " + e.getValue() + ": " + e.getKey().x + ", " + e.getKey().z));
                } else {
                    List<Pair<MinecraftKey, Integer>> info = list.entrySet().stream()
                            .filter(e -> names.contains(e.getKey()))
                            .map(e -> Pair.of(e.getKey(), e.getValue().left))
                            .sorted((a, b) -> !a.getRight().equals(b.getRight()) ? b.getRight() - a.getRight() : a.getKey().toString().compareTo(b.getKey().toString()))
                            .collect(Collectors.toList());

                    if (info == null || info.size() == 0) {
                        sender.sendMessage(ChatColor.RED + "No entities found.");
                        return;
                    }

                    int count = info.stream().mapToInt(Pair::getRight).sum();
                    sender.sendMessage("Total: " + count);
                    info.forEach(e -> sender.sendMessage("  " + e.getValue() + ": " + e.getKey()));
                }
                break;
        }
    }

    private void dumpHeap(CommandSender sender) {
        File file = new File(new File(new File("."), "dumps"),
                "heap-dump-" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now()) + "-server.hprof");
        Command.broadcastCommandMessage(sender, ChatColor.YELLOW + "Writing JVM heap data to " + file);
        if (CraftServer.dumpHeap(file)) {
            Command.broadcastCommandMessage(sender, ChatColor.GREEN + "Heap dump complete");
        } else {
            Command.broadcastCommandMessage(sender, ChatColor.RED + "Failed to write heap dump, see sever log for details");
        }
    }

    private void doReload(CommandSender sender) {
        Command.broadcastCommandMessage(sender, ChatColor.RED + "Please note that this command is not supported and may cause issues.");
        Command.broadcastCommandMessage(sender, ChatColor.RED + "If you encounter any issues please use the /stop command to restart your server.");

        MinecraftServer console = MinecraftServer.getServer();
        com.destroystokyo.paper.PaperConfig.init((File) console.options.valueOf("paper-settings"));
        for (WorldServer world : console.getWorlds()) {
            world.paperConfig.init();
        }
        console.server.reloadCount++;

        Command.broadcastCommandMessage(sender, ChatColor.GREEN + "Paper config reload complete.");
    }
}
