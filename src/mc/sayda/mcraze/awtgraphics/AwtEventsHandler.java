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
	 * Check if we're in multiplayer mode (not integrated server)
	 */
	private boolean isMultiplayerMode() {
		// In multiplayer, the client's connection is a NetworkConnection, not a LocalConnection
		return game.getClient() != null &&
			   game.getClient().connection != null &&
			   game.getClient().connection instanceof mc.sayda.mcraze.network.NetworkConnection;
	}

	/**
	 * Check if we should use packet-based input (true for multiplayer clients AND LAN hosts)
	 */
	private boolean shouldUsePackets() {
		// Use packets if:
		// 1. We're a multiplayer client (NetworkConnection), OR
		// 2. We're a LAN host (LocalConnection but LAN is enabled)
		if (isMultiplayerMode()) {
			return true;  // Multiplayer client
		}

		// Check if we're a LAN host
		if (game.getServer() != null && game.getServer().isLANEnabled()) {
			return true;  // LAN host - use packets for consistency with clients
		}

		return false;  // Pure singleplayer - can use direct manipulation
	}

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

		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		int hotbarSlot = (player != null && player.inventory != null) ? player.inventory.hotbarIdx : 0;

		mc.sayda.mcraze.network.packet.PacketPlayerInput packet = new mc.sayda.mcraze.network.packet.PacketPlayerInput(
			moveLeft,
			moveRight,
			climb,
			game.getClient().leftClick,
			game.getClient().rightClick,
			game.getClient().screenMousePos.x,
			game.getClient().screenMousePos.y,
			hotbarSlot
		);

		game.getClient().connection.sendPacket(packet);
	}
	
	private class MouseWheelInputHander implements MouseWheelListener {
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			// Get the correct player (works in both SP and MP)
			mc.sayda.mcraze.entity.Player player = getLocalPlayer();
			if (player != null) {
				player.scrollHotbar(e.getWheelRotation());
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

			// Handle pause menu separately
			if (game.getClient().isInPauseMenu()) {
				// ESC closes pause menu
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					game.getClient().closePauseMenu();
					e.consume();
				}
				return;
			}

			// Don't process game input if chat is open
			if (game.getClient().chat.isOpen()) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					game.submitChat();
				} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					game.getClient().chat.setOpen(false);
					e.consume();  // Prevent ESC from also triggering pause menu
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

			// In pure singleplayer, check if player exists
			if (!shouldUsePackets() && game.getServer().player == null) return;

			switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_SPACE:
				climb = true;
				if (shouldUsePackets()) {
					sendInputPacket();
				} else {
					game.getServer().player.startClimb();
				}
				break;
			case KeyEvent.VK_A:
				moveLeft = true;
				if (shouldUsePackets()) {
					sendInputPacket();
				} else {
					game.getServer().player.startLeft(e.isShiftDown());
				}
				break;
			case KeyEvent.VK_D:
				moveRight = true;
				if (shouldUsePackets()) {
					sendInputPacket();
				} else {
					game.getServer().player.startRight(e.isShiftDown());
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

			// In pure singleplayer, check if player exists
			if (!shouldUsePackets() && game.getServer().player == null) return;

			switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_SPACE:
				climb = false;
				if (shouldUsePackets()) {
					sendInputPacket();
				} else {
					game.getServer().player.endClimb();
				}
				break;
			case KeyEvent.VK_A:
				moveLeft = false;
				if (shouldUsePackets()) {
					sendInputPacket();
				} else {
					game.getServer().player.stopLeft();
				}
				break;
			case KeyEvent.VK_D:
				moveRight = false;
				if (shouldUsePackets()) {
					sendInputPacket();
				} else {
					game.getServer().player.stopRight();
				}
				break;
			case KeyEvent.VK_ESCAPE:
				// Close inventory if open, otherwise open pause menu
				mc.sayda.mcraze.entity.Player player = getLocalPlayer();
				if (player != null && player.inventory.isVisible()) {
					player.inventory.setVisible(false);
				} else {
					// Autosave before opening pause menu (only in singleplayer)
					if (game.getServer() != null) {
						game.saveGame();
					}
					// Open pause menu
					game.getClient().openPauseMenu();
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
				player.setHotbarItem(c - '1');
				break;
			case '0':
				player.setHotbarItem(9);
				break;
			case 'e':
				player.inventory.setVisible(!player.inventory.isVisible());
				break;
			case '=':
				// TODO: Implement zoom
				break;
			case 'p':
				// TODO: Implement pause
				break;
			case 'm':
                // TODO: Input does not appear to be an Ogg bitstream.
				game.getClient().musicPlayer.toggleSound();
				break;
			case 'o':
				// TODO: Implement zoom reset
				break;
			case '-':
				// TODO: Implement zoom out
				break;
			case 'f':
				game.getClient().toggleFPS();
				break;
			case 'q':
				// Toss item - this needs to go through server for multiplayer sync
				if (game.getServer() != null) {
					game.getServer().tossItem();
				}
				break;
			case 'r':
				// Respawn if player is dead
				mc.sayda.mcraze.entity.Player respawnPlayer = getLocalPlayer();
				if (respawnPlayer != null && respawnPlayer.dead) {
					if (game.getServer() != null) {
						game.getServer().respawnPlayer();
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
