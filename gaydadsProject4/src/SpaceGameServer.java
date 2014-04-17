import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import spaceWar.*;


/**
 * @author bachmaer
 *
 * SpaceGameServer. Implements a server to support a multiuser game in
 * which clients maneuver spacecraft through a shared virtual world and 
 * attempt to destroy each other by either firing torpedoes or ramming.
 * The server is the arbiter of all events resulting in the destruction
 * of a torpedo or a spacecraft and generates messages for removal 
 * accordingly. Removal information is sent via TCP. UDP update messages
 * that are received from clients are used to update the state of the game
 * and forwarded to all other clients.
 *   
 */
public class SpaceGameServer 
{
	// Random number generator for obstacle positions
	Random rand = new Random();

	// Contains all IP addresses and port numbers of the DatagramSockets
	// used by the clients. UDP segments are forwarded using the information
	// in this data member.
	private ArrayList<InetSocketAddress> clientDatagramSocketAddresses 
 		= new  ArrayList<InetSocketAddress>();
	
	// Contains references to threads that maintain a persistent connection
	// with each client. Data pertaining to the removal
	// of torpedoes and ships are sent reliably to clients using this
	// ArrayList.
	private ArrayList<PersistentConnectionToClient> playerTCPConnections 
		= new ArrayList<PersistentConnectionToClient>();
	
	// Simple gui to display what the server is tracking
	protected ServerGUI display;
	
	// Sector containing all information about the game state
	protected Sector sector;
	
	// True till GUI is closed. Setting to false cases all message forwarding
	// and game state updating to end.
	protected boolean playing = true;
	
	// Reference for timer object used to automatically update torpedoes
	protected Timer torpedoTimer;
	
	// Class which contains the torpedo update task
	protected TorpdedoUpdater torpUpdater;
	
	// Socket with which clients make contact when first starting up.
	ServerSocket gameServerSocket = null;
	
	/**
	 * Server constructor. Create data members to use for
	 * tracking and updating game information. Create obstacles.
	 * Create and start GUI. Start threads and timer tasks.
	 */
	public SpaceGameServer() 
	{
		// Create sector to hold all game information
		sector = new Sector();
		
		// Create and position the obstacles
		createObstacles();

		// Create the GUI that will display the sector
		display = new ServerGUI( sector );
		
		// Start the task to update the torpedoes
		torpedoTimer = new Timer();
		torpUpdater = new TorpdedoUpdater(this);
		torpedoTimer.scheduleAtFixedRate( torpUpdater, 0, 50);
		
		// Start the UDP server
		new BestEffortServer(this).start();

		// Start the TCP server
		createPersistentClientConnections();
		
	} // end SpaceGameServer constructor
	
	
	/**
	 * Synchronized method to create thread safe access to the playerTCPConnections data member.
	 * 
	 * @param persistConnect Object to be added to the ArrayList
	 */
	protected synchronized void addPersistentConnection( PersistentConnectionToClient persistConnect )
	{
		playerTCPConnections.add(persistConnect);
		
	} // end addPersistentConnection
	
	/**
	 * Synchronized method to create thread safe access to the playerTCPConnections data member.
	 * 
	 * @param persistConnect Object to be removed from the ArrayList
	 */
	protected synchronized void removePersistentConnection( PersistentConnectionToClient persistConnect )
	{
		playerTCPConnections.remove(persistConnect);
		
	}// end removePersistentConnection
	
	/**
	 * Synchronized method to create thread safe access to the clientDatagramSocketAddresses data member.
	 * 
	 * @param ISA IP address and port number of the DatagramSocket being added to the ArrayList
	 */
	protected synchronized void addClientDatagramSocketAddresses(InetSocketAddress ISA)
	{
		clientDatagramSocketAddresses.add(ISA);
		} // end addClientDatagramSocketAddresses
	
	/**
	 * Synchronized method to create thread safe access to the clientDatagramSocketAddresses data member.
	 * 
	 * @param ISA IP address and port number of the DatagramSocket to be removed from the ArrayList
	 */
	protected synchronized void removeClientDatagramSocketAddresses(InetSocketAddress ISA)
	{
		clientDatagramSocketAddresses.remove(ISA);
		
	} // end removeClientDatagramSocketAddresses
	

	/**
	 * Implements the "accept" loop of a TCP server. Instantiates a new PersistentConnectionToClient
	 * object for each connection, starts it, and saves a referenct to it in the playerTCPConnections
	 * ArrayList.
	 */
	protected void createPersistentClientConnections() {
		
		try {
			gameServerSocket = new ServerSocket(Constants.SERVER_PORT);

			while( playing ) {
				
				try {
					
					PersistentConnectionToClient pesistConnect 
						= new PersistentConnectionToClient( gameServerSocket.accept(), this);
					pesistConnect.start();
					addPersistentConnection(pesistConnect);
	
				} catch (IOException e) {
					System.err.println("Error creating persistent connection to client.");
				}
			}

			this.gameServerSocket.close();
			
		} catch (IOException e) {
			System.err.println("Error creating server socket used to listing for joining clients.");
			playing = false;
		}
		
	} // end createPersistentClientConnections
	
	
	/**
	 * Create a number of obstacles as determined by a value held in 
	 * Constants.NUMBER_OF_OBSTACLES. Obstacles are in random positions
	 * and are shared by all clients.
	 */
	protected void createObstacles() 
	{
		for(int i = 0 ; i < Constants.NUMBER_OF_OBSTACLES ; i++){
			
			sector.addObstacle( new Obstacle( rand.nextInt(Constants.MAX_SECTOR_X), 
											  rand.nextInt(Constants.MAX_SECTOR_Y) ) );
		}

	} // end createObstacles
	
	
	/**
	 * Causes all threads and timer tasks to cease execution and closes all
	 * sockets. Called when the GUI is closed.
	 */
	public void close ()
	{		
		playing = false;

	} // end close 
	
		
	/**
	 * Sends remove information for a particular SpaceCraft or Torpedo to all clients.
	 *
	 * @param sc ship to be removed
	 */
	synchronized protected void sendRemoves( SpaceCraft sc ) {
		
		// Go through all the players in the game
		for(int i = 0; i < playerTCPConnections.size(); i++ ) {

			// Get the Socket and DataOutputStream for a particular
			// player
			PersistentConnectionToClient pesistConnect = playerTCPConnections.get(i);

			// Send the remove information to a single client
			pesistConnect.sendRemoveToClient(sc);

		}

	} // end sendRemove
	
	
	/**
	 * Creates a update message for a torpedo and sends it to all
	 * clients.
	 * 
	 * @param sc torpedo being updated
	 * @param dgSock socket to use to send the message
	 */
	protected void sendTorpedoUpdate( Torpedo sc, DatagramSocket dgSock  ) {
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream( baos );

		try {
			// Write fields of the message
			dos.write( sc.ID.getAddress().getAddress());
			dos.writeInt( sc.ID.getPort());
			dos.writeInt( Constants.UPDATE_TORPEDO );
			dos.writeInt( sc.getXPosition() );
			dos.writeInt( sc.getYPosition() );
			dos.writeInt( sc.getHeading() );
			
		} catch (IOException e) {
			System.err.println("Error sending torpedo update.");
		}

		// Create the packet
		DatagramPacket dpack 
			= new DatagramPacket(baos.toByteArray(), baos.size() );
			
		// Send the packet to every client
		allForward( dpack, dgSock );
			
		try {
			dos.close();
		} catch (IOException e) {
			System.err.println("Error closing stream.");
		}

	} // end sendTorpedoUpdate
	
	
	/**
	 * Sends a datagram packet to all clients except one as 
	 * specified by an input argument.
	 * 
	 * @param fwdPack packet to send
	 * @param notSendTo address to skip
	 * @param dgSock socket to use to send the message
	 */
	synchronized protected void selectiveForward(DatagramPacket fwdPack, InetSocketAddress notSendTo, DatagramSocket dgSock )
	{
		for(InetSocketAddress isa : clientDatagramSocketAddresses ) {
						
			if( !isa.equals(notSendTo)) {
				
				fwdPack.setSocketAddress( isa );
				try {
					dgSock.send( fwdPack );

				} catch (IOException e) {
					System.err.println("Error performing selective forward.");
				}
			}
		}
	} // end selectiveForward
	
	
	/**
	 * Sends a datagram packet to all clients.
	 * 
	 * @param fwdPack packet to send
	 * @param dgSock socket to use to send the message
	 */
	synchronized protected void allForward(DatagramPacket fwdPack, DatagramSocket dgSock  )
	{
		for(InetSocketAddress isa : clientDatagramSocketAddresses ) {
				
			fwdPack.setSocketAddress( isa );
			
			try {
				dgSock.send( fwdPack );
			} catch (IOException e) {
				System.err.println("Error forward message to all clients.");
			}
		}
		
	} // end allForward


	/**
	 * Driver for starting the server.
	 * 
	 * @param args
	 */
	public static void main(String[] args) 
	{
		new SpaceGameServer();
		
	} // end main
	
	
} // end SpaceGameServer class

