import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.Vector;

import spaceWar.SpaceCraft;
import spaceWar.Torpedo;

/**
 * Task which periodically updates the torpedoes that
 * are in the sector and determines if they have hit 
 * anything.
 * 
 * @author bachmaer
 */
class TorpdedoUpdater extends TimerTask
{
	/**
	 * Socket through which all torpedo update messages will be sent
	 */
	DatagramSocket dgsock;
	
	/**
	 * Reference to the server that contains the Sector to be updated
	 */
	SpaceGameServer spaceGameServer;
	
	/**
	 * Creates a DatagramSocket that is used to send update mesages.
	 */
	public TorpdedoUpdater(SpaceGameServer spaceGameServer) {
		
		// Save a reference to the SpaceGameServer
		this.spaceGameServer = spaceGameServer;
		
		try {
			dgsock = new DatagramSocket();
		} catch (SocketException e) {
			System.err.println("Could not create Datagram Socket for torpedo updater.");
			
		}
	} // end TorpdedoUpdater constructor
	
	
	/**
	 * run method that will be called periodically by a Timer (sub-class of thread).
	 * It updates all torpedoes in the sector and then sends updates message to
	 * all clients to provide them with the new positions of the torpedoes. Additionally
	 * it sends remove messages for torpedoes and ships. Torpedoes are removed when 
	 * they reach the end of their life or hit a ship. Ships are removed if they
	 * are hit by torpedoes. 
	 */
	public void run() {
			
		// Move all torpedoes and determine if they hit anything 
		ArrayList<SpaceCraft> destroyed = spaceGameServer.sector.updateTorpedoes();
		
		// Send remove messages for any ships of torpedoes 
		// that are no longer in the game.
		if (destroyed != null ) {
			
			for ( SpaceCraft sc: destroyed) {
				
				spaceGameServer.sendRemoves( sc );
				
			}
		}
		
		// Access the torpedoes that are still in the sector
		Vector<Torpedo> remainingTorpedoes = spaceGameServer.sector.getTorpedoes();
		
		// Send update messages for torpedoes that are still
		// in the game
		for ( Torpedo t: remainingTorpedoes) {
			
			spaceGameServer.sendTorpedoUpdate( t, dgsock );
		}
		
		// Check to see if the game has ended
		if (spaceGameServer.playing == false ){
			this.cancel();
			dgsock.close();
		}

	} // end run
	
} // end TorpdedoUpdater class