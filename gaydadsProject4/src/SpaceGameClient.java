import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import spaceWar.*;

/**
 * @author bachmaer
 *
 * Driver class for a simple networked space game. Opponents try to destroy each 
 * other by ramming. Head on collisions destroy both ships. Ships move and turn 
 * through GUI mouse clicks. All friendly and alien ships are displayed on a 2D 
 * interface.  
 */
public class SpaceGameClient implements SpaceGUIInterface
{
	// Keeps track of the game state
	public static Sector sector;

	// User interface
	SpaceGameGUI gui;

	// IP address and port to identify ownship and the 
	// DatagramSocket being used for game play messages.
	InetSocketAddress ownShipID;

	// Socket for sending and receiving
	// game play messages.
	DatagramSocket gamePlaySocket;

	// Socket used to register and to receive remove information
	// for ships and 
	Socket reliableSocket;

	// Set to false to stops all receiving loops
	boolean playing = true;

	static final boolean DEBUG = false;

	DataInputStream dis;
	DataOutputStream dos;
	ByteArrayOutputStream baos;


	ByteArrayInputStream bais;
	DatagramPacket packet = new DatagramPacket(new byte[24], 24);

	/**
	 * Creates all components needed to start a space game. Creates Sector 
	 * canvas, GUI interface, a Sender object for sending update messages, a 
	 * Receiver object for receiving messages.
	 * @throws UnknownHostException 
	 */
	public SpaceGameClient()
	{
		// Create UDP Datagram Socket for sending and receiving
		// game play messages.
		try {

			gamePlaySocket = new DatagramSocket();
			gamePlaySocket.setSoTimeout(100);

			// Instantiate ownShipID using the DatagramSocket port
			// and the local IP address.
			ownShipID = new InetSocketAddress( InetAddress.getLocalHost(),
					gamePlaySocket.getLocalPort());

			// Create display, ownPort is used to uniquely identify the 
			// controlled entity.
			sector = new Sector( ownShipID );

			//	gui will call SpaceGame methods to handle user events
			gui = new SpaceGameGUI( this, sector ); 

			// Establish TCP connection with the server and pass the 
			// IP address and port number of the gamePlaySocket to the 
			// server.
			establishTCPConnectionWithServer();
			passClientIPAddressAndPortNumberToServer();

			// Call a method that uses TCP/IP to receive obstacles 
			// from the server. 
			receiveObstaclesFromTCPServer();

			// Start thread to listen on the TCP Socket and receive remove messages.
			new SpaceGameClientThread().start();


			// Infinite loop or separate thread to receive update 
			// messages from the server and use the messages to 
			// update the sector display
			while(playing) {

				try {
					gamePlaySocket.receive(packet);
					handlePacket(packet);
				} 
				catch( SocketTimeoutException e) {

				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}

		} catch (SocketException e) {
			System.err.println("Error creating game play datagram socket.");
			System.err.println("Server is not opening.");

		} catch (UnknownHostException e) {
			System.err.println("Error creating ownship ID. Exiting.");
			System.err.println("Server is not opening.");
		}

		//Print Polite Message to User and Exit
		JOptionPane.showMessageDialog(gui,
				"Game over! Sorry, the server has shutdown.",
				"",
				JOptionPane.PLAIN_MESSAGE);
		System.exit(0);

	} // end SpaceGame constructor


	/**
	 * This method takes in a packet from the UDP loop and handles it based on its type
	 * @param packet
	 */
	protected void handlePacket(DatagramPacket packet) {
		//Create Streams
		bais = new ByteArrayInputStream(packet.getData());
		dis = new DataInputStream(bais);


		int port = 0;
		int type = 0;
		int x = 0;
		int y = 0;
		int heading = 0;

		/*Read in the data to be used
		 * Actually no need to read in all the data, but it seemed like
		 * all the Datagrams should keep a uniform format.
		 * Kept it just in case we wanted to add functionality later.
		 */
		String iNetAddress = disReadInetAddress() ;
		try {
			port = dis.readInt();
			type = dis.readInt();
			x = dis.readInt();
			y = dis.readInt();
			heading = dis.readInt();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		InetSocketAddress ip = new InetSocketAddress(packet.getAddress(), port);
		if (type == Constants.JOIN) {
			sector.updateOrAddSpaceCraft(new AlienSpaceCraft(ip, x, y, heading));
		}
		if (type == Constants.UPDATE_SHIP) {
			sector.updateOrAddSpaceCraft(new AlienSpaceCraft(ip, x, y, heading));
		}
		if (type == Constants.UPDATE_TORPEDO) {
			sector.updateOrAddTorpedo(ip, x, y, heading);
		}


	}

	/**
	 * This method returns an InetSocketAddress when given an IP String and Port Number.
	 * Just used to clean up a bit.
	 * @param ip
	 * @param port
	 * @return
	 */
	protected InetSocketAddress createInetSocketAddress(String ip, int port) {
		InetSocketAddress isa = null;
		try {
			isa = new InetSocketAddress(InetAddress.getByName(ip), port);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return isa;
	}

	/**
	 * This is an odd way to transform the byte [] data into a string format,
	 * but it works so I kept it, even though there are other ways to do this.
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

	/**
	 * This method is just used to clean up code. Just uses the stream to read an int.
	 * @return
	 */
	protected int disReadInt() {
		try {
			return dis.readInt();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * This method gets the objects from the server at the beginning of the game.
	 */
	protected void receiveObstaclesFromTCPServer() {
		//X and Y Coordinates to place
		int x = 0;
		int y = 0;

		//If negative number is received, done with coordinates
		while (x >=0) {
			//Read X Coordinate
			try {
				x = dis.readInt();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (x < 0 ){

			}

			else {
				//Read Y Coordinate
				try {
					y = dis.readInt();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				sector.addObstacle(x, y);
			}

		}
	}

	/**
	 * This method passes the Server the Data it needs for the UDP.
	 */
	protected void passClientIPAddressAndPortNumberToServer() {
		/*pass the 
	IP address and port number of the gamePlaySocket to the 
	server.*/
		createTCPStreams();
		byte[] ip = null;
		try {
			ip = InetAddress.getLocalHost().getAddress();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			dos.write(ip);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			dos.writeInt(ownShipID.getPort());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * This is used to loop until the server connects with the client using TCP.
	 */
	protected void establishTCPConnectionWithServer() {
		if (DEBUG) System.out.println("Attempting to find server...");
		while (reliableSocket == null) {

			try {
				reliableSocket = new Socket(Constants.SERVER_IP, Constants.SERVER_PORT);
			} catch (IOException e) {

			}
		}
		if (DEBUG) System.out.println("Connected to Server");

	}

	/**
	 * This method is used to create TCP streams for the client
	 */
	protected void createTCPStreams() {
		try {
			dis = new DataInputStream(reliableSocket.getInputStream());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			dos = new DataOutputStream(reliableSocket.getOutputStream());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}



	/**
	 * Causes sector.ownShip to turn and sends an update message for the heading 
	 * change.
	 */
	public void turnRight()
	{
		if (sector.ownShip != null) {
			if ( DEBUG ) System.out.println( " Right Turn " );
			// Update the display			
			sector.ownShip.rightTurn();

			// Send update message to server with new heading.
			sendPacket(Constants.UPDATE_SHIP);

		} 

	} // end turnRight


	/**
	 * Causes sector.ownShip to turn and sends an update message for the heading 
	 * change.
	 */
	public void turnLeft()
	{
		// See if the player has a ship in play
		if (sector.ownShip != null) {		

			if ( DEBUG ) System.out.println( " Left Turn " );

			// Update the display
			sector.ownShip.leftTurn();

			// Send update message to other server with new heading.
			// TODO
			sendPacket(Constants.UPDATE_SHIP);
		}		

	} // end turnLeft


	/**
	 * Causes sector.ownShip to turn and sends an update message for the heading 
	 * change.
	 */
	public void fireTorpedo()
	{
		// See if the player has a ship in play
		if (sector.ownShip != null) {		

			if ( DEBUG ) System.out.println( "Informing server of new torpedo" );

			// Send code to let server know a torpedo is being fired.
			createTCPStreams();

			try {
				dos.writeInt(Constants.FIRED_TORPEDO);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Send Position and heading
			try {
				dos.writeInt(sector.ownShip.ID.getPort());
				dos.writeInt(sector.ownShip.getXPosition());
				dos.writeInt(sector.ownShip.getYPosition());
				dos.writeInt(sector.ownShip.getHeading());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}		

	} // end turnLeft


	/**
	 * Causes sector.ownShip to move forward and sends an update message for the 
	 * position change. If there is an obstacle in front of
	 * the ship it will not move forward and a message is not sent. 
	 */
	public void moveFoward()
	{
		// Check if the player has and unblocked ship in the game
		if ( sector.ownShip != null && sector.clearInfront() ) {

			if ( DEBUG ) System.out.println( " Move Forward" );

			//Update the displayed position of the ship
			sector.ownShip.moveForward();

			// Send a message with the updated position to server
			// TODO	
			sendPacket(Constants.UPDATE_SHIP);
		}

	} // end moveFoward


	/**
	 * Causes sector.ownShip to move forward and sends an update message for the 
	 * position change. If there is an obstacle in front of
	 * the ship it will not move forward and a message is not sent. 
	 */
	public void moveBackward()
	{
		// Check if the player has and unblocked ship in the game
		if ( sector.ownShip != null && sector.clearBehind() ) {

			if ( DEBUG ) System.out.println( " Move Backward" );

			//Update the displayed position of the ship
			sector.ownShip.moveBackward();

			// Send a message with the updated position to server
			// TODO	
			sendPacket(Constants.UPDATE_SHIP);
		}

	} // end moveFoward


	/**
	 * Creates a new sector.ownShip if one does not exist. Sends a join message 
	 * for the new ship.
	 *
	 */
	public void join()
	{
		if (sector.ownShip == null ) {

			if ( DEBUG ) System.out.println( " Join " );

			// Add a new ownShip to the sector display
			sector.createOwnSpaceCraft();

			// Send message to server let them know you have joined the game using the 
			// send object
			// TODO

			sendPacket(Constants.JOIN);
		}

	} // end join

	protected void sendPacket(int type) {
		//Create Packet and Streams
		DatagramPacket packet;
		baos = new ByteArrayOutputStream();
		dos = new DataOutputStream(baos);

		try {
			dos.write(sector.ownShip.ID.getAddress().getAddress());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		dosWriteInt(sector.ownShip.ID.getPort());
		dosWriteInt(type);
		dosWriteInt(sector.ownShip.getXPosition());
		dosWriteInt(sector.ownShip.getYPosition());
		dosWriteInt(sector.ownShip.getHeading());

		packet = new DatagramPacket(baos.toByteArray(), baos.size());
		packet.setAddress(Constants.SERVER_IP);
		packet.setPort(Constants.SERVER_PORT);
		try {
			gamePlaySocket.send(packet);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			baos.close();
			dos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void dosWriteInt(int var) {
		try {
			dos.writeInt(var);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	/**
	 *  Perform clean-up for application shut down
	 */
	public void stop()
	{
		if ( DEBUG ) System.out.println("stop");

		// Stop all thread and close all streams and sockets
		playing = false;

		// Send exit code to the server
		// TODO

		createTCPStreams();
		try {
			dos.writeInt(Constants.EXIT);
			dos.write(InetAddress.getLocalHost().getAddress());
			dos.writeInt(gamePlaySocket.getLocalPort());
			dos.writeInt(Constants.REMOVE_SHIP);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		try {
			if (DEBUG) System.out.println("InetSending... " + InetAddress.getLocalHost().toString());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		if (DEBUG) System.out.println("Port Sending... " + gamePlaySocket.getLocalPort());

		try {
			dos.close();
			dis.close();
			reliableSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	} // end stop


	/*
	 * Starts the space game. Driver for the application.
	 */


	class SpaceGameClientThread extends Thread {

		public void run() {
			// Start thread to listen on the TCP Socket and receive remove messages.
			while (playing) {
				DataInputStream dis1 = null;
				try {
					dis1 = new DataInputStream(reliableSocket.getInputStream());
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				try {

					byte [] ip = new byte[4];
					int clientPort = 0;

					dis1.read(ip);
					clientPort = dis1.readInt();

					InetAddress clientIp = null;
					clientIp = InetAddress.getByAddress(ip);

					if (DEBUG) System.out.println("IP: " + clientIp);
					if (DEBUG) System.out.println("Port: " + clientPort);

					int typeToRemove = 0;

					typeToRemove = dis1.readInt();


					if (typeToRemove == Constants.REMOVE_SHIP) {
						sector.removeSpaceCraft(new InetSocketAddress(clientIp, clientPort), 0, 0, 0);
						if (DEBUG) {
							System.out.println("ClientPort for ship to be removed" + clientPort);
							System.out.println("Removed Ship!!!");
						}
					}

					if (typeToRemove == Constants.REMOVE_TORPEDO) {
						sector.removeTorpedo(new InetSocketAddress(clientIp, clientPort), 0, 0, 0);
					}

				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
					if (DEBUG) System.out.println("Server Connection Ended.");
					playing = false;
				}
			}
			try {
				dis.close();
				dos.close();
				reliableSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		protected void createTCPStreams() {
			try {
				dis = new DataInputStream(reliableSocket.getInputStream());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			try {
				dos = new DataOutputStream(reliableSocket.getOutputStream());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}


	}

	public static void main(String[] args) 
	{	
		new SpaceGameClient();

	} // end main

} // end SpaceGame class



