package org.elasticsearch.plugin.river.redis;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.river.RiversModule;
import org.elasticsearch.river.redis.RedisRiverModule;

/**
 * @author leeadkins
 */

public class RedisRiverPlugin extends AbstractPlugin {
	@Inject public RedisRiverPlugin(){
	}
	
	@Override public String name(){
		return "river-redis";
	}
	
	@Override public String description(){
		return "Redis River Plugin";
	}
	
	public void onModule(RiversModule module){
		module.registerRiver("redis", RedisRiverModule.class);
	}
}
