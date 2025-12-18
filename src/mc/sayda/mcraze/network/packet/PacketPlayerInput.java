/*
 * Copyright 2025 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketHandler;

/**
 * Client -> Server: Player input (movement, mouse position)
 */
public class PacketPlayerInput extends Packet {
	private static final long serialVersionUID = 1L;

	public boolean moveLeft;
	public boolean moveRight;
	public boolean climb;
	public boolean leftClick;
	public boolean rightClick;
	public float mouseX;
	public float mouseY;
	public int hotbarSlot;

	public PacketPlayerInput() {}

	public PacketPlayerInput(boolean moveLeft, boolean moveRight, boolean climb,
							 boolean leftClick, boolean rightClick,
							 float mouseX, float mouseY, int hotbarSlot) {
		this.moveLeft = moveLeft;
		this.moveRight = moveRight;
		this.climb = climb;
		this.leftClick = leftClick;
		this.rightClick = rightClick;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.hotbarSlot = hotbarSlot;
	}

	@Override
	public int getPacketId() {
		return 1;
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handlePlayerInput(this);
	}
}
