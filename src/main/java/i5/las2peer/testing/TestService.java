package i5.las2peer.testing;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;

import i5.las2peer.api.Service;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.security.AgentContext;
import i5.las2peer.security.GroupAgent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.SerializationException;

/**
 * Simple test service for connectors to have a service with methods to call.
 */
public class TestService extends Service {

	private int iCounter = 0;
	private String sStorage = "";

	/**
	 * a simple counter, returns the number fo calls within each session.
	 *
	 * @return an int
	 */
	public int counter() {
		iCounter++;
		return iCounter;
	}

	/**
	 * returns the stored string
	 *
	 * @return a String
	 */
	public String getStoredString() {
		return sStorage;
	}

	/**
	 * Stores a String in the service instance
	 *
	 * @param s a String
	 */
	public void setStoredString(String s) {
		sStorage = s;
	}

	/**
	 * Throws an Exception
	 *
	 * @exception Exception on each call
	 */
	public void exceptionThrower() throws Exception {
		throw new Exception("An Exception to deal with!");
	}

	/**
	 * Throws a RuntimeException
	 *
	 * @exception RuntimeException on each call
	 */
	public void runtimeExceptionThrower() throws RuntimeException {
		throw new RuntimeException("A RuntimeException to deal with!");
	}

	/**
	 * throws an exception that shouldn't be kown at the client.
	 *
	 * @exception MyOwnException
	 */
	public void myExceptionThrower() throws MyOwnException {
		throw new MyOwnException("This is an exception not kown to the client!");
	}

	/**
	 * returns the sum of an bytearray
	 *
	 * @param bytes a byte[]
	 * @return a long
	 */
	public long byteAdder(byte[] bytes) {
		long result = 0;

		for (byte b : bytes) {
			result += b;
		}

		return result;
	}

	/**
	 * returns a Hashtable with one entry to test complex result types
	 *
	 * @return a Hashtable
	 */
	public Hashtable<String, String> getAHash() {
		Hashtable<String, String> hsResult = new Hashtable<String, String>();
		hsResult.put("x", "y");

		return hsResult;
	}

	/**
	 * Returns a fixed array with strings.
	 *
	 * @return a String[]
	 */
	public String[] stringArrayReturner() {
		return new String[] { "This", "is an", "array", "with Strings" };
	}

	/**
	 * Returns a reverted version of the given string array
	 *
	 * @param ar a String[]
	 * @return a String[]
	 */
	public String[] stringArrayReturner(String[] ar) {
		String[] asResult = new String[ar.length];

		for (int i = 0; i < asResult.length; i++) {
			asResult[asResult.length - i - 1] = ar[i];
		}

		return asResult;
	}

	/**
	 * @return an empty array of strings
	 */
	public String[] emptyStringArrayReturner() {
		return new String[0];
	}

	/**
	 * A method with multiple arguments returning a string summary over the argument
	 *
	 * @param i an int
	 * @param s a String
	 * @param l a long
	 * @param b a boolean
	 * @return a String
	 */
	public String multipleArguments(int i, String s, long l, boolean b) {
		return "The method has been called with the following arguments:\n" + "an Integer: " + i + "\n" + "a String: "
				+ s + "\n" + "a long: " + l + "\n" + "a boolean: " + b + "\n";
	}

	/**
	 * A method with multiple arguments returning a string summary over the argument
	 *
	 * @param i an int
	 * @param s a String
	 * @param l a long
	 * @param b a boolean
	 * @return a String
	 */
	public String multipleArguments2(Integer i, String s, Long l, Boolean b) {
		return "The method has been called with the following arguments:\n" + "an Integer: " + i + "\n" + "a String: "
				+ s + "\n" + "a long: " + l + "\n" + "a boolean: " + b + "\n";
	}

	/**
	 * throws an Exception indication, that the access (for the current user) has been denied.
	 *
	 * @exception L2pSecurityException
	 */
	public void accessForbidden() throws L2pSecurityException {
		throw new L2pSecurityException("access forbidden!");
	}

	/**
	 * concats an array of String to one single String. Basically for testing array of Strings as invocation parameter.
	 *
	 * @param strings a String[]
	 * @return a String
	 */
	public String concatStrings(String[] strings) {
		if (strings == null) {
			return "";
		}

		StringBuffer buffer = new StringBuffer();
		for (String string : strings) {
			buffer.append(string);
		}

		return buffer.toString();
	}

	/**
	 * simply returns the given byte array for connector coding tests
	 *
	 * @param ab a byte[]
	 * @return a byte[]
	 */
	public byte[] byteArrayReturner(byte[] ab) {
		return ab;
	}

	/**
	 * simply returns the given long array for connector coding testing
	 *
	 * @param al a long[]
	 * @return a long[]
	 */
	public long[] longArrayReturner(long[] al) {
		return al;
	}

	/**
	 * simply returns the given long value for connector coding testing
	 *
	 * @param l a long
	 * @return a long
	 */
	public long longReturner(long l) {
		return l;
	}

	/**
	 * Returns the given date increased by one day. This may be used as test for delivering Serializables in connectors.
	 *
	 * @param input a Date
	 * @return a Date
	 */
	public java.util.Date addADay(java.util.Date input) {
		long time = input.getTime();
		time += 1000 * 60 * 60 * 24;
		java.util.Date result = new java.util.Date(time);

		return result;
	}

	/**
	 * access to the property file
	 * 
	 * @return hashtable with properties
	 */
	public Hashtable<String, String> getProps() {
		return getProperties();
	}

	private Envelope cache = null;

	/**
	 * test for envelopes: store a string in an envelope
	 * 
	 * @return previously stored string
	 * @throws L2pSecurityException
	 * @throws CryptoException
	 * @throws SerializationException
	 */
	public String getEnvelopeString() throws L2pSecurityException, CryptoException, SerializationException {
		if (cache == null) {
			return "nothing stored!";
		}
		String result = (String) cache.getContent(AgentContext.getCurrent().getMainAgent());
		return result;
	}

	/**
	 * test for envelopes: get stored String
	 * 
	 * @param s
	 * @throws IllegalArgumentException
	 * @throws SerializationException
	 * @throws CryptoException
	 */
	public void storeEnvelopeString(String s) throws IllegalArgumentException, SerializationException, CryptoException {
		cache = AgentContext.getCurrent().createEnvelope(Long.toString(new Random().nextLong()), s);
	}

	private Envelope groupCache = null;

	/**
	 * get a string stored for the Group1
	 * 
	 * @return a simple stored string
	 * @throws L2pSecurityException
	 * @throws SerializationException
	 * @throws CryptoException
	 */
	public String getGroupEnvelopeString()
			throws L2pSecurityException, CryptoException, SerializationException, MalformedXMLException, IOException {
		if (groupCache == null) {
			return "nothing stored";
		}
		GroupAgent group = MockAgentFactory.getGroup1();
		group.unlockPrivateKey(AgentContext.getCurrent().getMainAgent());
		String result = (String) groupCache.getContent(group);
		return result;
	}

	/**
	 * store a simple string encrypted for the group
	 * 
	 * @param store a string to store for the group
	 * @throws IllegalArgumentException
	 * @throws SerializationException
	 * @throws CryptoException
	 * @throws MalformedXMLException
	 * @throws IOException
	 */
	public void storeGroupEnvelopeString(String store) throws IllegalArgumentException, SerializationException,
			CryptoException, MalformedXMLException, IOException {
		groupCache = AgentContext.getCurrent().createEnvelope(Long.toString(new Random().nextLong()), store,
				MockAgentFactory.getGroup1());
	}

}
