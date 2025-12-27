package mc.sayda.mcraze.awtgraphics;

import java.awt.Canvas;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import mc.sayda.mcraze.Game;

public class AwtEventsHandler {
	Game game;

	// Track input state for multiplayer
	private boolean moveLeft = false;
	private boolean moveRight = false;
	private boolean climb = false;
	private boolean sneak = false;
	private int desiredHotbarSlot = 0;  // Client's desired hotbar slot (sent to server)
	private boolean escUsedToCloseMenu = false;  // Track if ESC just closed a menu (prevent double-trigger)

	public AwtEventsHandler(Game game, Canvas canvas) {
		this.game = game;

		// Disable TAB focus traversal so we can use TAB for chat completion
		canvas.setFocusTraversalKeysEnabled(false);

		// add a key input system (defined below) to our canvas
		// so we can respond to key pressed
		canvas.addKeyListener(new KeyInputHandler());
		canvas.addMouseListener(new MouseInputHander());
		canvas.addMouseWheelListener(new MouseWheelInputHander());
		canvas.addMouseMotionListener(new MouseMoveInputHander());
	}

	/**
	 * NOTE: We ALWAYS use packets for ALL input, whether integrated server or dedicated server.
	 * There is NO "singleplayer" mode - only integrated servers with LAN disabled.
	 * The shouldUsePackets() method has been removed - packets are mandatory.
	 */

	/**
	 * Get the local player (works in both singleplayer and multiplayer)
	 */
	private mc.sayda.mcraze.entity.Player getLocalPlayer() {
		mc.sayda.mcraze.server.Server server = game.getServer();
		mc.sayda.mcraze.client.Client client = game.getClient();

		if (server != null && server.player != null) {
			return server.player;  // Singleplayer or LAN host
		} else if (client != null) {
			return client.getLocalPlayer();  // Multiplayer client
		}
		return null;
	}

	/**
	 * Send input packet to server (multiplayer mode)
	 */
	public void sendInputPacket() {
		if (game.getClient() == null) return;

		// Use desired hotbar slot instead of reading from player
		// Server will apply and broadcast the change
		mc.sayda.mcraze.network.packet.PacketPlayerInput packet = new mc.sayda.mcraze.network.packet.PacketPlayerInput(
			moveLeft,
			moveRight,
			climb,
			sneak,
			game.getClient().leftClick,
			game.getClient().rightClick,
			game.getClient().screenMousePos.x,
			game.getClient().screenMousePos.y,
			desiredHotbarSlot
		);

		game.getClient().connection.sendPacket(packet);
	}
	
	private class MouseWheelInputHander implements MouseWheelListener {
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			int scroll = e.getWheelRotation();

			// If main menu is open, pass wheel event to it
			if (game.getClient() != null && game.getClient().isInMenu() && game.getClient().getMenu() != null) {
				game.getClient().getMenu().handleMouseWheel(
					game.getClient().screenMousePos.x,
					game.getClient().screenMousePos.y,
					scroll
				);
			} else {
				// Update desired hotbar slot and send immediately
				desiredHotbarSlot = Math.max(0, Math.min(9, desiredHotbarSlot + scroll));
				// Send packet immediately so server gets hotbar change even when player is standing still
				sendInputPacket();
			}
		}
	}

	private class MouseMoveInputHander implements MouseMotionListener {
		@Override
		public void mouseDragged(MouseEvent arg0) {
			if (game.getClient() != null) {
				game.getClient().setMousePosition(arg0.getX(), arg0.getY());
			}
		}

		@Override
		public void mouseMoved(MouseEvent arg0) {
			if (game.getClient() != null) {
				game.getClient().setMousePosition(arg0.getX(), arg0.getY());
			}
		}
	}
	
	private class MouseInputHander extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent arg0) {
			if (game.getClient() == null) return;
			switch (arg0.getButton()) {
			case MouseEvent.BUTTON1:
				game.getClient().setLeftClick(true);
				break;
			case MouseEvent.BUTTON2: // fall through
			case MouseEvent.BUTTON3:
				game.getClient().setRightClick(true);
				break;
			}
		}

		@Override
		public void mouseReleased(MouseEvent arg0) {
			if (game.getClient() == null) return;
			switch (arg0.getButton()) {
			case MouseEvent.BUTTON1:
				game.getClient().setLeftClick(false);
				break;
			case MouseEvent.BUTTON2: // fall through
			case MouseEvent.BUTTON3:
				game.getClient().setRightClick(false);
				break;
			}
		}
	}
	
	private class KeyInputHandler extends KeyAdapter {
		/**
		 * Notification from AWT that a key has been pressed. Note that
		 * a key being pressed is equal to being pushed down but *NOT*
		 * released. Thats where keyTyped() comes in.
		 * 
		 * @param e
		 *            The details of the key that was pressed
		 */
		@Override
		public void keyPressed(KeyEvent e) {
			if (game.getClient() == null) return;

			// Don't process input if in main menu
			if (game.getClient().isInMenu()) {
				return;
			}

			// Handle ESC key for menus - check in priority order
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				// Priority 1: Close settings menu if open
				if (game.getClient().isInSettingsMenu()) {
					game.getClient().closeSettingsMenu();
					escUsedToCloseMenu = true;  // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 2: Close pause menu if open
				if (game.getClient().isInPauseMenu()) {
					game.getClient().closePauseMenu();
					escUsedToCloseMenu = true;  // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 3: Close chat if open
				if (game.getClient().chat.isOpen()) {
					game.getClient().chat.setOpen(false);
					escUsedToCloseMenu = true;  // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 4: Close chest UI if open
				if (game.getClient().isChestUIOpen()) {
					game.getClient().closeChestUI();
					escUsedToCloseMenu = true;  // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 5: Close inventory if open
				mc.sayda.mcraze.entity.Player player = getLocalPlayer();
				if (player != null && player.inventory.isVisible()) {
					player.inventory.setVisible(false);
					escUsedToCloseMenu = true;  // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 6: Open pause menu if nothing is open (handled below in keyReleased)
				// Reset flag if no menu was closed
				escUsedToCloseMenu = false;
			}

			// Handle pause menu input (but not ESC - already handled above)
			if (game.getClient().isInPauseMenu()) {
				// Pause menu is open, don't process game input
				return;
			}

			// Handle chat input
			if (game.getClient().chat.isOpen()) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					game.submitChat();
				} else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
					game.getClient().chat.backspace();
				} else if (e.getKeyCode() == KeyEvent.VK_TAB) {
					game.getClient().chat.tabComplete();
					e.consume();  // Prevent tab from changing focus
				} else if (e.getKeyCode() == KeyEvent.VK_UP) {
					game.getClient().chat.historyUp();
					e.consume();  // Prevent default behavior
				} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					game.getClient().chat.historyDown();
					e.consume();  // Prevent default behavior
				}
				return;
			}

			// ALWAYS use packets for input (integrated or dedicated server)
			switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_SPACE:
				climb = true;
				sendInputPacket();
				break;
			case KeyEvent.VK_A:
				moveLeft = true;
				sendInputPacket();
				break;
			case KeyEvent.VK_D:
				moveRight = true;
				sendInputPacket();
				break;
			case KeyEvent.VK_SHIFT:
				sneak = true;
				sendInputPacket();
				break;
			case KeyEvent.VK_F3:
				// Toggle debug overlay (F3)
				if (game.getClient() != null && game.getClient().getDebugOverlay() != null) {
					game.getClient().getDebugOverlay().toggle();
				}
				break;
			}
		}
		
		/**
		 * Notification from AWT that a key has been released.
		 * 
		 * @param e
		 *            The details of the key that was released
		 */
		@Override
		public void keyReleased(KeyEvent e) {
			if (game.getClient() == null) return;

			// Don't process input if in main menu
			if (game.getClient().isInMenu()) {
				return;
			}

			// Don't process input if pause menu is open
			if (game.getClient().isInPauseMenu()) {
				return;
			}

			// Don't process game input if chat is open
			if (game.getClient().chat.isOpen()) {
				return;
			}

			// ALWAYS use packets for input (integrated or dedicated server)
			switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_SPACE:
				climb = false;
				sendInputPacket();
				break;
			case KeyEvent.VK_A:
				moveLeft = false;
				sendInputPacket();
				break;
			case KeyEvent.VK_D:
				moveRight = false;
				sendInputPacket();
				break;
			case KeyEvent.VK_SHIFT:
				sneak = false;
				sendInputPacket();
				break;
			case KeyEvent.VK_ESCAPE:
				// Skip if ESC just closed a menu (prevent double-trigger)
				if (escUsedToCloseMenu) {
					escUsedToCloseMenu = false;  // Reset flag
					break;
				}

				// Open pause menu if nothing else is open
				// (Closing inventory/settings/pause/chat/chest is handled in keyPressed and above)
				if (!game.getClient().isInPauseMenu() &&
				    !game.getClient().isInSettingsMenu() &&
				    !game.getClient().chat.isOpen() &&
				    !game.getClient().isChestUIOpen()) {
					mc.sayda.mcraze.entity.Player player = getLocalPlayer();
					// Only if inventory is also not visible
					if (player == null || !player.inventory.isVisible()) {
						// Autosave before opening pause menu (only for integrated servers)
						if (game.getServer() != null) {
							game.saveGame();
						}
						// Open pause menu
						game.getClient().openPauseMenu();
					}
				}
				break;
			}
		}
		
		@Override
		public void keyTyped(KeyEvent e) {
			if (game.getClient() == null) return;

			char c = e.getKeyChar();

			// Handle login screen typing (highest priority)
			if (game.isShowingLoginScreen()) {
				mc.sayda.mcraze.ui.LoginScreen loginScreen = game.getLoginScreen();
				if (loginScreen != null) {
					loginScreen.handleKeyTyped(c);
				}
				return;
			}

			// Handle menu typing (when in main menu)
			if (game.getClient().isInMenu()) {
				game.getClient().getMenu().handleKeyTyped(c);
				return;
			}

			// Allow commands in multiplayer mode
			mc.sayda.mcraze.server.Server server = game.getServer();
			mc.sayda.mcraze.client.Client client = game.getClient();

			// Handle chat typing (works in both SP and MP)
			if (client != null && client.chat.isOpen()) {
				// Allow typing printable characters
				if (c >= 32 && c <= 126) {
					client.chat.typeChar(c);
				}
				return;
			}

			// For multiplayer, use client's tracked player instead of server.player
			mc.sayda.mcraze.entity.Player player = null;
			if (server != null) {
				player = server.player;  // Singleplayer
			} else if (client != null) {
				player = client.getLocalPlayer();  // Multiplayer
			}

			if (player == null) return;  // No player to control

			// Game commands
			switch (c) {
			case '1':
			case '2': // these all fall through to 9
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				// Update desired hotbar slot and send immediately
				desiredHotbarSlot = c - '1';
				sendInputPacket();
				break;
			case '0':
				// Update desired hotbar slot and send immediately
				desiredHotbarSlot = 9;
				sendInputPacket();
				break;
			case 'e':
				player.inventory.setVisible(!player.inventory.isVisible());
				break;
			case '=':
			case '+':
				// Zoom in
				game.getClient().zoomIn();
				break;
			case 'p':
				// TODO: Implement pause
				break;
			case 'm':
                // TODO: Input does not appear to be an Ogg bitstream.
				game.getClient().musicPlayer.toggleSound();
				break;
			case 'b':
			case 'B':
				// Toggle backdrop placement mode - send packet to server
				if (game.getClient() != null && game.getClient().connection != null) {
					mc.sayda.mcraze.network.packet.PacketToggleBackdropMode packet =
						new mc.sayda.mcraze.network.packet.PacketToggleBackdropMode();
					game.getClient().connection.sendPacket(packet);
				}
				break;
			case 'c':
				// Reset zoom to default
				game.getClient().resetZoom();
				break;
			case '-':
			case '_':
				// Zoom out
				game.getClient().zoomOut();
				break;
			case 'f':
				game.getClient().toggleFPS();
				break;
			case 'q':
				// Toss item - send packet to server (works in singleplayer and multiplayer)
				if (game.getClient() != null && game.getClient().connection != null) {
					mc.sayda.mcraze.network.packet.PacketItemToss tossPacket =
						new mc.sayda.mcraze.network.packet.PacketItemToss();
					game.getClient().connection.sendPacket(tossPacket);
				}
				break;
			case 'r':
				// Respawn if player is dead - send packet to server
				// Architecture: ALL player actions use packets, even in integrated server with LAN disabled
				mc.sayda.mcraze.entity.Player respawnPlayer = getLocalPlayer();
				if (respawnPlayer != null && respawnPlayer.dead) {
					if (game.getClient() != null && game.getClient().connection != null) {
						mc.sayda.mcraze.network.packet.PacketRespawn respawnPacket =
							new mc.sayda.mcraze.network.packet.PacketRespawn();
						game.getClient().connection.sendPacket(respawnPacket);
					}
				}
				break;
			case 't':
			case 'T':
				// Open chat
				game.getClient().chat.setOpen(true);
				break;
			}
		}
	}
}
