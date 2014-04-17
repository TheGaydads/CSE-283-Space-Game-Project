import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import spaceWar.Constants;
import spaceWar.SpaceCraft;
import spaceWar.Torpedo;


/**
 *  Class to receive and forward UDP packets containing
 *  updates from clients. In addition, it checks for 
 *  collisions caused by client movements and sends
 *  appropriate removal information.
 *  
 * @author bachmaer
 */
class BestEffortServer extends Thread {

	// Socket through which all client UDP messages
	// are received
	protected DatagramSocket gamePlaySocket = null;

	// Reference to the server which holds the sector to be updated
	SpaceGameServer spaceGameServer;

	//Packet to be used throughout
	DatagramPacket packet = new DatagramPacket(new byte[24], 24);

	//Streams to read packets
	ByteArrayInputStream bais;
	DataInputStream dis;



	/**
	 * Creates DatagramSocket through which all client update messages
	 * will be received and forwarded.
	 */
	public BestEffortServer(SpaceGameServer spaceGameServer) {

		// Save reference to the server
		this.spaceGameServer = spaceGameServer;

		try {

			gamePlaySocket = new DatagramSocket( Constants.SERVER_PORT );

		} catch (IOException e) {

			System.err.println("Error creating socket to receive and forward UDP messages.");
			spaceGameServer.playing = false;
		}

	} // end gamePlayServer


	/**
	 * run method that continuously receives update messages, updates the display, 
	 * and then forwards update messages.
	 */
	public void run() {

		// Receive and forward messages. Update the sector display
		while (spaceGameServer.playing) {

			//TODO
			//Get new Packet
			try {
				gamePlaySocket.receive(packet);
				handlePacket(packet);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	} // end run

	/**
	 * This method uses the packet received and sends the appropriate messages
	 * to the clients and updates the server sector as needed.
	 * @param packet
	 */
	protected void handlePacket(DatagramPacket packet) {
		bais = new ByteArrayInputStream(packet.getData());
		dis = new DataInputStream(bais);

		//Not all of these variables needed to be passed, but kept for consistent dg formatting
		String ip = disReadInetAddress() ;
		int port = 0;
		int type = 0;
		int x = 0;
		int y = 0;
		int heading = 0;
		
		//Read in all dg data to be used... Not all of data actually used.
		try {
			port = dis.readInt();
			type = dis.readInt();
			x = dis.readInt();
			y = dis.readInt();
			heading = dis.readInt();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		InetSocketAddress id = new InetSocketAddress(packet.getAddress(), port);

		if (type == Constants.JOIN || type == Constants.UPDATE_SHIP ) {
			// Create a temp spacecraft for adding to the sector display
			// or for updating.
			SpaceCraft ship = new SpaceCraft(id, x, y, heading );
			// Update the sector display spaceGameServer.sector.updateOrAddSpaceCraft( ship );
			// Check to see if any collisions have occurred 
			ArrayList<SpaceCraft> destroyed = spaceGameServer.sector.collisionCheck( ship );
			// Send remove information if something was destroyed in a
			// collision.

			if (type == Constants.JOIN) {
				spaceGameServer.sector.updateOrAddSpaceCraft(id, x, y, heading);
				spaceGameServer.selectiveForward(packet, id, gamePlaySocket);
			}

			if (type == Constants.UPDATE_SHIP && destroyed == null) {
				spaceGameServer.sector.updateOrAddSpaceCraft(id, x, y, heading);
				spaceGameServer.selectiveForward(packet, id, gamePlaySocket);
			}

			if (destroyed != null ) {
				for ( SpaceCraft sc: destroyed) {
					spaceGameServer.sendRemoves( sc ); 
				}
			}
		}

		if (type == Constants.UPDATE_TORPEDO) {
			spaceGameServer.sector.updateTorpedoes();
		}
		
		//Close Streams
		try {
			dis.close();
			bais.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	/**
	 * This method returns a String representation of the InetAddress,
	 * couldv'e used byte [] but chose not to.
	 * @return
	 */
	protected String disReadInetAddress() {
		String ip = "";

		for (int i = 0; i < 4; i++) {
			try {
				ip += dis.read();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (i < 3) {
				ip += ".";
			}
		}
		return ip;
	}

} // end BestEffortServer class
