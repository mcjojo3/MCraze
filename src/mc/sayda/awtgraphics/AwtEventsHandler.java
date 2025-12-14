package mc.sayda.awtgraphics;

import java.awt.Canvas;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import mc.sayda.Game;

public class AwtEventsHandler {
	Game game;
	
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
	
	private class MouseWheelInputHander implements MouseWheelListener {
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			if (game.getServer() != null && game.getServer().player != null) {
				game.getServer().player.scrollHotbar(e.getWheelRotation());
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
			if (game.getClient() == null || game.getServer() == null) return;

			// Don't process game input if chat is open
			if (game.getClient().chat.isOpen()) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					game.submitChat();
				} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					game.getClient().chat.setOpen(false);
					e.consume();  // Prevent ESC from also triggering menu
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

			if (game.getServer().player == null) return;

			switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_SPACE:
				game.getServer().player.startClimb();
				break;
			case KeyEvent.VK_A:
				game.getServer().player.startLeft(e.isShiftDown());
				break;
			case KeyEvent.VK_D:
				game.getServer().player.startRight(e.isShiftDown());
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
			if (game.getClient() == null || game.getServer() == null) return;

			// Don't process game input if chat is open
			if (game.getClient().chat.isOpen()) {
				return;
			}

			if (game.getServer().player == null) return;

			switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
			case KeyEvent.VK_SPACE:
				game.getServer().player.endClimb();
				break;
			case KeyEvent.VK_A:
				game.getServer().player.stopLeft();
				break;
			case KeyEvent.VK_D:
				game.getServer().player.stopRight();
				break;
			case KeyEvent.VK_ESCAPE:
				if (game.getServer().player.inventory.isVisible()) {
					game.getServer().player.inventory.setVisible(false);
				} else {
					game.getClient().goToMainMenu();
				}
				break;
			}
		}
		
		@Override
		public void keyTyped(KeyEvent e) {
			if (game.getClient() == null || game.getServer() == null) return;

			char c = e.getKeyChar();

			// Handle chat typing
			if (game.getClient().chat.isOpen()) {
				// Allow typing printable characters
				if (c >= 32 && c <= 126) {
					game.getClient().chat.typeChar(c);
				}
				return;
			}

			if (game.getServer().player == null) return;

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
				game.getServer().player.setHotbarItem(c - '1');
				break;
			case '0':
				game.getServer().player.setHotbarItem(9);
				break;
			case 'e':
				game.getServer().player.inventory.setVisible(!game.getServer().player.inventory.isVisible());
				break;
			case '=':
				// TODO: Implement zoom
				break;
			case 'p':
				// TODO: Implement pause
				break;
			case 'm':
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
				game.getServer().tossItem();
				break;
			case 'r':
				// Respawn if player is dead
				if (game.getServer().player.dead) {
					game.getServer().respawnPlayer();
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
