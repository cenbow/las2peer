package i5.las2peer.logging;

import java.util.Date;

import rice.pastry.PastryNode;
import rice.pastry.socket.SocketNodeHandle;

/**
 * 
 * The NodeObserver is an abstract class providing all necessary methods
 * to log all interesting node events for a {@link i5.las2peer.p2p.Node}
 * (mainly {@link i5.las2peer.p2p.PastryNodeImpl}).
 * 
 * @author Holger Jan&szlig;en
 *
 */
public abstract class NodeObserver {
	
	/**
	 * An enumeration element with all types of events.
	 * 
	 * @author Holger Jan&szlig;en
	 * 
	 */
	public enum Event {
		NODE_CREATED (100),
		NEW_NODE_NOTICE (110),
		NEW_AGENT (200), //Unused
		NEW_AGENT_NOTICE (210), //Unused
		
		NODE_STATUS_CHANGE (700),
		
		AGENT_SEARCH_STARTED (1000),
		AGENT_SEARCH_RECEIVED (1100),
		AGENT_SEARCH_ANSWER_SENT (1150),
		AGENT_SEARCH_ANSWER_RECEIVED (1160),
		AGENT_SEARCH_FINISHED (1200),
		AGENT_SEARCH_FAILED(-1500),
		
		ARTIFACT_ADDED (2000),
		ARTIFACT_UPDATED (2050),
		ARTIFACT_FETCH_STARTED (2060),
		ARTIFACT_RECEIVED (2065),
		ARTIFACT_FETCH_FAILED (-2067),
		ARTIFACT_NOTIFICATION (2100),
		ARTIFACT_UPLOAD_FAILED (-2200),
		ARTIFACT_OVERWRITE_FAILED (-2201),
		
		AGENT_REGISTERED (3000), //Done at the node superclass
		AGENT_UNLOCKED (3010),
		AGENT_UNLOCK_FAILED (-3020),
		AGENT_CREATED (3050),
		AGENT_REMOVED (3060),
		AGENT_UPLOAD_STARTED (3100),
		AGENT_UPLOAD_SUCCESS (3101),
		AGENT_UPLOAD_FAILED (-3102),
		AGENT_GET_STARTED (3200),
		AGENT_GET_SUCCESS (3201),
		AGENT_GET_FAILED (-3202),
		AGENT_LOAD_FAILED (-3000),
		
		RMI_SENT (4000),
		RMI_RECEIVED(4100),
		RMI_ANSWERED(4150),
		RMI_ANSWER_RECEIVED(4200),
		
		MESSAGE_RECEIVED (5000),
		MESSAGE_RECEIVED_ANSWER (5001),
		MESSAGE_SENDING(5100),
		MESSAGE_FORWARDING (5200),
		MESSAGE_FAILED (-5300),
		MESSAGE_RECEIVED_UNKNOWN(-5310),
		
		RESPONSE_SENDING (5500),
		RESPONSE_FAILED  (5600),
		
		PASTRY_NEW_TOPIC_CHILD (6000),
		PASTRY_REMOVED_TOPIC_CHILD (6010),
		PASTRY_TOPIC_SUBSCRIPTION_FAILED (-6100),
		PASTRY_TOPIC_SUBSCRIPTION_SUCCESS (6110),
		
		SERVICE_STARTUP (6400),
		SERVICE_SHUTDOWN (6410),
		SERVICE_INVOKATION (6440),
		SERVICE_INVOKATION_FINISHED (6450),
		SERVICE_INVOKATION_FAILED (-6460),
		
		SERVICE_MESSAGE (6500),
		//To be used by the service developer
		SERVICE_CUSTOM_MESSAGE_1 (6501),
		SERVICE_CUSTOM_MESSAGE_2 (6502),
		SERVICE_CUSTOM_MESSAGE_3 (6503),
		SERVICE_CUSTOM_MESSAGE_4 (6504),
		SERVICE_CUSTOM_MESSAGE_5 (6505),
		SERVICE_CUSTOM_MESSAGE_6 (6506),
		SERVICE_CUSTOM_MESSAGE_7 (6507),
		SERVICE_CUSTOM_MESSAGE_8 (6508),
		SERVICE_CUSTOM_MESSAGE_9 (6509),
		SERVICE_CUSTOM_MESSAGE_10 (6510),
		
		SERVICE_ERROR (-6505),
		//To be used by the service developer
		SERVICE_CUSTOM_ERROR_1 (-6501),
		SERVICE_CUSTOM_ERROR_2 (-6502),
		SERVICE_CUSTOM_ERROR_3 (-6503),
		SERVICE_CUSTOM_ERROR_4 (-6504),
		SERVICE_CUSTOM_ERROR_5 (-6505),
		SERVICE_CUSTOM_ERROR_6 (-6506),
		SERVICE_CUSTOM_ERROR_7 (-6507),
		SERVICE_CUSTOM_ERROR_8 (-6508),
		SERVICE_CUSTOM_ERROR_9 (-6509),
		SERVICE_CUSTOM_ERROR_10 (-6510),
		
		HTTP_CONNECTOR_MESSAGE (8000),
		HTTP_CONNECTOR_REQUEST (8001),
		HTTP_CONNECTOR_ERROR (-8100),
		
		NODE_SHUTDOWN (10000),
		
		NODE_ERROR (-1000);
		
		/**
		 * a numeric event code
		 */
		private int value = -1;
		
		/**
		 * simple constructor for the code assignment
		 * @param value
		 */
		private Event ( int value ) { this.value = value; }
		
		/**
		 * @return code of the event
		 */
		public int getCode() { return value; }
	}
	
	
	
	/**
	 * Logs a node event.
	 * 
	 * @param event
	 */
	public void logEvent ( Event event ) {
		log (null, event, null, null, null, null, null);
	}
	
	
	/**
	 * Logs a node event.
	 * 
	 * @param event
	 * @param remarks
	 */
	public void logEvent ( Event event, String remarks ) {
		log (null, event, null, null, null, null, remarks);		
	}
	
	
	/**
	 * Logs a node event.
	 * 
	 * @param event
	 * @param sourceNode
	 * @param remarks
	 */
	public void logEvent ( Event event, Object sourceNode, String remarks ) {
		log (null, event, sourceNode, null, null, null, remarks);		
	}
	
	
	/**
	 * Logs a node event.
	 * 
	 * @param event
	 * @param sourceNode
	 * @param sourceAgentId
	 * @param remarks
	 */
	public void logEvent ( Event event, Object sourceNode, Long sourceAgentId, String remarks ) {
		log (null, event, sourceNode, sourceAgentId, null, null, remarks);
	}
	
	
	/**
	 * Logs a node event.
	 * 
	 * @param event
	 * @param sourceNode
	 * @param sourceAgentId
	 * @param destinationNode
	 * @param destinationAgentId
	 * @param remarks
	 */
	public void logEvent ( Event event, Object sourceNode, Long sourceAgentId, Object destinationNode, Long destinationAgentId, String remarks ) {
		log (null, event, sourceNode, sourceAgentId, destinationNode, destinationAgentId, remarks);
	}
	
	
	/**
	 * Logs a node event.
	 * 
	 * @param timespan
	 * @param event
	 * @param sourceNode
	 * @param sourceAgentId
	 * @param destinationNode
	 * @param destinationAgentId
	 * @param remarks
	 */
	public void logEvent (Event event, Long timespan, Object sourceNode, Long sourceAgentId, Object destinationNode, Long destinationAgentId, String remarks ) {
		log (timespan, event, sourceNode, sourceAgentId, destinationNode, destinationAgentId, remarks);
	}
	
	/**
	 * Derive a String representation for a node from the given identifier
	 * object. The type of the object depends on the setting of the current node.
	 * 
	 * Tries to specify an ip address and a port for an actual p2p node
	 * ({@link i5.las2peer.p2p.PastryNodeImpl} or {@link rice.pastry.NodeHandle}).
	 * 
	 * @param node
	 * @return string representation for the given node object
	 */
	private String getNodeRepresentation ( Object node ) {
		if ( node == null)
			return null;
		else if ( node instanceof SocketNodeHandle ) {
			SocketNodeHandle nh = (SocketNodeHandle) node;
			return nh.getId() + "/" + nh.getIdentifier();
		}
		else if ( node instanceof PastryNode ) {
			PastryNode pNode = (PastryNode) node;
			return getNodeRepresentation ( pNode.getLocalNodeHandle());
		}
		else
			return "" + node + " (" + node.getClass().getName() + ")";
	}
	
	
	
	/**
	 * Implemented by the MonitoringObserver.
	 * 
	 * @param serviceAgentId the service to be monitored
	 */
	protected abstract void enableServiceMonitoring(Long serviceAgentId);
	
	
	/**
	 * Enables monitoring for the given service agent.
	 * This only affects the MonitoringObserver.
	 * 
	 * @param serviceAgentId the service to be monitored
	 */
	public void setServiceMonitoring(Long serviceAgentId){
		enableServiceMonitoring(serviceAgentId);
	}
	
	
	/**
	 * Writes a log entry.
	 * 
	 * @param timespan (can be null)
	 * @param event necessary
	 * @param sourceNode an object representing some kind of node
	 * 		will be transferred to an node representation if possible (see {@link #getNodeRepresentation}) (can be null)
	 * @param sourceAgentId the id of the source agent (can be null)
	 * @param destinationNode (can be null)
	 * @param destinationAgentId (can be null)
	 * @param remarks (can be null)
	 */
	protected void log (Long timespan, Event event, Object sourceNode, Long sourceAgentId, Object destinationNode , Long destinationAgentId, String remarks ) {
		long timestamp =  new Date().getTime();
		String sourceNodeRepresentation = getNodeRepresentation (sourceNode);
		String destinationNodeRepresentation = getNodeRepresentation (destinationNode);
		
		writeLog (timestamp, timespan, event, sourceNodeRepresentation, sourceAgentId, destinationNodeRepresentation, destinationAgentId, remarks);
	}
	
	
	/**
	 * This method has to be implemented by any (non abstract) deriving class.
	 * Each call represents one event to log by this observer. All parameters except the time stamp 
	 * and the event may be null.
	 * 
	 * @param timestamp		UNIX time stamp of the event
	 * @param timespan		a time span (e.g. for an answer retrieval, the time span between sending and answer)
	 * @param event			the event to log
	 * @param sourceNode	a source (p2p) node of the event (e.g. message sender)
	 * @param sourceAgentId	a source (las2peer) agent of the event (e.g. message sender)
	 * @param destinationNode	a destination (p2p) node for the event (e.g. message receiver)
	 * @param destinationAgentId a destination (las2peer) agent of the event (e.g. message receiver)
	 * @param remarks		(optional) additional remarks
	 */
	protected abstract void writeLog ( long timestamp, Long timespan, Event event, String sourceNode, Long sourceAgentId, String destinationNode, Long destinationAgentId, String remarks );
	
	
}