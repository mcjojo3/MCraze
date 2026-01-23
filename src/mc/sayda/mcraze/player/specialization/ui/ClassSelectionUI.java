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

package mc.sayda.mcraze.player.specialization.ui;

import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.player.specialization.network.PacketClassSelect;
import mc.sayda.mcraze.player.specialization.PlayerClass;
import mc.sayda.mcraze.player.specialization.SpecializationPath;
import mc.sayda.mcraze.ui.component.Button;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic UI for class selection.
 */
public class ClassSelectionUI {
    private final Game game;
    private final List<Button> buttons = new ArrayList<>();
    private boolean visible = false;

    public ClassSelectionUI(Game game) {
        this.game = game;
        buildButtons();
    }

    private void buildButtons() {
        buttons.clear();
        int startY = 150;
        int spacing = 50;

        buttons.add(new Button("vanguard", "Vanguard (Warrior)", startY, 200, 40)
                .setOnClick(() -> selectClass(PlayerClass.VANGUARD)));

        buttons.add(new Button("engineer", "Engineer (Builder)", startY + spacing, 200, 40)
                .setOnClick(() -> selectClass(PlayerClass.ENGINEER)));

        buttons.add(new Button("arcanist", "Arcanist (Mage)", startY + spacing * 2, 200, 40)
                .setOnClick(() -> selectClass(PlayerClass.ARCANIST)));

        buttons.add(new Button("druid", "Druid (Support)", startY + spacing * 3, 200, 40)
                .setOnClick(() -> selectClass(PlayerClass.DRUID)));
    }

    private void selectClass(PlayerClass pClass) {
        SpecializationPath[] paths = new SpecializationPath[0];
        if (pClass == PlayerClass.VANGUARD) {
            paths = new SpecializationPath[] { SpecializationPath.SENTINEL, SpecializationPath.CHAMPION,
                    SpecializationPath.BLACKSMITH };
        } else if (pClass == PlayerClass.ENGINEER) {
            paths = new SpecializationPath[] { SpecializationPath.MARKSMAN, SpecializationPath.TRAP_MASTER,
                    SpecializationPath.LUMBERJACK };
        } else if (pClass == PlayerClass.ARCANIST) {
            paths = new SpecializationPath[] { SpecializationPath.ELEMENTALIST, SpecializationPath.GUARDIAN_ANGEL,
                    SpecializationPath.ALCHEMIST };
        } else if (pClass == PlayerClass.DRUID) {
            paths = new SpecializationPath[] { SpecializationPath.CULTIVATOR, SpecializationPath.BEAST_TAMER,
                    SpecializationPath.CHEF };
        }

        PacketClassSelect packet = new PacketClassSelect(pClass, paths);
        game.getClient().connection.sendPacket(packet);

        // CRITICAL: Update local player class immediately to prevent Game.java
        // auto-reopen
        // The server will also update, but this prevents the race condition where
        // Game.java sees selectedClass==NONE and re-opens the UI before server responds
        mc.sayda.mcraze.player.Player localPlayer = game.getClient().getLocalPlayer();
        if (localPlayer != null) {
            localPlayer.selectClass(pClass, paths);
        }

        setVisible(false);
        // logger.info("ClassSelectionUI: Selected " + pClass.getDisplayName());
    }

    public void draw(GraphicsHandler g) {
        if (!visible)
            return;

        int screenWidth = g.getScreenWidth();
        int mouseX = game.getClient().screenMousePos.x;
        int mouseY = game.getClient().screenMousePos.y;

        g.setColor(new mc.sayda.mcraze.graphics.Color(0, 0, 0, 180));
        g.fillRect(0, 0, g.getScreenWidth(), g.getScreenHeight());

        g.setColor(mc.sayda.mcraze.graphics.Color.white);
        g.setFont("Dialog", GraphicsHandler.FONT_BOLD, 24);
        String title = "Choose Your Specialization";
        int titleX = (screenWidth - g.getStringWidth(title)) / 2;
        g.drawString(title, titleX, 100);

        for (Button button : buttons) {
            button.updatePosition(screenWidth);
            button.updateHover(mouseX, mouseY);
            button.draw(g);
        }

        if (game.getClient().leftClick) {
            for (Button button : buttons) {
                if (button.handleClick(mouseX, mouseY)) {
                    game.getClient().leftClick = false;
                    break;
                }
            }
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }
}
