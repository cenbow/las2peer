package i5.las2peer.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.serialization.SerializationException;
import i5.las2peer.tools.CryptoException;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class GroupAgentImplTest {

	private static final String ADAMSPASS = "adamspass";
	private static final String EVESSPASS = "evesspass";
	private static final String KAINSPASS = "kainspass";
	private static final String ABELSPASS = "abelspass";

	private UserAgentImpl adam;
	private UserAgentImpl eve;
	private UserAgentImpl kain;
	private UserAgentImpl abel;

	@Before
	public void setUp() throws NoSuchAlgorithmException, L2pSecurityException, CryptoException {
		adam = UserAgentImpl.createUserAgent(ADAMSPASS);
		eve = UserAgentImpl.createUserAgent(EVESSPASS);
		kain = UserAgentImpl.createUserAgent(KAINSPASS);
		abel = UserAgentImpl.createUserAgent(ABELSPASS);
	}

	@Test
	public void testXmlAndBack() throws NoSuchAlgorithmException, L2pSecurityException, CryptoException,
			SerializationException, MalformedXMLException, AgentAccessDeniedException, AgentOperationFailedException {
		GroupAgentImpl testee = GroupAgentImpl.createGroupAgent(new AgentImpl[] { adam, eve });
		assertEquals(2, testee.getSize());
		assertFalse(testee.hasMember(kain));
		assertFalse(testee.hasMember(abel.getIdentifier()));
		assertTrue(testee.hasMember(adam));
		assertTrue(testee.hasMember(eve.getIdentifier()));

		assertTrue(testee.isLocked());

		adam.unlock(ADAMSPASS);
		testee.unlock(adam);
		String groupname = "Test Group";
		testee.setName(groupname);
		String userData = "This is the user data attachement.";
		testee.setUserData(userData);

		String xml = testee.toXmlString();
		System.out.println(xml);

		GroupAgentImpl fromXml = GroupAgentImpl.createFromXml(xml);

		assertEquals(2, fromXml.getSize());
		assertFalse(testee.hasMember(kain));
		assertFalse(testee.hasMember(abel.getIdentifier()));
		assertTrue(testee.hasMember(adam));
		assertTrue(testee.hasMember(eve.getIdentifier()));

		assertTrue(fromXml.isLocked());

		assertEquals(fromXml.getName(), groupname);
		assertEquals(fromXml.getUserData(), userData);
	}

	@Test
	public void testUnlocking() throws L2pSecurityException, CryptoException, SerializationException,
			AgentAccessDeniedException, AgentOperationFailedException {
		GroupAgentImpl testee = GroupAgentImpl.createGroupAgent(new AgentImpl[] { adam, eve });

		try {
			testee.addMember(kain);
			fail("SecurityException should have been thrown!");
		} catch (L2pSecurityException e) {
		}

		try {
			testee.unlock(adam);
			fail("SecurityException should have been thrown!");
		} catch (AgentAccessDeniedException e) {
		}

		adam.unlock(ADAMSPASS);
		testee.unlock(adam);
		assertSame(adam, testee.getOpeningAgent());
		assertFalse(testee.isLocked());

		try {
			testee.unlock(eve);
			fail("SecurityException should have been thrown");
		} catch (AgentAccessDeniedException e) {
		}

		testee.lockPrivateKey();
		assertTrue(testee.isLocked());
		assertNull(testee.getOpeningAgent());

	}

	@Test
	public void testAdding() throws L2pSecurityException, CryptoException, SerializationException,
			AgentAccessDeniedException, AgentOperationFailedException {
		GroupAgentImpl testee = GroupAgentImpl.createGroupAgent(new AgentImpl[] { adam, eve });
		abel.unlock(ABELSPASS);

		try {
			testee.unlock(abel);
			fail("SecurityException should have been thrown");
		} catch (AgentAccessDeniedException e) {
		}

		eve.unlock(EVESSPASS);
		testee.unlock(eve);

		assertFalse(testee.hasMember(abel));

		testee.addMember(abel);
		testee.lockPrivateKey();

		assertTrue(testee.hasMember(abel));

		testee.unlock(abel);
	}

	@Test
	public void testSubGrouping() throws SerializationException, CryptoException, L2pSecurityException,
			AgentAccessDeniedException, AgentOperationFailedException {
		GroupAgentImpl subGroup = GroupAgentImpl.createGroupAgent(new AgentImpl[] { adam, eve });
		GroupAgentImpl superGroup = GroupAgentImpl.createGroupAgent(new AgentImpl[] { abel, subGroup });

		assertTrue(superGroup.hasMember(subGroup));

		eve.unlock(EVESSPASS);
		try {
			superGroup.unlock(subGroup);
			fail("SecurityException should have been thrown!");
		} catch (AgentAccessDeniedException e) {
		}

		try {
			superGroup.unlock(eve);
			fail("SecurityException should have been thrown!");
		} catch (AgentAccessDeniedException e) {
		}

		subGroup.unlock(eve);

		superGroup.unlock(subGroup);
		assertSame(subGroup, superGroup.getOpeningAgent());
	}
	
	public void testApply() throws L2pSecurityException, CryptoException, SerializationException, AgentAccessDeniedException, AgentOperationFailedException {
		GroupAgentImpl agent = GroupAgentImpl.createGroupAgent(new AgentImpl[] { adam, eve });
		assertTrue(agent.hasMember(adam));
		assertTrue(agent.hasMember(eve));
		assertEquals(agent.getSize(), 2);
		assertTrue(Arrays.asList(agent.getMemberList()).contains(adam.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(eve.getIdentifier()));
		
		agent.addMember(abel);
		
		assertTrue(agent.hasMember(adam));
		assertTrue(agent.hasMember(eve));
		assertTrue(agent.hasMember(abel));
		assertEquals(agent.getSize(), 3);
		assertTrue(Arrays.asList(agent.getMemberList()).contains(adam.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(eve.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(abel.getIdentifier()));
		
		agent.revokeMember(adam);
		
		assertFalse(agent.hasMember(adam));
		assertTrue(agent.hasMember(eve));
		assertTrue(agent.hasMember(abel));
		assertEquals(agent.getSize(), 2);
		assertFalse(Arrays.asList(agent.getMemberList()).contains(adam.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(eve.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(abel.getIdentifier()));
		
		agent.revokeMember(abel);
		
		assertFalse(agent.hasMember(adam));
		assertTrue(agent.hasMember(eve));
		assertFalse(agent.hasMember(abel));
		assertEquals(agent.getSize(), 1);
		assertFalse(Arrays.asList(agent.getMemberList()).contains(adam.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(eve.getIdentifier()));
		assertFalse(Arrays.asList(agent.getMemberList()).contains(abel.getIdentifier()));
		
		agent.addMember(adam);
		
		assertTrue(agent.hasMember(adam));
		assertTrue(agent.hasMember(eve));
		assertFalse(agent.hasMember(abel));
		assertEquals(agent.getSize(), 2);
		assertTrue(Arrays.asList(agent.getMemberList()).contains(adam.getIdentifier()));
		assertTrue(Arrays.asList(agent.getMemberList()).contains(eve.getIdentifier()));
		assertFalse(Arrays.asList(agent.getMemberList()).contains(abel.getIdentifier()));
		
		try {
			agent.apply();
			fail("Exception expected");
		}
		catch(Exception e) {
		}
		
		try {
			agent.unlock(adam);
			agent.apply();
		}
		catch(Exception e) {
			fail("Got unexpected exception: " + e);
		}
	}

}