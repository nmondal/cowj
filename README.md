# cowj

[C]onfiguration [O]nly [Web] on [J]VM 

<img src="https://icons.iconarchive.com/icons/iconarchive/cute-animal/512/Cute-Cow-icon.png" style="zoom:20%;" />

[TOC]

## Goal


COWJ is pronounced as `Cow Jay`. Reason for emphasising on COW is the configuration only web,
powered by the polyglot support from underlying JVM, which is by and large
one of the phenomenal piece of Engineering done, till date.
Author tried doing the same with GO but not with much success - here is the link to that project:
https://github.com/nmondal/goscow

> Configuration-Only-Web-over-JVM

It should be very clear from the naming that:

> objective is to optimize back-end development and replacing it with configurations.


Cowj let's you build back-end systems :

1. APIs

2. Batch processing with Cron 

3. Event Processing 

4. Data Processing 

via configurations and scripts. It's backbone is written using `spark-11`,  `jetty-11` ,  `quartz` and `casbin`. 

### Very Fast `Hello World`

1. Download the appropriate binary - or build to that you have the `cowj-jar`  ready with `./deps`  pointing to dependencies.

2. Create a directory `hello` 

3. Inside `hello` create a directory `static`

4. Inside `static` folder created `index.html` and write down `hello, world!`.

5. Inside the `hello` folder create a `hello.yaml` file as follows:

```yaml
# hello/hello.yaml
port: 8080
```

 Now run the hello project as :

```shell
java -jar cowj-*.jar hello/hello.yaml
```

Open browser and visit `localhost:8080/index.html`  you will see `hello,world`. 

That is it. That is  all it takes to setup a `Cowj` server.

What about actual service endpoints?

Just type down these into the `hello.yaml` :



```yaml
# hello/hello.yaml
port: 8080
routes:
  get:
    /hello: _/hello.js
```

And restart cowj again.
Hit this endpoint in curl:

```shell
curl -XGET "http://localhost:8080/hello"
```

You would get back `Hello, World!`.

What is really happening here is Cowj system is detecting an expression written using `js` engine - and evaluating and returning.

One can of course, for betterment move the expression from inside to outside, e.g. create a file `hello.js` in the `hello` folder, and then update the `hello.yaml` as follows:

And the `hello.js` :

```js
// hello.js
"Hello, World!"
```

Now, restart cowj, run the same curl command - and voila, you would have `Hello, World` again.

As you can see, code is configuration and configuration is code in Cowj.

With this note, we shall dive into the world of BED - back end development.

## Back End Development

### Development Today

It is pretty apparent that a lot of back-end "development" goes under:

1. Trivial CRUD over data sources
2. Reading from data sources and object massaging - via object mapper, thus mapping input to output
3. Aggregation of multiple back-end services
4. Adding random business logic to various section of the API workflows 

COWJ aims to solve all these 4 problems, such that what was accomplished by 100 of developers can be done by less than 10, a ratio of 90% above in being effective.

### How To Do it?

#### Deprecation : Service Framework

Lately, 10+ people gets allocated to maintain "how to create a service" end point.
This must stop. The following must be made true:

1. Creating a service end point should be just adding configuration 
2. Writing the service should be just typing in scriptable code 
3. Input / Output parameters are to be assumed typeless JSON at worst 
4. We can, at best do a lazy type checking to validate Schema - alternatives 
   1. RAML 
   2. Open API 
   3. JSON Schema 

#### "Business" Logic

There should not be any "business logic" in the code. They are susceptible to change,
hence they should be hosted outside the API end points - DSL should be created to maintain.

#### Data Store Access

Any "Service" point requiring any "data store" access need to declare it, specifically 
as part of the service configuration process. Objective of the engine would be to handle the data transfer. JSON is the choice for data transfer for now.

## Unit Testing - Code Coverage

If one is willing to use this, one must wonder  - what Makes Anything 'Prod Ready' ?
Only suitable answer is the core components must be excessively well tested, 
and should have really good instruction and branch coverage.

### Cowj All Coverage

<img src="manual/coverage-all.png" style="zoom:85%;" />

Thus the engine core is immensely tested, and is at per with industry standard quality.

### Cowj Core Coverage

<img src="manual/coverage-core.png" style="zoom:72%;" />

### Cowj Plugin Coverage

<img src="manual/coverage-plugin.png" style="zoom:72%;" />

Even plugins are reasonably tested, and are ready for production usage.

## Current Implementation

### Service Configuration

Here is the config:

```yaml
# This shows how COWJ service is routed

# port of the server
port : 8000

# threading related stuff
threading:
  min: 4 # min no of threads 
  max : 8 # max no of threads 
  timeout: 30000 # which ms to give timeouts 

# async IO configuration 
async:
   threads: 8 # for async io, if not specified, infinite in practice  
   keep: 32 # keep only 32 task results , if not specified 1024 by default 
   fail: _/scripts/js/async_task_failure_handler.js #  async task failure handler 

# routes information
routes:
  get:
    /hello : _/scripts/js/hello.js
    /param/:id  : _/scripts/js/param.js

  post:
    /hello : _/scripts/js/hello.js
    /_async_/put : _/scripts/zm/put.zm # special async push back route


# route forwarding local /users points to json_place
proxies:
  get:
    /users: json_place/users
  post:
    /users: json_place/users


# filters - before and after an URI
filters:
  before:
    "*" : _/before.zm
  after:
    "*": _/after.zm

# how to load various data sources - plugin based architecture 
plugins:
  # the package 
  cowj.plugins:
    # items from each package class::field_name
    curl: CurlWrapper::CURL
    redis: RedisWrapper::REDIS

# data store connections
data-sources:
  redis :
    type : redis
    urls: [ "localhost:6379"]

  json_place:
    type: curl
    url: https://jsonplaceholder.typicode.com

cron:
  cache:
    exec: _/cache.md
    boot: true
    at: "*/5 * * * *"
```

It simply defines the routes - as well the handler script for such a route.
In this way it is very similar to PHP doctrine, as well as DJango or Play.

### Scripting

Is Polyglot.  We support JSR-223 languages - in built support is provided right now for:

1. JavaScript 
2. Python via Jython 
3. Groovy 
4. ZoomBA  

Underlying we are using the specially cloned and jetty 11 migrated spark-java fork:

https://github.com/nmondal/spark-11

Also it uses ZoomBA extensively : https://gitlab.com/non.est.sacra/zoomba  

Here is how it works:

1. Client calls server
2. Server creates a `Request, Response` pair and sends it to a handler function
3. A script is the handler function which receives the context as `req,resp` and can use it to extract whatever it wants.
4. DataSources are loaded and injected with `_ds`  variable.

Here is one such example of routes being implemented:

```js
// javascript
let x = { "id" : req.params("id") };
x;// return 
```

The context is defined as:

https://sparkjava.com/documentation#request

https://sparkjava.com/documentation#response

A good read about why we try to avoid type systems inside can be found here:

[Clojure vs. The Static Typing World](https://ericnormand.me/article/clojure-and-types)

#### Shared Memory

Cowj supports a global, non session oriented global memory - which can be used
to double time as a poor substitute for in memory cache -
and it is a `ConcurrentHashMap` - accessible by the binding variable `_shared`.

The syntax for using this would be:

```scala
v = _shared.<key_name> // groovy, zoomba 
v = _shared[<key_name>] // groovy, zoomba, js, python  
```

See the document  "A Guide to COWJ Scripting" found here - [Scripting](manual/scripting.md)

### Threading 

We can specify the `min` , `max`, and `timeout` for the underlying jetty threadpool.


### Routes
As expected routes are grouped under the `HTTP` verb.
The idea is pretty simple, in the left side we have the virtual path of the server,
while on the right side we have the real script location which should be executed to run it.

#### Async Routes 
A special prefix is reserved `_async_` , any route with this prefix would be executed asynchronously, 
and would return almost immediately responding with a plausible almost GUID string id for the task. 

If one wants to make the server work for long running tasks, programmatically,  
this is one way.

```yaml
# async IO configuration 
async:
   threads: 8 # for async io, if not specified, infinite in practice  
   keep: 32 # keep only 32 task results , if not specified 1024 by default 
   fail: _/scripts/js/async_task_failure_handler.js #  async task failure handler 
```

### Filters

These are how one can have before and after callback before and after any route pattern gets hit.  

A `before` filter gets hit before hitting the actual route, while an `after` filter gets hit after returning from the route, so one can modify the response if need be.

Classic example of `before` filter is for `auth` while `after` filter can be used to modify response type  by adding response headers.

#### Before

A typical use case from `auth`:

```yaml
routes:
  get:
    /users: _/users.zm
filters:
  before:
    "*" : _/auth.zm
```

Now inside `auth.zm` :

```scala
// auth.zm
def validate_token(){ /* verification here */ }
token = req.headers("auth")
assert("Invalid Request", 403) as { validate_token(token) } 
```

#### Finally

A typical use case to json format error messages:

```yaml
routes:
  get:
    /users: _/users.zm
filters:
  finally:
     "*" : _/finally.zm
```

Now inside `finally.zm` :

```scala
// finally.zm
if ( resp.status @ [200:400] ){ return }
error_msg = resp.body
resp.body( jstr( { "error" : error_msg } ) )
```

This ensures all errors are formatted as json.

### Cron

In the `cron` section one can specify the "recurring" tasks 
for a project. The key becomes name of the task, 
while:

1. `exec` : script that needs to be executed
2. `boot` : to run while system startup 
3. `at` : cron expression to define the recurrence

### Data Sources

If the idea of COWJ is to do CRUD, where it does CRUD to/from? The underlying data is provided via the data sources.

How data sources work? There is a global map for registered data sources, which gets injected in the scripts as `_ds`  :

```js
_ds.get("redis") // javascript 
_ds["redis"] // zoomba, groovy, python 
_ds.redis // zoomba, groovy 
```

Would access the data source which is registered in the name of `redis` .

Right now there are the following data sources supported:

##### JDBC

JDBC data source - anything that can connect to JDDBC drivers.

```yaml
some_jdbc:
  type: jdbc # shoule have been registered before
  driver : "full-class-for-driver"
  connection: "connection-string"
  properties: # properties for connection
    x : y # all of them will be added 
```

##### CURL

External Web Service calling. This is the underlying mechanism to call Proxies to forwarding data.

```yaml
some_curl:
  type: curl # shoule have been registered before
  url : "https://jsonplaceholder.typicode.com" # the url 
  headers: # headers we want to pass through
    x : y # all of them will be added 
```

##### Google Storage

Exposes Google Storage as a data source.

```yaml
googl_storage:
  type: g_storage # shoule have been registered before
```

##### Redis

Exposes Redis cluster  as a data source:

```yaml
 redis :
   type : redis # should register before 
   urls: [ "localhost:6379"] # bunch of urls to the cluster 
```

##### Firebase Notification Service

Firebase  as a data source.

### Plugins

All data sources are implemented as plug and play architecture such that no code is required to change for adding plugins to the original one.

This is how one can register a plugin to the system - the following showcases all default ones:

```yaml
# how to load various data sources - plugin based architecture 
plugins:
  # the package 
  cowj.plugins:
    # items from each package class::field_name
    curl: CurlWrapper::CURL
    fcm: FCMWrapper::FCM
    g_storage: GoogleStorageWrapper::STORAGE
    jdbc: JDBCWrapper::JDBC
    redis: RedisWrapper::REDIS
```

As one can see, we have multiple keys inside the `plugins` which corresponds to multiple packages - and under each package there are `type` of `datasource` we want to register it as, and the right side is the `implementor_class::static_field_name`.

In plain language it reads:

> A static filed `CURL` of the class `cowj.plugins.CurlWrapper` implements a `DataStore.Creator` class and is being registered as `curl` type of data store creator.

#### Library Folder

From where plugins should be loaded? If one chose not to compile their code with COWJ - as majority of the case would be - there is a special folder in the base director defaults to `lib`.  All jars in all directories, recursively will be loaded in system boot and would be put into class path,

One can naturally change the location of the lib folder by:

```yaml
lib: _/some_other_random_dir
```

How to author COWJ plugins can be found in here [Writing Custom Plugins](manual/plugins.md)

### Proxies

Path/Packet forwarding. One simply creates a base host - in the data source section of type `curl` and then use that key as base for all forwarding.

In the Yaml example the following routing is done:

```
localhost:5003/users 
--> https://jsonplaceholder.typicode.com/users
```

System responds back with the same status as of the external web service as well as the response from the web service gets transferred back to the original caller.

Proxies can be used to transform the payload to the external server, as well as can be used to transform back the data from the external server.

First one we call "forward" transform, and the other one "reverse" transform.

#### Forward Transform

This is easy with the `before` filter. The idea is as follows:

```scala
// before.zm 
forward_payload = {
  "headers" : {
    key : value
  },
  "query" : {
    key : value
  },  
  "body" : "request body"
}
req.attribute("_proxy", forward_payload) 
```

As for the payload it has the following to be forwarded to the destination server :

1. `headers` has a mutable map of request headers 
2. `queries` has mutable map of all query parameters 
3. `body` as the request body 

The underlying system picks up the request attribute named `_proxy` as present, and then forwards it to the destination server.

#### Reverse Transform

This is easily doable by the `after` filter.  Just intercept the response, and we can do whatever we want to do with it.

## Type System

We support `json schema` based input validation.
To read more see [Writing Input Validations](manual/types.md)

## Auth

We support `casbin` based Authorization and pretty basic token based Authentication too.
To read more see [Embedding Auth](manual/auth.md)

## Using Stand Alone 


### Building 

1. Clone the repo to a local directory  
2. Install / Download java 17 from adoptium ( https://adoptium.net ) 
3. If you do no have `gradle` install/download gradle  ( https://gradle.org/install ) 
4. Open command promt and run `gradle`, if it runs you are good.
5. Go to the local directory where cowj is cloned and issue the command `gradle build`
6. It would take some time and will be build.


### Running 

1. Build the app. 
2. Go to the `app/build/libs` folder.
3. Run the stand-alone binary by:

```shell
java -jar cowj-0.1-SNAPSHOT.jar  <config-file>
```

where you can chose any yaml config file. There are variety of files in the `app/samples` folder.
For example to run the `hello` app :

```shell
# you are in the folder app/build/libs 
java -jar cowj-0.1-SNAPSHOT.jar  ../../samples/hello/hello.yaml 
```

The jar has been created such as to have classpath property set to run
as long as all the dependencies are in the `deps` folder.

### Auto Load on Save for Development & Testing 

It was suggested that for development and testing it is better to have automatic reloading of some of the 
files on save. In a full configuration based system this pose a problem. 
However, we have implemented load on save for 

1. Scripts 
2. JSON Schema 

Any other configuration file have implication and needs to reload the entire app.

```shell
# you are in the folder app/build/libs 
java -jar cowj-0.1-SNAPSHOT.jar  ../../samples/hello/hello.yaml true # the last argument true ensures it is running in DEV mode  
```
And will load script and schema files automatically on save.
