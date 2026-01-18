package mc.sayda.mcraze.player.specialization.ui;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.player.specialization.PassiveEffectType;
import mc.sayda.mcraze.player.specialization.SkillPointManager;
import mc.sayda.mcraze.player.specialization.SpecializationPath;
import mc.sayda.mcraze.ui.component.Button;

import java.util.ArrayList;
import java.util.List;

public class SkillAssignmentUI {
    private final Game game;
    private boolean visible = false;
    private List<Button> buttons = new ArrayList<>();
    private List<PassiveEffectType> buttonAbilities = new ArrayList<>(); // Track which ability each button represents

    // Layout constants
    private static final int COL_WIDTH = 200;
    private static final int ROW_HEIGHT = 40;
    private static final int START_Y = 100;

    public SkillAssignmentUI(Game game) {
        this.game = game;
    }

    /**
     * Get the player instance - prefer server.player to ensure sync with commands
     */
    private Player getPlayer() {
        // First try server player (commands modify this directly)
        if (game.getServer() != null && game.getServer().player != null) {
            return game.getServer().player;
        }
        // Fallback to client's local player (for multiplayer)
        if (game.getClient() != null) {
            return game.getClient().getLocalPlayer();
        }
        return null;
    }

    public void refresh() {
        buttons.clear();
        buttonAbilities.clear();
        Player player = getPlayer();
        if (player == null)
            return;

        List<PassiveEffectType> abilities = SkillPointManager.getAbilitiesForClass(player.selectedClass);
        int screenWidth = GraphicsHandler.get().getScreenWidth();
        int startX = (screenWidth - (COL_WIDTH * 3)) / 2;

        // Group by path (simplified: assuming order in Enum matches 5 per path)
        for (int i = 0; i < abilities.size(); i++) {
            PassiveEffectType ability = abilities.get(i);
            buttonAbilities.add(ability);
            int col = i / 5; // 0, 1, 2
            int row = i % 5;

            int x = startX + (col * COL_WIDTH) + 10;
            int y = START_Y + (row * ROW_HEIGHT) + 40; // Offset for headers

            String label = ability.name().replace("_", " "); // Simple formatting
            // Check if unlocked
            boolean unlocked = player.unlockedPassives.contains(ability);
            if (unlocked) {
                label = "[X] " + label;
            }

            // Use new constructor with explicit X, Y, W, H
            Button btn = new Button("skill_" + i, label, x, y, 180, 30);

            // Disable button if already unlocked
            if (unlocked) {
                btn.setEnabled(false);
            }

            // Store index for onClick handler
            final int abilityIndex = i;

            // Logic to unlock - check state at click time, not at build time
            btn.setOnClick(() -> {
                Player p = getPlayer();
                if (p == null)
                    return;

                PassiveEffectType abilityToUnlock = buttonAbilities.get(abilityIndex);
                boolean alreadyUnlocked = p.unlockedPassives.contains(abilityToUnlock);

                if (!alreadyUnlocked && p.skillPoints > 0) {
                    // Networking: Send PacketSkillUpgrade
                    mc.sayda.mcraze.player.specialization.network.PacketSkillUpgrade packet = new mc.sayda.mcraze.player.specialization.network.PacketSkillUpgrade(
                            abilityToUnlock);
                    game.getClient().connection.sendPacket(packet);

                    // Client-side prediction (will be corrected by server sync if rejected)
                    p.unlockedPassives.add(abilityToUnlock);
                    p.skillPoints--;
                    refresh(); // Rebuild buttons with updated state
                }
            });

            buttons.add(btn);
        }
    }

    public void draw(GraphicsHandler g) {
        if (!visible)
            return;

        // Background
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, g.getScreenWidth(), g.getScreenHeight());

        Player player = getPlayer();
        if (player == null)
            return;

        g.setColor(Color.white);
        g.setFont("Dialog", GraphicsHandler.FONT_BOLD, 20);

        // Draw Points (always fetch fresh from player)
        String pointsText = "Skill Points: " + player.skillPoints;
        g.drawString(pointsText, 20, 30);

        // Draw Column Headers (Path Names)
        if (player.selectedPaths != null) {
            int screenWidth = g.getScreenWidth();
            int startX = (screenWidth - (COL_WIDTH * 3)) / 2;

            SpecializationPath[] allPaths = SpecializationPath.values();
            int pathCount = 0;
            for (SpecializationPath path : allPaths) {
                if (path.getParentClass() == player.selectedClass) {
                    if (pathCount >= 3)
                        break;
                    g.drawString(path.getDisplayName(), startX + (pathCount * COL_WIDTH) + 10, START_Y);
                    pathCount++;
                }
            }
        }

        // Draw Buttons (Abilities)
        int mouseX = game.getClient().screenMousePos.x;
        int mouseY = game.getClient().screenMousePos.y;

        for (Button btn : buttons) {
            // Button class now handles rendering at explicit coordinates
            btn.updateHover(mouseX, mouseY);
            btn.draw(g);
        }

        // Handle clicks
        if (game.getClient().leftClick) {
            for (Button btn : buttons) {
                if (btn.handleClick(mouseX, mouseY)) {
                    game.getClient().leftClick = false;
                    break;
                }
            }
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible)
            refresh(); // Refresh buttons on open
    }

    public boolean isVisible() {
        return visible;
    }
}
