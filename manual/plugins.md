# Guide to Write COWJ Plugins

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
At this point `fcm_instance` is the 


## Default Plugins 

### Web IO - CURL

### JDBC 

### REDIS

### Notification - FCM

### Cloud Storage - Google Storage

### Secret Managers


## References 

1. https://en.wikipedia.org/wiki/Plug-in_(computing) 
2. https://learn.microsoft.com/en-us/dotnet/api/microsoft.visualstudio.data.datasource?view=visualstudiosdk-2022 
3. https://developer.android.com/reference/android/arch/paging/DataSource 
4. https://en.wikipedia.org/wiki/CURL 
5. https://microsoft.github.io/reverse-proxy/articles/transforms.html 
6. https://en.wikipedia.org/wiki/Java_Database_Connectivity 
7. https://en.wikipedia.org/wiki/Redis 
8. https://redis.io/docs/clients/java/ 
   
