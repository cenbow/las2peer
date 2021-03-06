package i5.las2peer.persistency;

import java.io.Serializable;
import java.util.List;

import i5.las2peer.api.StorageCollisionHandler;
import i5.las2peer.api.StorageEnvelopeHandler;
import i5.las2peer.api.StorageExceptionHandler;
import i5.las2peer.api.StorageStoreResultHandler;
import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.api.exceptions.EnvelopeAlreadyExistsException;
import i5.las2peer.api.exceptions.StorageException;
import i5.las2peer.security.Agent;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.SerializationException;

public interface L2pStorageInterface {

	/**
	 * Creates a new version of an Envelope. The Envelope uses by default the start version number.
	 * 
	 * @param identifier An unique identifier for the Envelope.
	 * @param content The actual content that should be stored.
	 * @param readers An arbitrary number of Agents, who are allowed to read the content.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createEnvelope(String identifier, Serializable content, Agent... readers)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Creates a new version of an Envelope. The Envelope uses by default the start version number.
	 * 
	 * @param identifier An unique identifier for the Envelope.
	 * @param content The actual content that should be stored.
	 * @param readers An arbitrary number of Agents, who are allowed to read the content.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createEnvelope(String identifier, Serializable content, List<Agent> readers)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Creates an continuous version instance for the given Envelope. This method copies the reader list from the
	 * previous Envelope instance.
	 * 
	 * @param previousVersion The previous version of the Envelope that should be updated.
	 * @param content The updated content that should be stored.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createEnvelope(Envelope previousVersion, Serializable content)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Creates an continuous version instance for the given Envelope.
	 * 
	 * @param previousVersion The previous version of the Envelope that should be updated.
	 * @param content The updated content that should be stored.
	 * @param readers An arbitrary number of Agents, who are allowed to read the content.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createEnvelope(Envelope previousVersion, Serializable content, Agent... readers)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Creates an continous version instance for the given Envelope.
	 * 
	 * @param previousVersion The previous version of the Envelope that should be updated.
	 * @param content The updated content that should be stored.
	 * @param readers An arbitrary number of Agents, who are allowed to read the content.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createEnvelope(Envelope previousVersion, Serializable content, List<Agent> readers)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Creates a new version of an unencrypted Envelope. The Envelope uses by default the start version number.
	 * 
	 * @param identifier An unique identifier for the Envelope.
	 * @param content The updated content that should be stored.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createUnencryptedEnvelope(String identifier, Serializable content)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Creates an continous unencrypted version instance for the given Envelope.
	 * 
	 * @param previousVersion The previous version of the Envelope that should be updated.
	 * @param content The updated content that should be stored.
	 * @return Returns the Envelope instance.
	 * @throws IllegalArgumentException If the given identifier is null, the version number is below the start version
	 *             number or too high.
	 * @throws SerializationException If a problem occurs with object serialization.
	 * @throws CryptoException If an cryptographic issue occurs.
	 */
	public Envelope createUnencryptedEnvelope(Envelope previousVersion, Serializable content)
			throws IllegalArgumentException, SerializationException, CryptoException;

	/**
	 * Stores the given Envelope in the network. The content is signed with the key from the given author. If an
	 * exception occurs its wrapped as StorageException. With this method collisions are handled by throwing an
	 * {@link EnvelopeAlreadyExistsException}.
	 * 
	 * @param Envelope The Envelope to store in the network.
	 * @param author The author that is used to sign the content.
	 * @param timeoutMs A timeout after that an {@link StorageException} is thrown.
	 * @throws EnvelopeAlreadyExistsException If an Envelope with the given identifier and version is already known in
	 *             the network.
	 * @throws StorageException If an issue with the storage occurs.
	 */
	public void storeEnvelope(Envelope Envelope, Agent author, long timeoutMs)
			throws EnvelopeAlreadyExistsException, StorageException;

	/**
	 * Stores the given Envelope in the network. The content is signed with the key from the given author. If an
	 * exception occurs the operation is canceled and the exception handler is called. Same for collisions. If the
	 * operations is completed the result handler is called.
	 * 
	 * @param Envelope The Envelope to store in the network.
	 * @param author The author that is used to sign the content.
	 * @param resultHandler A result handler that is called, if the operation terminates.
	 * @param collisionHandler A collision handler that is called, if an Envelope with the given identifier and version
	 *            already exists.
	 * @param exceptionHandler An exception handler that is called, if an exception occurs.
	 */
	public void storeEnvelopeAsync(Envelope Envelope, Agent author, StorageStoreResultHandler resultHandler,
			StorageCollisionHandler collisionHandler, StorageExceptionHandler exceptionHandler);

	/**
	 * Fetches the latest version for the given identifier from the network.
	 * 
	 * @param identifier An unique identifier for the Envelope.
	 * @param timeoutMs A timeout after that an {@link StorageException} is thrown.
	 * @return Returns the fetched Envelope from the network.
	 * @throws ArtifactNotFoundException If no envelope or any part of it was not found in the network.
	 * @throws StorageException If an issue with the storage occurs.
	 */
	public Envelope fetchEnvelope(String identifier, long timeoutMs) throws ArtifactNotFoundException, StorageException;

	/**
	 * Fetches the latest version for the given identifier from the network.
	 * 
	 * @param identifier An unique identifier for the Envelope.
	 * @param envelopeHandler A result handler that is called, if the operation terminates.
	 * @param exceptionHandler An exception handler that is called, if an exception occurs.
	 */
	public void fetchEnvelopeAsync(String identifier, StorageEnvelopeHandler envelopeHandler,
			StorageExceptionHandler exceptionHandler);

	/**
	 * Removes the envelope with the given identifier from the network.
	 * 
	 * @param identifier An unique identifier for the Envelope.
	 * @throws ArtifactNotFoundException If no envelope or any part of it was not found in the network.
	 * @throws StorageException If an issue with the storage occurs.
	 */
	public void removeEnvelope(String identifier) throws ArtifactNotFoundException, StorageException;

}
