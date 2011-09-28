This river, and thus its documentation, is still in development.
It is, however, under active development, so expect this space
to change frequently.  Once it's at a stable enough place for use by
people other than me (which should be any day now), I'll throw
a ZIP of it into the Downloads section so you can do a standard
Elastic Search plugin install on it.


# Elastic Search Redis River

This is a simple River that utilizes the same Bulk API used 
in Elastic Search REST requests and the RabbitMQ River, but
with Redis.

You can get stuff into ES two ways with this river:
 - Push to a Redis List
 - Use Redis PubSub

## USAGE
	  	curl -XPUT 'localhost:9200/_river/my_redis_river/_meta' -d '{
		    "type" : "redis",
		    "redis" : {
		        "host" : "localhost", 
		        "port" : 6379,
		        "key"  : "redis_key_or_channel",
		        "mode" : "list_or_pubsub"
		    },
		    "index" : {
		        "bulk_size" : 100,
		        "bulk_timeout" : 5
		    }
		}'


Create your river using the standard river PUT request. Your options are:
 - key:          The Redis key to poll or Redis PubSub channel to subscribe to.
 - mode:         list or pubsub
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

## Using Redis PubSub

More info coming soon.