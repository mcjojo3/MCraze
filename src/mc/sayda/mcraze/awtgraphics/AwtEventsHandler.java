package mc.sayda.mcraze.awtgraphics;

import java.awt.Canvas;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.FocusListener;
import java.awt.event.FocusEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.client.Client;
import mc.sayda.mcraze.logging.GameLogger;

public class AwtEventsHandler {
	Game game;

	// Track input state for multiplayer
	private boolean shiftPressed = false; // Track shift key for inventory QoL features
	private int desiredHotbarSlot = 0; // Client's desired hotbar slot (sent to server)
	private boolean escUsedToCloseMenu = false; // Track if ESC just closed a menu (prevent double-trigger)
	private final Client client;
	private final GameLogger logger = GameLogger.get();

	public AwtEventsHandler(Game game, Canvas canvas) {
		this.game = game;
		this.client = game.getClient(); // Initialize client from game

		// Disable TAB focus traversal so we can use TAB for chat completion
		canvas.setFocusTraversalKeysEnabled(false);

		// add a key input system (defined below) to our canvas
		// so we can respond to key pressed
		canvas.addKeyListener(new KeyInputHandler());
		canvas.addMouseListener(new MouseInputHander());
		canvas.addMouseWheelListener(new MouseWheelInputHander());
		canvas.addMouseMotionListener(new MouseMoveInputHander());
		canvas.addFocusListener(new FocusInputHandler());
	}

	/**
	 * NOTE: We ALWAYS use packets for ALL input, whether integrated server or
	 * dedicated server.
	 * There is NO "singleplayer" mode - only integrated servers with LAN disabled.
	 * The shouldUsePackets() method has been removed - packets are mandatory.
	 */

	/**
	 * Get the local player (works in both singleplayer and multiplayer)
	 */
	private mc.sayda.mcraze.player.Player getLocalPlayer() {
		mc.sayda.mcraze.server.Server server = game.getServer();
		// mc.sayda.mcraze.client.Client client = game.getClient(); // Use the field
		// instead

		if (server != null && server.player != null) {
			return server.player; // Singleplayer or LAN host
		} else if (client != null) {
			return client.getLocalPlayer(); // Multiplayer client
		}
		return null;
	}

	/**
	 * Check if shift key is currently pressed (for inventory QoL features)
	 */
	public boolean isShiftPressed() {
		return shiftPressed;
	}

	/**
	 * Send input packet to server (multiplayer mode)
	 */
	public void sendInputPacket() {
		// mc.sayda.mcraze.client.Client client = game.getClient(); // Use the field
		// instead
		if (client == null)
			return;

		// Ensure worldMouseX/Y are fresh for the current screenMousePos
		client.computeWorldMouse();

		// Use coordinated camera and hotbar slot
		mc.sayda.mcraze.network.packet.PacketPlayerInput packet = new mc.sayda.mcraze.network.packet.PacketPlayerInput(
				client.moveLeft,
				client.moveRight,
				client.climb,
				client.sneak,
				client.leftClick,
				client.rightClick,
				client.cameraX + (client.screenMousePos.x / (float) client.getEffectiveTileSize()),
				client.cameraY + (client.screenMousePos.y / (float) client.getEffectiveTileSize()),
				desiredHotbarSlot);

		client.connection.sendPacket(packet);
	}

	private class MouseWheelInputHander implements MouseWheelListener {
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			int scroll = e.getWheelRotation();

			// If main menu is open, pass wheel event to it
			if (client != null && client.isInMenu() && client.getMainMenu() != null) {
				client.getMainMenu().handleMouseWheel(
						client.screenMousePos.x,
						client.screenMousePos.y,
						scroll);
			} else if (client != null && client.chat != null && client.chat.isOpen()) {
				// If chat is open, scroll chat history (inverted for natural feel)
				if (scroll > 0) {
					client.chat.scrollDown(); // Scroll wheel down = newer messages
				} else if (scroll < 0) {
					client.chat.scrollUp(); // Scroll wheel up = older messages
				}
			} else {
				// Update desired hotbar slot and send immediately
				desiredHotbarSlot = Math.max(0, Math.min(9, desiredHotbarSlot + scroll));
				// Send packet immediately so server gets hotbar change even when player is
				// standing still
				sendInputPacket();
			}
		}
	}

	private class MouseMoveInputHander implements MouseMotionListener {
		@Override
		public void mouseDragged(MouseEvent arg0) {
			if (client != null) {
				client.setMousePosition(arg0.getX(), arg0.getY());
			}
		}

		@Override
		public void mouseMoved(MouseEvent arg0) {
			if (client != null) {
				client.setMousePosition(arg0.getX(), arg0.getY());
			}
		}
	}

	private class FocusInputHandler implements FocusListener {
		@Override
		public void focusGained(FocusEvent e) {
			// Do nothing
		}

		@Override
		public void focusLost(FocusEvent e) {
			// Reset all input states when focus is lost to prevent phantom inputs
			if (client != null) {
				if (client.moveLeft || client.moveRight || client.climb
						|| client.sneak || client.leftClick || client.rightClick) {
					client.moveLeft = false;
					client.moveRight = false;
					client.climb = false;
					client.sneak = false;
					client.setLeftClick(false);
					client.setRightClick(false);

					client.sendInput(); // Notify server of reset

					// Also reset shift pressed state
					shiftPressed = false;

					if (logger != null && logger.isDebugEnabled()) {
						logger.debug("Focus lost - inputs reset");
					}
				}
			}
		}
	}

	private class MouseInputHander extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent arg0) {
			if (game.getClient() == null)
				return;
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
			if (game.getClient() == null)
				return;
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
		 *          The details of the key that was pressed
		 */
		@Override
		public void keyPressed(KeyEvent e) {
			if (game.getClient() == null)
				return;

			// Handle login screen key presses (highest priority)
			if (game.isShowingLoginScreen()) {
				mc.sayda.mcraze.ui.menu.LoginScreen loginScreen = game.getLoginScreen();
				if (loginScreen != null) {
					loginScreen.handleKeyPressed(
							e.getKeyCode(),
							e.isShiftDown(),
							e.isControlDown());
				}
				return;
			}

			// Handle main menu key presses
			if (game.getClient().isInMenu()) {
				game.getClient().getMainMenu().handleKeyPressed(
						e.getKeyCode(),
						e.isShiftDown(),
						e.isControlDown());
				return;
			}

			// Handle ESC key for menus - check in priority order
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				// Priority 0: Stop Bad Apple if playing
				if (game.getClient() != null && game.getClient().isBadApplePlaying()) {
					game.getClient().stopBadApple();
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}
				// Priority 1: Close settings menu if open
				if (game.getClient().isInSettingsMenu()) {
					game.getClient().closeSettingsMenu();
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 2: Close pause menu if open
				if (game.getClient().isInPauseMenu()) {
					game.getClient().closePauseMenu();
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 3: Close chat if open
				if (game.getClient().chat.isOpen()) {
					game.getClient().chat.setOpen(false);
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 3.5: Close Class Selection UI if open
				if (game.getClient().isClassSelectionUIOpen()) {
					game.getClient().closeClassSelectionUI();
					escUsedToCloseMenu = true;
					e.consume();
					return;
				}

				// Priority 3.6: Close Skill Assignment UI if open
				if (game.getClient().isSkillUIOpen()) {
					game.getClient().closeSkillUI();
					escUsedToCloseMenu = true;
					e.consume();
					return;
				}

				// Priority 4: Close chest UI if open
				if (game.getClient().isChestUIOpen()) {
					game.getClient().closeChestUI();
					// CRITICAL FIX: Send packet to server to ensure state is synchronized
					mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
							0, 0,
							mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.TOGGLE_INVENTORY);
					game.getClient().connection.sendPacket(packet);
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 5: Close furnace UI if open
				if (game.getClient().isFurnaceUIOpen()) {
					game.getClient().closeFurnaceUI();
					// CRITICAL FIX: Send packet to server to ensure state is synchronized
					mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
							0, 0,
							mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.TOGGLE_INVENTORY);
					game.getClient().connection.sendPacket(packet);
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 5.1: Close alchemy UI if open
				if (game.getClient().isAlchemyUIOpen()) {
					game.getClient().closeAlchemyUI();
					// CRITICAL FIX: Send packet to server to ensure state is synchronized
					mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
							0, 0,
							mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.TOGGLE_INVENTORY);
					game.getClient().connection.sendPacket(packet);
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
					e.consume();
					return;
				}

				// Priority 5.2: Close inventory if open
				mc.sayda.mcraze.player.Player player = getLocalPlayer();
				if (player != null && player.inventory.isVisible()) {
					// CRITICAL FIX: Use server-authoritative packet instead of direct modification
					mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
							0, 0,
							mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.TOGGLE_INVENTORY);
					game.getClient().connection.sendPacket(packet);
					escUsedToCloseMenu = true; // Mark that ESC closed a menu
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
				boolean ctrlDown = e.isControlDown();

				// Handle Ctrl combinations first
				if (ctrlDown) {
					if (e.getKeyCode() == KeyEvent.VK_A) {
						game.getClient().chat.selectAll();
						e.consume();
						return;
					} else if (e.getKeyCode() == KeyEvent.VK_C) {
						game.getClient().chat.copyToClipboard();
						e.consume();
						return;
					} else if (e.getKeyCode() == KeyEvent.VK_V) {
						game.getClient().chat.pasteFromClipboard();
						e.consume();
						return;
					}
				}

				// Handle regular keys
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					game.submitChat();
				} else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
					game.getClient().chat.backspace();
				} else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					game.getClient().chat.delete();
				} else if (e.getKeyCode() == KeyEvent.VK_TAB) {
					game.getClient().chat.tabComplete();
					e.consume(); // Prevent tab from changing focus
				} else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
					game.getClient().chat.moveCursorLeft(e.isShiftDown());
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
					game.getClient().chat.moveCursorRight(e.isShiftDown());
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_HOME) {
					game.getClient().chat.moveCursorHome(e.isShiftDown());
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_END) {
					game.getClient().chat.moveCursorEnd(e.isShiftDown());
					e.consume();
				} else if (e.getKeyCode() == KeyEvent.VK_UP) {
					game.getClient().chat.historyUp();
					e.consume(); // Prevent default behavior
				} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					game.getClient().chat.historyDown();
					e.consume(); // Prevent default behavior
				}
				return;
			}

			// ALWAYS use packets for input (integrated or dedicated server)
			if (game.getClient() != null) {
				switch (e.getKeyCode()) {
					case KeyEvent.VK_W:
					case KeyEvent.VK_SPACE:
						if (!game.getClient().climb) {
							game.getClient().climb = true;
							game.getClient().sendInput();
						}
						break;
					case KeyEvent.VK_A:
						if (!game.getClient().moveLeft) {
							game.getClient().moveLeft = true;
							game.getClient().sendInput();
						}
						break;
					case KeyEvent.VK_D:
						if (!game.getClient().moveRight) {
							game.getClient().moveRight = true;
							game.getClient().sendInput();
						}
						break;
					case KeyEvent.VK_SHIFT:
						if (!game.getClient().sneak) {
							game.getClient().sneak = true;
							shiftPressed = true; // Track for inventory QoL
							game.getClient().sendInput();
						}
						break;
					case KeyEvent.VK_F3:
						// Toggle debug overlay (F3)
						if (game.getClient().getDebugOverlay() != null) {
							game.getClient().getDebugOverlay().toggle();
						}
						break;
					case KeyEvent.VK_I:
						// Unified Class/Skill UI Toggle (I key)
						if (game.getClient() != null && !game.getClient().chat.isOpen()) {
							mc.sayda.mcraze.client.Client c = game.getClient();
							// Check authoritative player state (Server for integrated, Client for MP)
							mc.sayda.mcraze.player.Player p = (game.getServer() != null) ? game.getServer().player
									: c.getLocalPlayer();

							if (p != null) {
								if (p.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.NONE) {
									// No class -> Toggle Class Selection
									if (c.isClassSelectionUIOpen()) {
										c.closeClassSelectionUI();
									} else {
										c.getClassSelectionUI().setVisible(true);
										c.closeSkillUI();
									}
								} else {
									// Has class -> Toggle Skill Assignment UI
									c.toggleSkillUI();
									if (c.isSkillUIOpen()) {
										c.closeClassSelectionUI();
									}
								}
							}
						}
						break;
					case KeyEvent.VK_1:
					case KeyEvent.VK_2:
					case KeyEvent.VK_3:
					case KeyEvent.VK_4:
					case KeyEvent.VK_5:
					case KeyEvent.VK_6:
					case KeyEvent.VK_7:
					case KeyEvent.VK_8:
					case KeyEvent.VK_9:
						// Update desired hotbar slot and send immediately
						desiredHotbarSlot = e.getKeyCode() - KeyEvent.VK_1;
						// PREDICTIVE FIX: Update local player's inventory index immediately for
						// responsiveness
						if (game.getClient() != null && game.getClient().getLocalPlayer() != null
								&& game.getClient().getLocalPlayer().inventory != null) {
							game.getClient().getLocalPlayer().inventory.hotbarIdx = desiredHotbarSlot;
						}
						sendInputPacket();
						break;
					case KeyEvent.VK_0:
						// Update desired hotbar slot and send immediately
						desiredHotbarSlot = 9;
						// PREDICTIVE FIX: Update local player's inventory index immediately for
						// responsiveness
						if (game.getClient() != null && game.getClient().getLocalPlayer() != null
								&& game.getClient().getLocalPlayer().inventory != null) {
							game.getClient().getLocalPlayer().inventory.hotbarIdx = desiredHotbarSlot;
						}
						sendInputPacket();
						break;
				}
			}
		}

		/**
		 * Notification from AWT that a key has been released.
		 * 
		 * @param e
		 *          The details of the key that was released
		 */
		@Override
		public void keyReleased(KeyEvent e) {
			if (game.getClient() == null)
				return;

			// Check if we should ignore this release event
			boolean ignoreInput = false;

			// Don't process GENERAL game input (like pause menu toggle) if in main menu
			if (game.getClient().isInMenu()) {
				ignoreInput = true;
			}
			// Don't process GENERAL game input if pause menu is open
			else if (game.getClient().isInPauseMenu()) {
				ignoreInput = true;
			}
			// Don't process GENERAL game input if chat is open
			else if (game.getClient().chat.isOpen()) {
				ignoreInput = true;
			}

			// HOWEVER, we MUST process movement key RELEASES even if menus are open
			// This prevents "stuck" keys if you open a menu while holding a key
			boolean isMovementKey = false;
			switch (e.getKeyCode()) {
				case KeyEvent.VK_W:
				case KeyEvent.VK_SPACE:
				case KeyEvent.VK_A:
				case KeyEvent.VK_D:
				case KeyEvent.VK_SHIFT:
					isMovementKey = true;
					break;
			}

			if (ignoreInput && !isMovementKey) {
				return;
			}

			// ALWAYS use packets for input (integrated or dedicated server)
			if (game.getClient() != null) {
				switch (e.getKeyCode()) {
					case KeyEvent.VK_W:
					case KeyEvent.VK_SPACE:
						game.getClient().climb = false;
						game.getClient().sendInput();
						break;
					case KeyEvent.VK_A:
						game.getClient().moveLeft = false;
						game.getClient().sendInput();
						break;
					case KeyEvent.VK_D:
						game.getClient().moveRight = false;
						game.getClient().sendInput();
						break;
					case KeyEvent.VK_SHIFT:
						game.getClient().sneak = false;
						shiftPressed = false; // Track for inventory QoL
						game.getClient().sendInput();
						break;
					case KeyEvent.VK_ESCAPE:
						// Skip if ESC just closed a menu (prevent double-trigger)
						if (escUsedToCloseMenu) {
							escUsedToCloseMenu = false; // Reset flag
							break;
						}
						// Open pause menu if nothing else is open
						if (!game.getClient().isInPauseMenu() &&
								!game.getClient().isInSettingsMenu() &&
								!game.getClient().chat.isOpen() &&
								!game.getClient().chat.isOpen() &&
								!game.getClient().isChestUIOpen() &&
								!game.getClient().isFurnaceUIOpen() &&
								!game.getClient().isAlchemyUIOpen()) {
							mc.sayda.mcraze.player.Player player = getLocalPlayer();
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
		}

		@Override
		public void keyTyped(KeyEvent e) {
			if (game.getClient() == null)
				return;

			char c = e.getKeyChar();

			// Handle login screen typing (highest priority)
			if (game.isShowingLoginScreen()) {
				mc.sayda.mcraze.ui.menu.LoginScreen loginScreen = game.getLoginScreen();
				if (loginScreen != null) {
					loginScreen.handleKeyTyped(c);
				}
				return;
			}

			// Handle menu typing (when in main menu)
			if (game.getClient().isInMenu()) {
				game.getClient().getMainMenu().handleKeyTyped(c);
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
			mc.sayda.mcraze.player.Player player = null;
			if (server != null) {
				player = server.player; // Singleplayer
			} else if (client != null) {
				player = client.getLocalPlayer(); // Multiplayer
			}

			if (player == null)
				return; // No player to control

			// Game commands
			switch (c) {
				case 'e':
					// E key: Toggle inventory or close container UI if open
					// Closing priority: Chest > Furnace > Skill UI > Class UI > Main Inventory
					if (game.getClient().isChestUIOpen()) {
						game.getClient().closeChestUI();
					} else if (game.getClient().isFurnaceUIOpen()) {
						game.getClient().closeFurnaceUI();
					} else if (game.getClient().isSkillUIOpen()) {
						game.getClient().closeSkillUI();
						break; // Don't toggle inventory if closing skill UI
					} else if (game.getClient().isClassSelectionUIOpen()) {
						game.getClient().closeClassSelectionUI();
						break; // Don't toggle inventory if closing class UI
					} else if (game.getClient().isAlchemyUIOpen()) {
						game.getClient().closeAlchemyUI();
					}

					// ALWAYS send packet to server to ensure state is synchronized
					// (Server will clear openedFurnaceX/openedChestX or toggle main inventory)
					mc.sayda.mcraze.network.packet.PacketInteract togglePacket = new mc.sayda.mcraze.network.packet.PacketInteract(
							0, 0,
							mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.TOGGLE_INVENTORY);
					game.getClient().connection.sendPacket(togglePacket);
					break;
				case '=':
				case '+':
					// Zoom in
					game.getClient().zoomIn();
					break;
				case 'p':
					// Open pause menu if not already open
					if (!game.getClient().isInPauseMenu()) {
						game.getClient().openPauseMenu();
					}
					break;
				case 'm':
					game.getClient().getMusicPlayer().toggleSound();
					break;
				case 'b':
				case 'B':
					// Toggle backdrop placement mode - send packet to server
					if (game.getClient() != null && game.getClient().connection != null) {
						mc.sayda.mcraze.network.packet.PacketToggleBackdropMode packet = new mc.sayda.mcraze.network.packet.PacketToggleBackdropMode();
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
						mc.sayda.mcraze.network.packet.PacketItemToss tossPacket = new mc.sayda.mcraze.network.packet.PacketItemToss();
						game.getClient().connection.sendPacket(tossPacket);
					}
					break;
				case 'r':
					// Respawn if player is dead - send packet to server
					// Architecture: ALL player actions use packets, even in integrated server with
					// LAN disabled
					mc.sayda.mcraze.player.Player respawnPlayer = getLocalPlayer();
					if (respawnPlayer != null && respawnPlayer.dead) {
						if (game.getClient() != null && game.getClient().connection != null) {
							mc.sayda.mcraze.network.packet.PacketRespawn respawnPacket = new mc.sayda.mcraze.network.packet.PacketRespawn();
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
