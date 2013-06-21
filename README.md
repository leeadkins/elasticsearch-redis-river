# Elastic Search Redis River

This is a simple River that utilizes the same [Bulk API](http://www.elasticsearch.org/guide/reference/api/bulk.html) used 
in Elastic Search REST requests and the RabbitMQ River, but
with Redis.

Once you've constructed your bulk indexing command, you can push it into the Redis list specified when the river was created.
 
 
## INSTALLATION

For ElasticSearch v0.20.x, you can install it by doing the following:

bin/plugin -install redis-river -url https://github.com/downloads/leeadkins/elasticsearch-redis-river/elasticsearch-redis-river-0.0.4.zip

For older versions, use:

bin/plugin -install leeadkins/elasticsearch-redis-river/0.0.4

Don't forget to restart the node before trying to use it.

## USAGE
	  	curl -XPUT 'localhost:9200/_river/my_redis_river/_meta' -d '{
		    "type" : "redis",
		    "redis" : {
		        "host"     : "localhost", 
		        "port"     : 6379,
		        "key"      : "redis_key",
		        "mode"     : "list",
		        "database" : 0
		    },
		    "index" : {
		        "bulk_size" : 100,
		        "bulk_timeout" : 5
		    }
		}'


Create your river using the standard river PUT request. Your options are:
 - host:         Redis host
 - port:         Redis port
 - key:          The Redis key to poll or Redis PubSub channel to subscribe to.
 - mode:         list only for now
 - database:     (OPTIONAL) The Redis database number to use. Zero indexed, make sure you've properly setup your redis.conf if using more than 16 DBs.
 - bulk_size:    the maximum number of items to queue up before indexing.
 - bulk_timeout: the time (in seconds) to wait for more items before indexing.



## Using a Redis List
The first time you send something to be indexed to Redis (either list or pubsub),
the river start preparing a bulk request. This request will be executed once it
reaches the bulk size or after the bulk_timeout passes, whichever is first.

Setting the bulk_timeout to 0 when using a list doesn't mean that it won't wait.
It means that it will not timeout a bulk request. In other words, it will always
wait for enough messages to fill the bulk_size. To acheive the effect of not waiting for
anything while using a list, you could set both the bulk timeout and the bulk size
to 0, which would tell the river to send every single index request through automatically.
This probably isn't the best for performance purposes.
