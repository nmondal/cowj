# Guide to Write COWJ Plugins

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

### Cloud Storage - Google Storage

We try to avoid all database, because they are the architectural bottleneck, in the end.
We do directly support cloud storage, specifically google storage as follows:

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

There are various methods defined on the storage, as follows:

```java
public interface GoogleStorageWrapper {
  // underlying storage 
  Storage storage();
  // dumps the data to a bucket name with file name 
  Blob dumps(String bucketName, String fileName, String data);
  // dumps the object after converting it into json to a bucket name with file name
  Blob dump(String bucketName, String fileName, Object obj);
  // loads a bucket, file combo as string 
  String loads(String bucketName, String fileName);
  // loads a bucket, file combo - and then try converting to json obj 
  Object load(String bucketName, String fileName);
  // Generates a stream of blob objects from the various files in the bucket 
  Stream<Blob> all(String bucketName);
  // Gets stream of all string contents.. 
  Stream<String> allContent(String bucketName);
  // Gets objects of all ... if can not convert to json retain as string 
  Stream<Object> allData(String bucketName);

}
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
