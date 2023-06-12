# cowj
[C]onfiguration [O]nly [Web] on [J]VM 

<img src="https://icons.iconarchive.com/icons/iconarchive/cute-animal/512/Cute-Cow-icon.png" style="zoom:20%;" />


[toc]

## Goal

COWJ is pronounced as Cows, and stands for:

>Configuration-Only-Web-over-JVM

It should be very clear from the naming that:

>objective is to optimise back-end development and replacing it with configurations.


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

#### "Business" Logic 

There should not be any "business logic" in the code. They are susceptible to change,
hence they should be hosted outside the API end points - DSL should be created to maintain.


#### Data Store Access

Any "Service" point requiring any "data store" access need to declare it, specifically 
as part of the service configuration process. Objective of the engine would be to handle the data transfer. JSON is the choice for data transfer for now.


### Current Implementation 

#### Service Configuration

Here is the config:

```yaml
# This shows how COWJ service is routed

# port of the server
port : 8000

# routes information
routes:
  get:
    /hello : _/scripts/js/hello.js
    /param/:id  : _/scripts/js/param.js

  post:
    /hello : _/scripts/js/hello.js

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

```
It simply defines the routes - as well the handler script for such a route.
In this way it is very similar to PHP doctrine, as well as DJango or Play.


#### Scripting 

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
let x = { "id" : req.params("id") };
x;// return 
```
The context is defined as:

https://sparkjava.com/documentation#request

https://sparkjava.com/documentation#response



#### Filters 

These are how one can have before and after callback before and after any route pattern gets hit. 



#### Proxies 

Path/Packet forwarding. One simply creates a base host - in the data source section of type `curl` and then use that key as base for all forwarding.

In the Yaml example the following routing is done:

``` 
localhost:1003/users 
--> https://jsonplaceholder.typicode.com/users
```

System responds back with the same status as of the external web service as well as the response from the web service gets transferred back to the original caller.



### Running 

1. Get the spark-11 cloned in local 
2. Get ZoomBA cloned in local 
3. Build the app.
4. Run the app.

Note: It also has `fat-jar` via `shadowJar()` task, one can have one single fat jar for the whole project.







 





