# Guide to Write COWJ Plugins

[toc]

## About : Plugins
Basic idea of a plugin is one can have sort of LEGO blocks,
that one can insert into approriate point at will - and thus, 
can extend the experience of the base engine one created.

A very simple example of plugin is codecs, coder-decoder
which based on the appropriate media format the media players load.

## Cowj Plugins

Essentially, COWJ has a plugin baed model.

### Scriptable 

`Scriptable` is very much plug-n-play, although we do not expose the ability 
to adding custom engine that easy. But we can.
All `jsr-223` engines are plugin based, while `ZoomBA` is a special plugin.

To add a `Scriptable` plugin:

1.  drop a `JSR-223` engine binary to the `lib` folder with all dependencies
2.  register the type of the engine - manually - in some way 
3.  register the type to the `UNIVERRSAL` Scriptable creator
4.  And you are done.

It is the step [3] that would require one to change Cowj sourece code, 
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

## Plugin Life Cyle 

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

The following code get's a data source back:

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
    config: key-for-config
    project-id: some-project-id
```

Now, one can use this into any other plugin, if need be.


### Web IO - CURL

The implementor class is `cowj.plugins.CurlWrapper`.

This does web IO.
This is how a data souce looks like:

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
_ds.json_place.send( "get", "/users", {:}, {:} , "" )
```

### JDBC 

JDBC abstracts the connection provided by JDBC drivers.
Typlical usage looks like:

```yaml
plugins:
  cowj.plugins:
    gsm: SecretManager::GSM
    jdbc: JDBCWrapper::JDBC
    

data-sources:

  secret_source: # define the secret manager to maintain env 
    type: gsm
    config: key-for-config
    project-id: some-project-id

  mysql:
    type: jdbc
    secrets: secret_source # use the secret manager 
    properties:
      user: ${DB_USERNAME_READ}
      password: ${DB_PASSWORD_READ}
    connection: "jdbc:mysql://${DB_HOST_READ}/${DB_DATABASE_READ}"

```
In this implementation, we are using the `SecretManager` named `secret_source`.
The JDBC connection properties are then substituted with the syntax `${key}` 
where `key` must be present in the environment provided by the secret manager.

`connection` is the typical connection string for JDBC.

```yaml
connection: "jdbc:derby:memory:cowjdb;create=true"
```
is a typical string that we use to test the wrapper itself using derby.

The basic interfacer is as follows:

```java
public interface JDBCWrapper {
    // underlying connection object  
    Connection connection(); 
    // from sql get the java object
    Object getObject(Object value);  
    // fortmatter query, returns a list of json style objects ( map )
    EitherMonad<List<Map<String,Object>>> select(String query, List<Object> args);
}
```

As one can surmise, we do not want to generally use the DB, but in rare cases
we may want to read, and if write is necessary we can do that with the underlying connection.
Mostly, we shall be using read.


### REDIS

### Notification - FCM

### Cloud Storage - Google Storage

## References 

1. https://en.wikipedia.org/wiki/Plug-in_(computing) 
2. https://learn.microsoft.com/en-us/dotnet/api/microsoft.visualstudio.data.datasource?view=visualstudiosdk-2022 
3. https://developer.android.com/reference/android/arch/paging/DataSource 
4. https://en.wikipedia.org/wiki/CURL 
5. https://microsoft.github.io/reverse-proxy/articles/transforms.html 
6. https://en.wikipedia.org/wiki/Java_Database_Connectivity 
7. https://en.wikipedia.org/wiki/Redis 
8. https://redis.io/docs/clients/java/ 
   
