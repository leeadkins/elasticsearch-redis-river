# Elastic Search Redis River

Tested up to Elasticsearch 1.0.1

This is a simple River that utilizes the same [Bulk API](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/docs-bulk.html) used 
in Elastic Search REST requests and the RabbitMQ River, but
with Redis.

Once you've constructed your bulk indexing command, you can push it into the Redis list specified when the river was created.
 
I chose the Bulk API because I needed the flexibilty of dumping lots of different things into the same place while indexing. If you're looking for the easiest way to just index some JSON, you might be interested in the newer [elasticsearch-river-redis](https://github.com/sksamuel/elasticsearch-river-redis). I'm not affiliated with that project, but it's definitely more straightforward if you're just trying to get some JSON into a single index.
 
## INSTALLATION

This isn't in Maven. I rely on Github Releases, so you'll need to do this:

```
bin/plugin -install redis-river -url https://github.com/leeadkins/elasticsearch-redis-river/releases/download/v0.0.5/elasticsearch-redis-river-0.0.5.zip
```


Don't forget to restart the node before trying to use it.

## USAGE
	  	curl -XPUT 'localhost:9200/_river/my_redis_river/_meta' -d '{
		    "type" : "redis",
		    "redis" : {
		        "host"     : "localhost", 
		        "port"     : 6379,
		        "key"      : "redis_key",
		        "mode"     : "list",
		        "password" : "yourpassword",
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
 - password:     (OPTIONAL) Your password, if your redis-server is password protected.
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


## Example

As mentioned above, this particular river uses the Bulk API. If you're in a redis-cli console, and you have a river setup like the above example, this redis command should put something in your elasticsearch node.

```
LPUSH redis_key "{\"index\":{\"_index\":\"analytics\",\"_type\":\"analytic\",\"_id\":1}}\n{\"id\":1,\"age\":25,\"name\":\"My Name\"}\n"
```
