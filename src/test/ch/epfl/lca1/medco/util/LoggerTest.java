package ch.epfl.lca1.medco.util;

import org.junit.Assert;
import org.junit.Test;


/**
 * Logger class tests
 */
public class LoggerTest {

	@Test
	public void testLog() throws Exception {
		Logger.fatal("text fatal msg");
	}

	@Test
	public void testLogEx() {
		try {
			throw Logger.warn("message", new IllegalArgumentException());
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
		Assert.fail();
	}
}

