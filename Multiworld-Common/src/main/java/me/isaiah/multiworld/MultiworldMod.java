/**
 * Multiworld Mod
 * Copyright (c) 2021-2024 by Isaiah.
 */
package me.isaiah.multiworld;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.isaiah.multiworld.command.CreateCommand;
import me.isaiah.multiworld.command.DifficultyCommand;
import me.isaiah.multiworld.command.GameruleCommand;
import me.isaiah.multiworld.command.PortalCommand;
import me.isaiah.multiworld.command.SetspawnCommand;
import me.isaiah.multiworld.command.SpawnCommand;
import me.isaiah.multiworld.command.TpCommand;
import me.isaiah.multiworld.perm.Perm;
import me.isaiah.multiworld.portal.Portal;
import me.isaiah.multiworld.portal.WandEventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Multiworld Mod
 */
public class MultiworldMod {
	
	public static final Logger LOGGER = LoggerFactory.getLogger("multiworld");

    public static final String MOD_ID = "multiworld";
    public static MinecraftServer mc;
    public static String CMD = "mw";
    public static ICreator world_creator;
    private static final List<String> PENDING_WORLD_RESTORES = new ArrayList<>();
    private static int RESTORE_DELAY_TICKS = 60; // delay restores ~3s after server start (at 20 TPS)
    private static int restoreDelayLeft = 0;
    private static final long RESTORE_TICK_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(10); // 10ms budget per tick
    
    public static String[] COMMAND_HELP = {
    		"&4Multiworld Mod Commands:&r",
    		"&a/mw spawn&r - Teleport to current world spawn",
    		"&a/mw setspawn&r - Sets the current world spawn",
    		"&a/mw tp <id>&r - Teleport to a world",
    		"&a/mw list&r - List all worlds",
    		"&a/mw gamerule <rule> <value>&r - Change a worlds Gamerules",
    		"&a/mw create <id> <env> [-g=<generator> -s=<seed>]&r - create a new world",
    		"&a/mw difficulty <value> [world id] - Sets the difficulty of a world"
    };

	// Mod Version
	public static final String VERSION = "1.10";

    public static void setICreator(ICreator ic) {
        world_creator = ic;
    }

    /**
     * Gets the Multiversion ICreator instance
     */
    public static ICreator get_world_creator() {
    	return world_creator;
    }

    public static ServerWorld create_world(String id, Identifier dim, ChunkGenerator gen, Difficulty dif, long seed) {
    	return world_creator.create_world(id, dim, gen, dif, seed);
    }

    /**
     * ModInitializer onInitialize
     * 
     * @see {@link me.isaiah.multiworld.fabric.MultiworldModFabric}
     */
    public static void init() {
        System.out.println("Multiworld init");
        
        // TODO: Testing
        // PortalCommand.test();
        
        //WandEventHandler.register();
    }

    public static Identifier new_id(String id) {
    	// tryParse works from 1.18 to 1.21+
    	return Identifier.tryParse(id);
    }

    // On server start
    public static void on_server_started(MinecraftServer mc) {
        MultiworldMod.mc = mc;
        
        // LOGGER.info("Registering events...");
        // WandEventHandler.register();
		
		File cfg_folder = new File("config");
		if (cfg_folder.exists()) {
			File folder = new File(cfg_folder, "multiworld");
			File worlds = new File(folder, "worlds");
			List<String> found = new ArrayList<>();
			if (worlds.exists()) {
				for (File f : worlds.listFiles()) {
					if (f.getName().equals("minecraft")) {
						continue;
					}
					for (File fi : f.listFiles()) {
						if (!fi.getName().endsWith(".yml")) continue;
						String id = f.getName() + ":" + fi.getName().replace(".yml", "");
						LOGGER.info("Found saved world " + id);
						found.add(id);
					}
				}
			}
			// Enqueue restores to spread work across ticks
			enqueue_world_restores(found);
			// initialize delay before starting restores to avoid competing with startup work
			restoreDelayLeft = RESTORE_DELAY_TICKS;
			
			int loaded = Portal.reinit_portals_from_config(mc);
			if (loaded > 0) {
				LOGGER.info("Found " + loaded + " saved world portals.");
			}
		}
    }

	public static void enqueue_world_restores(Collection<String> ids) {
		if (ids == null) return;
		PENDING_WORLD_RESTORES.addAll(ids);
	}

	// Called per tick by platform layer to avoid heavy work in a single tick
	public static void processPendingRestores(int maxPerTick) {
		if (PENDING_WORLD_RESTORES.isEmpty()) return;
		if (restoreDelayLeft > 0) {
			restoreDelayLeft--;
			return;
		}
		int n = Math.max(1, maxPerTick);
		long start = System.nanoTime();
		int restored = 0;
		while (!PENDING_WORLD_RESTORES.isEmpty() && restored < n) {
			// stop if we exceed our per-tick time budget
			if (System.nanoTime() - start > RESTORE_TICK_BUDGET_NANOS) break;
			String id = PENDING_WORLD_RESTORES.remove(0);
			try {
				long s = System.nanoTime();
				CreateCommand.reinit_world_from_config(mc, id);
				long durMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s);
				LOGGER.info("Restored world {} in {} ms", id, durMs);
			} catch (Exception e) {
				LOGGER.error("Failed to restore world {}", id, e);
			}
			restored++;
		}
	}

    public static ServerPlayerEntity get_player(ServerCommandSource s) throws CommandSyntaxException {
    	ServerPlayerEntity plr = s.getPlayer();
    	if (null == plr) {
    		// s.sendMessage(text_plain("Multiworld Mod for Minecraft " + mc.getVersion()));
    		// s.sendMessage(text_plain("These commands currently require a Player."));
    		
    		throw ServerCommandSource.REQUIRES_PLAYER_EXCEPTION.create();
    	}
    	return plr;
    }
    
    public static boolean isPlayer(ServerCommandSource s) {
    	try {
    		ServerPlayerEntity plr = s.getPlayer();
    		if (null == plr) {
    			return false;
    		}
    	} catch (Exception ex) {
    		if (ex instanceof CommandSyntaxException) {
    			if (s.getName().equalsIgnoreCase("Server")) return false;
    		}
    	}
    	return true;
    }

    // On command register
    public static void register_commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal(CMD)
                    .requires(source -> {
                        try {
                            return source.hasPermissionLevel(1) || Perm.has(get_player(source), "multiworld.cmd") ||
                                    Perm.has(get_player(source), "multiworld.admin");
                        } catch (Exception e) {
                            return source.hasPermissionLevel(1);
                        }
                    }) 
                        .executes(ctx -> {
                            return broadcast(ctx.getSource(), Formatting.AQUA, null);
                        })
                        .then(argument("message", greedyString()).suggests(new InfoSuggest())
                                .executes(ctx -> {
                                    try {
                                        return broadcast(ctx.getSource(), Formatting.AQUA, getString(ctx, "message") );
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return 1;
                                    }
                                 }))); 
    }
    

    public static int broadcast(ServerCommandSource source, Formatting formatting, String message) throws CommandSyntaxException {
    	/*
    	if (!source.isExecutedByPlayer()) {
    		if (!source.getName().equalsIgnoreCase("Server")) return 1;
    		return broadcast_console(source, message);
    	}
    	*/
    	
    	if (!isPlayer(source)) {
    		ConsoleCommand.broadcast_console(mc, source, message);
    		return 1;
    	}
    	
    	final ServerPlayerEntity plr = get_player(source); // source.getPlayerOrThrow();

        if (null == message) {
            message(plr, "&bMultiworld Mod for Minecraft " + mc.getVersion());
            
            World world = plr.getWorld();
            Identifier id = world.getRegistryKey().getValue();
            
            message(plr, "Currently in: " + id.toString());
            
            return 1;
        }

        boolean ALL = Perm.has(plr, "multiworld.admin");
        String[] args = message.split(" ");
        
        /*if (args[0].equalsIgnoreCase("portaltest")) {
            BlockPos pos = plr.getBlockPos();
            pos = pos.add(2, 0, 2);
            ServerWorld w = plr.getWorld();

            Portal p = new Portal();
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 5; y++) {
                    BlockPos pos2 = pos.add(x, y, 0);
                    if ((x > 0 && x < 3) && (y > 0 && y < 4)) {
                        p.blocks.add(pos2);
                        w.setBlockState(pos2, Blocks.NETHER_PORTAL.getDefaultState());
                    } else
                    w.setBlockState(pos2, Blocks.STONE.getDefaultState());
                }
            }
            p.addToMap();
            try {
                p.save();
            } catch (IOException e) {
                plr.sendMessage(text("Failed saving portal data. Check console for details.", Formatting.RED), false);
                e.printStackTrace();
            }
        }*/
        
        // Help Command
        if (args[0].equalsIgnoreCase("help")) {
            for (String s : COMMAND_HELP) {
            	message(plr, s);
            }
        }
        
        // Debug
        if (args[0].equalsIgnoreCase("debugtick")) {
        	ServerWorld w = (ServerWorld) plr.getWorld();
        	Identifier id = w.getRegistryKey().getValue();
        	message(plr, "World ID: " + id.toString());
        	message(plr, "Players : " + w.getPlayers().size());
        	w.tick(() -> true);
        }

        // SetSpawn Command
        if (args[0].equalsIgnoreCase("setspawn") && (ALL || Perm.has(plr, "multiworld.setspawn") )) {
            return SetspawnCommand.run(mc, plr, args);
        }

        // Spawn Command
        if (args[0].equalsIgnoreCase("spawn") && (ALL || Perm.has(plr, "multiworld.spawn")) ) {
            return SpawnCommand.run(mc, plr, args);
        }
        
        // Gamerule Command
        if (args[0].equalsIgnoreCase("gamerule") && (ALL || Perm.has(plr, "multiworld.gamerule"))) {
        	return GameruleCommand.run(mc, plr, args);
        }
        
        // Difficulty Command
        if (args[0].equalsIgnoreCase("difficulty") && (ALL || Perm.has(plr, "multiworld.difficulty"))) {
        	return DifficultyCommand.run(mc, plr, args);
        }

        // TP Command
        if (args[0].equalsIgnoreCase("tp") ) {
            if (!(ALL || Perm.has(plr, "multiworld.tp"))) {
                plr.sendMessage(Text.of("No permission! Missing permission: multiworld.tp"), false);
                return 1;
            }
            if (args.length == 1) {
                plr.sendMessage(text_plain("Usage: /" + CMD + " tp <world>"), false);
                return 0;
            }
            return TpCommand.run(mc, plr, args);
        }

        // List Command
        if (args[0].equalsIgnoreCase("list") ) {
            if (!(ALL || Perm.has(plr, "multiworld.cmd"))) {
                plr.sendMessage(Text.of("No permission! Missing permission: multiworld.cmd"), false);
                return 1;
            }

            message(plr, "&bAll Worlds:");
            
            World pworld = plr.getWorld();
            Identifier pwid = pworld.getRegistryKey().getValue();
            
            mc.getWorlds().forEach(world -> {
            	Identifier id = world.getRegistryKey().getValue();
                String name = id.toString();
                if (name.startsWith("multiworld:")) name = name.replace("multiworld:", "");

                if (id.equals(pwid)) {
                	message(plr, "- " + name + " &a(Currently in)");
                } else {
                	message(plr, "- " + name);
                }
            });
        }

        // Version Command
        if (args[0].equalsIgnoreCase("version") && (ALL || Perm.has(plr, "multiworld.cmd")) ) {
            message(plr, "Multiworld Mod version " + VERSION);
            return 1;
        }

        // Create Command
        if (args[0].equalsIgnoreCase("create") ) {
            if (!(ALL || Perm.has(plr, "multiworld.create"))) {
                message(plr, "No permission! Missing permission: multiworld.create");
                return 1;
            }
            return CreateCommand.run(mc, plr, args);
        }
        
        // Delete Command
        if (args[0].equalsIgnoreCase("delete")) {
        	if (!ALL) {
                message(plr, "No permission! Missing permission: multiworld.admin");
                return 1;
            }
        	message(plr, "Delete Command is Console-only for security.");
        }
        
        // Help Command
        if (args[0].equalsIgnoreCase("portal")) {
        	if (!(ALL || Perm.has(plr, "multiworld.portal"))) {
                message(plr, "No permission! Missing permission: multiworld.portal");
                return 1;
            }
        	
        	PortalCommand.run(mc, plr, args);
        }

        return Command.SINGLE_SUCCESS; // Success
    }

	public static Text text(String message) {
		try {
			return Text.of(translate_alternate_color_codes('&', message));
		} catch (Exception e) {
			e.printStackTrace();
			return text_plain(message);
		}
	}

	public static void message(PlayerEntity player, String message) {
		try {
			player.sendMessage(Text.of(translate_alternate_color_codes('&', message)), false);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    private static final char COLOR_CHAR = '\u00A7';
    private static String translate_alternate_color_codes(char altColorChar, String textToTranslate) {
        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(b[i+1]) > -1) {
                b[i] = COLOR_CHAR;
                b[i+1] = Character.toLowerCase(b[i+1]);
            }
        }
        return new String(b);
    }

	public static Text text_plain(String txt) {
		return Text.of(txt);
	}

}