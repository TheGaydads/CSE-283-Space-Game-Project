import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import spaceWar.Constants;
import spaceWar.Obstacle;
import spaceWar.SpaceCraft;
import spaceWar.Torpedo;

/**
 * @author bachmaer
 * 
 * Class to listen for clients sending information reliably 
 * using TCP. It takes care of the following events:
 * 1. Client coming into the game
 * 2. Client firing torpedoes
 * 3. Client leaving the game
 * 4. Sending remove messages to the client
 */
public class PersistentConnectionToClient extends Thread {


	Socket clientConnection = null;

	SpaceGameServer spaceGameServer;

	boolean thisClientIsPlaying = true;

	DataOutputStream dos;
	DataInputStream dis;
	InetSocketAddress clientISA;
	static final boolean DEBUG = false;

	public PersistentConnectionToClient(Socket sock, SpaceGameServer spaceGameServer) {

		this.clientConnection = sock;
		this.spaceGameServer = spaceGameServer;

	} // end PersistentConnectionToClient


	/**
	 * Listens for join and exiting clients using TCP. Joining clients are sent
	 * the x and y coordinates of all obstacles followed by a negative number. Receives
	 * fire messages from clients and the exit code when a client is leaving the game.
	 */
	public void run(){

		//TODO
		createStreams();
		sendClientISAToServer();
		sendObstacles();


		while( thisClientIsPlaying && spaceGameServer.playing ){ // loop till playing is set to false
			createStreams();
			int code = 0;
			try {
				code = dis.readInt();
			} 
			catch (SocketTimeoutException e) {

			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


			if (DEBUG) System.out.println("Code from client: " + code);

			if (code == Constants.FIRED_TORPEDO) {
				if (DEBUG) System.out.println("Persist client fired Torpedo!!!");

				int x = 0;
				int y = 0;
				int heading = 0;
				int clientPort = 0;
				try {
					clientPort = dis.readInt();
					x = dis.readInt();
					y = dis.readInt();
					heading = dis.readInt();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				InetAddress clientIp = clientConnection.getLocalAddress();

				if (DEBUG) System.out.println("ClientPort for Torp" + clientPort);

				Torpedo torpedo = new Torpedo(new InetSocketAddress(clientIp, clientPort), x, y ,heading);

				//Update Sector with torpedo and send update to clients
				spaceGameServer.sector.updateOrAddTorpedo(torpedo);
				spaceGameServer.sector.updateTorpedoes();


			}


			if (code == Constants.EXIT) {
				byte [] ip = new byte[4];
				int clientPort = 0;
				int shipType = 0;
				InetAddress clientIp = null;
				try {
					dis.read(ip);
					clientIp = InetAddress.getByAddress(ip);
					clientPort = dis.readInt();
					shipType = dis.readInt();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if (DEBUG) System.out.println("Ip from Client: " + clientIp.toString());
				if (DEBUG) System.out.println("Port from Client: " + clientPort);


				InetSocketAddress clientShip = new InetSocketAddress(clientIp, clientPort);
				if (shipType == Constants.REMOVE_SHIP) {
					if (DEBUG) System.out.println("Persist remove Ship");

					//Remove all client data and send removes to clients
					SpaceCraft sc = new SpaceCraft(clientShip, 0, 0, 0);
					spaceGameServer.sector.removeSpaceCraft(sc);
					spaceGameServer.removeClientDatagramSocketAddresses(clientShip);
					spaceGameServer.removePersistentConnection(this);
					spaceGameServer.sendRemoves(sc);
					thisClientIsPlaying = false;
					
					try {
						clientConnection.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (shipType == Constants.REMOVE_TORPEDO) {
					if (DEBUG) System.out.println("Persist remove Torpedo");
					spaceGameServer.sector.removeTorpedo(new Torpedo(clientShip, 0, 0, 0));
				}
			}
		} // end while


		//Close Streams and TCP Connection
		try {
			dos.close();
			dis.close();
			clientConnection.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



	} // end run


	/**
	 * This method sends the obstacles to the client during initial startup
	 */
	protected void sendObstacles() {
		//Send client number of objects to receive coordinates for.

		for (int i = 0; i < spaceGameServer.sector.getObstacles().size(); i++) {
			try {
				dos.writeInt(spaceGameServer.sector.getObstacles().get(i).getXPosition());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				dos.writeInt(spaceGameServer.sector.getObstacles().get(i).getYPosition());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		//Once done sending coordinates, send -1 to end service
		try {
			dos.writeInt(Constants.REGISTER);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * This method takes the client info and sends it to the server to store
	 * and send updates to other clients
	 */
	protected void sendClientISAToServer() {
		byte [] ip = new byte[4];
		int clientPort = 0;
		try {
			dis.read(ip);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		InetAddress clientIp = null;
		try {
			clientIp = InetAddress.getByAddress(ip);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			clientPort = dis.readInt();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		clientISA = new InetSocketAddress(clientIp, clientPort);
		spaceGameServer.addClientDatagramSocketAddresses(clientISA);

	}

	/**
	 * Method to create TCP streams
	 */
	protected void createStreams() {
		try {
			dis = new DataInputStream(clientConnection.getInputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			dos = new DataOutputStream(clientConnection.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * This method sends removes to the clients ip, port, and depending on the type
	 * of space craft/torpedo, a code telling the client what type of object to remove.
	 * @param sc
	 */
	protected void sendRemoveToClient( SpaceCraft sc)
	{
		//TODO
		byte [] ip = sc.ID.getAddress().getAddress();

		if (DEBUG) {
		System.out.println("sc IP: " + sc.ID.getAddress());
		System.out.println("sc IP Byte []: " + sc.ID.getAddress().getAddress());
		System.out.println("sc Port: " + sc.ID.getPort());
		}
		try {
			dos.write(ip);
			dos.writeInt(sc.ID.getPort());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//If sc is a torpedo, send the torpedo removal code
		if (sc instanceof Torpedo) {
			try {
				dos.writeInt(Constants.REMOVE_TORPEDO);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		else {
			try {
				dos.writeInt(Constants.REMOVE_SHIP);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}


	} // end sendRemoveToClient


} // end PersistentConnectionToClient class