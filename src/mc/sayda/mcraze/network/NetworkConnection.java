package mc.sayda.mcraze.network;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP/IP socket-based connection for multiplayer using binary protocol
 */
public class NetworkConnection implements Connection {
	private Socket socket;
	private DataOutputStream out;
	private DataInputStream in;
	private List<Packet> receivedPackets = new ArrayList<>();
	private Thread receiveThread;
	private boolean connected = true;

	public NetworkConnection(Socket socket) throws IOException {
		this.socket = socket;
		// Use DataOutputStream/DataInputStream for binary protocol
		this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
		this.out.flush();
		this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

		// Start background thread to receive packets
		startReceiveThread();
		System.out.println("NetworkConnection: Connected to " + socket.getRemoteSocketAddress());
	}

	/**
	 * Connect to a server
	 */
	public static NetworkConnection connect(String host, int port) throws IOException {
		System.out.println("NetworkConnection: Connecting to " + host + ":" + port);
		Socket socket = new Socket(host, port);
		socket.setSoTimeout(30000);  // 30 second read timeout
		return new NetworkConnection(socket);
	}

	private void startReceiveThread() {
		receiveThread = new Thread(() -> {
			System.out.println("NetworkConnection: Receive thread started");
			while (connected) {
				try {
					// Read packet ID (1 byte)
					int packetId = in.readInt();

					// Read packet length (4 bytes)
					int packetLength = in.readInt();

					// Read packet data
					byte[] packetData = new byte[packetLength];
					in.readFully(packetData);

					// Decode packet
					ByteBuffer buffer = ByteBuffer.wrap(packetData);
					Packet packet = PacketRegistry.decode(packetId, buffer);

					// Only log non-spam packets
					String packetType = packet.getClass().getSimpleName();
					if (!packetType.equals("PacketEntityUpdate") && !packetType.equals("PacketWorldUpdate")) {
						System.out.println("NetworkConnection: Received " + packetType + " (ID: " + packetId + ", " + packetLength + " bytes)");
					}

					synchronized (receivedPackets) {
						receivedPackets.add(packet);
					}
				} catch (EOFException e) {
					// Connection closed
					System.out.println("NetworkConnection: Connection closed by remote");
					connected = false;
				} catch (IOException e) {
					if (connected) {
						System.err.println("NetworkConnection: Error receiving packet: " + e.getMessage());
						e.printStackTrace();
						connected = false;
					}
				} catch (Exception e) {
					// Catch packet decoding errors
					System.err.println("NetworkConnection: Error decoding packet: " + e.getMessage());
					e.printStackTrace();
					connected = false;
				}
			}
			System.out.println("NetworkConnection: Receive thread stopped");
		}, "NetworkReceive");
		receiveThread.setDaemon(true);
		receiveThread.start();
	}

	@Override
	public void sendPacket(Packet packet) {
		if (!connected) {
			System.err.println("NetworkConnection: Cannot send packet, not connected");
			return;
		}

		try {
			// Encode packet to binary
			byte[] packetData = packet.encode();
			int packetId = packet.getPacketId();

			synchronized (out) {
				// Write packet ID (4 bytes)
				out.writeInt(packetId);

				// Write packet length (4 bytes)
				out.writeInt(packetData.length);

				// Write packet data
				out.write(packetData);
				out.flush();

				// Only log non-spam packets
				String packetType = packet.getClass().getSimpleName();
				if (!packetType.equals("PacketEntityUpdate")
                        && !packetType.equals("PacketWorldUpdate")
                        && !packetType.equals("PacketInventoryUpdate")
                        && !packetType.equals("PacketPlayerInput")
                ) {
					System.out.println("NetworkConnection: Sent " + packetType + " (ID: " + packetId + ", " + packetData.length + " bytes)");
				}
			}
		} catch (IOException e) {
			System.err.println("NetworkConnection: Error sending packet: " + e.getMessage());
			e.printStackTrace();
			connected = false;
		}
	}

	@Override
	public Packet[] receivePackets() {
		synchronized (receivedPackets) {
			Packet[] packets = receivedPackets.toArray(new Packet[0]);
			receivedPackets.clear();
			return packets;
		}
	}

	@Override
	public boolean isConnected() {
		return connected && socket != null && !socket.isClosed();
	}

	@Override
	public void disconnect() {
		System.out.println("NetworkConnection: Disconnecting");
		connected = false;
		try {
			// Close streams before closing socket
			if (out != null) {
				out.flush();
				out.close();
			}
			if (in != null) {
				in.close();
			}
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			// Log but don't throw - we're cleaning up
			System.err.println("NetworkConnection: Error during disconnect: " + e.getMessage());
		}
	}
}
