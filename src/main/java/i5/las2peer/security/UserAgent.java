package i5.las2peer.security;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Random;
import java.util.regex.Pattern;

import org.w3c.dom.Element;

import i5.las2peer.communication.Message;
import i5.las2peer.communication.MessageException;
import i5.las2peer.communication.PingPongContent;
import i5.las2peer.p2p.NodeNotFoundException;
import i5.las2peer.persistency.EncodingFailedException;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.CryptoTools;
import i5.las2peer.tools.SerializationException;
import i5.las2peer.tools.SerializeTools;
import i5.las2peer.tools.XmlTools;

/**
 * An UserAgent represent a (End)user of the las2peer system.
 * 
 */
public class UserAgent extends PassphraseAgent {

	private String sLoginName = null;
	private String sEmail = null;
	private Serializable userData = null;

	/**
	 * atm constructor for the MockAgent class, just don't know, how agent creation will take place later
	 * 
	 * @param id
	 * @param pair
	 * @param passphrase
	 * @param salt
	 * @throws L2pSecurityException
	 * @throws CryptoException
	 */
	protected UserAgent(long id, KeyPair pair, String passphrase, byte[] salt)
			throws L2pSecurityException, CryptoException {
		super(id, pair, passphrase, salt);

	}

	/**
	 * create an agent with a locked private key
	 * 
	 * used within {@link #createFromXml}
	 * 
	 * @param id
	 * @param pubKey
	 * @param encryptedPrivate
	 * @param salt
	 */
	protected UserAgent(long id, PublicKey pubKey, byte[] encryptedPrivate, byte[] salt) {
		super(id, pubKey, encryptedPrivate, salt);
	}

	/**
	 * get the login name stored for this user agent
	 * 
	 * @return the user login name
	 */
	public String getLoginName() {
		return sLoginName;
	}

	/**
	 * has this user a login name
	 * 
	 * @return true, if a login name is assigned
	 */
	public boolean hasLogin() {
		return sLoginName != null;
	}

	/**
	 * select a login name for this agent
	 * 
	 * @param loginName
	 * @throws L2pSecurityException
	 * @throws UserAgentException
	 */
	public void setLoginName(String loginName) throws L2pSecurityException, UserAgentException {
		if (this.isLocked()) {
			throw new L2pSecurityException("unlock needed first!");
		}

		if (loginName != null && loginName.length() < 4) {
			throw new UserAgentException("please use a login name longer than three characters!");
		}

		if (loginName != null && !(loginName.matches("[a-zA-Z].*"))) {
			throw new UserAgentException("please use a login name startung with a normal character (a-z or A-Z)");
		}

		// duplicate check is performed when storing/updating an UserAgent in a Node
		this.sLoginName = loginName;
	}

	/**
	 * select an email address to assign to this user agent
	 * 
	 * @param email
	 * @throws L2pSecurityException
	 * @throws UserAgentException
	 */
	public void setEmail(String email) throws L2pSecurityException, UserAgentException {
		if (this.isLocked()) {
			throw new L2pSecurityException("unlock needed first!");
		}

		// http://stackoverflow.com/questions/153716/verify-email-in-java
		Pattern rfc2822 = Pattern.compile(
				"^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$");

		if (email != null && !email.contains("@") && !rfc2822.matcher(email).matches()) {
			throw new UserAgentException("Invalid e-mail address");
		}

		// duplicate check is performed when storing/updating an UserAgent in a Node
		this.sEmail = email.toLowerCase();
	}

	/**
	 * Attaches the given object directly to this agent. The user data represent a field of this user agent and should
	 * be used with small values (&lt; 1MB) only. Larger byte amounts could handicap the agent handling inside the
	 * network.
	 * 
	 * @param object The user data object to be serialized and attached.
	 * @throws L2pSecurityException When the user agent is still locked.
	 */
	public void setUserData(Serializable object) throws L2pSecurityException {
		if (this.isLocked()) {
			throw new L2pSecurityException("unlock needed first!");
		}
		this.userData = object;
	}

	@Override
	public String toXmlString() {
		try {
			StringBuffer result = new StringBuffer("<las2peer:agent type=\"user\">\n" + "\t<id>" + getId() + "</id>\n"
					+ "\t<publickey encoding=\"base64\">" + SerializeTools.serializeToBase64(getPublicKey())
					+ "</publickey>\n" + "\t<privatekey encrypted=\"" + CryptoTools.getSymmetricAlgorithm()
					+ "\" keygen=\"" + CryptoTools.getSymmetricKeygenMethod() + "\">\n"
					+ "\t\t<salt encoding=\"base64\">" + Base64.getEncoder().encodeToString(getSalt()) + "</salt>\n"
					+ "\t\t<data encoding=\"base64\">" + getEncodedPrivate() + "</data>\n" + "\t</privatekey>\n");

			if (sLoginName != null) {
				result.append("\t<login>" + sLoginName + "</login>\n");
			}
			if (sEmail != null) {
				result.append("\t<email>" + sEmail + "</email>\n");
			}
			if (userData != null) {
				result.append("\t<userdata encoding=\"base64\">" + SerializeTools.serializeToBase64(userData)
						+ "</userdata>\n");
			}

			result.append("</las2peer:agent>\n");

			return result.toString();
		} catch (SerializationException e) {
			throw new RuntimeException("Serialization problems with keys");
		}
	}

	/**
	 * sets the state of the object from a string representation resulting from a previous {@link #toXmlString} call.
	 *
	 * Usually, a standard constructor is used to get a fresh instance of the class and the set the complete state via
	 * this method.
	 *
	 *
	 * @param xml a String
	 * @return
	 *
	 * @exception MalformedXMLException
	 *
	 */
	public static UserAgent createFromXml(String xml) throws MalformedXMLException {
		return createFromXml(XmlTools.getRootElement(xml, "las2peer:agent"));
	}

	/**
	 * Create a new UserAgent protected by the given passphrase.
	 * 
	 * @param passphrase passphrase for the secret key of the new user
	 * @return a new UserAgent
	 * @throws CryptoException
	 * @throws L2pSecurityException
	 */
	public static UserAgent createUserAgent(String passphrase) throws CryptoException, L2pSecurityException {
		Random r = new Random();

		byte[] salt = CryptoTools.generateSalt();

		return new UserAgent(r.nextLong(), CryptoTools.generateKeyPair(), passphrase, salt);
	}

	/**
	 * Create a new UserAgent with a given id protected by the given passphrase.
	 * 
	 * @param id agent id of new user
	 * @param passphrase passphrase for the secret key of the new user
	 * @return a new UserAgent
	 * @throws CryptoException
	 * @throws L2pSecurityException
	 */
	public static UserAgent createUserAgent(long id, String passphrase) throws CryptoException, L2pSecurityException {

		byte[] salt = CryptoTools.generateSalt();

		return new UserAgent(id, CryptoTools.generateKeyPair(), passphrase, salt);
	}

	/**
	 * Sets the state of the object from a string representation resulting from a previous {@link #toXmlString} call.
	 *
	 * @param root parsed XML document
	 * @return
	 *
	 * @exception MalformedXMLException
	 *
	 */
	public static UserAgent createFromXml(Element root) throws MalformedXMLException {
		try {
			// read id field from XML
			Element elId = XmlTools.getSingularElement(root, "id");
			long id = Long.parseLong(elId.getTextContent());
			// read public key from XML
			Element pubKey = XmlTools.getSingularElement(root, "publickey");
			if (!pubKey.getAttribute("encoding").equals("base64")) {
				throw new MalformedXMLException("base64 encoding expected");
			}
			PublicKey publicKey = (PublicKey) SerializeTools.deserializeBase64(pubKey.getTextContent());
			// read private key from XML
			Element privKey = XmlTools.getSingularElement(root, "privatekey");
			if (!privKey.getAttribute("encrypted").equals(CryptoTools.getSymmetricAlgorithm())) {
				throw new MalformedXMLException(CryptoTools.getSymmetricAlgorithm() + " expected");
			}
			if (!privKey.getAttribute("keygen").equals(CryptoTools.getSymmetricKeygenMethod())) {
				throw new MalformedXMLException(CryptoTools.getSymmetricKeygenMethod() + " expected");
			}
			Element dataPrivate = XmlTools.getSingularElement(privKey, "data");
			byte[] encPrivate = Base64.getDecoder().decode(dataPrivate.getTextContent());
			// read salt from XML
			Element elSalt = XmlTools.getSingularElement(root, "salt");
			if (!elSalt.getAttribute("encoding").equals("base64")) {
				throw new MalformedXMLException("base64 encoding expected");
			}
			byte[] salt = Base64.getDecoder().decode(elSalt.getTextContent());

			// required fields complete, create result
			UserAgent result = new UserAgent(id, publicKey, encPrivate, salt);

			// read and set optional fields

			// optional login name
			Element login = XmlTools.getOptionalElement(root, "login");
			if (login != null) {
				result.sLoginName = login.getTextContent();
			}
			// optional email address
			Element email = XmlTools.getOptionalElement(root, "email");
			if (email != null) {
				result.sEmail = email.getTextContent();
			}
			// optional user data
			Element userdata = XmlTools.getOptionalElement(root, "userdata");
			if (userdata != null) {
				if (userdata.hasAttribute("encoding")
						&& !userdata.getAttribute("encoding").equalsIgnoreCase("base64")) {
					throw new MalformedXMLException("base64 encoding expected");
				}
				result.userData = SerializeTools.deserializeBase64(userdata.getTextContent());
			}

			return result;
		} catch (SerializationException e) {
			throw new MalformedXMLException("Deserialization problems", e);
		}
	}

	@Override
	public void receiveMessage(Message message, AgentContext context) throws MessageException {
		try {
			message.open(this, getRunningAtNode());
			Object content = message.getContent();

			if (content instanceof PingPongContent) {
				Message answer = new Message(message, new PingPongContent());

				System.out.println("PingPong: sending answer!");

				getRunningAtNode().sendResponse(answer, message.getSendingNodeId());
			} else {
				System.out.println("got message: " + message.getContent().getClass() + " / " + message.getContent());
				System.out.println("response: " + message.getResponseToId());
				throw new MessageException("What to do with this message?!");
			}
		} catch (L2pSecurityException e) {
			throw new MessageException("Security problems handling the received message", e);
		} catch (EncodingFailedException e) {
			throw new MessageException("encoding problems with sending an answer", e);
		} catch (SerializationException e) {
			throw new MessageException("serialization problems with sending an answer", e);
		} catch (AgentException e) {
			// just fire and forget
		} catch (NodeNotFoundException e) {
			// just fire and forget
		}
	}

	@Override
	public void notifyUnregister() {
		// do nothing
	}

	/**
	 * get the email address assigned to this agent
	 * 
	 * @return an email address
	 */
	public String getEmail() {
		return sEmail;
	}

	/**
	 * has this user a registered email address?
	 * 
	 * @return true, if an email address is assigned
	 */
	public boolean hasEmail() {
		return sEmail != null;
	}

	/**
	 * get the user data assigned to this agent
	 * 
	 * @return Returns the user data object
	 */
	public Serializable getUserData() {
		return this.userData;
	}

}
