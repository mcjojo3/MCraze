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

package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Client → Server: Player input (movement, mouse position in WORLD coordinates)
 * Binary protocol: 6 booleans + 2 floats + 1 int = 18 bytes (was ~200 bytes!)
 *
 * IMPORTANT: worldMouseX/worldMouseY are in WORLD coordinates, not screen
 * coordinates!
 * Client must convert screen coords to world coords before sending.
 */
public class PacketPlayerInput extends ClientPacket {
	public boolean moveLeft;
	public boolean moveRight;
	public boolean climb;
	public boolean sneak;
	public boolean leftClick;
	public boolean rightClick;
	public float worldMouseX; // World coordinates (not screen coordinates!)
	public float worldMouseY; // World coordinates (not screen coordinates!)
	public int hotbarSlot;

	public PacketPlayerInput() {
	}

	public PacketPlayerInput(boolean moveLeft, boolean moveRight, boolean climb, boolean sneak,
			boolean leftClick, boolean rightClick,
			float worldMouseX, float worldMouseY, int hotbarSlot) {
		this.moveLeft = moveLeft;
		this.moveRight = moveRight;
		this.climb = climb;
		this.sneak = sneak;
		this.leftClick = leftClick;
		this.rightClick = rightClick;
		this.worldMouseX = worldMouseX;
		this.worldMouseY = worldMouseY;
		this.hotbarSlot = hotbarSlot;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketPlayerInput.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handlePlayerInput(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(18);
		buf.put((byte) (moveLeft ? 1 : 0));
		buf.put((byte) (moveRight ? 1 : 0));
		buf.put((byte) (climb ? 1 : 0));
		buf.put((byte) (sneak ? 1 : 0));
		buf.put((byte) (leftClick ? 1 : 0));
		buf.put((byte) (rightClick ? 1 : 0));
		buf.putFloat(worldMouseX);
		buf.putFloat(worldMouseY);
		buf.putInt(hotbarSlot);
		return buf.array();
	}

	public static PacketPlayerInput decode(ByteBuffer buf) {
		boolean moveLeft = buf.get() == 1;
		boolean moveRight = buf.get() == 1;
		boolean climb = buf.get() == 1;
		boolean sneak = buf.get() == 1;
		boolean leftClick = buf.get() == 1;
		boolean rightClick = buf.get() == 1;
		float worldMouseX = buf.getFloat();
		float worldMouseY = buf.getFloat();
		int hotbarSlot = buf.getInt();
		return new PacketPlayerInput(moveLeft, moveRight, climb, sneak, leftClick, rightClick, worldMouseX, worldMouseY,
				hotbarSlot);
	}
}
