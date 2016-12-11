package i5.las2peer.security;

import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.api.exceptions.EnvelopeAlreadyExistsException;
import i5.las2peer.api.exceptions.StorageException;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.p2p.Node;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.SerializationException;

/**
 * Maps usernames and emails to {@link UserAgent}s.
 *
 */
public class UserAgentManager {

	private static final String PREFIX_USER_NAME = "USER_NAME-";
	private static final String PREFIX_USER_MAIL = "USER_MAIL-";

	private Node node;

	public UserAgentManager(Node node) {
		this.node = node;
	}

	/**
	 * Stores login name and email of an user agent to the network
	 * 
	 * @param agent an unlocked UserAgent
	 * @throws DuplicateEmailException
	 * @throws DuplicateLoginNameException
	 * @throws AgentLockedException
	 */
	public void registerUserAgent(UserAgent agent)
			throws DuplicateEmailException, DuplicateLoginNameException, AgentLockedException {
		if (agent.isLocked()) {
			throw new AgentLockedException("Only unlocked Agents can be registered!");
		}
		Long content = agent.getId();
		if (agent.hasLogin()) {
			try {
				String identifier = PREFIX_USER_NAME + agent.getLoginName().toLowerCase();
				Envelope envName = null;
				try {
					Envelope stored = node.fetchEnvelope(identifier);
					envName = node.createUnencryptedEnvelope(stored, content);
				} catch (ArtifactNotFoundException e) {
					envName = node.createUnencryptedEnvelope(identifier, content);
				}
				node.storeEnvelope(envName, agent);
			} catch (EnvelopeAlreadyExistsException e) {
				throw new DuplicateLoginNameException();
			} catch (SerializationException | CryptoException | StorageException e) {
				node.observerNotice(Event.NODE_ERROR, "Envelope error while updating user list: " + e);
			}
		}

		if (agent.hasEmail()) {
			try {
				String identifier = PREFIX_USER_MAIL + agent.getEmail().toLowerCase();
				Envelope envMail = null;
				try {
					envMail = node.fetchEnvelope(identifier);
				} catch (ArtifactNotFoundException e) {
					envMail = node.createUnencryptedEnvelope(identifier, content);
				}
				node.storeEnvelope(envMail, agent);
			} catch (EnvelopeAlreadyExistsException e) {
				throw new DuplicateEmailException();
			} catch (SerializationException | CryptoException | StorageException e) {
				node.observerNotice(Event.NODE_ERROR, "Envelope error while updating user list: " + e);
			}
		}
	}

	/**
	 * updates login name and email of an user
	 * 
	 * @param agent
	 * @throws AgentLockedException
	 * @throws DuplicateEmailException
	 * @throws DuplicateLoginNameException
	 */
	public void updateUserAgent(UserAgent agent)
			throws AgentLockedException, DuplicateEmailException, DuplicateLoginNameException {
		registerUserAgent(agent);
	}

	/**
	 * get an {@link UserAgent}'s id by login name
	 * 
	 * @param name
	 * @return
	 * @throws AgentNotKnownException If no agent for the given login is found
	 * @throws AgentException If any other issue with the agent occurs, e. g. XML not readable
	 */
	public long getAgentIdByLogin(String name) throws AgentNotKnownException, AgentException {
		if (name.equalsIgnoreCase("anonymous")) {
			return node.getAnonymous().getId();
		}
		try {
			Envelope env = node.fetchEnvelope(PREFIX_USER_NAME + name.toLowerCase());
			return (Long) env.getContent();
		} catch (ArtifactNotFoundException e) {
			throw new AgentNotKnownException("Username not found!", e);
		} catch (StorageException | SerializationException | L2pSecurityException | CryptoException e) {
			throw new AgentException("Could not read agent id from storage");
		}
	}

	/**
	 * get an {@link UserAgent}'s id by email address
	 * 
	 * @param email
	 * @return
	 * @throws AgentNotKnownException If no agent for the given email is found
	 * @throws AgentException If any other issue with the agent occurs, e. g. XML not readable
	 */
	public long getAgentIdByEmail(String email) throws AgentNotKnownException, AgentException {
		try {
			Envelope env = node.fetchEnvelope(PREFIX_USER_MAIL + email.toLowerCase());
			return (Long) env.getContent();
		} catch (ArtifactNotFoundException e) {
			throw new AgentNotKnownException("Email not found!", e);
		} catch (StorageException | SerializationException | L2pSecurityException | CryptoException e) {
			throw new AgentException("Could not read email from storage");
		}
	}
}
