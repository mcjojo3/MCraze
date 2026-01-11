package mc.sayda.mcraze.network;

import mc.sayda.mcraze.logging.GameLogger;

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
	private static final int MAX_PACKET_QUEUE_SIZE = 5000;  // Increased for large world support (was 1000)
	private long lastOverflowWarning = 0;  // Rate limit overflow warnings
	private static final long OVERFLOW_WARNING_INTERVAL = 1000;  // 1 second between warnings
	private int droppedPacketCount = 0;  // Track dropped packets for rate-limited warning
	private Thread receiveThread;
	private boolean connected = true;

	public NetworkConnection(Socket socket) throws IOException {
		this.socket = socket;

		// PERFORMANCE FIX: Disable Nagle's algorithm to prevent packet batching delays
		// This ensures packets are sent immediately without waiting for TCP buffer to fill
		// Critical for low-latency multiplayer (auth packets, entity updates)
		socket.setTcpNoDelay(true);

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
		socket.setTcpNoDelay(true);  // Disable Nagle's algorithm for low latency
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

					// Only log in debug mode (reduces console spam from 240+/sec to near zero)
					GameLogger logger = GameLogger.get();
					if (logger != null && logger.isDebugEnabled()) {
						String packetType = packet.getClass().getSimpleName();
						logger.debug("NetworkConnection: Received " + packetType + " (ID: " + packetId + ", " + packetLength + " bytes)");
					}

					synchronized (receivedPackets) {
						// Prevent unbounded growth - drop oldest packets if queue is full
						if (receivedPackets.size() >= MAX_PACKET_QUEUE_SIZE) {
							receivedPackets.remove(0);  // Drop oldest (FIFO)
							GameLogger queueLogger = GameLogger.get();
							if (queueLogger != null) {
								queueLogger.warn("NetworkConnection: Packet queue overflow! Dropped oldest packet. Client may be lagging.");
							}
						}
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

				// PERFORMANCE FIX: Don't flush after every packet - batch flushes instead
				// flush() is a blocking I/O syscall (1-10ms on Windows) that kills server performance
				// With 60 packets/sec, individual flushes = 60-600ms of I/O blocking per second!
				// Instead, flush() should be called periodically (once per tick) to batch I/O
				// BufferedOutputStream (8KB) provides automatic flushing when full
				// Caller should call connection.flush() once per tick for low latency

				// Only log in debug mode (reduces console spam from 240+/sec to near zero)
				GameLogger logger = GameLogger.get();
				if (logger != null && logger.isDebugEnabled()) {
					String packetType = packet.getClass().getSimpleName();
					logger.debug("NetworkConnection: Sent " + packetType + " (ID: " + packetId + ", " + packetData.length + " bytes)");
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

	/**
	 * Flush pending packets to the network socket.
	 * This should be called once per server tick (60 Hz) to batch I/O operations.
	 * Batching flushes reduces I/O syscall overhead from 60-600ms/sec to 1-10ms/sec.
	 */
	public void flush() {
		if (!connected) {
			return;
		}
		try {
			synchronized (out) {
				out.flush();
			}
		} catch (IOException e) {
			System.err.println("NetworkConnection: Error flushing output: " + e.getMessage());
			connected = false;
		}
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
