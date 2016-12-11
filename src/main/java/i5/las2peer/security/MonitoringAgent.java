package i5.las2peer.security;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Random;

import org.w3c.dom.Element;

import i5.las2peer.communication.Message;
import i5.las2peer.communication.MessageException;
import i5.las2peer.execution.L2pServiceException;
import i5.las2peer.execution.NoSuchServiceException;
import i5.las2peer.logging.monitoring.MonitoringMessage;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.CryptoTools;
import i5.las2peer.tools.SerializationException;
import i5.las2peer.tools.SerializeTools;
import i5.las2peer.tools.XmlTools;

/**
 * 
 * A MonitoringAgent is responsible for sending monitoring information collected at the
 * {@link i5.las2peer.logging.monitoring.MonitoringObserver}. It should only be used for this task.
 * 
 * 
 *
 */
public class MonitoringAgent extends PassphraseAgent {

	public static final String PROCESSING_SERVICE_CLASS_NAME = "i5.las2peer.services.mobsos.dataProcessing.MonitoringDataProcessingService";

	/**
	 * 
	 * Creates a new MonitoringAgent.
	 * 
	 * @param id
	 * @param pair
	 * @param passphrase
	 * @param salt
	 * @throws L2pSecurityException
	 * @throws CryptoException
	 * 
	 */
	protected MonitoringAgent(long id, KeyPair pair, String passphrase, byte[] salt)
			throws L2pSecurityException, CryptoException {
		super(id, pair, passphrase, salt);
	}

	/**
	 * 
	 * Creates a new MonitoringAgent with a locked private key.
	 * 
	 * Used within {@link #createFromXml}.
	 * 
	 * @param id
	 * @param pubKey
	 * @param encodedPrivate
	 * @param salt
	 * 
	 */
	protected MonitoringAgent(long id, PublicKey pubKey, byte[] encodedPrivate, byte[] salt) {
		super(id, pubKey, encodedPrivate, salt);
	}

	/**
	 * 
	 * Create a new MonitoringAgent protected by the given passphrase.
	 * 
	 * @param passphrase passphrase for the secret key of the new agent
	 * @return a new UserAgent
	 * @throws CryptoException
	 * @throws L2pSecurityException
	 * 
	 */
	public static MonitoringAgent createMonitoringAgent(String passphrase)
			throws CryptoException, L2pSecurityException {
		Random r = new Random();
		return new MonitoringAgent(r.nextLong(), CryptoTools.generateKeyPair(), passphrase, CryptoTools.generateSalt());
	}

	/**
	 * 
	 * This method is called by the node this agent is running at. In this context, it is used to receive monitoring
	 * messages send by the {@link i5.las2peer.logging.monitoring.MonitoringObserver}s of the monitored nodes to the
	 * central processing service. Every other type of communication (to agents not resided at the monitoring node,
	 * other types of content than {@link i5.las2peer.logging.monitoring.MonitoringMessage}s will result in an
	 * Exception.
	 * 
	 * @param message
	 * @param context
	 * @throws MessageException
	 * 
	 */
	@Override
	public void receiveMessage(Message message, AgentContext context) throws MessageException {
		try {
			// Test for instance
			message.open(this, getRunningAtNode());
			Object content = message.getContent();
			if (content instanceof MonitoringMessage[]) {
				Serializable[] parameters = { (Serializable) content };
				try {
					// Try to send the content of the message to the Processing Service
					boolean success = (Boolean) getRunningAtNode().invoke(this, PROCESSING_SERVICE_CLASS_NAME,
							"getMessages", parameters);
					if (!success) {
						// TODO: Check for performance of message receiving
						System.out.println(
								"Monitoring: Something went wrong while invoking Processing Service to deliver a monitoring message!");
					}
				} catch (NoSuchServiceException e) {
					System.out.println("Monitoring: I am not the Processing Service!");
				} catch (L2pServiceException e) {
					System.out.println("Monitoring: Something went wrong while invoking Processing Service!");
					e.printStackTrace();
				} catch (InterruptedException e) {
					System.out.println("Monitoring: Something went wrong while invoking Processing Service!");
					e.printStackTrace();
				}
			} else {
				throw new MessageException("MonitoringAgents only receive monitoring messages!");
			}
		} catch (L2pSecurityException e) {
			throw new MessageException("Security problems handling the received message", e);
		} catch (AgentNotKnownException e) {
			// Do nothing..("this" is not known..would be strange, eh?)
		} catch (AgentException e) {
			throw new MessageException("Could not read the sender agent", e);
		}
	}

	/**
	 * Can be used to return a XML representation of the MonitoringAgent.
	 * 
	 * @return a XML representation of the MonitoringAgent
	 * @throws RuntimeException thrown, if problems with the serialization came up
	 */
	@Override
	public String toXmlString() {
		try {
			StringBuffer result = new StringBuffer("<las2peer:agent type=\"monitoring\">\n" + "\t<id>" + getId()
					+ "</id>\n" + "\t<publickey encoding=\"base64\">" + SerializeTools.serializeToBase64(getPublicKey())
					+ "</publickey>\n" + "\t<privatekey encrypted=\"" + CryptoTools.getSymmetricAlgorithm()
					+ "\" keygen=\"" + CryptoTools.getSymmetricKeygenMethod() + "\">\n"
					+ "\t\t<salt encoding=\"base64\">" + Base64.getEncoder().encodeToString(getSalt()) + "</salt>\n"
					+ "\t\t<data encoding=\"base64\">" + getEncodedPrivate() + "</data>\n" + "\t</privatekey>\n");

			result.append("</las2peer:agent>\n");

			return result.toString();
		} catch (SerializationException e) {
			throw new RuntimeException("Serialization problems with keys");
		}
	}

	/**
	 * 
	 * Sets the state of the object from a string representation resulting from a previous {@link #toXmlString} call.
	 *
	 * Usually, a standard constructor is used to get a fresh instance of the class and to set the complete state via
	 * this method.
	 * 
	 * @param xml a String
	 * @return
	 * @exception MalformedXMLException
	 * 
	 */
	public static MonitoringAgent createFromXml(String xml) throws MalformedXMLException {
		return createFromXml(XmlTools.getRootElement(xml, "las2peer:agent"));
	}

	/**
	 * 
	 * Sets the state of the object from a string representation resulting from a previous {@link #toXmlString} call.
	 * 
	 * @param rootElement parsed XML document
	 * @return
	 * @exception MalformedXMLException
	 * 
	 */
	public static MonitoringAgent createFromXml(Element rootElement) throws MalformedXMLException {
		try {
			// read id from XML
			Element elId = XmlTools.getSingularElement(rootElement, "id");
			long id = Long.parseLong(elId.getTextContent());
			// read public key from XML
			Element pubKey = XmlTools.getSingularElement(rootElement, "publickey");
			if (!pubKey.getAttribute("encoding").equals("base64")) {
				throw new MalformedXMLException("base64 encoding expected");
			}
			PublicKey publicKey = (PublicKey) SerializeTools.deserializeBase64(pubKey.getTextContent());
			// read private key from XML
			Element privKey = XmlTools.getSingularElement(rootElement, "privatekey");
			if (!privKey.getAttribute("encrypted").equals(CryptoTools.getSymmetricAlgorithm())) {
				throw new MalformedXMLException(CryptoTools.getSymmetricAlgorithm() + " expected");
			}
			if (!privKey.getAttribute("keygen").equals(CryptoTools.getSymmetricKeygenMethod())) {
				throw new MalformedXMLException(CryptoTools.getSymmetricKeygenMethod() + " expected");
			}
			// read salt from XML
			Element elSalt = XmlTools.getSingularElement(rootElement, "salt");
			if (!elSalt.getAttribute("encoding").equals("base64")) {
				throw new MalformedXMLException("base64 encoding expected");
			}
			byte[] salt = Base64.getDecoder().decode(elSalt.getTextContent());
			// read data from XML
			Element data = XmlTools.getSingularElement(rootElement, "data");
			if (!data.getAttribute("encoding").equals("base64")) {
				throw new MalformedXMLException("base64 encoding expected");
			}
			byte[] encPrivate = Base64.getDecoder().decode(data.getTextContent());

			MonitoringAgent result = new MonitoringAgent(id, publicKey, encPrivate, salt);

			return result;
		} catch (SerializationException e) {
			throw new MalformedXMLException("Deserialization problems", e);
		}
	}

	/**
	 * Does nothing.
	 */
	@Override
	public void notifyUnregister() {
		// well..do nothing for the moment.. (something necessary?)
	}

}
