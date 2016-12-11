package i5.las2peer.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import i5.las2peer.api.Service;
import i5.las2peer.security.Agent;

public class LocalServiceTestCaseTest extends LocalServiceTestCase {

	@Test
	public void test() {
		try {
			assertEquals(TestService.class, getServiceClass());
			assertEquals(TestService.class.getName(), getMyAgent().getServiceNameVersion().getName());

			assertTrue(getNode().hasAgent(getMyAgent().getId()));

			assertEquals(TestService.class, getServiceInstance().getClass());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Test
	public void testMockAgents() {
		try {
			Agent eve = MockAgentFactory.getEve();
			getNode().hasAgent(eve.getId());

			assertTrue(getNode().hasAgent(MockAgentFactory.getAdam().getId()));
			assertTrue(getNode().hasAgent(MockAgentFactory.getAbel().getId()));
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Override
	public Class<? extends Service> getServiceClass() {
		return TestService.class;
	}

	@Override
	public String getServiceVersion() {
		return "1.0";
	}

}
