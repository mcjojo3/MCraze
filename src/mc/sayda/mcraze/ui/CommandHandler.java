/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.ui;

import java.util.HashMap;
import java.util.Map;
import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.entity.Player;
import mc.sayda.mcraze.server.PlayerConnection;
import mc.sayda.mcraze.world.TileTemplate;

/**
 * Handles command parsing and execution
 */
public class CommandHandler {
    private mc.sayda.mcraze.server.Server server;
    private mc.sayda.mcraze.server.SharedWorld sharedWorld; // For broadcasting command effects
    private Chat chat;

    // Command registry for tab completion
    private Map<String, String[]> commandArguments = new HashMap<>();

    public CommandHandler(mc.sayda.mcraze.server.Server server, mc.sayda.mcraze.server.SharedWorld sharedWorld,
            Chat chat) {
        this.server = server;
        this.sharedWorld = sharedWorld;
        this.chat = chat;
        registerCommands();
    }

    /**
     * Set SharedWorld reference (called after SharedWorld is created)
     */
    public void setSharedWorld(mc.sayda.mcraze.server.SharedWorld sharedWorld) {
        this.sharedWorld = sharedWorld;
    }

    /**
     * CRITICAL FIX: Admin block break that properly handles chests and item drops
     * This ensures WorldEdit commands don't bypass important game logic
     */
    private void adminBreakBlock(int x, int y) {
        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        // Check if breaking a chest - must drop contents
        Constants.TileID currentTile = world.tiles[x][y].type.name;
        if (currentTile == Constants.TileID.CHEST) {
            mc.sayda.mcraze.world.ChestData chestData = world.getChest(x, y);
            if (chestData != null) {
                // Drop all items from the chest as entities
                for (int cx = 0; cx < 9; cx++) {
                    for (int cy = 0; cy < 3; cy++) {
                        mc.sayda.mcraze.item.InventoryItem invItem = chestData.items[cx][cy];
                        if (invItem != null && !invItem.isEmpty()) {
                            for (int i = 0; i < invItem.getCount(); i++) {
                                mc.sayda.mcraze.item.Item droppedItem = invItem.getItem().clone();
                                droppedItem.x = x + (float) Math.random() * 0.5f;
                                droppedItem.y = y + (float) Math.random() * 0.5f;
                                droppedItem.dy = -0.07f;
                                sharedWorld.getEntityManager().add(droppedItem);
                            }
                        }
                    }
                }
                // Remove chest data
                world.removeChest(x, y);
            }
        }

        // Remove the tile
        world.removeTile(x, y);
    }

    /**
     * CRITICAL FIX: Admin block place that ensures proper tile placement
     */
    private void adminPlaceBlock(int x, int y, Constants.TileID tileId) {
        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        // First break any existing block (handles chests properly)
        if (world.tiles[x][y].type.name != Constants.TileID.AIR) {
            adminBreakBlock(x, y);
        }

        // Place new tile
        world.addTile(x, y, tileId);

        // Broadcast the change
        sharedWorld.broadcastBlockChange(x, y, tileId);
    }

    /**
     * Send a message to all players via broadcast
     */
    private void sendMessage(String message, Color color) {
        if (sharedWorld != null) {
            // Broadcast to all players (message will come back to sender too)
            mc.sayda.mcraze.network.packet.PacketChatMessage packet = new mc.sayda.mcraze.network.packet.PacketChatMessage(
                    message, color);
            sharedWorld.broadcastPacket(packet);
        } else if (chat != null) {
            // Fallback to chat if available (integrated server)
            chat.addMessage(message, color);
        } else {
            // No way to send message (dedicated server with no SharedWorld)
            System.err.println("WARNING: CommandHandler has no SharedWorld or Chat! Message: " + message);
        }
    }

    /**
     * Register all available commands and their argument options
     */
    private void registerCommands() {
        // Commands with arguments
        commandArguments.put("/gamerule", new String[] { "keepInventory", "daylightCycle", "spelunking" });
        commandArguments.put("/time", new String[] { "set", "add" });
        commandArguments.put("/give", new String[] {}); // Item names are too many to list
        commandArguments.put("/summon", new String[] {}); // Item names are too many to list
        commandArguments.put("/teleport", new String[] {});
        commandArguments.put("/tp", new String[] {});
        commandArguments.put("/we", new String[] { "set", "fill", "replace", "undo", "sphere" }); // WorldEdit commands

        // Commands without arguments (empty array)
        commandArguments.put("/help", new String[] {});
        commandArguments.put("/kill", new String[] {});
        commandArguments.put("/noclip", new String[] {});
        commandArguments.put("/speed", new String[] {});
        commandArguments.put("/heal", new String[] {});
        commandArguments.put("/fly", new String[] {});
        commandArguments.put("/spawn", new String[] {});
        commandArguments.put("/ping", new String[] {});
        commandArguments.put("/template", new String[] {});
        commandArguments.put("/debug", new String[] {});
    }

    /**
     * Get all available command names
     */
    public String[] getAvailableCommands() {
        return commandArguments.keySet().toArray(new String[0]);
    }

    /**
     * Get argument completion options for a specific command
     */
    public String[] getCommandArguments(String command) {
        return commandArguments.get(command.toLowerCase());
    }

    /**
     * Get completion options based on full command context
     * For example: "/gamerule keepInventory " should suggest "true" or "false"
     */
    public String[] getContextualCompletions(String[] parts) {
        if (parts.length < 2) {
            return null;
        }

        String command = parts[0].toLowerCase();

        switch (command) {
            case "/gamerule":
                // If we have the rule name, suggest true/false
                if (parts.length == 2) {
                    return new String[] { "true", "false" };
                }
                break;

            case "/time":
                // If we have the action (set/add), suggest common time values
                if (parts.length == 2) {
                    String action = parts[1].toLowerCase();
                    if (action.equals("set")) {
                        return new String[] { "0", "6000", "12000", "18000" }; // dawn, noon, dusk, midnight
                    } else if (action.equals("add")) {
                        return new String[] { "1000", "6000", "12000" }; // Common increments
                    }
                }
                break;
        }

        return null;
    }

    /**
     * Execute a command or send a chat message
     * 
     * @param input           The command string
     * @param executingPlayer The player executing the command (null = use host
     *                        player)
     */
    public void executeCommand(String input, mc.sayda.mcraze.entity.Player executingPlayer) {
        if (input == null || input.trim().isEmpty()) {
            return;
        }

        // Default to host player if no player specified
        if (executingPlayer == null) {
            executingPlayer = server.player;
        }

        String[] parts = input.trim().split("\\s+");
        String command = parts[0].toLowerCase();

        if (!command.startsWith("/")) {
            // Regular chat message - broadcast to all players
            String username = executingPlayer != null ? executingPlayer.username : "Unknown";
            sendMessage("<" + username + "> " + input, Color.white);
            System.out.println("[CHAT] " + username + ": " + input);
            return;
        }

        // Echo the command - broadcast so executor sees it
        String username = executingPlayer != null ? executingPlayer.username : "Unknown";
        sendMessage("> [" + username + "] " + input, Color.lightGray);

        // Remove the "/" prefix
        command = command.substring(1);

        switch (command) {
            case "help":
                showHelp();
                break;
            case "gamerule":
                handleGamerule(parts);
                break;
            case "give":
                handleGive(parts, executingPlayer);
                break;
            case "we":
                handleWorldEdit(parts, executingPlayer);
                break;
            case "teleport":
            case "tp":
                handleTeleport(parts, executingPlayer);
                break;
            case "time":
                handleTime(parts);
                break;
            case "kill":
                handleKill(executingPlayer);
                break;
            case "noclip":
                handleNoclip(executingPlayer);
                break;
            case "godmode":
                handleGodmode(executingPlayer);
                break;
            case "reload":
                handleReload(executingPlayer);
                break;
            case "speed":
                handleSpeed(parts, executingPlayer);
                break;
            case "heal":
                handleHeal(parts, executingPlayer);
                break;
            case "fly":
                handleFly(parts, executingPlayer);
                break;
            case "spawn":
                handleSpawn(parts, executingPlayer);
                break;
            case "ping":
                handlePing(parts, executingPlayer);
                break;
            case "template":
                handleTemplate(parts, executingPlayer);
                break;
            case "summon":
                handleSummon(parts, executingPlayer);
                break;

            case "debug":
                handleDebug(parts, executingPlayer);
                break;
            default:
                sendMessage("Unknown command: /" + command, new Color(255, 100, 100));
                sendMessage("Type /help for a list of commands", Color.gray);
        }
    }

    /**
     * Execute a command with default player (host)
     * 
     * @deprecated Use executeCommand(String, Player) instead
     */
    @Deprecated
    public void executeCommand(String input) {
        executeCommand(input, null);
    }

    private void showHelp() {
        sendMessage("=== Available Commands ===", Color.orange);
        sendMessage("/gamerule <rule> [value] - Get/set game rules", Color.white);
        sendMessage("/give <item> [amount] - Give yourself items", Color.white);
        sendMessage("/teleport <x> <y> - Teleport to coordinates", Color.white);
        sendMessage("/time <set|add> <value> - Manage world time", Color.white);
        sendMessage("/noclip - Toggle noclip mode (fly + ghost through blocks)", Color.white);
        sendMessage("/godmode - Toggle godmode (invincibility)", Color.white);
        sendMessage("/reload - Reload world lighting (fixes visual glitches)", Color.white);
        sendMessage("/speed <multiplier> - Set movement speed (1.0 = normal)", Color.white);
        sendMessage("/debug - Enables some options useful for debugging", Color.white);
        sendMessage("/kill - Kill yourself (respawn)", Color.white);
        sendMessage("/help - Show this help", Color.white);
        sendMessage("/heal [amount] - Restore health", Color.white);
        sendMessage("/fly - Toggle flight mode", Color.white);
        sendMessage("/spawn - Teleport to world spawn", Color.white);
        sendMessage("/ping - Test server response", Color.white);
        sendMessage("/template - Spawns a structure at your position", Color.white);
        sendMessage("=== WE Commands ===", Color.orange);
        sendMessage("/we set <tile> <x> <y> - Place a block", Color.white);
        sendMessage("/we fill <tile> <x1> <y1> <x2> <y2> - Fill area", Color.white);
        sendMessage("/we replace <from> <to> <x1> <y1> <x2> <y2> - Replace nearby blocks", Color.white);
    }

    private void handleGamerule(String[] parts) {
        if (parts.length < 2) {
            sendMessage("Usage: /gamerule <rule> [value]", new Color(255, 200, 100));
            sendMessage("Available rules:", Color.gray);
            sendMessage("  keepInventory - Keep items on death (true/false)", Color.gray);
            sendMessage("  daylightCycle - Enable day/night cycle (true/false)", Color.gray);
            sendMessage("  spelunking - Disable darkness (true/false)", Color.gray);
            return;
        }

        String rule = parts[1].toLowerCase();

        // Get value
        if (parts.length == 2) {
            switch (rule) {
                case "keepinventory":
                    sendMessage("keepInventory = " + server.world.keepInventory, Color.green);
                    break;
                case "daylightcycle":
                    sendMessage("daylightCycle = " + server.world.daylightCycle, Color.green);
                    break;
                case "spelunking":
                    sendMessage("spelunking = " + server.world.spelunking, Color.green);
                    break;
                default:
                    sendMessage("Unknown gamerule: " + rule, new Color(255, 100, 100));
            }
            return;
        }

        // Set value
        String value = parts[2].toLowerCase();
        boolean boolValue;

        if (value.equals("true") || value.equals("1")) {
            boolValue = true;
        } else if (value.equals("false") || value.equals("0")) {
            boolValue = false;
        } else {
            sendMessage("Invalid value. Use: true/false or 1/0", new Color(255, 100, 100));
            return;
        }

        switch (rule) {
            case "keepinventory":
                server.world.keepInventory = boolValue;
                sendMessage("Set keepInventory to " + boolValue, Color.green);
                if (sharedWorld != null)
                    sharedWorld.broadcastGamerules(); // Sync to all clients
                break;
            case "daylightcycle":
                server.world.daylightCycle = boolValue;
                sendMessage("Set daylightCycle to " + boolValue, Color.green);
                if (sharedWorld != null)
                    sharedWorld.broadcastGamerules(); // Sync to all clients
                break;
            case "spelunking":
                server.world.spelunking = boolValue;
                sendMessage("Set spelunking to " + boolValue, Color.green);
                if (sharedWorld != null)
                    sharedWorld.broadcastGamerules(); // Sync to all clients
                break;
            default:
                sendMessage("Unknown gamerule: " + rule, new Color(255, 100, 100));
        }
    }

    private void handleTime(String[] parts) {
        if (parts.length < 3) {
            sendMessage("Usage: /time <set|add> <value>", new Color(255, 200, 100));
            sendMessage("Example: /time set 0 (dawn)", Color.gray);
            sendMessage("Example: /time set 6000 (noon)", Color.gray);
            sendMessage("Example: /time set 12000 (dusk)", Color.gray);
            return;
        }

        String action = parts[1].toLowerCase();

        try {
            long value = Long.parseLong(parts[2]);

            // Use SharedWorld to access world and broadcast changes
            mc.sayda.mcraze.world.World world = sharedWorld != null ? sharedWorld.getWorld() : server.world;

            if (world != null) {
                if (action.equals("set")) {
                    world.setTicksAlive(value);
                    sendMessage("Time set to " + value, Color.green);
                } else if (action.equals("add")) {
                    world.setTicksAlive(world.getTicksAlive() + value);
                    sendMessage("Added " + value + " to time", Color.green);
                } else {
                    sendMessage("Unknown action: " + action, new Color(255, 100, 100));
                    return;
                }

                // Broadcast world update to sync time change immediately
                if (sharedWorld != null) {
                    // Time will be broadcast in next world update packet automatically
                    // No need for manual broadcast - world updates include ticksAlive
                }
            }
        } catch (NumberFormatException e) {
            sendMessage("Invalid time value: " + parts[2], new Color(255, 100, 100));
        }
    }

    private void handleKill(mc.sayda.mcraze.entity.Player executingPlayer) {
        if (executingPlayer != null) {
            if (executingPlayer.dead) {
                sendMessage("You are already dead!", new Color(255, 100, 100));
            } else {
                executingPlayer.takeDamage(executingPlayer.hitPoints);
                // Force immediate death processing (don't wait for tick)
                if (executingPlayer.hitPoints <= 0 && !executingPlayer.dead) {
                    executingPlayer.dead = true;
                    // Find PlayerConnection and trigger death immediately
                    for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
                        if (pc.getPlayer() == executingPlayer) {
                            pc.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketPlayerDeath());
                            // Drop items
                            java.util.ArrayList<mc.sayda.mcraze.item.Item> droppedItems = executingPlayer
                                    .dropAllItems(new java.util.Random());
                            for (mc.sayda.mcraze.item.Item item : droppedItems) {
                                sharedWorld.addEntity(item);
                            }
                            sharedWorld.broadcastInventoryUpdates();
                            break;
                        }
                    }
                }
                sendMessage("Killed " + executingPlayer.username, Color.green);
            }
        }
    }

    private void handleGive(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
        if (parts.length < 2) {
            sendMessage("Usage: /give <item> [amount]", new Color(255, 200, 100));
            sendMessage("Example: /give dirt 64", Color.gray);
            sendMessage("Example: /give stone_pickaxe", Color.gray);
            return;
        }

        String itemId = parts[1].toLowerCase();
        int amount = 1;

        // Parse amount if provided
        if (parts.length >= 3) {
            try {
                amount = Integer.parseInt(parts[2]);
                if (amount <= 0) {
                    sendMessage("Amount must be positive", new Color(255, 100, 100));
                    return;
                }
            } catch (NumberFormatException e) {
                sendMessage("Invalid amount: " + parts[2], new Color(255, 100, 100));
                return;
            }
        }

        // Get the item from registry
        mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
        if (item == null) {
            sendMessage("Unknown item: " + itemId, new Color(255, 100, 100));
            sendMessage("Available items: dirt, stone, cobble, wood, plank, torch, etc.", Color.gray);
            return;
        }

        // Give the item to player
        if (executingPlayer != null && executingPlayer.inventory != null) {
            mc.sayda.mcraze.item.Item giveItem = item.clone();
            int remaining = executingPlayer.inventory.addItem(giveItem, amount);
            if (remaining == 0) {
                sendMessage("Gave " + amount + "x " + itemId + " to " + executingPlayer.username, Color.green);
            } else if (remaining < amount) {
                sendMessage("Gave " + (amount - remaining) + "x " + itemId + " to " + executingPlayer.username
                        + " (inventory full)", new Color(255, 200, 100));
            } else {
                sendMessage("Inventory full! Could not give any items to " + executingPlayer.username,
                        new Color(255, 100, 100));
            }

            // Broadcast inventory update to all clients
            if (sharedWorld != null) {
                sharedWorld.broadcastInventoryUpdates();
            }
        }
    }

    private void handleTeleport(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
        if (parts.length < 3) {
            sendMessage("Usage: /teleport <x> <y>", new Color(255, 200, 100));
            sendMessage("Example: /teleport 100 50", Color.gray);
            return;
        }

        try {
            int x = parseCoordinate(parts[1], executingPlayer.x);
            int y = parseCoordinate(parts[2], executingPlayer.y);

            if (executingPlayer != null) {
                // Check if coordinates are within world bounds
                if (server.world != null) {
                    if (x < 0 || x >= server.world.width || y < 0 || y >= server.world.height) {
                        sendMessage("Coordinates out of bounds! World size: " +
                                server.world.width + "x" + server.world.height, new Color(255, 100, 100));
                        return;
                    }
                }

                executingPlayer.x = x;
                executingPlayer.y = y;
                executingPlayer.dx = 0;
                executingPlayer.dy = 0;
                // Broadcast entity update immediately to all clients
                sharedWorld.broadcastEntityUpdate();
                sendMessage("Teleported " + executingPlayer.username + " to (" + x + ", " + y + ")", Color.green);
            }
        } catch (NumberFormatException e) {
            sendMessage("Invalid coordinates", new Color(255, 100, 100));
        }
    }

    private void handleNoclip(mc.sayda.mcraze.entity.Player executingPlayer) {
        if (executingPlayer != null) {
            // Toggle flying and noclip together
            executingPlayer.flying = !executingPlayer.flying;
            executingPlayer.noclip = executingPlayer.flying; // noclip follows flying state

            // Broadcast entity update immediately to all clients
            sharedWorld.broadcastEntityUpdate();

            if (executingPlayer.flying) {
                sendMessage("Noclip mode enabled for " + executingPlayer.username + " (ghost through blocks)",
                        Color.green);
            } else {
                sendMessage("Noclip mode disabled for " + executingPlayer.username, Color.green);
            }
        }
    }

    private void handleGodmode(mc.sayda.mcraze.entity.Player executingPlayer) {
        if (executingPlayer != null) {
            // Toggle godmode (invincibility)
            executingPlayer.godmode = !executingPlayer.godmode;

            // Broadcast entity update immediately to all clients
            sharedWorld.broadcastEntityUpdate();

            if (executingPlayer.godmode) {
                sendMessage("Godmode enabled for " + executingPlayer.username + " (invincibility)", Color.green);
            } else {
                sendMessage("Godmode disabled for " + executingPlayer.username, Color.green);
            }
        }
    }

    private void handleReload(mc.sayda.mcraze.entity.Player executingPlayer) {
        if (executingPlayer != null) {
            sendMessage("Reloading world lighting...", Color.orange);

            // Refresh lighting in background thread to prevent blocking
            new Thread(() -> {
                try {
                    sharedWorld.getWorld().refreshLighting();
                    sendMessage("Lighting reload complete!", Color.green);
                } catch (Exception e) {
                    sendMessage("Error reloading lighting: " + e.getMessage(), new Color(255, 100, 100));
                    e.printStackTrace();
                }
            }, "LightingReloadThread").start();
        }
    }

    private void handleSpeed(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
        if (parts.length < 2) {
            sendMessage("Usage: /speed <multiplier>", new Color(255, 200, 100));
            sendMessage("Example: /speed 2.0 (2x speed)", Color.gray);
            sendMessage("Example: /speed 1.0 (normal speed)", Color.gray);
            sendMessage("Example: /speed 0.5 (half speed)", Color.gray);
            return;
        }

        try {
            float multiplier = Float.parseFloat(parts[1]);
            if (multiplier <= 0) {
                sendMessage("Speed multiplier must be positive", new Color(255, 100, 100));
                return;
            }
            if (multiplier > 10) {
                sendMessage("Speed multiplier capped at 10x", new Color(255, 200, 100));
                multiplier = 10;
            }

            if (executingPlayer != null) {
                executingPlayer.speedMultiplier = multiplier;
                // Broadcast entity update immediately to all clients
                sharedWorld.broadcastEntityUpdate();
                sendMessage("Set speed to " + multiplier + "x for " + executingPlayer.username, Color.green);
            }
        } catch (NumberFormatException e) {
            sendMessage("Invalid speed multiplier: " + parts[1], new Color(255, 100, 100));
        }
    }

    private void handleHeal(String[] parts, Player executingPlayer) {
        if (executingPlayer == null)
            return;

        if (parts.length > 2) {
            sendMessage("Usage: /heal [amount]", new Color(255, 200, 100));
            sendMessage("Example: /heal 10", Color.gray);
            sendMessage("Example: /heal (full heal)", Color.gray);
            return;
        }

        int amount;

        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage("Invalid heal amount", new Color(255, 100, 100));
                return;
            }
        } else {
            // Full heal: only heal missing HP (overflow-safe)
            amount = executingPlayer.getMaxHP() - executingPlayer.hitPoints;
        }

        if (amount <= 0) {
            sendMessage("Already at full health", new Color(255, 200, 100));
            return;
        }

        executingPlayer.heal(amount);
        sharedWorld.broadcastEntityUpdate();

        sendMessage("Healed " + executingPlayer.username, Color.green);
    }

    private void handleFly(String[] parts, Player executingPlayer) {
        if (executingPlayer == null)
            return;

        if (parts.length > 1) {
            sendMessage("Usage: /fly - Toggle flight mode", new Color(255, 200, 100));
            return;
        }

        executingPlayer.flying = !executingPlayer.flying;
        sharedWorld.broadcastEntityUpdate();

        sendMessage(
                "Fly " + (executingPlayer.flying ? "enabled" : "disabled"),
                Color.green);
    }

    private void handleSpawn(String[] parts, Player executingPlayer) {
        if (executingPlayer == null || sharedWorld == null)
            return;

        if (parts.length > 1) {
            sendMessage("Usage: /spawn - Teleport to world spawn", new Color(255, 200, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();
        if (world == null || world.spawnLocation == null) {
            sendMessage("Spawn not available", new Color(255, 100, 100));
            return;
        }

        executingPlayer.x = world.spawnLocation.x;
        executingPlayer.y = world.spawnLocation.y;
        executingPlayer.dx = 0;
        executingPlayer.dy = 0;

        sharedWorld.broadcastEntityUpdate();
        sendMessage("Teleported to spawn", Color.green);
    }

    private void handlePing(String[] parts, Player executingPlayer) {
        if (parts.length > 1) {
            sendMessage("Usage: /ping - Test server response", new Color(255, 200, 100));
            return;
        }

        sendMessage("Pong!", Color.green);
    }

    private void handleTemplate(String[] parts, Player executingPlayer) {
        if (executingPlayer == null || sharedWorld == null)
            return;

        if (parts.length != 2) {
            sendMessage("Usage: /template <name>", new Color(255, 200, 100));
            sendMessage("Available: " + TileTemplate.REGISTRY.keySet(), Color.gray);
            return;
        }

        TileTemplate tpl = TileTemplate.get(parts[1]);
        if (tpl == null) {
            sendMessage("Unknown template: " + parts[1], new Color(255, 100, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();
        if (world == null)
            return;

        int x = Math.round(executingPlayer.x);
        int y = Math.round(executingPlayer.y) + 1; // correct for top-left origin

        world.placeTemplate(tpl, x, y);

        // CRITICAL FIX: Broadcast all template blocks to multiplayer clients
        // Templates can place many blocks at once (trees, dungeons, etc.)
        if (tpl.template != null) {
            for (int i = 0; i < tpl.template.length; i++) {
                for (int j = 0; j < tpl.template[0].length; j++) {
                    if (tpl.template[i][j] != mc.sayda.mcraze.Constants.TileID.NONE) {
                        int blockX = x - tpl.spawnY + i;
                        int blockY = y - tpl.spawnX + j;
                        if (blockX >= 0 && blockX < world.width && blockY >= 0 && blockY < world.height) {
                            sharedWorld.broadcastBlockChange(blockX, blockY, tpl.template[i][j]);
                        }
                    }
                }
            }
        }

        sendMessage("Placed template: " + parts[1], Color.green);
    }

    private void handleSummon(String[] parts, Player executingPlayer) {
        if (executingPlayer == null || sharedWorld == null) {
            return;
        }

        if (parts.length < 2) {
            sendMessage("Usage: /summon <id>", new Color(255, 200, 100));
            sendMessage("Example: /summon wheat_seeds", Color.gray);
            sendMessage("Example: /summon dirt", Color.gray);
            sendMessage("Example: /summon dummy", Color.gray);
            return;
        }

        String id = parts[1].toLowerCase();
        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        // Spawn position (slightly above player feet)
        float x = executingPlayer.x;
        float y = executingPlayer.y - 1;

        // ITEM SUMMON
        mc.sayda.mcraze.item.Item baseItem = Constants.itemTypes.get(id);
        if (baseItem != null) {
            mc.sayda.mcraze.item.Item item = (mc.sayda.mcraze.item.Item) baseItem.clone();
            item.x = x;
            item.y = y;
            item.dy = -0.1f;

            sharedWorld.addEntity(item);
            sendMessage("Summoned item: " + id, Color.green);
            return;
        }

        // DEBUG DUMMY ENTITY (temporary living entity)
        if (id.equals("dummy")) {
            LivingEntity dummy = new LivingEntity(true, x, y, 16, 16) {
            };
            dummy.sprite = mc.sayda.mcraze.SpriteStore.get()
                    .getSprite("sprites/entities/player.png");

            sharedWorld.addEntity(dummy);
            sendMessage("Summoned dummy entity", Color.green);
            return;
        }

        // SHEEP
        if (id.equals("sheep")) {
            mc.sayda.mcraze.entity.EntitySheep sheep = new mc.sayda.mcraze.entity.EntitySheep(x, y);
            sharedWorld.addEntity(sheep);
            sendMessage("Summoned sheep", Color.green);
            return;
        }

        // unknown entity
        sendMessage("Unknown summon id: " + id, new Color(255, 100, 100));
    }

    private static final int WE_BLOCK_LIMIT = 10_000;

    private void handleWorldEdit(String[] parts, Player executingPlayer) {
        if (sharedWorld == null || executingPlayer == null) {
            sendMessage("WorldEdit unavailable", new Color(255, 100, 100));
            return;
        }

        if (parts.length < 2) {
            sendMessage("WorldEdit commands:", Color.orange);
            sendMessage("/we set <block> <x> <y>", Color.gray);
            sendMessage("/we fill <block> <x1> <y1> <x2> <y2>", Color.gray);
            sendMessage("/we replace <from> <to> <x1> <y1> <x2> <y2>", Color.gray);
            return;
        }

        switch (parts[1].toLowerCase()) {
            case "set":
                handleWeSet(parts, executingPlayer);
                break;
            case "fill":
                handleWeFill(parts, executingPlayer);
                break;
            case "replace":
                handleWeReplace(parts, executingPlayer);
                break;
            case "sphere":
                handleWeSphere(parts, executingPlayer);
                break;
            case "undo":
                handleWeUndo(parts, executingPlayer);
                break;
            case "redo":
                handleWeRedo(parts, executingPlayer);
                break;

            default:
                sendMessage("Unknown /we command: " + parts[1], new Color(255, 100, 100));
        }
    }

    private void handleWeFill(String[] parts, Player executingPlayer) {
        if (parts.length < 7) {
            sendMessage("Usage: /we fill <block> <x1> <y1> <x2> <y2>", new Color(255, 200, 100));
            return;
        }

        if (sharedWorld == null || sharedWorld.getWorld() == null) {
            sendMessage("World not available", new Color(255, 100, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        mc.sayda.mcraze.Constants.TileID tileId;
        try {
            tileId = mc.sayda.mcraze.Constants.TileID.valueOf(parts[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendMessage("Unknown tile: " + parts[2], new Color(255, 100, 100));
            return;
        }

        int x1, y1, x2, y2;
        try {
            x1 = parseCoordinate(parts[3], executingPlayer.x);
            y1 = parseCoordinate(parts[4], executingPlayer.y);
            x2 = parseCoordinate(parts[5], executingPlayer.x);
            y2 = parseCoordinate(parts[6], executingPlayer.y);
        } catch (NumberFormatException e) {
            sendMessage("Invalid coordinates", new Color(255, 100, 100));
            return;
        }

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        int area = (maxX - minX + 1) * (maxY - minY + 1);
        if (area > WE_BLOCK_LIMIT) {
            sendMessage("Fill too large (" + area + " blocks, limit " + WE_BLOCK_LIMIT + ")", new Color(255, 100, 100));
            return;
        }

        int placed = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (x < 0 || x >= world.width || y < 0 || y >= world.height) {
                    continue;
                }

                // CRITICAL FIX: Use proper admin method that handles chests
                adminPlaceBlock(x, y, tileId);
                placed++;
            }
        }

        sendMessage("WorldEdit: Filled " + placed + " blocks with " + tileId, Color.green);
    }

    private void handleWeSet(String[] parts, Player executingPlayer) {
        // Expected: /we set <block> <x> <y>
        if (parts.length < 5) {
            sendMessage("Usage: /we set <block> <x> <y>", new Color(255, 200, 100));
            return;
        }

        int x, y;
        try {
            x = parseCoordinate(parts[3], executingPlayer.x);
            y = parseCoordinate(parts[4], executingPlayer.y);
        } catch (NumberFormatException e) {
            sendMessage("Invalid coordinates", new Color(255, 100, 100));
            return;
        }

        if (sharedWorld == null || sharedWorld.getWorld() == null) {
            sendMessage("World not available", new Color(255, 100, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        if (x < 0 || x >= world.width || y < 0 || y >= world.height) {
            sendMessage("Coordinates out of bounds", new Color(255, 100, 100));
            return;
        }

        mc.sayda.mcraze.Constants.TileID tileId;
        try {
            tileId = mc.sayda.mcraze.Constants.TileID.valueOf(parts[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendMessage("Unknown tile: " + parts[2], new Color(255, 100, 100));
            return;
        }

        // CRITICAL FIX: Use proper admin method that handles chests
        adminPlaceBlock(x, y, tileId);

        sendMessage(
                "Set block " + tileId + " at (" + x + ", " + y + ")",
                Color.green);
    }

    private void handleWeReplace(String[] parts, Player executingPlayer) {
        // /we replace <from> <to> <x1> <y1> <x2> <y2>
        if (parts.length < 8) {
            sendMessage("Usage: /we replace <from> <to> <x1> <y1> <x2> <y2>",
                    new Color(255, 200, 100));
            return;
        }

        if (sharedWorld == null || sharedWorld.getWorld() == null) {
            sendMessage("World not available", new Color(255, 100, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        mc.sayda.mcraze.Constants.TileID fromId;
        mc.sayda.mcraze.Constants.TileID toId;
        try {
            fromId = mc.sayda.mcraze.Constants.TileID.valueOf(parts[2].toUpperCase());
            toId = mc.sayda.mcraze.Constants.TileID.valueOf(parts[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendMessage("Unknown tile type", new Color(255, 100, 100));
            return;
        }

        int x1, y1, x2, y2;
        try {
            x1 = parseCoordinate(parts[4], executingPlayer.x);
            y1 = parseCoordinate(parts[5], executingPlayer.y);
            x2 = parseCoordinate(parts[6], executingPlayer.x);
            y2 = parseCoordinate(parts[7], executingPlayer.y);
        } catch (NumberFormatException e) {
            sendMessage("Invalid coordinates", new Color(255, 100, 100));
            return;
        }

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        int area = (maxX - minX + 1) * (maxY - minY + 1);
        if (area > WE_BLOCK_LIMIT) {
            sendMessage("Replace too large (" + area + " blocks, limit " + WE_BLOCK_LIMIT + ")",
                    new Color(255, 100, 100));
            return;
        }

        int replaced = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (x < 0 || x >= world.width || y < 0 || y >= world.height) {
                    continue;
                }

                mc.sayda.mcraze.Constants.TileID current = world.tiles[x][y].type.name;
                if (current != fromId) {
                    continue;
                }

                // CRITICAL FIX: Use proper admin method that handles chests
                adminPlaceBlock(x, y, toId);
                replaced++;
            }
        }

        sendMessage(
                "WorldEdit: Replaced " + replaced + " blocks (" + fromId + " -> " + toId + ")",
                Color.green);
    }

    private void handleWeSphere(String[] parts, Player executingPlayer) {
        // /we sphere <block> <cx> <cy> <radius>
        if (parts.length < 6) {
            sendMessage("Usage: /we sphere <block> <cx> <cy> <radius>",
                    new Color(255, 200, 100));
            return;
        }

        if (sharedWorld == null || sharedWorld.getWorld() == null) {
            sendMessage("World not available", new Color(255, 100, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();

        mc.sayda.mcraze.Constants.TileID tileId;
        try {
            tileId = mc.sayda.mcraze.Constants.TileID.valueOf(parts[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendMessage("Unknown tile: " + parts[2], new Color(255, 100, 100));
            return;
        }

        int cx, cy, r;
        try {
            cx = parseCoordinate(parts[3], executingPlayer.x);
            cy = parseCoordinate(parts[4], executingPlayer.y);
            r = Integer.parseInt(parts[5]);

        } catch (NumberFormatException e) {
            sendMessage("Invalid coordinates or radius", new Color(255, 100, 100));
            return;
        }

        if (r <= 0) {
            sendMessage("Radius must be positive", new Color(255, 100, 100));
            return;
        }

        int area = (int) (Math.PI * r * r);
        if (area > WE_BLOCK_LIMIT) {
            sendMessage("Sphere too large (" + area + " blocks, limit " + WE_BLOCK_LIMIT + ")",
                    new Color(255, 100, 100));
            return;
        }

        java.util.List<weChange> undo = new java.util.ArrayList<>();
        int placed = 0;

        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                if (x < 0 || x >= world.width || y < 0 || y >= world.height)
                    continue;

                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy > r * r)
                    continue;

                mc.sayda.mcraze.Constants.TileID before = world.tiles[x][y].type.name;
                if (before == tileId)
                    continue;

                undo.add(new weChange(x, y, before, tileId));

                // CRITICAL FIX: Use proper admin method that handles chests
                adminPlaceBlock(x, y, tileId);
                placed++;
            }
        }

        weUndo.put(executingPlayer, undo);
        weRedo.remove(executingPlayer);

        sendMessage("WorldEdit: Sphere placed (" + placed + " blocks)", Color.green);
    }

    // Simple WorldEdit undo/redo (single-level per player)
    private static class weChange {
        final int x, y;
        final mc.sayda.mcraze.Constants.TileID before;
        final mc.sayda.mcraze.Constants.TileID after;

        weChange(int x, int y,
                mc.sayda.mcraze.Constants.TileID before,
                mc.sayda.mcraze.Constants.TileID after) {
            this.x = x;
            this.y = y;
            this.before = before;
            this.after = after;
        }
    }

    private java.util.Map<Player, java.util.List<weChange>> weUndo = new java.util.HashMap<>();
    private java.util.Map<Player, java.util.List<weChange>> weRedo = new java.util.HashMap<>();

    private void handleWeUndo(String[] parts, Player executingPlayer) {
        java.util.List<weChange> undo = weUndo.get(executingPlayer);
        if (undo == null || undo.isEmpty()) {
            sendMessage("Nothing to undo", new Color(255, 200, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();
        java.util.List<weChange> redo = new java.util.ArrayList<>();

        for (weChange c : undo) {
            redo.add(new weChange(c.x, c.y, c.after, c.before));

            // CRITICAL FIX: Use proper admin method that handles chests
            if (c.before != mc.sayda.mcraze.Constants.TileID.AIR) {
                adminPlaceBlock(c.x, c.y, c.before);
            } else {
                adminBreakBlock(c.x, c.y);
                sharedWorld.broadcastBlockChange(c.x, c.y, Constants.TileID.AIR);
            }
        }

        weUndo.remove(executingPlayer);
        weRedo.put(executingPlayer, redo);

        sendMessage("WorldEdit: Undo complete (" + undo.size() + " blocks)", Color.green);
    }

    private void handleWeRedo(String[] parts, Player executingPlayer) {
        java.util.List<weChange> redo = weRedo.get(executingPlayer);
        if (redo == null || redo.isEmpty()) {
            sendMessage("Nothing to redo", new Color(255, 200, 100));
            return;
        }

        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();
        java.util.List<weChange> undo = new java.util.ArrayList<>();

        for (weChange c : redo) {
            undo.add(new weChange(c.x, c.y, c.before, c.after));

            // CRITICAL FIX: Use proper admin method that handles chests
            if (c.after != mc.sayda.mcraze.Constants.TileID.AIR) {
                adminPlaceBlock(c.x, c.y, c.after);
            } else {
                adminBreakBlock(c.x, c.y);
                sharedWorld.broadcastBlockChange(c.x, c.y, Constants.TileID.AIR);
            }
        }

        weRedo.remove(executingPlayer);
        weUndo.put(executingPlayer, undo);

        sendMessage("WorldEdit: Redo complete (" + redo.size() + " blocks)", Color.green);
    }

    private void handleDebug(String[] parts, Player executingPlayer) {
        if (executingPlayer == null) {
            sendMessage("Debug command requires a player context", new Color(255, 100, 100));
            return;
        }

        boolean debugActive = executingPlayer.debugMode;

        // -----------------------------------------
        // CASE 1: /debug <number>
        // -----------------------------------------
        if (parts.length >= 2) {
            float speed;

            try {
                speed = Float.parseFloat(parts[1]);
                if (speed <= 0) {
                    sendMessage("Speed must be positive", new Color(255, 100, 100));
                    return;
                }
            } catch (NumberFormatException e) {
                sendMessage("Invalid speed value: " + parts[1], new Color(255, 100, 100));
                return;
            }

            if (!debugActive) {
                // Enable full debug mode
                executingPlayer.debugMode = true;
                executingPlayer.flying = true;
                executingPlayer.noclip = true;
                server.world.spelunking = true;
            }

            // Always update speed
            executingPlayer.speedMultiplier = speed;

            // Sync
            sharedWorld.broadcastEntityUpdate();
            sharedWorld.broadcastGamerules();

            sendMessage("Debug mode " +
                    (debugActive ? "updated" : "enabled") +
                    " (speed " + speed + "x)", Color.green);
            return;
        }

        // -----------------------------------------
        // CASE 2: /debug (no number)
        // -----------------------------------------
        if (debugActive) {
            executingPlayer.debugMode = false;

            executingPlayer.flying = false;
            executingPlayer.noclip = false;
            executingPlayer.speedMultiplier = 1.0f;
            server.world.spelunking = false;

            sharedWorld.broadcastEntityUpdate();
            sharedWorld.broadcastGamerules();

            sendMessage("Debug mode disabled (defaults restored)", Color.green);
        } else {
            // ENABLE default debug
            executingPlayer.debugMode = true;
            executingPlayer.flying = true;
            executingPlayer.noclip = true;
            executingPlayer.speedMultiplier = 10.0f;
            server.world.spelunking = true;

            sharedWorld.broadcastEntityUpdate();
            sharedWorld.broadcastGamerules();

            sendMessage("Debug mode enabled (speed 10x)", Color.green);
        }
    }

    private int parseCoordinate(String token, float current) throws NumberFormatException {
        token = token.trim();

        // Relative coordinate
        if (token.startsWith("~")) {
            if (token.length() == 1) {
                // "~"
                return Math.round(current);
            }

            // "~+1", "~-2", "~5"
            String offset = token.substring(1);
            int delta = offset.isEmpty() ? 0 : Integer.parseInt(offset);
            return Math.round(current) + delta;
        }

        // Absolute coordinate
        return Integer.parseInt(token);
    }

}
