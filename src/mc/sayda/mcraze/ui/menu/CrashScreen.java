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

package mc.sayda.mcraze.ui.menu;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.logging.CrashReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen displayed when a crash occurs
 */
public class CrashScreen {

    private String crashReportPath;
    private Throwable cause;
    private String title = "Game Crashed!";
    private List<String> logLines = new ArrayList<>();

    // UI State
    private int scrollOffset = 0;

    public CrashScreen(String crashReportPath, Throwable cause) {
        this.crashReportPath = crashReportPath;
        this.cause = cause;

        // Parse stack trace into lines for display
        if (cause != null) {
            logLines.add(cause.toString());
            for (StackTraceElement ste : cause.getStackTrace()) {
                logLines.add("  at " + ste.toString());
            }
            if (cause.getCause() != null) {
                logLines.add("Caused by: " + cause.getCause().toString());
                for (StackTraceElement ste : cause.getCause().getStackTrace()) {
                    logLines.add("  at " + ste.toString());
                }
            }
        } else {
            logLines.add("No exception details available.");
        }
    }

    public void tick() {
        // Handle input if we had input handling hooked up here
        // For simple implementation, we might just rely on AWT events or basic polling
        // in the loop
    }

    public void draw(GraphicsHandler g, int mouseX, int mouseY) {
        int w = g.getScreenWidth();
        int h = g.getScreenHeight();

        // Fallback to red background as requested
        g.setColor(new Color(139, 0, 0)); // Dark Red
        g.fillRect(0, 0, w, h);

        /*
         * // Dirt pattern background (like other menus)
         * mc.sayda.mcraze.graphics.Sprite dirtSprite =
         * mc.sayda.mcraze.graphics.SpriteStore.get()
         * .getSprite("assets/sprites/tiles/dirt.png");
         * if (dirtSprite != null) {
         * int tileSize = 32;
         * for (int x = 0; x < w; x += tileSize) {
         * for (int y = 0; y < h; y += tileSize) {
         * dirtSprite.draw(g, x, y, tileSize, tileSize);
         * }
         * }
         * } else {
         * // Fallback to dark background
         * g.setColor(new Color(40, 0, 0));
         * g.fillRect(0, 0, w, h);
         * }
         */

        // Header
        g.setColor(Color.white);
        String header = "GAME CRASHED";
        g.drawString(header, (w - g.getStringWidth(header)) / 2, 50);

        g.setColor(new Color(200, 200, 200));
        String subMsg = "We're sorry, but MCraze has encountered an error.";
        g.drawString(subMsg, (w - g.getStringWidth(subMsg)) / 2, 80);

        String pathMsg = "Crash report saved to: " + (crashReportPath != null ? crashReportPath : "Unknown");
        g.drawString(pathMsg, (w - g.getStringWidth(pathMsg)) / 2, 110);

        // Log Box Background
        int boxX = 50;
        int boxY = 150;
        int boxW = w - 100;
        int boxH = h - 250;

        g.setColor(new Color(0, 0, 0, 100));
        g.fillRect(boxX, boxY, boxW, boxH);

        g.setColor(new Color(255, 100, 100));
        g.drawRect(boxX, boxY, boxW, boxH);

        // Render Log Lines
        g.setColor(new Color(200, 200, 200));
        int clipY = boxY + 5;
        int lineHeight = 16;
        int maxLines = boxH / lineHeight;

        // Very basic rendering of lines
        for (int i = 0; i < Math.min(logLines.size(), maxLines); i++) {
            String line = logLines.get(i);
            // Truncate if too long
            if (g.getStringWidth(line) > boxW - 10) {
                line = line.substring(0, Math.min(line.length(), 100)) + "...";
            }
            g.drawString(line, boxX + 5, clipY + (i + 1) * lineHeight);
        }

        if (logLines.size() > maxLines) {
            g.drawString("... (" + (logLines.size() - maxLines) + " more lines) ...", boxX + 5,
                    clipY + (maxLines) * lineHeight);
        }

        // Buttons (Visual only for now, input handled in Game loop for loop)
        // Quit Button
        int btnW = 200;
        int btnH = 40;
        int btnX = (w - btnW) / 2;
        int btnY = h - 80;

        g.setColor(new Color(60, 60, 60));
        g.fillRect(btnX, btnY, btnW, btnH);
        g.setColor(Color.white);
        g.drawRect(btnX, btnY, btnW, btnH);

        String btnText = "Click Window Close [X] to Quit";
        g.drawString(btnText, btnX + (btnW - g.getStringWidth(btnText)) / 2, btnY + 25);
    }
}
