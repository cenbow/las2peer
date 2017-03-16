package i5.las2peer.execution;

import i5.las2peer.api.Context;
import i5.las2peer.api.Service;
import i5.las2peer.api.execution.InternalServiceException;
import i5.las2peer.api.execution.ServiceAccessDeniedException;
import i5.las2peer.api.execution.ServiceInvocationException;
import i5.las2peer.api.execution.ServiceInvocationFailedException;
import i5.las2peer.api.execution.ServiceMethodNotFoundException;
import i5.las2peer.api.execution.ServiceNotAvailableException;
import i5.las2peer.api.execution.ServiceNotFoundException;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeAccessDeniedException;
import i5.las2peer.api.persistency.EnvelopeCollisionHandler;
import i5.las2peer.api.persistency.EnvelopeException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.persistency.EnvelopeOperationFailedException;
import i5.las2peer.api.persistency.MergeFailedException;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentAlreadyExistsException;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.api.security.GroupAgent;
import i5.las2peer.api.security.ServiceAgent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.AgentAlreadyRegisteredException;
import i5.las2peer.p2p.Node;
import i5.las2peer.persistency.EnvelopeImpl;
import i5.las2peer.persistency.EnvelopeVersion;
import i5.las2peer.security.AgentContext;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.security.GroupAgentImpl;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.security.ServiceAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.serialization.SerializationException;
import i5.las2peer.tools.CryptoException;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ExecutionContext implements Context {
	final private AgentContext callerContext;
	final private ServiceAgentImpl serviceAgent;
	final private ServiceThreadFactory threadFactory;
	final private ExecutorService executor;
	final private Node node;

	public ExecutionContext(ServiceAgentImpl agent, AgentContext context, Node node) {
		this.serviceAgent = agent;
		this.callerContext = context;
		this.node = node;
		this.threadFactory = new ServiceThreadFactory(this);
		this.executor = Executors.newSingleThreadExecutor(this.threadFactory);
	}

	public static ExecutionContext getCurrent() {
		return ServiceThread.getCurrentContext();
	}

	public AgentContext getCallerContext() {
		return callerContext;
	}

	/*
	 * Context implementation
	 */

	@Override
	public ClassLoader getServiceClassLoader() {
		return serviceAgent.getServiceInstance().getClass().getClassLoader();
	}

	@Override
	public ExecutorService getExecutor() {
		return this.executor;
	}

	@Override
	public Service getService() {
		return this.serviceAgent.getServiceInstance();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends Service> T getService(Class<T> serviceType) {
		return (T) getService();
	}

	@Override
	public ServiceAgent getServiceAgent() {
		return serviceAgent;
	}

	@Override
	public Agent getMainAgent() {
		return callerContext.getMainAgent();
	}

	@Override
	public Serializable invoke(String service, String method, Serializable... parameters)
			throws ServiceNotFoundException, ServiceNotAvailableException, InternalServiceException,
			ServiceMethodNotFoundException, ServiceInvocationFailedException, ServiceAccessDeniedException {
		return invoke(ServiceNameVersion.fromString(service), method, parameters);
	}

	@Override
	public Serializable invoke(ServiceNameVersion service, String method, Serializable... parameters)
			throws ServiceNotFoundException, ServiceNotAvailableException, InternalServiceException,
			ServiceMethodNotFoundException, ServiceInvocationFailedException, ServiceAccessDeniedException {
		return invokeWithAgent(callerContext.getMainAgent(), service, method, parameters);
	}

	@Override
	public Serializable invokeInternally(String service, String method, Serializable... parameters)
			throws ServiceNotFoundException, ServiceNotAvailableException, InternalServiceException,
			ServiceMethodNotFoundException, ServiceInvocationFailedException, ServiceAccessDeniedException {
		return invokeInternally(ServiceNameVersion.fromString(service), method, parameters);
	}

	@Override
	public Serializable invokeInternally(ServiceNameVersion service, String method, Serializable... parameters)
			throws ServiceNotFoundException, ServiceNotAvailableException, InternalServiceException,
			ServiceMethodNotFoundException, ServiceInvocationFailedException, ServiceAccessDeniedException {
		return invokeWithAgent(serviceAgent, service, method, parameters);
	}

	private Serializable invokeWithAgent(AgentImpl agent, ServiceNameVersion service, String method,
			Serializable[] parameters) throws ServiceNotFoundException, ServiceNotAvailableException,
			InternalServiceException, ServiceMethodNotFoundException, ServiceInvocationFailedException,
			ServiceAccessDeniedException {
		try {
			return callerContext.getLocalNode().invoke(agent, service, method, parameters);
		} catch (ServiceNotFoundException | ServiceNotAvailableException | InternalServiceException
				| ServiceMethodNotFoundException | ServiceInvocationFailedException | ServiceAccessDeniedException e) {
			throw e;
		} catch (ServiceInvocationException e) {
			throw new ServiceInvocationFailedException("Service invocation failed.", e);
		} catch (L2pSecurityException e) {
			throw new IllegalStateException("Agent should be unlocked, but it isn't.");
		}
	}

	@Override
	public void monitorEvent(MonitoringEvent event, String message) {
		monitorEvent(null, event, message);
	}

	@Override
	public void monitorEvent(Object from, MonitoringEvent event, String message) {
		monitorEvent(from, event, message, false);

	}

	@Override
	public void monitorEvent(Object from, MonitoringEvent event, String message, boolean includeActingUser) {
		Agent actingUser = null;
		if (includeActingUser) {
			actingUser = getMainAgent();
		}
		String msg = message;
		if (from != null) {
			msg = from.getClass().getName() + ": " + message;
		}
		node.observerNotice(event, node.getNodeId(), (AgentImpl) serviceAgent, null, (AgentImpl) actingUser, msg);

	}

	@Override
	public UserAgent createUserAgent(String passphrase) throws AgentOperationFailedException {
		try {
			UserAgent agent = UserAgentImpl.createUserAgent(passphrase);
			agent.unlock(passphrase);
			return agent;
		} catch (CryptoException | L2pSecurityException | AgentAccessDeniedException e) {
			throw new AgentOperationFailedException(e);
		}
	}

	@Override
	public GroupAgent createGroupAgent(Agent[] members) throws AgentOperationFailedException {
		try {
			GroupAgent agent = GroupAgentImpl.createGroupAgent(members);
			agent.unlock(members[0]);
			return agent;
		} catch (L2pSecurityException | CryptoException | SerializationException | AgentAccessDeniedException e) {
			throw new AgentOperationFailedException(e);
		}
	}

	@Override
	public Agent fetchAgent(String agentId) throws AgentNotFoundException, AgentOperationFailedException {
		try {
			return node.getAgent(agentId);
		} catch (AgentNotFoundException e) {
			throw e;
		} catch (AgentException e) {
			throw new AgentOperationFailedException("Error!", e);
		}

	}

	@Override
	public Agent requestAgent(String agentId, Agent using) throws AgentAccessDeniedException, AgentNotFoundException,
			AgentOperationFailedException {
		return node.getAgentContext((AgentImpl) using).requestAgent(agentId);
	}

	@Override
	public Agent requestAgent(String agentId) throws AgentAccessDeniedException, AgentNotFoundException,
			AgentOperationFailedException {
		return callerContext.requestAgent(agentId);
	}

	@Override
	public void storeAgent(Agent agent) throws AgentAccessDeniedException, AgentAlreadyExistsException,
			AgentOperationFailedException {
		if (agent instanceof GroupAgentImpl) {
			((GroupAgentImpl) agent).apply();
		}

		try {
			node.storeAgent((AgentImpl) agent);
		} catch (AgentAlreadyRegisteredException e) {
			throw new AgentAlreadyExistsException(e);
		} catch (L2pSecurityException e) {
			throw new AgentAccessDeniedException(e);
		} catch (AgentException e) {
			throw new AgentOperationFailedException(e);
		}
	}

	@Override
	public boolean hasAccess(String agentId, Agent using) throws AgentNotFoundException {
		return node.getAgentContext((AgentImpl) using).hasAccess(agentId);
	}

	@Override
	public boolean hasAccess(String agentId) throws AgentNotFoundException {
		return callerContext.hasAccess(agentId);
	}

	// --------------------------------------------------------------

	@Override
	public Logger getLogger(Class<?> cls) {
		return L2pLogger.getInstance(cls);
	}

	@Override
	public Envelope requestEnvelope(String identifier, Agent using) throws EnvelopeAccessDeniedException,
			EnvelopeNotFoundException, EnvelopeOperationFailedException {
		EnvelopeVersion version;
		try {
			version = node.fetchEnvelope(identifier);
		} catch (EnvelopeNotFoundException e1) {
			throw e1;
		} catch (EnvelopeException e1) {
			throw new EnvelopeOperationFailedException("Problems with the storage!", e1);
		}

		Envelope envelope;
		try {
			envelope = new EnvelopeImpl(version, node.getAgentContext((AgentImpl) using));
		} catch (CryptoException | L2pSecurityException e) {
			throw new EnvelopeAccessDeniedException("This agent does not have access to the envelope!", e);
		} catch (SerializationException e) {
			throw new EnvelopeOperationFailedException("Envelope cannot be deserialized!", e);
		}
		return envelope;
	}

	@Override
	public Envelope requestEnvelope(String identifier) throws EnvelopeAccessDeniedException, EnvelopeNotFoundException,
			EnvelopeOperationFailedException {
		return requestEnvelope(identifier, callerContext.getMainAgent());
	}

	@Override
	public void storeEnvelope(Envelope env, Agent using) throws EnvelopeAccessDeniedException,
			EnvelopeOperationFailedException {
		storeEnvelope(env, (Envelope env1, Envelope env2) -> {
			throw new MergeFailedException("No collision handler implemented.");
		}, using);
	}

	@Override
	public void storeEnvelope(Envelope env) throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
		storeEnvelope(env, callerContext.getMainAgent());
	}

	@Override
	public void storeEnvelope(Envelope env, EnvelopeCollisionHandler handler, Agent using)
			throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
		// TODO API collision handler

		EnvelopeImpl envelope = (EnvelopeImpl) env;

		Set<PublicKey> keys;

		if (envelope.getRevokeAllReaders()) {
			keys = new HashSet<PublicKey>();
		} else {
			keys = envelope.getVersion().getReaderKeys().keySet();
		}

		for (AgentImpl a : envelope.getReaderToRevoke()) {
			keys.remove(a.getPublicKey());
		}
		for (AgentImpl a : envelope.getReaderToAdd()) {
			keys.add(a.getPublicKey());
		}

		EnvelopeVersion version;
		try {
			AgentImpl signing = (AgentImpl) requestAgent(envelope.getSigningAgentId());
			version = node.createEnvelope(envelope.getVersion(), envelope.getContent(), keys);
			node.storeEnvelope(version, signing);
		} catch (IllegalArgumentException | SerializationException e) {
			throw new EnvelopeOperationFailedException(e);
		} catch (AgentAccessDeniedException | AgentNotFoundException | EnvelopeAccessDeniedException | CryptoException e) {
			throw new EnvelopeAccessDeniedException(e);
		} catch (EnvelopeException | AgentException e) {
			throw new EnvelopeOperationFailedException(e);
		}

		envelope.setVersion(version);

	}

	@Override
	public void storeEnvelope(Envelope env, EnvelopeCollisionHandler handler) throws EnvelopeAccessDeniedException,
			EnvelopeOperationFailedException {
		storeEnvelope(env, handler, callerContext.getMainAgent());
	}

	@Override
	public void reclaimEnvelope(String identifier, Agent using) throws EnvelopeAccessDeniedException,
			EnvelopeNotFoundException, EnvelopeOperationFailedException {
		try {
			node.removeEnvelope(identifier);
		} catch (EnvelopeException e) {
			throw new EnvelopeOperationFailedException("The operation failed.", e);
		}
	}

	@Override
	public void reclaimEnvelope(String identifier) throws EnvelopeAccessDeniedException, EnvelopeNotFoundException,
			EnvelopeOperationFailedException {
		reclaimEnvelope(identifier, callerContext.getMainAgent());
	}

	@Override
	public Envelope createEnvelope(String identifier, Agent using) throws EnvelopeOperationFailedException {
		EnvelopeImpl envelope = new EnvelopeImpl(identifier, (AgentImpl) using);
		envelope.addReader(using);
		return envelope;
	}

	@Override
	public Envelope createEnvelope(String identifier) throws EnvelopeOperationFailedException {
		return createEnvelope(identifier, callerContext.getMainAgent());
	}

}