# COWJ Plugin Guide

[TOC]

## About : Plugins

Basic idea of a plugin is one can have sort of LEGO blocks,
that one can insert into appropriate point at will - and thus, 
can extend the experience of the base engine one created.

A very simple example of plugin is codecs, coder-decoder
which based on the appropriate media format the media players load.

## Cowj Plugins

Essentially, COWJ has a plugin based model.

### Scriptable

`Scriptable` is very much plug-n-play, although we do not expose the ability 
to adding custom engine that easy. But we can.
All `jsr-223` engines are plugin based, while `ZoomBA` is a special plugin.

To add a `Scriptable` plugin:

1. drop a `JSR-223` engine binary to the `lib` folder with all dependencies
2. register the type of the engine - manually - in some way 
3. register the type to the `UNIVERRSAL` Scriptable creator
4. And you are done.

It is the step [3] that would require one to change Cowj source code, 
and thus, technically, the Scriptable are not really a plug-in.

### Data Sources

Cowj runs on CRUD, and `DataSource` are the abstract entities from where and to where Cowj
reads from and write on.
As we do not know the type of the services it would provide, we abstract it as `Object`
and nothing more.

A data source always relies upon the `type` of data source.
This underlying type depends on underlying plugin.

For example, take this:

```yaml
plugins:
  cowj.plugins:
    fcm: FCMWrapper::FCM

data-sources:
  fcm_ds:
    type: fcm
    credentials_file: _/credentials.json
```

So what is happening? the `type` of `data-source` named `fcm_ds` is `fcm` and that is
the registration name under the plugin `fcm` - hence it would use the static field `FCM`
of the full class `cowj.plugins.FCMWrapper` to create such a plugin.

All plugins in COWJ are, as of now, always producing `DataSource` type objects.

## Plugin Life Cycle

### Registration And Creation

Registration flow of plugin is as follows:

1. System looks for the `plugins` key in the config file
2. Any sub-key under the hood is the package name of the java class who has the plugin implemented 
3. Anything within that are of the form: `type_registry_name : class_name::FIELD_NAME` 
4. `FIELD_NAME` is the static field that would be a `DataSource.Creator` type 
5. This will be called to generate one `DataSource` object 
6. the `type_registry_name` gets stored as the key in the `Scriptable.DATA_SOURCES` static map 
7. The created data source object `proxy()` method's result gets stored as the value 

### Usage

#### Inside Source Code

The following code gets a data source back:

```java
Object ds = Scriptable.DATA_SOURCES.get("fcm_ds");
```

#### Scripting Usage

In various scripts the `Scriptable.DATA_SOURCES` gets injected as a `Bindings` variable 
with variable name `_ds`.

Thus, based on the language the usage can be:

```scala
fcm_instance = _ds.fcm_ds // zoomba , groovy 
fcm_instance = _ds["fcm_ds"] // zoomba, js, groovy, python 
```

At this point `fcm_instance` is the instance returned by the `proxy()` method
of the underlying data source.

## Default Plugins

### Either Monad

This is a way to create a monadic container to wrap `result, error` while calling APIs.

```java
public final class EitherMonad<V> {
  public boolean inError();
  public boolean isSuccessful();
  public V value();
  public Throwable error();
}
```

It is highly encouraged to wrap around plugins exposed APIs with this class.
Usage is as follows:

```java
EitherMonad<Integer> EitherMonad.value( 42 );
EitherMonad<Integer> EitherMonad.error( new NumberFormatException("Integer can not be parsed!") );
```

### Secrets Manager

Essentially to read configurations.
The Plugin implementation is supposed to provide
access to a Map of type `Map<String,String>` because 
one really want to serialize the data.
See https://docs.oracle.com/javase/tutorial/essential/environment/env.html .

Technically, a Secret Manager provides an app to run using some virtual environment.

To define and use:

```yaml
plugins:
  cowj.plugins:
    gsm: SecretManager::GSM # google secret manager impl 
    local: SecretManager::LOCAL # just the system env variable 


data-sources:
  secret_source:
    type: gsm
    config: ${key-for-config}
    project-id: some-project-id
```

Now, one can use this into any other plugin, if need be.
In a very special case the `port` attribute of the main config file
can be redirected to any variable - because of obvious reason:

```yaml
port : ${PORT}
```

In which case system uses the `PORT` variable from the local secret manager
which is the systems environment variable.

This also is true for any `${key}` directive in any `SecretManager` ,  the problem of bootstrapping or who watches the watcher gets avoided by booting from a bunch of `ENV` variable passed into the system - and then `SecretManager` can be loaded and then the system can use the secret manager.

### Web IO - CURL

The implementer class is `cowj.plugins.CurlWrapper`.

This does web IO.
This is how a data source looks like:

```yaml
plugins:
  cowj.plugins:
    curl: CurlWrapper::CURL # add to the plugins

data-source:
  json_place: # name of the ds 
    type: curl # type must match the registered type of the curl plugin
    url: https://jsonplaceholder.typicode.com # base url to connec to
    proxy: _/proxy_transform.zm # use for transforming the request to proxy request
```

The wrapper in essence has 2 interface methods:

```java
public interface CurlWrapper {
  // sends a request to a path for the underlying data source 
  EitherMonad<ZWeb.ZWebCom> send(String verb, String path, 
        Map<String,String> headers,
        Map<String,String> params, 
        String body);

  Function<Request, EitherMonad<Map<String,Object>>> proxyTransformation();

  String proxy(String verb, String destPath, 
       Request request, Response response){}
}
```

The function `proxy()` gets used in the forward proxying, to modify the request headers, queries, and body to send to the destination server.

The system then returns the response from the destination server verbatim.
This probably we should change, so that another layer of transformation
can be applied to the incoming response to produce the final response to the client.

The `curl` plugin can be used programmatically, if need be via:

```scala
em = _ds.json_place.send( "get", "/users", {:}, {:} , "" )
assert( em.isSuccessful(), "Got a boom!" )
result = em.value()
result.body() // here is the body 
```

### JDBC

JDBC abstracts the connection provided by JDBC drivers.
Typical usage looks like:

```yaml
plugins:
  cowj.plugins:
    gsm: SecretManager::GSM
    jdbc: JDBCWrapper::JDBC

data-sources:

  secret_sorce: # define the secret manager to maintain env 
    type: gsm
    config: key-for-config
    project-id: some-project-id

  mysql: #  mysql connection 
    type: jdbc
    secrets: secret_source # use the secret manager 
    properties:
      user: ${DB_USERNAME_READ}
      password: ${DB_PASSWORD_READ}
    connection: "jdbc:mysql://${DB_HOST_READ}/${DB_DATABASE_READ}"

  druid: # druid connection using avatica driver
    type: jdbc
    connection: "jdbc:avatica:remote:url=http://localhost:8082/druid/v2/sql/avatica/"

  derby: # apache derby connection 
    type: jdbc
    stale: "values current_timestamp" # notice the custom stale connection check query
    connection: "jdbc:derby:memory:cowjdb;create=true"
```

In this implementation, we are using the `SecretManager` named `secret_source`.
The JDBC connection properties are then substituted with the syntax `${key}` 
where `key` must be present in the environment provided by the secret manager.

`connection` is the typical connection string for JDBC.

```yaml
connection: "jdbc:derby:memory:cowjdb;create=true"
```

is a typical string that we use to test the wrapper itself using derby.

The basic interface is as follows:

```java
public interface JDBCWrapper {
    // underlying connection object  
    EitherMonad<Connection> connection(); 
    // create a connection object  
    EitherMonad<Connection> create();
    // check if connection is valid   
    boolean isValid(); 
    // how to check if connection is stale?
    default String staleCheckQuery(){
        /*
        * Druid, MySQL, PGSQL ,AuroraDB
        * Oracle will not work SELECT 1 FROM DUAL
        * */
        return "SELECT 1";
    }
    // fortmatter query, returns a list of json style objects ( map )
    EitherMonad<List<Map<String,Object>>> select(String query, List<Object> args);
}
```

As one can surmise, we do not want to generally use the DB, but in rare cases
we may want to read, and if write is necessary we can do that with the underlying connection.
Mostly, we shall be using read.

`isValid()` is the method that uses some sort of heuristic to figure out if the `connection()` is actually valid.  For that, it relies on `staleCheckQuery()` which is exposed as `stale` parameter as shown in the yaml.

There will be one guaranteed connection per JDBC, on boot. Then on, if any jetty thread access the db, a dedicated connection will be opened, and will be reused on the lifetime of the thread.

Work is underway to clean up the connection when the thread ends.

### REDIS

A redis ds is pretty straight forward, it is unauthenticated, 
and we simply specify the `urls` as follows:

```yaml
plugins:
  cowj.plugins:
    redis: RedisWrapper::REDIS

data-sources:
  local_redis :
    type : redis
    urls: [ "localhost:6379"]
```

It returns the underlying `UnifiedJedis` instance.
The key `urls` can also be loaded from `SecretManager` if need be.

```yaml
prod_redis :
  type : redis
  secrets: some-secret-mgr
  urls: ${REDIS.URLS}
```

### Notification - FCM

Firebase notification is included, this is how we use it:

```yaml
plugins:
  cowj.plugins:
    fcm: FCMWrapper::FCM
    gsm: SecretManager::GSM

data-sources:
  secret_source:
    type: gsm
    config: QA
    project-id: blox-tech

  fcm:
    type: fcm
    secrets: secret_source
    key: FCM_CREDENTIALS
```

The usage is pretty straightforward:

```scala
payload = { "tokens" : tokens , "title" : body.title, "body" : body.body, "image": body.image ?? '',"data": body.data ?? dict()}
response = _ds.fcm.sendMulticast(payload)
```

The underlying wrapper has methods as follows:

```java
public interface FCMWrapper {
  // underlying real object 
  FirebaseMessaging messaging();
  // create a single recipient message 
  static Message message(Map<String, Object> message);
  // multicast message 
  static MulticastMessage multicastMessage(Map<String, Object> message);
  // send messages after creation 
  BatchResponse sendMulticast(Map<String, Object> data) throws FirebaseMessagingException ;
  String sendMessage(Map<String, Object> data) throws FirebaseMessagingException;
}
```

### Storage 

We try to avoid all database, because they are the architectural bottleneck, in the end.
We do directly support cloud storage.

#### Universal Implementation 
This is the universal implementaion

```java
    /**
     * Dump String to Cloud Storage
     * @param bucketName the bucket
     * @param fileName   the file
     * @param data       which to be dumped encoding used is UTF-8
     * @return a R object
     */
    R dumps( String bucketName, String fileName, String data);
    /**
     * Dump Object to Storage after converting it to JSON String
     *
     * @param bucketName the bucket
     * @param fileName   the file
     * @param obj        which to be dumped
     * @return a Blob object
     */
    R dump(String bucketName, String fileName, Object obj);

    /**
     * In case file exists
     * @param bucketName in the bucket name
     * @param fileName having the name
     * @return true if it is a blob , false if it does not exist
     */
    boolean fileExist(String bucketName, String fileName);

    /**
     * Get the input data type I
     * @param bucketName name of the bucket
     * @param fileName name of the file
     * @return data of type I
     */
    I data(String bucketName, String fileName);

    /**
     * Load data from Google Storage as bytes
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return byte[] - content of the file
     */
    default byte[] loadb(String bucketName, String fileName);

    /**
     * Load data from Google Storage as String - encoding is UTF-8
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return data string - content of the file
     */
    default String loads(String bucketName, String fileName);

    /**
     * Load data from Google Storage as Object
     *
     * @param bucketName from this bucket name
     * @param fileName   from this file
     * @return data object - content of the file after parsing it as JSON
     */
    default Object load(String bucketName, String fileName);

    /**
     * Gets a Stream of objects from a bucket
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of Blob of type I
     */
    Stream<I> stream(String bucketName, String directoryPrefix);

    /**
     * Gets a Stream of String from a bucket
     *
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of String after reading each Blob as String use UTF-8 encoding
     */
    default Stream<String> allContent(String bucketName, String directoryPrefix);

    /**
     * Gets a Stream of Object from a bucket
     * after reading each Blob as String use UTF-8 encoding
     * In case it can parse it as JSON return that object, else return the string
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @return a Stream of Object or String
     */
    default Stream<Object> allData(String bucketName, String directoryPrefix);

    /**
     * Create a new bucket
     *
     * @param bucketName name of the bucket
     * @param location location of the bucket
     * @param preventPublicAccess if set to true, ensures global read access is disabled
     * @return a type B
     */
    B createBucket(String bucketName, String location, boolean preventPublicAccess);

    /**
     * Deletes the bucket
     * @param bucketName name of bucket
     * @return true if bucket was deleted false if bucket does not exist
     */
    boolean deleteBucket(String bucketName);

    /**
     * Deletes the file from the bucket
     * @param bucketName name of the bucket
     * @param path path of the file - example - "abc/def.json"
     * @return true if file was deleted, false if file does not exist
     */
    boolean delete(String bucketName, String path);

```

#### S3 Storage 

Usage is as follows:

```yaml
plugins:
  cowj.plugins:
    s3-storage: S3StorageWrapper::STORAGE

data-sources:
  storage:
    type: s3-storage
    region-id : ap-southeast-1 # region of the bucket 
    page-size: 5000 # 5000 S3Objects, per page

```

There are some extra methods defined on the Google Storage, as follows:

```java
public interface S3StorageWrapper {
  
    /**
     * Underlying S3Client instance
     *
     * @return S3Client
     */
    S3Client s3client();
}
```



#### Google Storage

Usage is as follows:

```yaml
plugins:
  cowj.plugins:
    g_storage: GoogleStorageWrapper::STORAGE

data-sources:
  storage:
    type: g_storage
```

And if configured properly, we can simply load whatever we want via this:

```scala
storage = _ds.storage
data = storage.load(_ds.secret_source.getOrDefault("AWS_BUCKET", ""), "static_data/teams.json")
_shared["qa:cowj:notification:team"] = data
panic (empty(data), "teams are empty Please report to on call", 500)
```

There are some extra methods defined on the Google Storage, as follows:

```java
public interface GoogleStorageWrapper {
  // underlying storage 
  Storage storage();

    /**
     * Gets a Stream of Object from a bucket
     * after reading each Blob as String use UTF-8 encoding
     * In case it can parse it as JSON return that object, else return the string
     * @param bucketName name of the bucket
     * @param directoryPrefix prefix we use to get files in the directory
     * @param recurse should we go down to the subdirectories
     * @return a Stream of Object or String
     */
    default Stream<Object> allData(String bucketName, String directoryPrefix, boolean recurse);
    
}
```

### Authenticators 

There are two types of authentication mechanism provided in plugins. 

1. Storage Based : [StorageAuthenticator](../app/src/main/java/cowj/plugins/StorageAuthenticator.java)
2. JWT Based : [JWTAuthenticator](../app/src/main/java/cowj/plugins/JWTAuthenticator.java)

To use any of these authenticators, one must create the authentication by adding a `auth/auth.yaml` file in the app directory.
Cowj system automatically loads the authentication scheme. 

#### Storage Authentication 

In the actual app `yaml` file:

```yaml
plugins:
  cowj.plugins:
    auth-jdbc : StorageAuthenticator::JDBC

data-sources:  
  mysql: #  mysql connection 
    type: jdbc
    secrets: secret_source # use the secret manager 
    properties:
      user: ${DB_USERNAME_READ}
      password: ${DB_PASSWORD_READ}
    connection: "jdbc:mysql://${DB_HOST_READ}/${DB_DATABASE_READ}"

```
And in the actual `auth/auth.yaml` :

```yaml
# The standard location auth file
# is it enabled?
disabled: false


provider:
  type: "auth-jdbc"
  jdbc: "mysql"
  token: "body:token"
  query: "select user, expiry from users_table where token = '%s'"
  user: "user"
  expiry: "expiry"


user-header: "u"
# casbin policy
policy:
  # this is adapter type file
  adapter: file
  # for a file type, CSV is the way
  file: policy.csv
message: "thou shall not pass!"

```


#### JWT Authentication 

In the actual app `yaml` file:

```yaml
plugins:
  cowj.plugins:
    auth-jwt : JWTAuthenticator::JWT

```

In the `auth.yaml` file inside the app `auth` folder:
```yaml
# The standard location auth file
# is it enabled?
disabled: false

provider:
  type: "auth-jwt"
  secret-key: "42"
  issuer: "test"
  expiry: 60 # 1 minute, JWT everything is seconds
  risks:
    - "/token"


user-header: "u"
# casbin policy
policy:
  # this is adapter type file
  adapter: file
  # for a file type, CSV is the way
  file: policy.csv
message: "thou shall not pass!"

```

### RAMA 

First check the basic idea behind [RAMA event bus](./RAMA.md)
The syntax is as follows:

```yaml
plugins:
  cowj.plugins:
    g_storage: GoogleStorageWrapper::STORAGE
    rama: JvmRAMA::RAMA

data-sources:
  google-storage:
    type: g_storage
  event_bus:
    type: rama 
    storage: google-storage # underlying prefixed storage impl to be used 
    uuid: my-name-is-nobody # unique id for the RAMA writer node 
```

Usage is very simple, once the plugins are loaded, simply use:

#### Write Events 
```scala
// this is how you put stuff into the RAMA bus 
_ds.event_bus.put("topic_name", "this is my data")
```

#### Read Events
```scala
// this is how you get stuff from the RAMA bus 
em = _ds.event_bus.get("topic_name", "2024/02/13/20/05", 100, 0)
// 2nd param is the time stamp which you want to data to be extracted 
// 3rd param is the number of record you want to extract 
// 4th param is the offset within the time stamp 
```

The `em` will be an `EitherMonad` of type `JvmRAMA.Response` object 
which is defined here : https://github.com/nmondal/cowj/app/src/main/java/cowj/plugins/JvmRAMA.java#L76
having basic structure:

```java
class Response {
    /**
     * List of data Entry, Key String, Value Strings, each value string is string rep of the data object
     */
    public final List<Map.Entry<String,String>> data;
    /**
     * Till how much it read in the current get call
     * Greater than 0 implies we have more data for same prefix
     * Less than or equal to zero implies all data has been read
     */
    public final long readOffset;
    /**
     * Does the prefix has more data to read? Then true, else false
     */
    public final boolean hasMoreData;
}
```
So that we know there are more data.

##### Cron Reading

RAMA allows to have cron jobs to read periodically to fetch latest events.
See the project [RAMA App](../app/samples/rama) for entire source.
Specifically, it can be used as follows:

```yaml
  rama:
    type: jvm-rama
    storage: storage
    uuid: "rama-42-0"
    topics:
      EVENT_1:
        create: true
        at: "0 0/1 * * * ?"
        prefix: "yyyy/MM/dd/HH/mm"
        offset: "PT-1M"
        step: "PT1M"
        page-size: 100
        consumers:
          - _/evt_1.zm # this would work
      EVENT_2:
        create: false
        at: "0 */5 * * * ?"
        prefix: "yyyy/MM/dd/HH/mm"
        offset: "PT-5M"
        step: "PT1M"
        page-size: 100
        consumers:
          - _/evt_1.zm
          - _/evt_2.zm
```
The idea is having `at` same as any cron job, that is the periodic fetch done automatically to read more events.
This of course is a matter of convenience. RAMA does not stream in conventional sense.
Now then - the `prefix` is used get to the `prefixKey` to read data from from bucket.
System would get the current time in `UTC`, then apply the `prefix` format on top of it, 
and then go to past time applying the `offset` which is `java.time.Duration` format.
That would be the starting bucket. Now system applies `step`, again using `Duration` format, to 
cover all buckets between past the current, excluding the current bucket.

`page-size` is the size to read in each reading, till the bucket is over.
`consumers` list down the scripts which will be passed those events.

Multiple consumer script can be attached with single `topic` while, 
same script can be used as consumer for multiple `topic` as depicted.

A typical script is as follows:

```scala
_shared["cnt"] = (_shared["cnt"]  ?? 0 ) + 1
_log.info( "topic {}, key {}, value {}", event, body.key , body.value )
```
Notice the `event`, which will be the topic reading from.
`body` is `Map.Entry<String,String>` where `key` is the key of the event, while `value` is the string data.

By carefully selecting the `at` and `offset` and `step` we can get event streaming delay as minimum as 1 sec.
This would be done via this configuration:

```yaml
EVENT_1_SEC:
    create: false
    at: "0/1 * * * * ? *"
    prefix: "yyyy/MM/dd/HH/mm/ss" # bucketing to 1 sec
    offset: "PT-1S" # go back 1 sec from current time 
    step: "PT1S" # next step is to add 1 sec to current 
    page-size: 100 # read 100 record in one go, iterate till it all is exhausted 
    consumers:
      - _/super_fast.zm # consume super-fast

```


## References

1. https://en.wikipedia.org/wiki/Plug-in_(computing) 
2. https://learn.microsoft.com/en-us/dotnet/api/microsoft.visualstudio.data.datasource?view=visualstudiosdk-2022 
3. https://developer.android.com/reference/android/arch/paging/DataSource 
4. https://en.wikipedia.org/wiki/CURL 
5. https://microsoft.github.io/reverse-proxy/articles/transforms.html 
6. https://en.wikipedia.org/wiki/Java_Database_Connectivity 
7. https://en.wikipedia.org/wiki/Redis 
8. https://redis.io/docs/clients/java/ 
9. https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/FirebaseMessaging
10. https://cloud.google.com/storage/docs/reference/libraries
