package com.beckettobrien.ctfmc;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

public class CTF extends JavaPlugin {

    public final String WORLD_NAME = "CTFWorld";

    YamlConfiguration config = new YamlConfiguration();
    File configFile;
    String configFileName = "config.yml";

    // Config Variables Here:
    public HashMap<Player, CTF_PlayerData> players = new HashMap<Player, CTF_PlayerData>();
    public ArrayList<Location> BlueSpawns = new ArrayList<Location>();
    public ArrayList<Location> RedSpawns = new ArrayList<Location>();
    public HashMap<Location, ItemStack[]> chests = new HashMap<Location, ItemStack[]>();
    public HashMap<Location, Flag> flags = new HashMap<Location, Flag>();

    public Location[] gameBounds = {new Location(Bukkit.getWorld(WORLD_NAME), -81, 0, 48), new Location(Bukkit.getWorld(WORLD_NAME), 280, 168, 340)};

    public Scoreboard sb;
    public Objective scores;
    public Team blue;
    public Team red;
    public int blueScore = 0;
    public int redScore = 0;

    public Banner TempBannerSelect = null;
    public ChestLoader cl;

    boolean listeners = false;
    boolean gameActive = false;

    Random rand = new Random();

    @Override
    public void onEnable() {
        this.getCommand("ctfconfig").setExecutor(new CTF_Config(this));
        this.getCommand("ctfrun").setExecutor(new CTF_Run(this));
        this.getCommand("ctfprint").setExecutor(new CTF_Print(this));

        try {
            this.sb = Bukkit.getServer().getScoreboardManager().getMainScoreboard();
            this.scores = sb.getObjective("SCORES");
            this.scores.setDisplaySlot(DisplaySlot.SIDEBAR);
            scores.getScore(ChatColor.BLUE + "BLUE").setScore(blueScore);
            scores.getScore(ChatColor.RED + "RED").setScore(redScore);
            this.blue = sb.getTeam("ctf-blue");
            this.red = sb.getTeam("ctf-red");
            for (String e : sb.getTeam("ctf-blue").getEntries()) {
                sb.getTeam("ctf-blue").removeEntry(e);
            }
            for (String e : sb.getTeam("ctf-red").getEntries()) {
                sb.getTeam("ctf-red").removeEntry(e);
            }
        } catch (NullPointerException e) {
            this.sb.registerNewTeam("ctf-blue");
            this.sb.getTeam("ctf-blue").setDisplayName("BLUE");
            this.sb.getTeam("ctf-blue").setColor(org.bukkit.ChatColor.BLUE);
            this.sb.getTeam("ctf-blue").setAllowFriendlyFire(false);
            this.sb.registerNewTeam("ctf-red");
            this.sb.getTeam("ctf-red").setDisplayName("RED");
            this.sb.getTeam("ctf-red").setColor(org.bukkit.ChatColor.RED);
            this.sb.getTeam("ctf-red").setAllowFriendlyFire(false);
            this.scores = sb.getObjective("SCORES");
            this.scores.setDisplaySlot(DisplaySlot.SIDEBAR);
            scores.getScore(ChatColor.BLUE + "BLUE").setScore(blueScore);
            scores.getScore(ChatColor.RED + "RED").setScore(redScore);
            System.out.println(e.toString());
        }
    }

    public void enableEvents() {
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        listeners = true;
    }

    public void disableEvents() {
        HandlerList.unregisterAll();
        listeners = false;
    }

    @Override
    public void onDisable(){
        disableEvents();
        for (String e : sb.getTeam("ctf-blue").getEntries()) {
            sb.getTeam("ctf-blue").removeEntry(e);
        }
        for (String e : sb.getTeam("ctf-red").getEntries()) {
            sb.getTeam("ctf-red").removeEntry(e);
        }
        sb.getTeam("ctf-blue").unregister();
        sb.getTeam("ctf-red").unregister();
    }

    public void broadcast(String s) {
        for (Player p : Bukkit.getWorld(WORLD_NAME).getPlayers()) {
            p.sendMessage(s);
        }
    }

    public void newPlayer(Player p) {
        String team;
        String kit = "melee";

        try {
            if (this.sb.getTeam("ctf-blue").getEntries().size() < this.sb.getTeam("ctf-red").getEntries().size()) {
                team = "blue";
            } else if (this.sb.getTeam("ctf-red").getEntries().size() < this.sb.getTeam("ctf-blue").getEntries().size()) {
                team = "red";
            } else {
                team = new String[]{"blue", "red"}[this.rand.nextInt(2)];
            }
        } catch (NullPointerException e) {
            team = new String[]{"blue", "red"}[this.rand.nextInt(2)];
        }

        CTF_PlayerData data = new CTF_PlayerData(team, kit);

        players.put(p, data);

        ComponentBuilder welcomeMessage = new ComponentBuilder(
                "Welcome To Capture the Flag!\n").bold(true);
        if (team.equals("blue")) {
            welcomeMessage.append("You are now on the BLUE TEAM.").color(ChatColor.BLUE).bold(false);
            sb.getTeam("ctf-blue").addEntry(p.getName());
        } else if (team.equals("red")) {
            welcomeMessage.append("You are now on the RED TEAM.").color(ChatColor.RED).bold(false);
            sb.getTeam("ctf-red").addEntry(p.getName());
        }
        p.spigot().sendMessage(welcomeMessage.create());
    }

    public void leave(Player p) {
        sb = Bukkit.getServer().getScoreboardManager().getMainScoreboard();
        sb.getTeam("ctf-" + players.get(p).team).removeEntry(p.getName());
        players.remove(p);
        if (p.hasMetadata("flag-holder")) {
            Location flagLoc = (Location) p.getMetadata("flag-holder").get(0).value();
            Flag held = this.flags.get(flagLoc);
            dropFlag(flagLoc, held, p.getLocation());
            p.removeMetadata("flag-holder", this);
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.removePotionEffect(PotionEffectType.SLOW);
        }
    }

    public void start() {
        this.broadcast(ChatColor.GREEN + "GAME ACTIVE!");
        this.cl = new ChestLoader(this.chests);
        cl.runTaskTimer(this, 0, 6000);
        for (Player p : this.players.keySet()) {
            Location loc;
            try {
                if (players.get(p).team.equals("blue")) {
                    loc = BlueSpawns.get(rand.nextInt(BlueSpawns.size()));
                } else if (players.get(p).team.equals("red")) {
                    loc = RedSpawns.get(rand.nextInt(RedSpawns.size()));
                } else {
                    p.sendMessage("Uh oh! Something went wrong!");
                    return;
                }
            } catch (IndexOutOfBoundsException e) {
                p.sendMessage("Uh oh! Something went wrong!");
                return;
            }
            p.teleport(loc);
            giveKit(p);
        }
        this.gameActive = true;
    }

    public void respawn(Player p) {
        Location loc;
        try {
            if (players.get(p).team.equals("blue")) {
                loc = BlueSpawns.get(rand.nextInt(BlueSpawns.size()));
            } else if (players.get(p).team.equals("red")) {
                loc = RedSpawns.get(rand.nextInt(RedSpawns.size()));
            } else {
                p.sendMessage("Uh oh! Something went wrong!");
                return;
            }
        } catch (IndexOutOfBoundsException e) {
            p.sendMessage("Uh oh! Something went wrong!");
            return;
        }
        p.teleport(loc);
        giveKit(p);
        this.broadcast(p.getDisplayName() + " has respawned!");
        p.removeMetadata("respawning", this);
    }

    public void changeKit(Player p, String kit) {
        players.get(p).changeKit(kit);
        TextComponent currentClass = new TextComponent(ChatColor.GREEN + "Selected Kit: " + players.get(p).kit.name.toUpperCase());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, currentClass);
    }

    public void changeTeam(Player p) {
        String oldTeam = players.get(p).team;
        String newTeam;

        if (oldTeam.equals("blue")) {
            newTeam = "red";
            sb.getTeam("ctf-blue").removeEntry(p.getName());
            sb.getTeam("ctf-red").addEntry(p.getName());
            p.sendMessage(ChatColor.RED + "You are now on team RED");
        } else if (oldTeam.equals("red")) {
            newTeam = "blue";
            sb.getTeam("ctf-red").removeEntry(p.getName());
            sb.getTeam("ctf-blue").addEntry(p.getName());
            p.sendMessage(ChatColor.BLUE + "You are now on team BLUE");
        } else {
            return;
        }
        players.get(p).team = newTeam;
        if (!(this.gameActive)) {
            return;
        }

        respawn(p);
    }

    public void giveKit(Player p) {
        CTF_Kit kit = players.get(p).kit;
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, kit.weapon1);
        inv.setItem(1, kit.weapon2);
        inv.setHelmet(kit.helmet);
        inv.setChestplate(kit.chestplate);
        inv.setLeggings(kit.leggings);
        inv.setBoots(kit.boots);
        inv.setItem(7, kit.extraWool);
        inv.setItem(8, kit.wool);
    }

    public void resetFlag(Flag flag) {
        flag.startPos.getBlock().setType(Material.BLACK_BANNER);
        Banner b = ((Banner) flag.startPos.getBlock().getState());
        b.setPatterns(flag.flagPattern);
        b.setBlockData(flag.data);
        b.update();
        Bukkit.getServer().createBlockData(flag.data.getAsString());
        flag.state = "base";
        Location key = flag.startPos;
        for (Map.Entry<Location, Flag> e : this.flags.entrySet()) {
            if (e.getValue().equals(flag)) {
                key = e.getKey();
            }
        }
        this.flags.remove(key);
        this.flags.put(flag.startPos, flag);
    }

    public void dropFlag(Location key, Flag flag, Location dropLoc) {
        dropLoc.getBlock().setType(Material.BLACK_BANNER);
        Banner b = ((Banner) dropLoc.getBlock().getState());
        b.setPatterns(flag.flagPattern);
        b.setBlockData(flag.data);
        b.update();
        Bukkit.getServer().createBlockData(flag.data.getAsString());
        flag.state = "dropped";
        this.flags.remove(key);
        this.flags.put(b.getLocation(), flag);
        System.out.println(this.flags.get(b.getLocation()).state);
    }

    public void captureFlag(Flag flag, Player p) {
        this.broadcast(p.getDisplayName() + " has captured " + flag.name);
        p.getInventory().setItemInOffHand(null);
        p.removeMetadata("flag-holder", this);
        p.removePotionEffect(PotionEffectType.GLOWING);
        p.removePotionEffect(PotionEffectType.SLOW);

        if (flag.team.equals("blue")) {
            redScore += 1;
            scores.getScore(ChatColor.RED + "RED").setScore(redScore);
            if (scores.getScore(ChatColor.RED + "RED").getScore() == 3) {
                this.broadcast(ChatColor.RED + "ROUND OVER! RED TEAM HAS WON!");
                resetGame();
            }
        } else if (flag.team.equals("red")) {
            blueScore += 1;
            scores.getScore(ChatColor.BLUE + "BLUE").setScore(blueScore);
            if (scores.getScore(ChatColor.BLUE + "BLUE").getScore() == 3) {
                this.broadcast(ChatColor.BLUE + "ROUND OVER! BLUE TEAM HAS WON!");
                resetGame();
            }
        }
    }

    public void resetGame() {
        this.gameActive = false;
        cl.cancel();
        for (Player p : this.players.keySet()) {
            p.teleport(p.getWorld().getSpawnLocation());
            if (p.hasMetadata("flag-holder")) {
                p.removeMetadata("flag-holder", this);
                p.removePotionEffect(PotionEffectType.GLOWING);
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
            p.getInventory().clear();
        }
        this.blueScore = 0;
        this.redScore = 0;
        this.scores.getScore(ChatColor.BLUE + "BLUE").setScore(0);
        this.scores.getScore(ChatColor.RED + "RED").setScore(0);

        Vector max = Vector.getMaximum(this.gameBounds[0].toVector(), this.gameBounds[1].toVector());
        Vector min = Vector.getMinimum(this.gameBounds[0].toVector(), this.gameBounds[1].toVector());
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = Bukkit.getWorld(WORLD_NAME).getBlockAt(x,y,z);
                    if (block.hasMetadata("breakable") || block.getType() == Material.BLACK_BANNER) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        try {
            for (Entity e : Bukkit.getWorld(WORLD_NAME).getEntities()) {
                if (e.getType() == EntityType.DROPPED_ITEM) {
                    e.remove();
                }
            }
        } catch (NullPointerException e) {
            System.out.println(e.toString());
        }

        for (Flag f : this.flags.values()) {
            this.resetFlag(f);
        }
        for (Location c : this.chests.keySet()) {
            c.getBlock().setType(Material.CHEST);
        }
        cl.run();
    }

    public void getAllChests(Player p) {
        Vector max = Vector.getMaximum(this.gameBounds[0].toVector(), this.gameBounds[1].toVector());
        Vector min = Vector.getMinimum(this.gameBounds[0].toVector(), this.gameBounds[1].toVector());
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (Bukkit.getWorld(WORLD_NAME).getBlockAt(x,y,z).getType() == Material.CHEST) {
                        Chest chest = (Chest) Bukkit.getWorld(WORLD_NAME).getBlockAt(x, y, z).getState();
                        Location loc = chest.getLocation();
                        switch (((Directional) chest.getBlockData()).getFacing()) {
                            case NORTH:
                                loc.setYaw(0);
                                break;
                            case EAST:
                                loc.setYaw(90);
                                break;
                            case SOUTH:
                                loc.setYaw(180);
                                break;
                            case WEST:
                                loc.setYaw(270);
                                break;
                        }
                        this.chests.put(loc, chest.getInventory().getStorageContents());
                        p.sendMessage("Added Chest with Inventory: " + Arrays.toString(chest.getInventory().getStorageContents()));
                    }
                }
            }
        }
    }

    public String serializeLocation(Location loc) {
        return (loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getWorld().getUID()).replace('.', '_');
    }

    public Location locFromString(String loc) {
        String[] vars = loc.replace('_', '.').split(";");
        double x = Double.parseDouble(vars[0]);
        double y = Double.parseDouble(vars[1]);
        double z = Double.parseDouble(vars[2]);
        float yaw = Float.parseFloat(vars[3]);
        Location out = new Location(Bukkit.getWorld(UUID.fromString(vars[4])), x, y, z);
        out.setYaw(yaw);
        return out;
    }

    public List<Map<String, Object>> serializeInventory(ItemStack[] items) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();

        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                out.add(null);
                continue;
            }
            out.add(items[i].serialize());
        }

        return out;
    }

    public ItemStack[] deserializeInventory(List<Map<?, ?>> contents) {
        ItemStack[] out = new ItemStack[contents.size()];

        for (int i = 0; i < out.length; i++) {
            if (contents.get(i) == null) {
                out[i] = null;
                continue;
            }
            out[i] = ItemStack.deserialize((Map<String, Object>) contents.get(i));
        }

        return out;
    }
}

class CTF_Config implements CommandExecutor {
    public CTF game;

    CTF_Config(CTF game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Sorry, you must be a player to configure a new CTF game");
            return false;
        }
        Player p = (Player) commandSender;
        ComponentBuilder configMessage = new ComponentBuilder(
                "Welcome To The CTFMC Configuration\n")
                .append("CLICK HERE To set a new BLUE Spawn\n").color(ChatColor.BLUE).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun spawnblue"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Blue").color(ChatColor.BLUE).create()))
                .append("CLICK HERE To set a new RED Spawn\n").color(ChatColor.RED).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun spawnred"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Red").color(ChatColor.RED).create()))

                .append("\nCLICK HERE To Toggle Event Handlers\n").color(ChatColor.GRAY).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun listentoggle"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("ListenToggle").create()))

                .append("\nJOIN").color(ChatColor.GREEN).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun join"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Join").create())).append(" | ").color(ChatColor.GRAY).bold(true)

                .append("LEAVE").color(ChatColor.DARK_RED).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun leave"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Leave").create())).append("\n").bold(false)

                .append("\nCLICK HERE to Toggle Chest Selector\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun chestselect"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("/ctfrun chestselect").create())).color(ChatColor.GRAY)
                .append("CLICK HERE to Toggle Flag Selector\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun bannerselect"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Use /ctfrun addbaner To Add Selected Banner").create()))
                .append("CLICK HERE to Toggle BLUE Dropoff Selector\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun dropoff blue")).color(ChatColor.BLUE)
                .append("CLICK HERE to Toggle RED Dropoff Selector").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun dropoff red")).color(ChatColor.RED)

                .append("\nCLICK HERE To load the default config file into memory. Use /ctfrun load to overwrite the current config with the file's data.").color(ChatColor.GRAY)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun loadFile"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Or use /ctfrun loadFile filename to load a specific file").create()));
        p.spigot().sendMessage(configMessage.create());

        return true;
    }
}

class CTF_Run implements CommandExecutor {
    public CTF game;

    CTF_Run(CTF game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Only Players may set CTF variables");
            return false;
        }
        Player p = (Player) commandSender;
        Location loc;
        switch (args[0]) {
            case "spawnblue":
                loc = center(p.getLocation());
                this.game.BlueSpawns.add(loc);
                p.sendMessage("Added " + loc.toString() + " to Blue Spawns");
                return true;
            case "spawnred":
                loc = center(p.getLocation());
                this.game.RedSpawns.add(loc);
                p.sendMessage("Added " + loc.toString() + " to Red Spawns");
                return true;
            case "listentoggle":
                if (this.game.listeners) {
                    this.game.disableEvents();
                } else {
                    this.game.enableEvents();
                }
                p.sendMessage("Event Listeners is now set to: " + this.game.listeners);
                return true;
            case "join":
                if (this.game.players.containsKey(p)) {
                    return true;
                }
                this.game.newPlayer(p);
                return true;
            case "leave":
                if (!(this.game.players.containsKey(p))) {
                    return true;
                }
                this.game.leave(p);
                p.sendMessage("You have left the game");
                return true;
            case "chestselect":
                if (!(p.isOp())) {
                    return false;
                }
                if (p.hasMetadata("ctf-chest-select")) {
                    p.removeMetadata("ctf-chest-select", this.game);
                    p.sendMessage("Chest Selector Now Disabled");
                } else {
                    p.setMetadata("ctf-chest-select", new FixedMetadataValue(this.game, true));
                    p.sendMessage("Chest Selector Now Enabled");
                }
                return true;
            case "getchests":
                this.game.getAllChests(p);
                return true;
            case "rpackreload":
                for (Player t : Bukkit.getServer().getOnlinePlayers()) {
                    t.setResourcePack("https://www.dropbox.com/sh/6w8l331nr1t5b3f/AABOqcuvafnmjGsT62OMq636a?dl");
                }
                return true;
            case "changeteam":
                Player target = Bukkit.getServer().getPlayer(args[1]);
                if (!(this.game.players.containsKey(target))) {
                    p.sendMessage("That player is not in the game!");
                    return false;
                }
                this.game.changeTeam(target);
                p.sendMessage("Player is now on team " + this.game.players.get(target).team.toUpperCase());
                return true;
            case "bannerselect":
                if (!(p.isOp())) {
                    return false;
                }
                if (p.hasMetadata("ctf-flag-select")) {
                    p.removeMetadata("ctf-flag-select", this.game);
                    p.sendMessage("Flag Selector Now Disabled");
                } else {
                    p.setMetadata("ctf-flag-select", new FixedMetadataValue(this.game, true));
                    p.sendMessage("Flag Selector Now Enabled");
                }
                return true;
            case "addbanner":
                if (this.game.TempBannerSelect == null) {
                    p.sendMessage(ChatColor.RED + "You must select a banner first!");
                    return false;
                }
                if (this.game.flags.containsKey(this.game.TempBannerSelect.getLocation())) {
                    p.sendMessage(ChatColor.RED + "There is already a flag at this location!");
                    return false;
                }
                this.game.flags.put(this.game.TempBannerSelect.getLocation(), new Flag(
                        this.game.TempBannerSelect.getBlockData(),
                        this.game.TempBannerSelect.getPatterns(),
                        this.game.TempBannerSelect.getLocation(),
                        args[1], args[2].replace('_', ' ')
                ));
                this.game.TempBannerSelect = null;
                return true;
            case "resetflags":
                for (Map.Entry<Location, Flag> f : this.game.flags.entrySet()) {
                    this.game.resetFlag(f.getValue());
                }
                return true;
            case "loadFile":
                String fileName;
                try {
                    fileName = this.game.getDataFolder().toString() + "/" + args[1];
                } catch (IndexOutOfBoundsException e) {
                    fileName = this.game.getDataFolder().toString() + "/" + this.game.configFileName;
                }
                this.game.configFile = new File(fileName);
                try {
                    this.game.config.load(this.game.configFile);
                } catch (IOException | InvalidConfigurationException e) {
                    ComponentBuilder message = new ComponentBuilder("No configuration file exists. Specify a name with load name or CLICK HERE to create one under the default name.")
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ctfrun create"));
                    p.spigot().sendMessage(message.create());
                }
                p.sendMessage("Config file loaded to memory");
                return true;
            case "create":
                try {
                    fileName = this.game.getDataFolder().toString() + "/" + args[1];
                } catch (IndexOutOfBoundsException e) {
                    fileName = this.game.getDataFolder().toString() + "/" + this.game.configFileName;
                }
                this.game.configFile = new File(fileName);
                try {
                    if (this.game.configFile.createNewFile()) {
                        this.game.config.load(this.game.configFile);
                        p.sendMessage("New config file " + fileName + " was created and loaded.");
                    } else {
                        p.sendMessage(fileName + " already exists.");
                    }
                } catch (IOException | InvalidConfigurationException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            case "save":
                this.game.config.set("BlueSpawns", this.game.BlueSpawns.stream().map(i -> this.game.serializeLocation(i)).collect(Collectors.toList()));
                this.game.config.set("RedSpawns", this.game.RedSpawns.stream().map(i -> this.game.serializeLocation(i)).collect(Collectors.toList()));

                Map<String, Object> chests = new HashMap<String, Object>();
                for (Map.Entry<Location, ItemStack[]> c : this.game.chests.entrySet()) {
                    chests.put(
                            this.game.serializeLocation(c.getKey()),
                            this.game.serializeInventory(c.getValue())
                    );
                }
                this.game.config.set("Chests", chests);

                Map<String, Object> flags = new HashMap<String, Object>();
                for (Map.Entry<Location, Flag> f : this.game.flags.entrySet()) {
                    flags.put(
                            this.game.serializeLocation(f.getKey()),
                            f.getValue().serialize()
                    );
                }
                this.game.config.set("Flags", flags);

                try {
                    this.game.config.save(this.game.configFile);
                    p.sendMessage("Config saved.");
                } catch (IOException e) {
                    e.printStackTrace();
                    p.sendMessage("There was a problem saving the config.");
                }
                return true;
            case "load":
                this.game.BlueSpawns = new ArrayList<Location>(((ArrayList<String>) this.game.config.get("BlueSpawns")).stream().map(i -> this.game.locFromString(i)).collect(Collectors.toList()));
                this.game.RedSpawns = new ArrayList<Location>(((ArrayList<String>) this.game.config.get("RedSpawns")).stream().map(i -> this.game.locFromString(i)).collect(Collectors.toList()));

                this.game.chests = new HashMap<Location, ItemStack[]>();
                try {
                    for (Map.Entry<String, Object> c : (this.game.config.getConfigurationSection("Chests")).getValues(true).entrySet()) {
                        this.game.chests.put(
                                this.game.locFromString(c.getKey()),
                                this.game.deserializeInventory((List<Map<?, ?>>) c.getValue())
                        );
                    }
                } catch (NullPointerException e) {
                    System.out.println(e.toString());
                }
                this.game.cl = new ChestLoader(this.game.chests);

                this.game.flags = new HashMap<Location, Flag>();
                try {
                    for (Map.Entry<String, Object> f : (this.game.config.getConfigurationSection("Flags").getValues(false).entrySet())) {
                        this.game.flags.put(
                                this.game.locFromString(f.getKey()),
                                new Flag(((MemorySection) f.getValue()).getValues(true))
                        );
                    }
                } catch (NullPointerException e) {
                    System.out.println(e.toString());
                }

                p.sendMessage("Successfully loaded config from file " + this.game.configFileName);
                return true;
            case "dropoff":
                String team = args[1];
                if (p.hasMetadata("ctf-dropoff-select")) {
                    if (p.getMetadata("ctf-dropoff-select").get(0).value().equals(team)) {
                        p.removeMetadata("ctf-dropoff-select", this.game);
                        p.sendMessage("Dropoff selector disabled");
                        return true;
                    }
                }
                p.setMetadata("ctf-dropoff-select", new FixedMetadataValue(this.game, team));
                return true;
            case "start":
                new StartCountdown(this.game).runTaskTimer(this.game, 0, 20);
                return true;
            case "stop":
                this.game.gameActive = false;
                this.game.resetGame();
                return true;
            case "reset":
                this.game.resetGame();
                p.sendMessage("Game Reset!");
                return true;
            case "shuffleteams":
                List<Player> shuffledPlayers = (List<Player>) this.game.players.keySet();
                Collections.shuffle(shuffledPlayers);
                for (String entry : this.game.sb.getTeam("ctf-blue").getEntries()) {
                    this.game.sb.getTeam("ctf-blue").removeEntry(entry);
                }
                for (String entry : this.game.sb.getTeam("ctf-red").getEntries()) {
                    this.game.sb.getTeam("ctf-red").removeEntry(entry);
                }
                for (Player player : shuffledPlayers.subList(0, shuffledPlayers.size()/2)) {
                    this.game.players.get(player).team = "blue";
                    this.game.sb.getTeam("ctf-blue").addEntry(p.getName());
                    player.sendMessage(ChatColor.BLUE + "You are now on team BLUE");
                }
                for (Player player : shuffledPlayers.subList(shuffledPlayers.size()/2, shuffledPlayers.size())) {
                    this.game.players.get(player).team = "red";
                    this.game.sb.getTeam("ctf-red").addEntry(p.getName());
                    player.sendMessage(ChatColor.RED + "You are now on team RED");
                }
                return true;
            case "resetchests":
                this.game.cl.run();
                return true;
            default:
                p.sendMessage(ChatColor.RED + "Please enter a valid ctf command to run");
                return false;
        }
    }

    public static Location center(Location location) {
        String x = "" + location.getX();
        String z = "" + location.getZ();
        if(x.contains(".")) x = x.substring(0, x.indexOf("."));
        if(z.contains(".")) z = z.substring(0, z.indexOf("."));
        x+=".5";
        z+=".5";
        location.setX(Double.parseDouble(x));
        location.setZ(Double.parseDouble(z));
        location.setYaw(Math.round(location.getYaw() / 90) * 90f);
        location.setPitch(0f);
        return location;
    }
}

class CTF_Print implements CommandExecutor {
    public CTF game;

    CTF_Print(CTF game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        System.out.println("Blue Spawns: " + this.game.BlueSpawns.toString());
        System.out.println("Red Spawns: " + this.game.RedSpawns.toString());
        System.out.println("Chests: " + this.game.chests.toString());
        System.out.println("Flags: " + this.game.flags.toString());
        System.out.println("Players: " + this.game.players.toString());
        return true;
    }
}

class PlayerEventListener implements Listener {
    public CTF game;

    PlayerEventListener(CTF game) {
        this.game = game;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        System.out.println(p.isDead());
        if (!(p.getWorld().getName().equals(game.WORLD_NAME))) {
            return;
        }
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        if (p.hasMetadata("flag-holder")) {
            Flag f = this.game.flags.get(p.getMetadata("flag-holder").get(0).value());
            if (p.getLastDamageCause().getCause().equals(EntityDamageEvent.DamageCause.VOID)) {
                this.game.resetFlag(f);
                this.game.broadcast(f.name + " has been reset");
                return;
            }
            this.game.dropFlag((Location) p.getMetadata("flag-holder").get(0).value(), f, p.getLocation());
            p.removeMetadata("flag-holder", this.game);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!(this.game.players.containsKey(p))) {
            return;
        }
        p.teleport(p.getWorld().getSpawnLocation());
        p.sendMessage("You Will Respawn in 10 Seconds.");
        TextComponent currentClass = new TextComponent(ChatColor.GREEN + "Selected Kit: " + this.game.players.get(p).kit.name.toUpperCase());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, currentClass);
        if (!(this.game.gameActive)) {
            return;
        }
        p.setMetadata("respawning", new FixedMetadataValue(this.game, true));
        new RespawnCountdown(this.game, p).runTaskTimer(this.game, 20, 20);
    }

    @EventHandler
    public void onPlayerJoinGame(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld().getName().equals(game.WORLD_NAME)) {
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(p.getWorld().getSpawnLocation());
            this.game.newPlayer(p);
        }

        if (event.getFrom().getName().equals(game.WORLD_NAME)) {
            p.getInventory().clear();
            this.game.leave(p);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld().getName().equals(game.WORLD_NAME)) {
            p.setGameMode(GameMode.SURVIVAL);
            this.game.newPlayer(p);
            if (this.game.gameActive) {
                this.game.respawn(p);
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        if (event.getPlayer().getWorld().getName().equals(game.WORLD_NAME)) {
            event.getPlayer().getInventory().clear();
            this.game.leave(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.getPlayer().getWorld().getName().equals(game.WORLD_NAME)) {
            event.getPlayer().getInventory().clear();
            this.game.leave(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!(p.getWorld().getName().equals(game.WORLD_NAME))) {
            return;
        }
        if (this.game.players.containsKey(p)) {
            event.getBlock().setMetadata("breakable", new FixedMetadataValue(this.game, true));
            return;
        }
        if (!(p.isOp())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!(p.getWorld().getName().equals(game.WORLD_NAME))) {
            event.setCancelled(false);
            return;
        }
        if ((!(this.game.players.containsKey(p)))) {
            event.setCancelled(false);
            return;
        }
        if (!(event.getBlock().hasMetadata("breakable"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (!(this.game.players.containsKey(event.getPlayer()))) {
            return;
        }
         if (event.getRightClicked().getType() == EntityType.ITEM_FRAME) {
             event.setCancelled(true);
         }
    }

    @EventHandler
    public void onItemFrameDeath(EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() != EntityType.PLAYER) {
            return;
        }
        if (!(this.game.players.containsKey((Player) event.getDamager()))) {
            return;
        }
        if (event.getEntity().getType() == EntityType.ITEM_FRAME) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerClickSign(PlayerInteractEvent event){
        Player p = event.getPlayer();
        if (!(this.game.players.containsKey(p)) || (event.getAction() != Action.RIGHT_CLICK_BLOCK) ) {
            return;
        }
        if (this.game.gameActive && !(p.hasMetadata("respawning"))) {
            return;
        }
        if(event.getClickedBlock().getType() == Material.OAK_WALL_SIGN){
            if(event.getAction() == Action.RIGHT_CLICK_BLOCK){
                Sign sign = (Sign) event.getClickedBlock().getState();
                try {
                    this.game.changeKit(p, sign.getLine(1).toLowerCase());
                } catch (NullPointerException e) {
                    System.out.println(e.toString());
                }
            }
        }
    }

    @EventHandler
    public void onChestSelectTool(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!(p.hasMetadata("ctf-chest-select"))) {
            return;
        }
        event.setCancelled(true);
        if (event.getBlock().getType() != Material.CHEST) {
            return;
        }
        Chest chest = (Chest) event.getBlock().getState();
        switch (((Directional) chest.getBlockData()).getFacing()) {
            case NORTH:
                chest.getLocation().setYaw(0);
                break;
            case EAST:
                chest.getLocation().setYaw(90);
                break;
            case SOUTH:
                chest.getLocation().setYaw(180);
                break;
            case WEST:
                chest.getLocation().setYaw(270);
                break;
        }
        this.game.chests.put(chest.getLocation(), chest.getInventory().getStorageContents());
        p.sendMessage("Added Chest with Inventory: " + Arrays.toString(chest.getInventory().getStorageContents()));
    }

    @EventHandler
    public void onBannerSelect(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!(p.hasMetadata("ctf-flag-select"))) {
            return;
        }
        if (event.getBlock().getType() != Material.BLACK_BANNER) {
            event.setCancelled(true);
            return;
        }
        this.game.TempBannerSelect = (Banner) event.getBlock().getState();
        p.sendMessage("You have selected a banner");
        event.setCancelled(true);
    }

    @EventHandler
    public void onDropoffSelect(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!(p.hasMetadata("ctf-dropoff-select"))) {
            return;
        }
        Block b = event.getBlock();
        String team = (String) p.getMetadata("ctf-dropoff-select").get(0).value();
        b.setMetadata("ctf-dropoff", new FixedMetadataValue(this.game, team));
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntityType() == EntityType.PLAYER)) {
            return;
        }
        Player p = (Player) event.getEntity();
        if (this.game.players.containsKey(p)) {
            if (!(this.game.gameActive) || p.hasMetadata("respawning")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerOverlapFlag(PlayerMoveEvent event) {
        Block block = event.getTo().getBlock();
        if (block.getType() != Material.BLACK_BANNER) {
            return;
        }
        Flag f = this.game.flags.get(block.getLocation());
        Player p = event.getPlayer();
        CTF_PlayerData p_data = this.game.players.get(p);
        if (p_data.team.equals(f.team) && f.state.equals("dropped")) {
            block.setType(Material.AIR);
            this.game.resetFlag(f);
            this.game.broadcast(p.getDisplayName() + " has returned " + f.name);
        }
        if (!(p_data.team.equals(f.team)) && !(p.hasMetadata("flag-holder"))) {
            block.setType(Material.AIR);
            f.state = p.getName();
            ItemStack flagCarry = new ItemStack(Material.BLACK_BANNER);
            BannerMeta meta = ((BannerMeta) flagCarry.getItemMeta());
            meta.setPatterns(f.flagPattern);
            flagCarry.setItemMeta(meta);
            p.getInventory().setItemInOffHand(flagCarry);
            p.setMetadata("flag-holder", new FixedMetadataValue(this.game, block.getLocation()));
            f.state = "held";
            this.game.broadcast(p.getDisplayName() + " has " + f.name);
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1000000, 1, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 1000000, 1, true, false, false));
        }
    }

    @EventHandler
    public void onPlayerFlagDropoff(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!(p.hasMetadata("flag-holder"))) {
            return;
        }
        Block b = event.getPlayer().getLocation().getBlock().getRelative(BlockFace.DOWN);
        Flag f = this.game.flags.get(p.getMetadata("flag-holder").get(0).value());
        if (b.hasMetadata("ctf-dropoff")) {
            if (this.game.players.get(p).team.equals(b.getMetadata("ctf-dropoff").get(0).value())) {
                this.game.captureFlag(f, p);
            }
        }
    }

    @EventHandler
    public void onPlayerHandSwap(PlayerSwapHandItemsEvent event) {
        if (this.game.players.containsKey(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerAttemptPlaceFlag(BlockPlaceEvent event) {
        if ((event.getBlock().getType().equals(Material.BLACK_BANNER) || event.getBlock().getType().equals(Material.BLACK_WALL_BANNER)) && this.game.players.containsKey(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerItemDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld().getName().equals(game.WORLD_NAME) && this.game.players.containsKey(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInventoryInteractFlag(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getWhoClicked();
        if (p.getWorld().getName().equals(game.WORLD_NAME) && this.game.players.containsKey(p)) {
            try {
                if (event.getCurrentItem().getType() == Material.BLACK_BANNER) {
                    event.setCancelled(true);
                }
            } catch (NullPointerException e) {
                event.setCancelled(false);
            }
        }
    }

    @EventHandler
    public void onPlayerGetWoolOfColor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getWhoClicked();
        if (p.getWorld().getName().equals(game.WORLD_NAME) && this.game.players.containsKey(p)) {
            if (event.getCurrentItem().getType() == Material.WHITE_WOOL) {
                if (this.game.players.get(p).team.equals("blue")) {
                    ItemStack item = event.getCurrentItem();
                    item.setType(Material.BLUE_WOOL);
                    event.setCurrentItem(item);
                }
                if (this.game.players.get(p).team.equals("red")) {
                    ItemStack item = event.getCurrentItem();
                    item.setType(Material.RED_WOOL);
                    event.setCurrentItem(item);
                }
            }
            if (event.getClick().isShiftClick()) {
                ItemStack[] stacks = event.getView().getBottomInventory().getContents();
                for(ItemStack stack : stacks) {
                    if(stack == null ) {
                        continue;
                    }
                    if(stack.getType() == Material.PAPER) {
                        stack.setType(Material.WHITE_WOOL);
                    }
                }
            }

        }
    }

    @EventHandler
    public void onPlayerAttemptPutChest(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        if (top.getType() == InventoryType.CHEST && bottom.getType() == InventoryType.PLAYER) {
            if (this.game.players.containsKey(((Player) bottom.getHolder()))) {
                if (event.getClickedInventory() == bottom && event.getClick().isShiftClick()) {
                    event.setCancelled(true);
                }
                if (event.getClickedInventory() == top && event.getCursor().getType() != Material.AIR) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRegen(EntityRegainHealthEvent event) {
        if (this.game.players.containsKey(event.getEntity())) {
            event.setAmount(event.getAmount() * 0.1);
        }
    }
}

class RespawnCountdown extends BukkitRunnable {
    int i = 10;
    CTF game;
    Player player;

    RespawnCountdown(CTF game, Player p) {
        this.game = game;
        this.player = p;
    }

    @Override
    public void run() {
        if (!(this.game.gameActive)) {
            cancel();
        }
        if (i <= 0) {
            this.game.respawn(this.player);
            cancel();
        } else if (i <= 5) {
            this.player.sendMessage("You will Respawn in " + i + " Seconds.");
        }
        i--;
        TextComponent currentClass = new TextComponent(ChatColor.GREEN + "Selected Kit: " + this.game.players.get(this.player).kit.name.toUpperCase());
        this.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, currentClass);
    }
}

class StartCountdown extends BukkitRunnable {
    int i = 10;
    CTF game;

    StartCountdown(CTF game) {
        this.game = game;
    }

    @Override
    public void run() {
        if (i <= 0) {
            this.game.start();
            cancel();
        } else if (i <= 10) {
            this.game.broadcast(ChatColor.GOLD + "GAME STARTING IN " + i + " SECONDS");
        }
        i--;
    }
}

class ChestLoader extends BukkitRunnable {
    HashMap<Location, ItemStack[]> chests;

    ChestLoader(HashMap<Location, ItemStack[]> chests) {
        this.chests = chests;
    }

    @Override
    public void run() {
        for (Map.Entry<Location, ItemStack[]> c : this.chests.entrySet()) {
            c.getKey().getBlock().setType(Material.CHEST);
            Chest chest = (Chest) c.getKey().getBlock().getState();
            BlockFace direction;
            switch ((int) c.getKey().getYaw()) {
                case 90:
                    direction = BlockFace.EAST;
                    break;
                case 180:
                    direction = BlockFace.SOUTH;
                    break;
                case 270:
                    direction = BlockFace.WEST;
                    break;
                default:
                    direction = BlockFace.NORTH;
                    break;
            }
            Directional dir = ((Directional) chest.getBlockData());
            dir.setFacing(direction);
            chest.setBlockData(dir);
            chest.update(true);
            chest.getInventory().setStorageContents(c.getValue());
        }
    }
}

class CTF_PlayerData {
    public String team;
    public CTF_Kit kit;

    CTF_PlayerData(String team, String kit) {
        this.team = team;
        this.kit = new CTF_Kit(kit, team);
    }

    public void changeKit(String kit) {
        this.kit = new CTF_Kit(kit, team);
    }
}

class CTF_Kit {
    String name;

    ItemStack helmet;
    ItemStack chestplate;
    ItemStack leggings;
    ItemStack boots;
    ItemStack weapon1;
    ItemStack weapon2;
    ItemStack wool;
    ItemStack extraWool;

    CTF_Kit(String kit, String team) {
        switch (kit) {
            case "melee":
                this.name = "melee";
                this.weapon1 = new ItemStack(Material.IRON_SWORD);
                this.weapon1.addEnchantment(Enchantment.KNOCKBACK, 2);
                this.weapon2 = new ItemStack(Material.GOLDEN_APPLE, 2);
                this.helmet = new ItemStack(Material.CHAINMAIL_HELMET);
                this.chestplate = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
                this.leggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
                this.boots = new ItemStack(Material.CHAINMAIL_BOOTS);
                break;
            case "archer":
                this.name = "archer";
                this.weapon1 = new ItemStack(Material.BOW);
                this.weapon1.addEnchantment(Enchantment.ARROW_KNOCKBACK, 2);
                this.weapon1.addEnchantment(Enchantment.ARROW_DAMAGE, 2);
                this.weapon2 = new ItemStack(Material.ARROW, 20);
                this.helmet = new ItemStack(Material.CHAINMAIL_HELMET);
                this.chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
                this.leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                this.boots = new ItemStack(Material.CHAINMAIL_BOOTS);
                break;
            case "builder":
                this.name = "builder";
                this.weapon1 = new ItemStack(Material.STONE_SWORD);
                this.weapon2 = new ItemStack(Material.SHEARS);
                this.weapon2.addEnchantment(Enchantment.DIG_SPEED, 5);
                this.helmet = new ItemStack(Material.GOLDEN_HELMET);
                this.leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                this.boots = new ItemStack(Material.LEATHER_BOOTS);
                if (team.equals("blue")) {
                    this.extraWool = new ItemStack(Material.BLUE_WOOL, 64);
                }
                if (team.equals("red")) {
                    this.extraWool = new ItemStack(Material.RED_WOOL, 64);
                }
                break;
        }
        if (team.equals("blue")) {
            this.wool = new ItemStack(Material.BLUE_WOOL, 64);
        }
        if (team.equals("red")) {
            this.wool = new ItemStack(Material.RED_WOOL, 64);
        }
        this.weapon1.getItemMeta().setUnbreakable(true);
        this.weapon2.getItemMeta().setUnbreakable(true);
        this.helmet.getItemMeta().setUnbreakable(true);
        try {
            this.chestplate.getItemMeta().setUnbreakable(true);
        } catch (NullPointerException ignored) {}
        this.leggings.getItemMeta().setUnbreakable(true);
        this.boots.getItemMeta().setUnbreakable(true);
    }
}

class Flag {
    BlockData data;
    List<Pattern> flagPattern;
    Location startPos;
    String team;
    String name;
    public String state = "base";

    Flag(BlockData data, List<Pattern> flagPattern, Location startPos, String team, String name) {
        this.data = data;
        this.flagPattern = flagPattern;
        this.startPos = startPos;
        this.team = team;
        this.name = name;
    }

    Flag(Map<String, Object> in) {
        data = Bukkit.createBlockData((String) in.get("data"));
        flagPattern = deserializePatterns((List<Map<String, Object>>) in.get("flagPattern"));
        startPos = locFromString((String) in.get("startPos"));
        team = (String) in.get("team");
        name = (String) in.get("name");
    }

    public Map<String, Object> serialize() {
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("data", this.data.getAsString());
        out.put("flagPattern", serializePatterns());
        out.put("startPos", serializeLocation(startPos));
        out.put("team", team);
        out.put("name", name);

        return out;
    }

    public List<Map<String, Object>> serializePatterns() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Pattern p : flagPattern) {
            out.add(p.serialize());
        }
        return out;
    }

    public List<Pattern> deserializePatterns(List<Map<String, Object>> in) {
        List<Pattern> out = new ArrayList<Pattern>();
        for (Map<String, Object> p : in) {
            out.add(new Pattern(p));
        }
        return out;
    }

    public String serializeLocation(Location loc) {
        return (String.valueOf(loc.getX()) + ";" + String.valueOf(loc.getY()) + ";" + String.valueOf(loc.getZ()) + ";" + loc.getWorld().getUID()).replace('.', '_');
    }

    public Location locFromString(String loc) {
        String[] vars = loc.replace('_', '.').split(";");
        double x = Double.parseDouble(vars[0]);
        double y = Double.parseDouble(vars[1]);
        double z = Double.parseDouble(vars[2]);
        return new Location(Bukkit.getWorld(UUID.fromString(vars[3])), x, y, z);
    }
}