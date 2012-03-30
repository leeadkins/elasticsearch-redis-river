package org.elasticsearch.river.redis;

/*
	Elastic Search plugin imports.
*/
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.threadpool.ThreadPool;


/*
	Redis River specifics
*/

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.List;
import java.util.Map;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * @author leeadkins
 */
 
public class RedisRiver extends AbstractRiverComponent implements River {
	
	private final Client client;
	
	private volatile Thread thread;
	
	private volatile boolean closed = false;
	
	private volatile BulkRequestBuilder currentRequest;

	private volatile JedisPool jedisPool;
	
	/* Redis Related things */
	private final String  redisHost;
	private final int     redisPort;
	private final String  redisKey;
	private final String  redisMode;
	private final int     redisDB;


	private final int bulkSize;
	private final int bulkTimeout;


	
	@Inject
	public RedisRiver(RiverName riverName, RiverSettings settings, Client client) {
		super(riverName, settings);
		this.client = client;

		/* Build up the settings */  
		if(settings.settings().containsKey("redis")) {
			Map<String, Object> redisSettings = (Map<String, Object>) settings.settings().get("redis");
			redisHost = XContentMapValues.nodeStringValue(redisSettings.get("host"), "localhost");
			redisPort = XContentMapValues.nodeIntegerValue(redisSettings.get("port"), 6379);
			redisKey  = XContentMapValues.nodeStringValue(redisSettings.get("key"), "redis_river");
			redisMode = XContentMapValues.nodeStringValue(redisSettings.get("mode"), "list");
			redisDB   = XContentMapValues.nodeIntegerValue(redisSettings.get("database"), 0);
		} else {
			redisHost = "localhost";
			redisPort = 6379;
			redisKey  = "redis_river";
			redisMode = "list";
			redisDB   = 0;
		}
		
		if(settings.settings().containsKey("index")){
			Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
			bulkSize    = XContentMapValues.nodeIntegerValue(indexSettings.get("bulk_size"), 100);
			bulkTimeout = XContentMapValues.nodeIntegerValue(indexSettings.get("bulk_timeout"), 5);
		} else {
			bulkSize = 100;
			bulkTimeout = 5;   
		}

	}


	@Override
	public void start() {
		if(logger.isInfoEnabled()) logger.info("Starting Redis River stream");

		// Next, we'll try to connect our redis pool
		try {
			this.jedisPool = new JedisPool(this.redisHost, this.redisPort);  
		} catch (Exception e) {
			// We can't connect to redis for some reason, so
			// let's not even try to finish this.
			logger.warn("Unable to allocate redis pool. Disabling River.");
			return;
		}

		currentRequest = client.prepareBulk();
		
		if(redisMode.equalsIgnoreCase("list")){
			thread = EsExecutors.daemonThreadFactory(settings.globalSettings(), "redis_listener").newThread(new RedisListRunner());
		} else if (redisMode.equalsIgnoreCase("pubsub")){
			//thread = EsExecutors.daemonThreadFactory(settings.globalSettings(), "redis_listener").newThread(new RedisPubSubRunner());
			logger.warn("Not implemented");
			return;
		} else {
			logger.warn("Invalid redis river mode specified. Please check your river settings and try again.");
			return;
		}
			
		thread.start();
	}
	
	@Override
	public void close() {
		if(logger.isInfoEnabled()) logger.info("Closing down redis river");
		closed = true;
		if (thread != null) {
			thread.interrupt();
		}
	}

	private class RedisPubSubRunner implements Runnable {

		private Jedis jedis;

		private boolean updating = false;

		private final ScheduledExecutorService watchScheduler;
		private ScheduledFuture<?> watchFuture;

		public RedisPubSubRunner() {
			super();
			this.watchScheduler = Executors.newScheduledThreadPool(1);
		}

		@Override
		public void run(){

			try {
				this.jedis = jedisPool.getResource();
				if(redisDB > 0) {
				  this.jedis.select(redisDB);
				}
			} catch (Exception e) {
				logger.warn("Unable to connect to redis...");
				return;
			}

			logger.info("Is about to subscribe to");
			logger.info(redisKey);
			this.jedis.subscribe(new RiverListener(), redisKey);


			/* Setup a watcher task to flush the queue 
			   if we're waiting for too long for more
			   things.
			*/

			watchFuture = watchScheduler.scheduleWithFixedDelay((Runnable)new BulkWatcher(), 5, 5, TimeUnit.SECONDS);
		}


		private void processBulkIfNeeded(Boolean force) {
			logger.warn("Attempting to process bulk");
			if(updating){ return; }
			updating = true;

			int actionCount = currentRequest.numberOfActions();
			if(actionCount != 0 && (actionCount > bulkSize || force == true)){
				try{
					// This is a little slower than passing in an ActionListener
					// to execute(). However, it doesn't spawn thousands of 
					// zombie threads that hang out after it's done either.
					BulkResponse response = currentRequest.execute().actionGet();
					if(response.hasFailures()){
						logger.warn("failed to execute" + response.buildFailureMessage());
					}
				} catch(Exception e) {
					logger.warn("Failed to process bulk", e);
				}
			 currentRequest = client.prepareBulk();
			}
			updating = false;
			logger.warn("Ending bulk process");
		}

		private class BulkWatcher implements Runnable {
			@Override
			public void run(){
				processBulkIfNeeded(true);
			}
		}
		
		private class RiverListener extends JedisPubSub {

			private void queueMessage(String message){
				try {
					logger.warn("About to add a message...");
					byte[] data = message.getBytes();
					currentRequest.add(data, 0, data.length, false);
					logger.warn("Current size" + currentRequest.numberOfActions());
					processBulkIfNeeded(false);
				} catch (Exception e){
					logger.warn("Unable to build request");
				}

			}

			public void onMessage(String channel, String message) {
				queueMessage(message);
			}

			public void onSubscribe(String channel, int subscribedChannels) {}

			public void onUnsubscribe(String channel, int subscribedChannels) {}

			public void onPSubscribe(String pattern, int subscribedChannels) {}

			public void onPUnsubscribe(String pattern, int subscribedChannels) {}

			public void onPMessage(String pattern, String channel, String message) {
			}
		}

	}

	private class RedisListRunner implements Runnable {

		private Jedis jedis;

		@Override
		public void run() {
			logger.info("Opening up another new thread...");

			while(true){
				if(closed){
					return;
				}
				loop();
			}
		}
		
		private void loop() {
			List<String> response;
			try {
				this.jedis = jedisPool.getResource();
				if(redisDB > 0) {
				  jedis.select(redisDB);
				}
				response = jedis.blpop(bulkTimeout, redisKey);
			} catch (Exception e) {
				// Can't get a redis object. Return and
				// try again on the next loop.
				if(logger.isInfoEnabled()) logger.info("Can't read from redis. Waiting 5 seconds and trying again.");
				jedisPool.returnBrokenResource(this.jedis);
				try {
					Thread.sleep(5000);
				} catch(InterruptedException e1) {
					// Don't worry about this here. It'll close itself if it's in the
					// process of closing. 
				}  
				return;
			}
			
			if(response != null){
				try {
					byte[] data = response.get(1).getBytes();
					currentRequest.add(data, 0, data.length, false);
					processBulkIfNeeded(false);
				} catch (Exception e){
					logger.warn("Unable to build request");
				} 
			} else {
				processBulkIfNeeded(true);
			}
			jedisPool.returnResource(this.jedis);
		}
		
		
		private void processBulkIfNeeded(Boolean force) {
			int actionCount = currentRequest.numberOfActions();
			if(actionCount != 0 && (actionCount > bulkSize || force == true)){
				try{
					BulkResponse response = currentRequest.execute().actionGet();
					if(response.hasFailures()){
						logger.warn("failed to execute" + response.buildFailureMessage());
					}
				} catch(Exception e) {
					logger.warn("Failed to process bulk", e);
				}
			 currentRequest = client.prepareBulk();
			}
		}
				
	}
}

