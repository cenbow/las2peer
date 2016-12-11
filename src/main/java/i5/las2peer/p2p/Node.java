package i5.las2peer.p2p;

import java.io.File;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import com.sun.management.OperatingSystemMXBean;

import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.api.exceptions.EnvelopeAlreadyExistsException;
import i5.las2peer.api.exceptions.StorageException;
import i5.las2peer.classLoaders.L2pClassManager;
import i5.las2peer.classLoaders.libraries.Repository;
import i5.las2peer.communication.Message;
import i5.las2peer.communication.MessageException;
import i5.las2peer.communication.RMIExceptionContent;
import i5.las2peer.communication.RMIResultContent;
import i5.las2peer.communication.RMIUnlockContent;
import i5.las2peer.execution.L2pServiceException;
import i5.las2peer.execution.L2pThread;
import i5.las2peer.execution.NoSuchServiceException;
import i5.las2peer.execution.NotFinishedException;
import i5.las2peer.execution.RMITask;
import i5.las2peer.execution.ServiceInvocationException;
import i5.las2peer.execution.UnlockNeededException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.logging.monitoring.MonitoringObserver;
import i5.las2peer.p2p.NodeServiceCache.ServiceInstance;
import i5.las2peer.persistency.EncodingFailedException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.persistency.NodeStorageInterface;
import i5.las2peer.security.Agent;
import i5.las2peer.security.AgentContext;
import i5.las2peer.security.AgentException;
import i5.las2peer.security.AgentStorage;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.MessageReceiver;
import i5.las2peer.security.MonitoringAgent;
import i5.las2peer.security.PassphraseAgent;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UnlockAgentCall;
import i5.las2peer.security.UserAgent;
import i5.las2peer.security.UserAgentManager;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.CryptoTools;
import i5.las2peer.tools.SerializationException;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.socket.SocketNodeHandle;

/**
 * Base class for nodes in the las2peer environment.
 * 
 * A Node represents one enclosed unit in the network hosting an arbitrary number of agents willing to participate in
 * the P2P networking.
 * 
 */
public abstract class Node implements AgentStorage, NodeStorageInterface {

	/**
	 * The Sending mode for outgoing messages.
	 */
	public enum SendMode {
		ANYCAST,
		BROADCAST
	}

	/**
	 * Enum with the possible states of a node.
	 */
	public enum NodeStatus {
		UNCONFIGURED,
		CONFIGURED,
		STARTING,
		RUNNING,
		CLOSING,
		CLOSED
	}

	/**
	 * For performance measurement (load balance)
	 */
	private OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
			.getOperatingSystemMXBean();

	private NodeServiceCache nodeServiceCache;

	// TODO make node parameters configurable
	public static final double DEFAULT_CPU_LOAD_TRESHOLD = 0.5;
	private double cpuLoadThreshold = DEFAULT_CPU_LOAD_TRESHOLD;
	// should be lowered in future, currently services don't change often:
	private int nodeServiceCacheLifetime = 60; // time before cached node info becomes invalidated
	private int nodeServiceCacheResultCount = 3; // number of service instances to be collected from the network
	private int tidyUpTimerInterval = 60;
	private int agentContextLifetime = 60;
	private int invocationRetryCount = 3;

	/**
	 * observers to be notified of all occurring events
	 */
	private HashSet<NodeObserver> observers = new HashSet<NodeObserver>();

	/**
	 * contexts for local method invocation
	 */
	private Hashtable<Long, AgentContext> htLocalExecutionContexts = new Hashtable<Long, AgentContext>();

	/**
	 * Timer to tidy up hashtables etc (Contexts)
	 */
	private Timer tidyUpTimer;

	/**
	 * status of this node
	 */
	private NodeStatus status = NodeStatus.UNCONFIGURED;

	/**
	 * hashtable with all {@link i5.las2peer.security.MessageReceiver}s registered at this node
	 */
	private Hashtable<Long, MessageReceiver> htRegisteredReceivers = new Hashtable<Long, MessageReceiver>();

	/**
	 * map with all topics and their listeners
	 */
	private HashMap<Long, TreeMap<Long, MessageReceiver>> mapTopicListeners = new HashMap<Long, TreeMap<Long, MessageReceiver>>();
	/**
	 * other direction of {@link #mapTopicListeners}
	 */
	private HashMap<Long, TreeSet<Long>> mapListenerTopics = new HashMap<Long, TreeSet<Long>>();

	private L2pClassManager baseClassLoader = null;

	private Hashtable<Long, MessageResultListener> htAnswerListeners = new Hashtable<Long, MessageResultListener>();

	private static final String DEFAULT_INFORMATION_FILE = "etc/nodeInfo.xml";
	private String sInformationFileName = DEFAULT_INFORMATION_FILE;

	private KeyPair nodeKeyPair;

	/**
	 * maps names and emails to UserAgents
	 */
	private UserAgentManager userManager;

	/**
	 * maps service alias to service names
	 */
	private ServiceAliasManager aliasManager;

	/**
	 * Creates a new node, if the standardObserver flag is true, an observer logging all events to a simple plain text
	 * log file will be generated. If not, no observer will be used at startup.
	 * 
	 * @param standardObserver If true, the node uses the default logger.
	 */
	public Node(boolean standardObserver) {
		this(null, standardObserver);
	}

	/**
	 * Creates a new node with a standard plain text log file observer.
	 */
	public Node() {
		this(true);
	}

	/**
	 * @param baseClassLoader A default class loader used by this node.
	 */
	public Node(L2pClassManager baseClassLoader) {
		this(baseClassLoader, true);
	}

	/**
	 * @param baseClassLoader A default class loader used by this node.
	 * @param standardObserver If true, the node uses the default logger.
	 */
	public Node(L2pClassManager baseClassLoader, boolean standardObserver) {
		this(baseClassLoader, standardObserver, false);
	}

	/**
	 * Generates a new Node with the given baseClassLoader. The Observer-flags determine, which observers will be
	 * registered at startup.
	 * 
	 * @param baseClassLoader A default class loader used by this node.
	 * @param standardObserver If true, the node uses the default logger.
	 * @param monitoringObserver If true, the monitoring is enabled for this node.
	 */
	public Node(L2pClassManager baseClassLoader, boolean standardObserver, boolean monitoringObserver) {
		if (standardObserver) {
			initStandardLogfile();
		}
		if (monitoringObserver) {
			addObserver(new MonitoringObserver(50, this));
		}

		this.baseClassLoader = baseClassLoader;

		if (baseClassLoader == null) {
			this.baseClassLoader = new L2pClassManager(new Repository[0], this.getClass().getClassLoader());
		}

		nodeKeyPair = CryptoTools.generateKeyPair();
		nodeServiceCache = new NodeServiceCache(this, nodeServiceCacheLifetime, nodeServiceCacheResultCount);

		userManager = new UserAgentManager(this);
		aliasManager = new ServiceAliasManager(this);
	}

	/**
	 * Gets the public key of this node.
	 * 
	 * @return a public key
	 */
	public PublicKey getPublicNodeKey() {
		return nodeKeyPair.getPublic();
	}

	/**
	 * Creates an observer for a standard log-file. The name of the log-file will contain the id of the node to prevent
	 * The event for this notification. conflicts if running multiple nodes on the same machine.
	 */
	private void initStandardLogfile() {
		addObserver(L2pLogger.getInstance(Node.class.getName()));
	}

	/**
	 * Adds an observer to this node.
	 * 
	 * @param observer The observer that should be notified.
	 */
	public void addObserver(NodeObserver observer) {
		observers.add(observer);
	}

	/**
	 * Removes an observer from this node.
	 * 
	 * @param observer The observer that should be removed.
	 */
	public void removeObserver(NodeObserver observer) {
		observers.remove(observer);
	}

	/**
	 * Enables the service monitoring for the requested Service.
	 * 
	 * @param service The service that should be monitored.
	 */
	public void setServiceMonitoring(ServiceAgent service) {
		observerNotice(Event.SERVICE_ADD_TO_MONITORING, this.getNodeId(), service.getId(), null, null,
				service.getServiceNameVersion().toString());
	}

	/**
	 * Logs an event to all observers.
	 * 
	 * @param event The event for this notification.
	 * @param remarks Some free text note or description about this event.
	 */
	public void observerNotice(Event event, String remarks) {
		Long sourceAgentId = null; // otherwise calls are ambigious
		observerNotice(event, null, sourceAgentId, null, null, remarks);
	}

	/**
	 * Logs an event to all observers.
	 * 
	 * @param event The event for this notification.
	 * @param sourceNode
	 * @param remarks Some free text note or description about this event.
	 */
	public void observerNotice(Event event, Object sourceNode, String remarks) {
		Long sourceAgentId = null; // otherwise calls are ambigious
		observerNotice(event, sourceNode, sourceAgentId, null, null, remarks);
	}

	/**
	 * Logs an event to all observers.
	 * 
	 * @param event The event for this notification.
	 * @param sourceNode
	 * @param sourceAgentId
	 * @param remarks Some free text note or description about this event.
	 */
	public void observerNotice(Event event, Object sourceNode, long sourceAgentId, String remarks) {
		observerNotice(event, sourceNode, sourceAgentId, null, null, remarks);
	}

	/**
	 * Logs an event to all observers.
	 * 
	 * @param event The event for this notification.
	 * @param sourceNode
	 * @param sourceAgent
	 * @param remarks Some free text note or description about this event.
	 */
	public void observerNotice(Event event, Object sourceNode, MessageReceiver sourceAgent, String remarks) {
		Long sourceAgentId = null;
		if (sourceAgent != null) {
			sourceAgentId = sourceAgent.getResponsibleForAgentId();
		}
		observerNotice(event, sourceNode, sourceAgentId, null, null, remarks);
	}

	/**
	 * Logs an event to all observers.
	 * 
	 * @param event The event for this notification.
	 * @param sourceNode
	 * @param sourceAgent
	 * @param destinationNode
	 * @param destinationAgent
	 * @param remarks Some free text note or description about this event.
	 */
	public void observerNotice(Event event, Object sourceNode, Agent sourceAgent, Object destinationNode,
			Agent destinationAgent, String remarks) {
		Long sourceAgentId = null;
		if (sourceAgent != null) {
			sourceAgentId = sourceAgent.getId();
		}
		Long destinationAgentId = null;
		if (destinationAgent != null) {
			destinationAgentId = destinationAgent.getId();
		}
		observerNotice(event, sourceNode, sourceAgentId, destinationNode, destinationAgentId, remarks);
	}

	/**
	 * Logs an event to all observers.
	 * 
	 * @param event The event for this notification.
	 * @param sourceNode
	 * @param sourceAgentId
	 * @param destinationNode
	 * @param destinationAgentId
	 * @param remarks Some free text note or description about this event.
	 */
	public void observerNotice(Event event, Object sourceNode, Long sourceAgentId, Object destinationNode,
			Long destinationAgentId, String remarks) {
		long timestamp = new Date().getTime();
		String sourceNodeRepresentation = getNodeRepresentation(sourceNode);
		String destinationNodeRepresentation = getNodeRepresentation(destinationNode);
		for (NodeObserver ob : observers) {
			ob.log(timestamp, event, sourceNodeRepresentation, sourceAgentId, destinationNodeRepresentation,
					destinationAgentId, remarks);
		}
	}

	/**
	 * Derive a String representation for a node from the given identifier object. The type of the object depends on the
	 * setting of the current node.
	 * 
	 * Tries to specify an ip address and a port for an actual p2p node ({@link i5.las2peer.p2p.PastryNodeImpl} or
	 * {@link rice.pastry.NodeHandle}).
	 * 
	 * @param node The node that should be represented.
	 * @return string representation for the given node object
	 */
	protected String getNodeRepresentation(Object node) {
		if (node == null) {
			return null;
		} else if (node instanceof SocketNodeHandle) {
			SocketNodeHandle nh = (SocketNodeHandle) node;
			return nh.getId() + "/" + nh.getIdentifier();
		} else if (node instanceof PastryNode) {
			PastryNode pNode = (PastryNode) node;
			return getNodeRepresentation(pNode.getLocalNodeHandle());
		} else {
			return "" + node + " (" + node.getClass().getName() + ")";
		}
	}

	/**
	 * Gets the status of this node.
	 * 
	 * @return status of this node
	 */
	public NodeStatus getStatus() {
		return status;
	}

	/**
	 * Gets some kind of node identifier.
	 * 
	 * @return id of this node
	 */
	public abstract Serializable getNodeId();

	/**
	 * Gets the class loader, this node is bound to. In a <i>real</i> las2peer environment, this should refer to a
	 * {@link i5.las2peer.classLoaders.L2pClassManager}
	 * 
	 * Otherwise, the class loader of this Node class is used.
	 * 
	 * @return a class loader
	 */
	public L2pClassManager getBaseClassLoader() {
		return baseClassLoader;
	}

	/**
	 * Sets the status of this node.
	 * 
	 * @param newstatus The new status for this node.
	 */
	protected void setStatus(NodeStatus newstatus) {
		if (newstatus == NodeStatus.RUNNING && this instanceof PastryNodeImpl) {
			observerNotice(Event.NODE_STATUS_CHANGE, this.getNodeId(), "" + newstatus);
		} else if (newstatus == NodeStatus.CLOSING) {
			observerNotice(Event.NODE_STATUS_CHANGE, this.getNodeId(), "" + newstatus);
		} else {
			observerNotice(Event.NODE_STATUS_CHANGE, "" + newstatus);
		}
		status = newstatus;
	}

	/**
	 * Gets the filename of the current information file for this node. The file should be an XML file representation of
	 * a {@link NodeInformation}.
	 * 
	 * @return filename
	 */
	public String getInformationFilename() {
		return sInformationFileName;
	}

	/**
	 * Sets the nodes information filename.
	 * 
	 * @param filename The filename for the information file.
	 */
	public void setInformationFilename(String filename) {
		if (new File(filename).exists()) {
			sInformationFileName = filename;
		}
	}

	/**
	 * Gets information about this node including all registered service classes.
	 * 
	 * @return node information
	 * @throws CryptoException If an issue occurs with the given key or selected algorithm.
	 */
	public NodeInformation getNodeInformation() throws CryptoException {
		NodeInformation result = new NodeInformation(getRegisteredServices());

		try {
			if (sInformationFileName != null && new File(sInformationFileName).exists()) {
				result = NodeInformation.createFromXmlFile(sInformationFileName, getRegisteredServices());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		result.setNodeHandle(getNodeId());
		result.setNodeKey(nodeKeyPair.getPublic());

		result.setSignature(CryptoTools.signContent(result.getSignatureContent(), nodeKeyPair.getPrivate()));

		return result;
	}

	/**
	 * Gets information about a distant node.
	 * 
	 * @param nodeId
	 * @return information about the node
	 * @throws NodeNotFoundException
	 */
	public abstract NodeInformation getNodeInformation(Object nodeId) throws NodeNotFoundException;

	/**
	 * Gets an array with identifiers of other (locally known) nodes in this network.
	 * 
	 * @return array with handles of other (known) p2p network nodes
	 */
	public abstract Object[] getOtherKnownNodes();

	/**
	 * Starts this node.
	 * 
	 * @throws NodeException
	 */
	protected abstract void launchSub() throws NodeException;

	/**
	 * Starts this node.
	 * 
	 * @throws NodeException
	 */
	public final void launch() throws NodeException {
		launchSub();

		// store anonymous if not stored yet
		getAnonymous();

		startTidyUpTimer();
	}

	/**
	 * Stops the node.
	 */
	public synchronized void shutDown() {
		stopTidyUpTimer();

		Long[] receivers = htRegisteredReceivers.keySet().toArray(new Long[0]); // avoid ConcurrentModificationEception
		for (Long id : receivers) {
			htRegisteredReceivers.get(id).notifyUnregister();
		}
		observerNotice(Event.NODE_SHUTDOWN, this.getNodeId(), null);
		for (NodeObserver observer : observers) {
			if (observer instanceof MonitoringObserver) {
				try {
					System.out.println("Wait a little to give the observer time to send its last message...");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				break;
			}
		}
		htRegisteredReceivers = new Hashtable<Long, MessageReceiver>();
	}

	/**
	 * Registers a (local) Agent for usage through this node. The Agent has to be unlocked before registration.
	 * 
	 * @param receiver
	 *
	 * @throws AgentAlreadyRegisteredException the given agent is already registered to this node
	 * @throws L2pSecurityException the agent is not unlocked
	 * @throws AgentException any problem with the agent itself (probably on calling
	 *             {@link i5.las2peer.security.Agent#notifyRegistrationTo}
	 */
	public void registerReceiver(MessageReceiver receiver)
			throws AgentAlreadyRegisteredException, L2pSecurityException, AgentException {

		// TODO allow multiple mediators registered at the same time for one agent to avoid conflicts between connectors

		if (getStatus() != NodeStatus.RUNNING) {
			throw new IllegalStateException("You can register agents only to running nodes!");
		}

		if (htRegisteredReceivers.contains(receiver.getResponsibleForAgentId())
				&& htRegisteredReceivers.get(receiver.getResponsibleForAgentId()) != receiver) {
			throw new AgentAlreadyRegisteredException(
					"Another instance of this agent (or mediator) is already registered here!");
		}

		if ((receiver instanceof Agent)) {
			// we have an agent
			Agent agent = (Agent) receiver;
			if (agent.isLocked()) {
				throw new L2pSecurityException("An agent has to be unlocked for registering at a node");
			}

			try {
				// ensure (unlocked) context
				getAgentContext((Agent) receiver);
			} catch (Exception e) {
			}
			if (agent instanceof UserAgent) {
				observerNotice(Event.AGENT_REGISTERED, this.getNodeId(), agent, "UserAgent");
			} else if (agent instanceof ServiceAgent) {
				observerNotice(Event.AGENT_REGISTERED, this.getNodeId(), agent, "ServiceAgent");
			} else if (agent instanceof GroupAgent) {
				observerNotice(Event.AGENT_REGISTERED, this.getNodeId(), agent, "GroupAgent");
			} else if (agent instanceof MonitoringAgent) {
				observerNotice(Event.AGENT_REGISTERED, this.getNodeId(), agent, "MonitoringAgent");
			}
		} else {
			// ok, we have a mediator
			observerNotice(Event.AGENT_REGISTERED, this.getNodeId(), receiver, "Mediator");
		}

		htRegisteredReceivers.put(receiver.getResponsibleForAgentId(), receiver);

		try {
			receiver.notifyRegistrationTo(this);
		} catch (AgentException e) {
			observerNotice(Event.AGENT_LOAD_FAILED, this, receiver, e.toString());

			htRegisteredReceivers.remove(receiver.getResponsibleForAgentId());
			throw e;
		} catch (Exception e) {
			observerNotice(Event.AGENT_LOAD_FAILED, this, receiver, e.toString());

			htRegisteredReceivers.remove(receiver.getResponsibleForAgentId());
			throw new AgentException("problems notifying agent of registration", e);
		}

	}

	/**
	 * Unregisters a MessageReceiver from this node.
	 * 
	 * @param receiver the receiver to unregister
	 * @throws AgentNotKnownException The given MessageReceiver is not registered to this node
	 * @throws NodeException error in underlying layer
	 */
	public void unregisterReceiver(MessageReceiver receiver) throws AgentNotKnownException, NodeException {
		long agentId = receiver.getResponsibleForAgentId();
		unregisterReceiver(agentId);

		// unregister from topics
		if (mapListenerTopics.containsKey(agentId)) {
			Long[] topics = mapListenerTopics.get(agentId).toArray(new Long[0]);
			for (long topic : topics) {
				unregisterReceiverFromTopic(receiver, topic);
			}
		}
	}

	private void unregisterReceiver(long agentId) throws AgentNotKnownException {
		if (!htRegisteredReceivers.containsKey(agentId)) {
			throw new AgentNotKnownException(agentId);
		}
		observerNotice(Event.AGENT_REMOVED, getNodeId(), agentId, "");
		htRegisteredReceivers.remove(agentId).notifyUnregister();
	}

	/**
	 * register a receiver to a topic
	 * 
	 * @param receiver the MessageReceiver
	 * @param topic the topic id
	 * @throws AgentNotKnownException The given MessageReceiver is not registered to this node
	 */
	public void registerReceiverToTopic(MessageReceiver receiver, long topic) throws AgentNotKnownException {
		if (!htRegisteredReceivers.containsKey(receiver.getResponsibleForAgentId())) {
			throw new AgentNotKnownException(receiver.getResponsibleForAgentId());
		}

		synchronized (mapListenerTopics) {
			synchronized (mapTopicListeners) {
				if (mapListenerTopics.get(receiver.getResponsibleForAgentId()) == null) {
					mapListenerTopics.put(receiver.getResponsibleForAgentId(), new TreeSet<>());
				}

				if (mapListenerTopics.get(receiver.getResponsibleForAgentId()).add(topic)) {
					if (mapTopicListeners.get(topic) == null) {
						mapTopicListeners.put(topic, new TreeMap<>());
					}
					mapTopicListeners.get(topic).put(receiver.getResponsibleForAgentId(), receiver);
				}
			}
		}
	}

	/**
	 * unregister a receiver from a topic
	 * 
	 * @param receiver the recevier
	 * @param topic the topic id
	 * @throws NodeException
	 */
	public void unregisterReceiverFromTopic(MessageReceiver receiver, long topic) throws NodeException {
		unregisterReceiverFromTopic(receiver.getResponsibleForAgentId(), topic);
	}

	private void unregisterReceiverFromTopic(long receiverId, long topic) {
		synchronized (mapListenerTopics) {
			synchronized (mapTopicListeners) {
				if (!mapListenerTopics.containsKey(receiverId) || !mapListenerTopics.get(receiverId).contains(topic)) {
					return;
				}

				mapListenerTopics.get(receiverId).remove(topic);
				if (mapListenerTopics.get(receiverId).size() == 0) {
					mapListenerTopics.remove(receiverId);
				}

				mapTopicListeners.get(topic).remove(receiverId);
				if (mapTopicListeners.get(topic).size() == 0) {
					mapTopicListeners.remove(topic);
				}

			}
		}
	}

	/**
	 * checks if a receiver is registered to the topic
	 * 
	 * @param topic topic id
	 * @return true if someone is registered to the topic
	 */
	protected boolean hasTopic(long topic) {
		return mapTopicListeners.containsKey(topic);
	}

	/**
	 * @deprecated Use {@link Node#unregisterReceiver(MessageReceiver)} instead!
	 * 
	 *             Unregisters an agent from this node.
	 * 
	 * @param agent
	 * @throws AgentNotKnownException The given MessageReceiver is not registered to this node
	 * @throws NodeException
	 */
	@Deprecated
	public void unregisterAgent(Agent agent) throws AgentNotKnownException, NodeException {
		unregisterReceiver(agent);
	}

	/**
	 * @deprecated Use {@link Node#unregisterReceiver(MessageReceiver)} instead
	 * 
	 *             Is an instance of the given agent running at this node?
	 * 
	 * @param agentId
	 * @throws AgentNotKnownException The given MessageReceiver is not registered to this node
	 */
	@Deprecated
	public void unregisterAgent(long agentId) throws AgentNotKnownException {
		// when removed, merge both unregisterReceiver methods into one public
		unregisterReceiver(agentId);
	}

	/**
	 * Is an instance of the given agent running at this node?
	 * 
	 * @param agent
	 * @return true, if the given agent is running at this node
	 */
	public boolean hasLocalAgent(Agent agent) {
		return hasLocalAgent(agent.getId());
	}

	/**
	 * Is an instance of the given agent running at this node?
	 * 
	 * @param agentId
	 * @return true, if the given agent is registered here
	 */
	public boolean hasLocalAgent(long agentId) {
		return htRegisteredReceivers.get(agentId) != null;
	}

	/**
	 * Sends a message, recipient and sender are stated in the message. The node tries to find a node hosting the
	 * recipient and sends the message there.
	 * 
	 * @param message the message to send
	 * @param listener a listener for getting the result separately
	 */
	public void sendMessage(Message message, MessageResultListener listener) {
		sendMessage(message, listener, SendMode.ANYCAST);
	}

	/**
	 * Sends a message, recipient and sender are stated in the message. Depending on the mode, either all nodes running
	 * the given agent will be notified of this message, or only a random one.
	 * 
	 * NOTE: Pastry nodes will always use broadcast at the moment!
	 * 
	 * @param message the message to send
	 * @param listener a listener for getting the result separately
	 * @param mode is it a broadcast or an any-cast message?
	 */
	public abstract void sendMessage(Message message, MessageResultListener listener, SendMode mode);

	/**
	 * Sends a message to the agent residing at the given node.
	 * 
	 * @param message
	 * @param atNodeId
	 * @param listener a listener for getting the result separately
	 * @throws NodeNotFoundException
	 */
	public abstract void sendMessage(Message message, Object atNodeId, MessageResultListener listener)
			throws NodeNotFoundException;

	/**
	 * Sends the given response message to the given node.
	 * 
	 * @param message
	 * @param atNodeId
	 * @throws NodeNotFoundException
	 */
	public void sendResponse(Message message, Object atNodeId) throws NodeNotFoundException {
		sendMessage(message, atNodeId, null);
	}

	/**
	 * For <i>external</i> access to this node. Will be called by the (P2P) network library, when a new message has been
	 * received via the network and could not be handled otherwise.
	 * 
	 * Make sure, that the {@link #baseClassLoader} method is used for answer messages.
	 * 
	 * @param message
	 * @throws AgentNotKnownException If the designated recipient is not known at this node
	 * @throws AgentException If any other issue with the agent occurs, e. g. XML not readable
	 * @throws MessageException
	 * @throws L2pSecurityException
	 */
	public void receiveMessage(Message message)
			throws AgentNotKnownException, AgentException, MessageException, L2pSecurityException {
		if (message.isResponse()) {
			if (handoverAnswer(message)) {
				return;
			}
		}

		// Since this field is not always available
		if (message.getSendingNodeId() != null) {
			observerNotice(Event.MESSAGE_RECEIVED, message.getSendingNodeId(), message.getSenderId(), this.getNodeId(),
					message.getRecipientId(), message.getId() + "");
		} else {
			observerNotice(Event.MESSAGE_RECEIVED, null, message.getSenderId(), this.getNodeId(),
					message.getRecipientId(), message.getId() + "");
		}

		if (!message.isTopic()) {
			MessageReceiver receiver = htRegisteredReceivers.get(message.getRecipientId());

			if (receiver == null) {
				throw new AgentNotKnownException(message.getRecipientId());
			}

			receiver.receiveMessage(message, getAgentContext(message.getSenderId()));
		} else {
			TreeMap<Long, MessageReceiver> map = mapTopicListeners.get(message.getTopicId());

			if (map == null) {
				throw new MessageException("No receiver registered for this topic!");
			}

			try {
				synchronized (map) {
					for (MessageReceiver receiver : map.values()) {
						Message msg = message;
						if (map.size() > 1) {
							msg = msg.clone();
						}

						msg.setRecipientId(receiver.getResponsibleForAgentId());

						receiver.receiveMessage(msg, getAgentContext(message.getSenderId()));
					}
				}
			} catch (CloneNotSupportedException e) {
				throw new MessageException("Cloning failed", e);
			}
		}
	}

	/**
	 * @deprecated Use {@link #fetchEnvelope(String)} instead
	 * 
	 *             Gets an artifact from the p2p storage.
	 * 
	 * @param id
	 * @return the envelope containing the requested artifact
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 */
	@Deprecated
	public abstract Envelope fetchArtifact(long id) throws ArtifactNotFoundException, StorageException;

	/**
	 * @deprecated Use {@link #fetchEnvelope(String)} instead
	 * 
	 *             Gets an artifact from the p2p storage.
	 * 
	 * @param identifier
	 * @return the envelope containing the requested artifact
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 */
	@Deprecated
	public abstract Envelope fetchArtifact(String identifier) throws ArtifactNotFoundException, StorageException;

	/**
	 * @deprecated Use {@link #storeEnvelope(Envelope, Agent)} instead
	 * 
	 *             Stores an artifact to the p2p storage.
	 * 
	 * @param envelope
	 * @throws StorageException
	 */
	@Deprecated
	public abstract void storeArtifact(Envelope envelope) throws EnvelopeAlreadyExistsException, StorageException;

	/**
	 * @deprecated Use {@link #removeEnvelope(String)} instead
	 * 
	 *             Removes an artifact from the p2p storage. <i>NOTE: This is not possible with a FreePastry
	 *             backend!</i>
	 * 
	 * @param id
	 * @param signature
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 */
	@Deprecated
	public abstract void removeArtifact(long id, byte[] signature) throws ArtifactNotFoundException, StorageException;

	/**
	 * Searches the nodes for registered Versions of the given Agent. Returns an array of objects identifying the nodes
	 * the given agent is registered to.
	 * 
	 * @param agentId id of the agent to look for
	 * @param hintOfExpectedCount a hint for the expected number of results (e.g. to wait for)
	 * @return array with the IDs of nodes, where the given agent is registered
	 * @throws AgentNotKnownException If the agent is not registered at this node
	 */
	public abstract Object[] findRegisteredAgent(long agentId, int hintOfExpectedCount) throws AgentNotKnownException;

	/**
	 * Search the nodes for registered versions of the given agent. Returns an array of objects identifying the nodes
	 * the given agent is registered to.
	 * 
	 * @param agent
	 * @return array with the IDs of nodes, where the given agent is registered
	 * @throws AgentNotKnownException If the agent is not registered at this node
	 */
	public Object[] findRegisteredAgent(Agent agent) throws AgentNotKnownException {
		return findRegisteredAgent(agent.getId());
	}

	/**
	 * Searches the nodes for registered versions of the given agentId. Returns an array of objects identifying the
	 * nodes the given agent is registered to.
	 * 
	 * @param agentId id of the agent to look for
	 * @return array with the IDs of nodes, where the given agent is registered
	 * @throws AgentNotKnownException If the agent is not registered at this node
	 */
	public Object[] findRegisteredAgent(long agentId) throws AgentNotKnownException {
		return findRegisteredAgent(agentId, 1);
	}

	/**
	 * searches the nodes for registered versions of the given agent. Returns an array of objects identifying the nodes
	 * the given agent is registered to.
	 * 
	 * @param agent
	 * @param hintOfExpectedCount a hint for the expected number of results (e.g. to wait for)
	 * @return array with the IDs of nodes, where the given agent is registered
	 * @throws AgentNotKnownException If the agent is not registered at this node
	 */
	public Object[] findRegisteredAgent(Agent agent, int hintOfExpectedCount) throws AgentNotKnownException {
		return findRegisteredAgent(agent.getId(), hintOfExpectedCount);
	}

	/**
	 * Gets an agent description from the net.
	 * 
	 * make sure, always to return fresh versions of the requested agent, so that no thread can unlock the private key
	 * for another one!
	 * 
	 * @param id
	 * @return the requested agent
	 * @throws AgentNotKnownException If the agent is not found
	 * @throws AgentException If any other issue with the agent occurs, e. g. XML not readable
	 */
	@Override
	public abstract Agent getAgent(long id) throws AgentNotKnownException, AgentException;

	@Override
	public boolean hasAgent(long id) throws AgentException {
		// Since an request for this agent is probable after this check, it makes sense
		// to try to load it into this node and decide afterwards

		try {
			getAgent(id);
			return true;
		} catch (AgentNotKnownException e) {
			return false;
		}
	}

	/**
	 * Gets a local registered agent by its id.
	 * 
	 * @param id
	 * @return the agent registered to this node
	 * @throws AgentNotKnownException If the agent is not found at this node
	 */
	public Agent getLocalAgent(long id) throws AgentNotKnownException {
		MessageReceiver result = htRegisteredReceivers.get(id);

		if (result == null) {
			throw new AgentNotKnownException("The given agent agent is not registered to this node");
		}

		if (result instanceof Agent) {
			return (Agent) result;
		} else {
			throw new AgentNotKnownException("The requested Agent is only known as a Mediator here!");
		}
	}

	/**
	 * Gets an array with all {@link i5.las2peer.security.UserAgent}s registered at this node.
	 * 
	 * @return all local registered UserAgents
	 */
	public UserAgent[] getRegisteredAgents() {
		Vector<UserAgent> result = new Vector<UserAgent>();

		for (MessageReceiver rec : htRegisteredReceivers.values()) {
			if (rec instanceof UserAgent) {
				result.add((UserAgent) rec);
			}
		}

		return result.toArray(new UserAgent[0]);
	}

	/**
	 * Gets an array with all {@link i5.las2peer.security.ServiceAgent}s registered at this node.
	 * 
	 * @return all local registered ServiceAgents
	 */
	public ServiceAgent[] getRegisteredServices() {
		Vector<ServiceAgent> result = new Vector<ServiceAgent>();

		for (MessageReceiver rec : htRegisteredReceivers.values()) {
			if (rec instanceof ServiceAgent) {
				result.add((ServiceAgent) rec);
			}
		}

		return result.toArray(new ServiceAgent[0]);

	}

	/**
	 * Gets a local registered mediator for the given agent id. If no mediator exists, registers a new one to this node.
	 * 
	 * @param agent
	 * @return the mediator for the given agent
	 * @throws L2pSecurityException
	 * @throws AgentAlreadyRegisteredException If the agent is already directly registered at this node
	 */
	public Mediator createMediatorForAgent(Agent agent) throws L2pSecurityException, AgentAlreadyRegisteredException {
		if (agent.isLocked()) {
			throw new L2pSecurityException("You need to unlock the agent for mediation!");
		}
		MessageReceiver receiver = htRegisteredReceivers.get(agent.getId());

		if (receiver != null && !(receiver instanceof Mediator)) {
			throw new AgentAlreadyRegisteredException("The requested Agent is registered directly at this node!");
		}

		if (receiver == null) {
			getAgentContext(agent);
		}

		return new Mediator(this, agent);
	}

	/**
	 * Stores a new Agent to the network.
	 * 
	 * @param agent
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs
	 */
	public abstract void storeAgent(Agent agent) throws L2pSecurityException, AgentException;

	/**
	 * Updates an existing agent of the network.
	 * 
	 * @param agent
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs
	 * @throws StorageException
	 */
	public abstract void updateAgent(Agent agent) throws L2pSecurityException, AgentException, StorageException;

	private Agent anonymousAgent = null;

	/**
	 * get an agent to use, if no <i>real</i> agent is available
	 * 
	 * @return a generic anonymous agent
	 */
	public Agent getAnonymous() {
		if (anonymousAgent == null) {
			try {
				anonymousAgent = MockAgentFactory.getAnonymous();
				((UserAgent) anonymousAgent).unlockPrivateKey("anonymous");
			} catch (Exception e1) {
				throw new RuntimeException("No anonymous agent could be initialized!?!", e1);
			}
		}

		Agent result;
		try {
			result = anonymousAgent.cloneLocked();
			((UserAgent) result).unlockPrivateKey("anonymous");
		} catch (Exception e) {
			throw new RuntimeException("Strange - should not happen...");
		}

		return result;
	}

	/**
	 * returns the manager responsible for user management
	 * 
	 * @return this node's user manager
	 */
	public UserAgentManager getUserManager() {
		return userManager;
	}

	/**
	 * Gets an id for the user for the given login name.
	 * 
	 * @param login
	 * @return agent id
	 * @throws AgentNotKnownException If no agent for the given login is found
	 * @throws AgentException If any other issue with the agent occurs, e. g. XML not readable
	 */
	public long getAgentIdForLogin(String login) throws AgentNotKnownException, AgentException {
		return userManager.getAgentIdByLogin(login);
	}

	/**
	 * Gets an id for the user for the given email address.
	 * 
	 * @param email
	 * @return agent id
	 * @throws AgentNotKnownException If no agent for the given email is found
	 * @throws AgentException If any other issue with the agent occurs, e. g. XML not readable
	 */
	public long getAgentIdForEmail(String email) throws AgentNotKnownException, AgentException {
		return userManager.getAgentIdByEmail(email);
	}

	/**
	 * get the manager responsible for the mapping from service alias to service names
	 * 
	 * @return
	 */
	public ServiceAliasManager getServiceAliasManager() {
		return aliasManager;
	}

	/**
	 * Gets an currently running agent executing the given service.
	 * 
	 * Prefer using a locally registered agent.
	 * 
	 * @param service
	 * @param acting
	 * @return the ServiceAgent responsible for the given service class
	 * @throws AgentException If any issue with the agent occurs, e. g. not found, XML not readable
	 */
	public ServiceAgent getServiceAgent(ServiceNameVersion service, Agent acting) throws AgentException {
		ServiceInstance inst = nodeServiceCache.getServiceAgentInstance(service, true, false, acting);
		if (inst.local()) {
			return inst.getServiceAgent();
		} else {
			Agent result = getAgent(inst.getServiceAgentId());
			if (result == null || !(result instanceof ServiceAgent)) {
				throw new AgentNotKnownException("The corresponding agent is not a ServiceAgent!?");
			}
			return (ServiceAgent) result;
		}
	}

	/**
	 * invoke a service in the network
	 * 
	 * @param executing
	 * @param service
	 * @param method
	 * @param parameters
	 * @return
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs
	 * @throws L2pServiceException
	 * @throws InterruptedException
	 */
	public Serializable invoke(Agent executing, String service, String method, Serializable[] parameters)
			throws L2pSecurityException, AgentException, L2pServiceException, InterruptedException {
		return invoke(executing, ServiceNameVersion.fromString(service), method, parameters, false, false);
	}

	/**
	 * invoke a service in the network (choosing an appropriate version)
	 * 
	 * @param executing
	 * @param service
	 * @param method
	 * @param parameters
	 * @return
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs
	 * @throws L2pServiceException
	 * @throws InterruptedException
	 */
	public Serializable invoke(Agent executing, ServiceNameVersion service, String method, Serializable[] parameters)
			throws L2pSecurityException, AgentException, L2pServiceException, InterruptedException {
		return invoke(executing, service, method, parameters, false, false);
	}

	/**
	 * invoke a service in the network or locally
	 * 
	 * @param executing
	 * @param service
	 * @param method
	 * @param parameters
	 * @param exactVersion
	 * @return
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs
	 * @throws L2pServiceException
	 * @throws InterruptedException
	 */
	public Serializable invoke(Agent executing, ServiceNameVersion service, String method, Serializable[] parameters,
			boolean exactVersion)
			throws L2pSecurityException, AgentException, L2pServiceException, InterruptedException {
		return invoke(executing, service, method, parameters, exactVersion, false);
	}

	/**
	 * invoke a service method
	 * 
	 * @param executing the executing agent
	 * @param service service to be invoked
	 * @param method service method
	 * @param parameters invocation parameters
	 * @param exactVersion if true, an exact version match is required, otherwise, an appropriate version will be
	 *            choosen
	 * @param localOnly if true, only locally running services are executed
	 * @return invocation result
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs
	 * @throws L2pServiceException
	 * @throws InterruptedException
	 */
	public Serializable invoke(Agent executing, ServiceNameVersion service, String method, Serializable[] parameters,
			boolean exactVersion, boolean localOnly)
			throws L2pSecurityException, AgentException, L2pServiceException, InterruptedException {

		if (getStatus() != NodeStatus.RUNNING) {
			throw new IllegalStateException("You can invoke methods only on a running node!");
		}

		if (executing.isLocked()) {
			throw new L2pSecurityException("The executing agent has to be unlocked to call a RMI");
		}

		int retry = invocationRetryCount;
		while (retry > 0) {
			retry--;

			NodeServiceCache.ServiceInstance instance;

			try {
				instance = this.nodeServiceCache.getServiceAgentInstance(service, exactVersion, localOnly, executing);
			} catch (AgentNotKnownException e) {
				throw new NoSuchServiceException(service.toString(), e);
			}

			if (instance.local()) {
				return invokeLocally(executing, instance.getServiceAgent(), method, parameters);
			} else {
				try {
					return invokeGlobally(executing, instance.getServiceAgentId(), instance.getNodeId(), method,
							parameters);
				} catch (NodeNotFoundException | TimeoutException e) {
					nodeServiceCache.removeGlobalServiceInstance(instance);
					if (retry == 0) {
						throw new L2pServiceException("Cannot reach service.", e);
					}
				}
			}
		}

		throw new IllegalStateException();
	}

	/**
	 * invokes a locally running service agent
	 * 
	 * preferably, use {@link #invoke(Agent, ServiceNameVersion, String, Serializable[], boolean, boolean)}
	 * 
	 * @param executing the executing agent
	 * @param serviceAgent the service agent that should be invoked (must run on this node)
	 * @param method service method
	 * @param parameters method parameters
	 * @return innovation result
	 * @throws L2pSecurityException
	 * @throws AgentNotKnownException If the service agent is not known locally
	 * @throws InterruptedException
	 * @throws L2pServiceException
	 */
	public Serializable invokeLocally(Agent executing, ServiceAgent serviceAgent, String method,
			Serializable[] parameters)
			throws L2pSecurityException, AgentNotKnownException, InterruptedException, L2pServiceException {

		if (getStatus() != NodeStatus.RUNNING) {
			throw new IllegalStateException("You can invoke methods only on a running node!");
		}

		if (executing.isLocked()) {
			throw new L2pSecurityException("The executing agent has to be unlocked to call a RMI");
		}

		// check if local service agent
		if (!hasLocalAgent(serviceAgent)) {
			throw new AgentNotKnownException("This ServiceAgent is not known locally!");
		}

		// execute
		RMITask task = new RMITask(serviceAgent.getServiceNameVersion(), method, parameters);
		AgentContext context = getAgentContext(executing);
		L2pThread thread = new L2pThread(serviceAgent, task, context);
		thread.start();
		thread.join();

		if (thread.hasException()) {
			Exception e = thread.getException();

			if (e instanceof ServiceInvocationException) {
				throw (ServiceInvocationException) e;
			} else if ((e instanceof InvocationTargetException) && (e.getCause() instanceof L2pSecurityException)) {
				// internal L2pSecurityException (like internal method access or unauthorizes object access)
				throw new L2pSecurityException("internal securityException!", e.getCause());
			} else {
				throw new ServiceInvocationException("Internal exception in service", e);
			}
		}

		try {
			return thread.getResult();
		} catch (NotFinishedException e) {
			// should not occur, since we joined before
			throw new L2pServiceException("Interrupted service execution?!", e);
		}
	}

	/**
	 * invokes a service instance in the network
	 * 
	 * preferably, use {@link #invoke(Agent, ServiceNameVersion, String, Serializable[], boolean, boolean)}
	 * 
	 * @param executing teh executing agent
	 * @param serviceAgentId the id of the service agent
	 * @param nodeId id of the node running the agent (may be null)
	 * @param method service method
	 * @param parameters method parameters
	 * @return invocation result
	 * @throws L2pSecurityException
	 * @throws ServiceInvocationException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 * @throws UnlockNeededException
	 * @throws AgentException If any issue with the agent occurs
	 * @throws NodeNotFoundException
	 */
	public Serializable invokeGlobally(Agent executing, long serviceAgentId, Object nodeId, String method,
			Serializable[] parameters) throws L2pSecurityException, ServiceInvocationException, InterruptedException,
			TimeoutException, UnlockNeededException, AgentException, NodeNotFoundException {

		if (getStatus() != NodeStatus.RUNNING) {
			throw new IllegalStateException("You can invoke methods only on a running node!");
		}

		// Do not log service class name (privacy..)
		this.observerNotice(Event.RMI_SENT, this.getNodeId(), executing, null);

		if (executing.isLocked()) {
			throw new L2pSecurityException("The executing agent has to be unlocked to call a RMI");
		}

		ServiceAgent serviceAgent;
		try {
			serviceAgent = (ServiceAgent) getAgent(serviceAgentId);
		} catch (ClassCastException e) {
			throw new AgentNotKnownException("This is not a service agent!", e);
		}

		try {
			Serializable msg;
			if (executing instanceof PassphraseAgent) {
				msg = new UnlockAgentCall(new RMITask(serviceAgent.getServiceNameVersion(), method, parameters),
						((PassphraseAgent) executing).getPassphrase());
			} else {
				msg = new RMITask(serviceAgent.getServiceNameVersion(), method, parameters);
			}
			Message rmiMessage = new Message(executing, serviceAgent, msg);

			if (this instanceof LocalNode) {
				rmiMessage.setSendingNodeId((Long) getNodeId());
			} else {
				rmiMessage.setSendingNodeId((NodeHandle) getNodeId());
			}
			Message resultMessage;

			if (nodeId != null) {
				try {
					resultMessage = sendMessageAndWaitForAnswer(rmiMessage, nodeId);
				} catch (NodeNotFoundException nex) {
					throw nex;
				}
			} else {
				resultMessage = sendMessageAndWaitForAnswer(rmiMessage);
			}

			resultMessage.open(executing, this);
			Object resultContent = resultMessage.getContent();

			if (resultContent instanceof RMIUnlockContent) {
				// service method needed to unlock some envelope(s)
				this.observerNotice(Event.RMI_FAILED, this.getNodeId(), executing,
						"unlocked agent needed at the target node"); // Do not log service class name (privacy..)
				throw new UnlockNeededException("unlocked agent needed at the target node",
						resultMessage.getSendingNodeId(), ((RMIUnlockContent) resultContent).getNodeKey());
			} else if (resultContent instanceof RMIExceptionContent) {
				Exception thrown = ((RMIExceptionContent) resultContent).getException();
				// Do not log service class name (privacy..)
				this.observerNotice(Event.RMI_FAILED, this.getNodeId(), executing, thrown.toString());
				if (thrown instanceof ServiceInvocationException) {
					throw (ServiceInvocationException) thrown;
				} else if ((thrown instanceof InvocationTargetException)
						&& (thrown.getCause() instanceof L2pSecurityException)) {
					// internal L2pSecurityException (like internal method access or unauthorizes object access)
					throw new L2pSecurityException("internal securityException!", thrown.getCause());
				} else {
					throw new ServiceInvocationException("remote exception at target node", thrown);
				}

			} else if (resultContent instanceof RMIResultContent) {
				// Do not log service class name (privacy..)
				this.observerNotice(Event.RMI_SUCCESSFUL, this.getNodeId(), executing, null);
				return ((RMIResultContent) resultContent).getContent();
			} else {
				// Do not log service class name (privacy..)
				this.observerNotice(Event.RMI_FAILED, this.getNodeId(), executing,
						"Unknown RMI response type: " + resultContent.getClass().getCanonicalName());
				throw new ServiceInvocationException(
						"Unknown RMI response type: " + resultContent.getClass().getCanonicalName());
			}
		} catch (AgentNotKnownException e) {
			// Do not log service class name (privacy..)
			this.observerNotice(Event.RMI_FAILED, this.getNodeId(), executing, e.toString());
			throw new NoSuchServiceException(serviceAgent.getServiceNameVersion().toString(), e);
		} catch (EncodingFailedException e) {
			// Do not log service class name (privacy..)
			this.observerNotice(Event.RMI_FAILED, this.getNodeId(), executing, e.toString());
			throw new ServiceInvocationException("message problems!", e);
		} catch (SerializationException e) {
			// Do not log service class name (privacy..)
			this.observerNotice(Event.RMI_FAILED, this.getNodeId(), executing, e.toString());
			throw new ServiceInvocationException("message problems!", e);
		}
	}

	/**
	 * Tries to get an instance of the given class as a registered service of this node.
	 * 
	 * @param service
	 * 
	 * @return the instance of the given service class running at this node
	 * @throws NoSuchServiceException
	 */
	public ServiceAgent getLocalServiceAgent(ServiceNameVersion service) throws NoSuchServiceException {
		try {
			return nodeServiceCache.getLocalService(service);
		} catch (Exception e) {
			throw new NoSuchServiceException(service.toString());
		}
	}

	/**
	 * Registers a MessageResultListener for collecting answers.
	 * 
	 * @param messageId
	 * @param listener
	 */
	public void registerAnswerListener(long messageId, MessageResultListener listener) {
		if (listener == null) {
			return;
		}
		htAnswerListeners.put(messageId, listener);
	}

	/**
	 * Hands over an answer message to the corresponding listener.
	 * 
	 * @param answer
	 * @return true, if a listener for this answer was notified
	 */
	public boolean handoverAnswer(Message answer) {
		if (!answer.isResponse()) {
			return false;
		}

		observerNotice(Event.MESSAGE_RECEIVED_ANSWER, answer.getSendingNodeId(), answer.getSenderId(), this.getNodeId(),
				answer.getRecipientId(), "" + answer.getResponseToId());

		MessageResultListener listener = htAnswerListeners.get(answer.getResponseToId());
		if (listener == null) {
			System.out.println("Did not find corresponding observer!");
			return false;
		}

		listener.collectAnswer(answer);

		return true;
	}

	/**
	 * Sends a message and wait for one answer message.
	 * 
	 * @param m
	 * @return a (possible) response message
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public Message sendMessageAndWaitForAnswer(Message m) throws InterruptedException, TimeoutException {
		long timeout = m.getTimeoutTs() - new Date().getTime();
		MessageResultListener listener = new MessageResultListener(timeout);

		sendMessage(m, listener);

		listener.waitForOneAnswer();

		if (listener.isSuccess()) {
			return listener.getResults()[0];
		} else {
			throw new TimeoutException();
		}
	}

	/**
	 * Sends a message to the given id and wait for one answer message.
	 * 
	 * @param m
	 * @param atNodeId
	 * @return a response message
	 * @throws NodeNotFoundException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public Message sendMessageAndWaitForAnswer(Message m, Object atNodeId)
			throws NodeNotFoundException, InterruptedException, TimeoutException {
		long timeout = m.getTimeoutTs() - new Date().getTime();
		MessageResultListener listener = new MessageResultListener(timeout);

		sendMessage(m, atNodeId, listener);

		listener.waitForOneAnswer();

		if (listener.getResults().length == 0) {
			throw new TimeoutException("No answer received!");
		}

		return listener.getResults()[0];
	}

	/**
	 * Sends a message and wait for answer messages
	 * 
	 * Uses a broadcast
	 * 
	 * @param m
	 * @param recipientCount expected number of answers
	 * @return
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public Message[] sendMessageAndCollectAnswers(Message m, int recipientCount)
			throws InterruptedException, TimeoutException {
		long timeout = m.getTimeoutTs() - new Date().getTime();
		MessageResultListener listener = new MessageResultListener(timeout, timeout / 4);
		listener.addRecipients(recipientCount);

		sendMessage(m, listener, SendMode.BROADCAST);

		listener.waitForAllAnswers(false);

		Message[] results = listener.getResults();

		if (results.length > 0) {
			return results;
		} else {
			throw new TimeoutException("No answer received!");
		}
	}

	/**
	 * Gets the local execution context of an agent. If there is currently none, a new one will be created and stored
	 * for later use.
	 * 
	 * @param agentId
	 * @return the context for the given agent
	 * @throws L2pSecurityException
	 * @throws AgentException If any issue with the agent occurs, e. g. XML not readable
	 */
	protected AgentContext getAgentContext(long agentId) throws L2pSecurityException, AgentException {
		AgentContext result = htLocalExecutionContexts.get(agentId);

		if (result == null) {
			Agent agent = getAgent(agentId);
			result = new AgentContext(this, agent);
			htLocalExecutionContexts.put(agentId, result);
		}

		result.touch();

		return result;
	}

	/**
	 * Gets a (possibly fresh) context for the given agent.
	 * 
	 * @param agent
	 * @return a context
	 */
	protected AgentContext getAgentContext(Agent agent) {
		AgentContext result = htLocalExecutionContexts.get(agent.getId());

		if (result == null || (result.getMainAgent().isLocked() && !agent.isLocked())) {
			try {
				result = new AgentContext(this, agent);
			} catch (L2pSecurityException e) {
			}
			htLocalExecutionContexts.put(agent.getId(), result);
		}

		result.touch();

		return result;
	}

	/**
	 * Checks, if the given service class is running at this node.
	 * 
	 * @param service
	 * @return true, if this node as an instance of the given service running
	 */
	public boolean hasService(ServiceNameVersion service) {
		// return hasAgent(ServiceAgent.serviceClass2Id(service));
		try {
			nodeServiceCache.getLocalService(service);
			return true;
		} catch (AgentNotKnownException e) {
			return false;
		}
	}

	/**
	 * get the NodeServiceCache of this node
	 * 
	 * @return
	 */
	public NodeServiceCache getNodeServiceCache() {
		return this.nodeServiceCache;
	}

	/**
	 * Gets the approximate CPU load of the JVM the Node is running on. Correct value only available a few seconds after
	 * the start of the Node.
	 * 
	 * @return value between 0 and 1: CPU load of the JVM process running this node
	 */
	public double getNodeCpuLoad() {
		double load = osBean.getProcessCpuLoad();
		if (load < 0) { // no CPU load information are available
			load = 0;
		} else if (load > 1) {
			load = 1;
		}
		return load;
	}

	public boolean isBusy() {
		return (getNodeCpuLoad() > cpuLoadThreshold);
	}

	// Tidy up Timer

	/**
	 * starts the tidy up timer
	 */
	private void startTidyUpTimer() {
		if (tidyUpTimer != null) {
			return;
		}
		tidyUpTimer = new Timer();
		tidyUpTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				runTidyUpTimer();
			}
		}, 0, tidyUpTimerInterval * 1000);
	}

	/**
	 * stops the tidy up timer
	 */
	private void stopTidyUpTimer() {
		if (tidyUpTimer != null) {
			tidyUpTimer.cancel();
			tidyUpTimer = null;
		}
	}

	/**
	 * executed by the tidy up timer, currently it does:
	 * 
	 * * Deleting old {@link AgentContext} objects from {@link #htLocalExecutionContexts}
	 */
	protected void runTidyUpTimer() {
		Set<Entry<Long, AgentContext>> s = htLocalExecutionContexts.entrySet();
		synchronized (htLocalExecutionContexts) {
			Iterator<Entry<Long, AgentContext>> i = s.iterator();
			while (i.hasNext()) {
				Entry<Long, AgentContext> e = i.next();
				if (e.getValue().getLastUsageTimestamp() <= new Date().getTime() - agentContextLifetime * 1000) {
					i.remove();
				}
			}
		}
	}

}
