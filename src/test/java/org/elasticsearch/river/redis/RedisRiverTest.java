package org.elasticsearch.river.redis;

import static org.junit.Assert.*;
import org.junit.*;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import redis.clients.jedis.Jedis;


/**
 * @author leeadkins
 */
 
public class RedisRiverTest {
	
	private static Node node;
	
	private static Jedis jedis;
	
	@BeforeClass
	public static void setupTest() throws Exception {
		jedis = new Jedis("localhost");
	}
	
	@Test
	public void canConnect() throws Exception {
		assertNotNull(jedis);
	}
	
	@Test
	public void canPush() throws Exception {
		assertNotNull(jedis);
	}
}