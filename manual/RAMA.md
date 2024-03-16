# RAMA

[TOC]

<img src="https://cdnb.artstation.com/p/assets/images/images/024/396/631/large/andrzej-gdula-post-1.jpg" style="zoom:15%;" />

## About 



### Naming

>  **RAMA** stands for **R**estful **A**synchronous **M**essaging **A**lternative.
>
> Objective is to have a restful mechanism to build an Universally Applicable pub/sub alternative at a throwaway price.

Inspired by the closed self sufficient ecosystem  inside a cylinder theme - https://en.wikipedia.org/wiki/Rendezvous_with_Rama 

### Why & What 

Pub-Sub is over indulged in the industry with varieties of choice to the point of newly added buzzword "streaming". That is in fact just a different name of low latency pub sub with massive fan out. While alternatives exists, none of them exists which has the following criterion

1. Swiss Knife of Pub/Sub ( everything, every use cases, just not real time streaming or query )
   1. Pub/Sub
   2. Queuing 
   3.  Temporally Ordered Message passing
   4. Event Bus 
2. Cost and maintainability ( 1 million request/sec throughput gets done at cost of 100$ a month or less )
   1. Extremely Low cost setup 
   2. Easy to Maintain - Zero Maintenance   
3. Reasonably Low latency L
   1. Maximum of ~ 3 secs to reach to subscriber
   2. Can be taken to sub millisecond 
4. Well Ordering with coarse timing which is configurable 
   1. Within a limit a message B sent before will be received before a message A sent after it
   2. Can configure the precision up-to which the messages will be FIFO
   3. Can be customized to guarantee FIFO with a bit more latency of order of ms.
5. Data Durability 
   1. Data never gets written off 
   2. Unless configured 
   3. Acts like a time series data storage - minus the query capability 

### How 

#### Storage

Store data in any underlying key value store with buckets for each time window of a span of optimal granularity not exceeding a minute. Within these bucket message ordering is not guaranteed, but outside that time delta ordering is guaranteed. 

#### Interfacing - Producer

Create proxy to write back message inside time buckets - it is fairly automatic with the key being the timestamp itself. 

#### Interfacing - Consumer

Create proxy to read back messages  from past time buckets - in chunks and load into shared memory and distribute from there. 



## Principles 

### Messages

The message of the pub sub system. For all practical purpose there should be no limit on message size, although for transport reason one can safely imagine an upper cut off of 1MB maximum message size. 

### Channel 

Messages are published into a channel and read from a channel. In Kafka parlance this is a topic.

### Timing 

Each message has a mandatory time stamp in UTC, to be cross checked against server timestamp, if they are reasonably same, it is allowed to pass. This timestamp is used to create virtual temporal buckets for messages.

### Storage & Durability 

Any key value store with ability to prefix search on key suffices, which gives guarantee on durability as well as fast retrieval.

### Client Identity 

Clients are registered with a topic and receives unique identifiers which are to be passed to verify the client identity to publish messages or to read messages.

### REST 

#### Topic Creation  

```
curl -XPUT localhost:8080/create_topic -d '{ "topic" : <name of topic> }'
```

#### Client Registration  

```
curl -XPOST localhost:8080/register/<topic_name> -d '{ "client_key" : <client-key> }'
```

#### Publish Message  

```
curl -XPUT localhost:8080/<topic_name> -H "client_key : <client-key>" -d '{ "x" : 42 , "_ts" : 12131313131 }'
```

#### Read Message   

```
curl -XGET localhost:8080/<topic_name>/<time_stamp>[?o=offset&n=num_of_reccord] -H "client_key : <client-key>"
```

### In the Wild 

A typical RAMA endpoint is depicted in the `42` COWJ project as here :  https://github.com/nmondal/42/blob/main/events/events.yaml 

One can naturally add schema - hence - https://github.com/nmondal/42/blob/main/events/static/types/Event.json for the typed write.

## Detailing  

A prefixed value data store should be the underlying storage. This ensures, that the data retrieval for a prefix can be done in sun linear time. AWS provides S3 for the same, same goes with Google Storage, and even Azure supports prefix in keys.

### Publishing

#### Requirements

1. A Key,  Prefixed Value Data Store : $D$ 
2.  Registered Publishers $P_k$ to a channel name $C$ 
3. $r$ th Messages sent by $k$ Publisher : $m_{kr}$  with timestamp $t_{kr}$
4. A time granularity in micro, ms, or second defined to be time window $T$ 
5. Let $H(P_k,n)$ produces a hashing of $p_k$ restricted to $n$ digits. 

#### Algorithm 

1. Receive the $r$ th Messages sent by $k$ Publisher : $m_{kr}$  with timestamp $t_{kr}$ 

2. Let the time up to precision $T$ for $t_{kr}$ be $B_{kr} = \tau(t_{kr},T)$

3. Let $A \odot B$ means `A/B` a string separated by suitable separator 

4. The key for the message would be as follows:    

    $K = C \odot B_{kr} \odot (t_{kr} \; mod \;  T) \odot H(P_k,n)$

5. Value would be the message body as a whole

6. Put this $K$ key with value $V$ into the Data Store $D$



### Reading

#### Requirements

1. A Key,  Prefixed Value Data Store : $D$ 

2. An offset Tuple for each Consumer of the form  $(c_k : (B_r,I))$

where   $I \in t_{kr} \; mod \;  T$ , defines the smaller buckets inside the larger $B_r$ buckets.

#### Algorithm 

1. Given a client $c_k$ just read back all data in the prefix 
   
$C \odot B_{kr} \odot I$

2. Increment the value of $I$ and store back in the Tuple 

3. Return the data


### Example 

Let's scale to 1 million ( $1000000 = 10^6$ ) request a second.  It is a breeze with this as follows:

1. Let's set the time slice is set to be micro-second : $10^6$ possible $I$ .  
2. The approximate collision is of the order of $O(1)$ now.
3. Timestamp gets received in  `ms` precision which immediately puts into the bucket at `micro` level. 
4. Readers start reading at ms level of precision guaranteeing ordering  at above micro-sec level 

A typical implementation may ease out the entire reading via clever use of batch processing  - see this:

https://github.com/nmondal/42/blob/main/events/events.yaml#L41 



 ## References 

1. Prefix Storage - Default Cloud 
   1. https://cloud.google.com/storage/docs/samples/storage-list-files-with-prefix 
   2. https://stackoverflow.com/questions/52443839/s3-what-exactly-is-a-prefix-and-what-ratelimits-apply 
   3. https://learn.microsoft.com/en-us/python/api/azure-storage-blob/azure.storage.blob.blobprefix?view=azure-python 
2. Custom Prefixed Storage - Trees
   1. https://commons.apache.org/proper/commons-collections/apidocs/org/apache/commons/collections4/trie/PatriciaTrie.html 
   2. https://github.com/indeedeng/lsmtree 
   3. https://ozone.apache.org/docs/current/feature/prefixfso.html 
3. Local File System Implementation - https://github.com/nmondal/cowj/blob/main/app/src/main/java/cowj/plugins/FileBackedStorage.java#L122 
4. A (primitive) RAMA Implementation - https://github.com/nmondal/cowj/blob/main/app/src/main/java/cowj/plugins/JvmRAMA.java 