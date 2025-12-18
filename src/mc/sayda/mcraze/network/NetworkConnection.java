package mc.sayda.mcraze.network;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP/IP socket-based connection for multiplayer
 */
public class NetworkConnection implements Connection {
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private List<Packet> receivedPackets = new ArrayList<>();
	private Thread receiveThread;
	private boolean connected = true;

	public NetworkConnection(Socket socket) throws IOException {
		this.socket = socket;
		// IMPORTANT: Create output stream BEFORE input stream to avoid deadlock
		this.out = new ObjectOutputStream(socket.getOutputStream());
		this.out.flush();
		this.in = new ObjectInputStream(socket.getInputStream());

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
					Packet packet = (Packet) in.readObject();
					// Only log non-spam packets
					String packetType = packet.getClass().getSimpleName();
					if (!packetType.equals("PacketEntityUpdate") && !packetType.equals("PacketWorldUpdate")) {
						System.out.println("NetworkConnection: Received " + packetType);
					}
					synchronized (receivedPackets) {
						receivedPackets.add(packet);
					}
				} catch (EOFException e) {
					// Connection closed
					System.out.println("NetworkConnection: Connection closed by remote");
					connected = false;
				} catch (IOException | ClassNotFoundException e) {
					if (connected) {
						System.err.println("NetworkConnection: Error receiving packet: " + e.getMessage());
						e.printStackTrace();
						connected = false;
					}
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
			synchronized (out) {
				out.writeObject(packet);
				out.flush();
				// Only log non-spam packets
				String packetType = packet.getClass().getSimpleName();
				if (!packetType.equals("PacketEntityUpdate") && !packetType.equals("PacketWorldUpdate")) {
					System.out.println("NetworkConnection: Sent " + packetType);
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
