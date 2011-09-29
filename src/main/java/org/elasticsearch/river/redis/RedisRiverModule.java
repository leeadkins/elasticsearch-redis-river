package org.elasticsearch.river.redis;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.river.River;

/**
 * @author leeadkins
 */
 
public class RedisRiverModule extends AbstractModule {
 	@Override protected void configure(){
		bind(River.class).to(RedisRiver.class).asEagerSingleton();
	}
}