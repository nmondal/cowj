# Scripting for COWJ

## Why Scripts?

### Motivation

Cowj was build to replace code with versioned configuration - to ensure that the `back end development` does not require beyond a limited no of Engineers. As would be apparent from the main doc, the motivation is very anti establishment, and as one can find, the focus here to help business out, not to promote development or increase cost in development.

### Fallacies , Economics

There are some inherent fallacies which a developer should be aware of.
An Engineer builds engine, and engines do not get changed per day, even per year, perhaps an improvement can be found in every decade or say. In software systems this timeline gets shrunk, but even then acceptable change in engines are perhaps even 2 times a decade.

It took 3 decades of effort to move out of the B-Tree and get along with LSM Trees for the masses.
Such core algorithmic changes are what we can call Engine changes, they are the engine. Naturally these algorithms are part of the storage engine.

This is however what we do not see in `business development`.  Business Development is like building a typical building. It requires masons and not Engineers. This is entirely different from developing a bridge, and clearly building Burj Khalifa is not a simple job for masons.

Building SQL Engines are engineering, of sorts, and under no circumstances writing query on top of them are not.
Business Development requires CRUD and influx of some random `business logic` into the mix, there is nothing foundational, nothing fanciful about it.

Lot of noobs talk about `scale`.  In reality one only talks about scale when there is no fundamental problem around it. Sometimes scale poses its own problem - 1 billion customer needs to be searched in less than a second. That is not really a problem of scale, it is a matter of algorithmic and Engineering ingenuity. You do not change the algorithm to do so each day. 

Compare this to  business code - which is throwaway, all the time. Business will keep on updating the code, and there is no two ways about it. Coders will not be able to cope up with it, it is not possible. 
This culminates to the fallacy of business development - it is not development - it is almost always a hack that is there for incredibly short amount of time - with a life maximally upto a year.

There is no "practice" that takes this fallacy of business development into account, because they are paid to do the quite opposite. More changes would require more people. 

The proper bane for this fallacy has a name in enterprise software - "Custom Development" or "Solution Engineering". Most of the developers are not building any product - they are doing "Custom Development".
It is always throwaway code, always.

### Types, Domain, Existence

So what if we want to get a "Custom Solution" built in no time, say in less than 1 day? What does a custom solution would feel like? As again - any business is nothing but CRUD, no matter how much the "Senior Engineer" groups cry about it. 

So CRUD against what? Definitely a bunch of data sources. Data in what form? This is where the jury has 100s of different ways to get data and set data. Compression? Encoding? All of them are just triviality, in the end business data is all having some schema for NOW, which would change in next 10 days even. 

JSON is for the win. Type systems got to go, with type verification of fields to be put in as configuration in case they are needed. JSON schema, RAML and OpenAPI schemas help. One can even get into compression if need be. But for a normal business it is overkill.

So if one look at the fallacy and the economy angle, and then look at the type and domain angle, one must realize this is a matter of writing random scripts and getting it away. This is precisely what mulesoft has done, and done very well. It is not random that Salesforce gobbled them up for billions.

Enterprise Software is CRUD + Reports.
Enterprise Software is matchstick engineering or rather just write scripts which runs.

But can they run fast? 
How fast is fast enough? 
Druid Engine exposes its data via a custom SQL layer - and even with that layer it can respond with less than a second for 10 million records in a 2 GB machine. These are queries an enterprise class system would take seconds.
Speed is not really the problem of enterprise.
Agility is.
It is for being Agile alone, enterprises digitised themselves, and the first computing revolution happened.
Forget AI, the enterprise must reinvent itself to move fast, because the 2nd revolution would make many of them redundant to the core.

## Engines

If businesses programming is assembly - we need components, and we must democratize it. 
Thus, we wanted polyglot support - and hence JVM was put into action. JVM has JSR-223 standard, via which 
many languages can be used as a scripting language.

### Default Engines

The following languages are default in the system:

1. JavaScript - via Mozilla Rhino Engine 
2. Python - via Jython binding
3. Groovy - as standard Java Scripting 
4. ZoomBA - a custom made language created to do spaghetti coding easy

These are the ones which would not require any code change, they are available, as is, via default.
Also Java class instances can be directly called up as scripts, see interfacing section for more.

### Importing Other Engines

On the way to support pluggable engines.
Check plugins document to see more.

## Interfacing

A script essentially abstracts a java 8 `Function` of the form:

```java
Function<Binding,Object> function;
```

while a `Binding` is nothing but a name,value pair map - an abstraction created for JSR-223.
Consider a function as follows:

```java
int add( int a, int b){ return a + b ; }
int r = add(10, 32);
```

this can be very well abstracted by a function as follows:

```javascript
function script( parameterMap ){ /* implementation */ }
let r = script( { a : 10, b :  32  } ); 
```

Once we have this abstraction, we can build anything on top of it.

### Lifecycle of a Script

#### Identification

Given a string is to be interpreted as a script,
based on the extension of the script COWJ Engine loads appropriate
engine for the script.

1. `js` --> Rhino ( JavaScript )
2. `py` --> Python ( Jython )
3. `groovy` --> Groovy 
4. `zm,zmb` --> ZoomBA
5. `class` --> JVM Binary Execution 

#### Load

Loading requires absolute path of the script.
Which is non trivial, hence the special syntax `_/` is provided, 
this points to the base directory, the directory of the configuration `yaml` file.
Thus, if the script path is this:

```yaml
# I am  /home/user_name/hello/config.yaml 
get:
   /x : _/x.zm
```

the base directory would be `/home/user_name/hello/` and thus, 
the route for `x` is going to be: `/home/user_name/hello/x.zm`.

For the  `.class` extension - full class name for the class is necessary.
System uses reflection, and we have to make sure the class implements `Scriptable` interface, 
specifically the method `exec(Bindings)`.

#### Compile And Cache

For the first time load the scripts gets compiled into JVM form - so that it gets near native JVM speed
in further execution, sans, ZoomBA scripts. There are engine specific cache in which compiled forms 
are stored for faster access.

This cache is lifetime cache, there is no way to invalidate during runtime of COWJ.

#### Execution

Scriptable gets the data it needs in the `Bindings` object, which is a JSR-223 standard.

```java
Object exec(Bindings b) throws Exception;
```

Then on top of it executes and can throw exception.
Following variables gets injected in the `Bindings` variable:

1. DataSources - marked as `_ds`
2. Asserters - sans ZoomBA `Test.expect, Test.panic`

##### Route and Filter

Following variables gets injected in the `Bindings` variable:

1. Request - marked as `req`
2. Response `resp`
3. Error if any `_ex` 
4. Result to be returned `_res`

Note that for `Filter` the response object is not used, while for `Route`, the response object 
is returned as response body automatically.
Implementation is done re-using the exec function.

```java
Object exec(Request request, Response response);
```

##### Proxy Hook

Abstraction about the proxy is as follows:

```java
 Function<Request, EitherMonad<Map<String,Object>>> proxyTransformation();
```

In specificity, for the scriptable we add the following parameters to the `Bindings` :

1. `query` : query map for the request 
2. `headers` : headers map for the request 
3. `body` : body of the request 

In the end it is supposed to return error or a map comprise of these 3 keys, 
which can then be used to send the crafted request to the destination.

#### Errors

##### Default Handling

Error generated, from the script, any script will raise `500` error, by default.
The request body would be the `toString()` of the exception that was raise.

##### Raising

One can raise custom errors via `Test.expect()` and `Test.panic()` functions family in JSR langs, sans ZoomBA, 
and in case of ZoomBA default support is given using `assert()` and `panic()` function family.

The syntax are as follows:

```scala
// this is JSR 223 - does not have default asserters, so it is inserted 
Test.expect(false) // raise error 
Test.expect(false, "Message") // raise error with message 
Test.expect(false, "Message", 418 ) // raise error with message with a status 

Test.panic(true) // raise error 
Test.panic(true, "Message") // raise error with message 
Test.panic(true, "Message", 418 ) // raise error with message with a status 
```

```scala
// this is ZoomBA - has default assert and panic 
assert(false) // raise error 
assert(false, "Message") // raise error with message 
assert(false, "Message", 418 ) // raise error with message with a status 

panic(true) // raise error 
panic(true, "Message") // raise error with message 
panic(true, "Message", 418 ) // raise error with message with a status 
```

### Logging

The special variable `_log` is always inside any script to `log` messages.
This is a `sl4j` log binding via proxy - and always prints the name of the script 
from which it got invoked.

## Debugging

WIP.

## Jython Usage 

Given Jython is closed at 2.7, one should use it as wrapper to run Java classes in a clean way.
One can understand the way to do Jython - using underlying Java classes from here:
https://www.tutorialspoint.com/jython/jython_importing_java_libraries.htm

Also, there is `app/samples/jython` project to see how to get `json` working out.
Evidently the dialect will be Pythonic, rest would be `JVM` based.

### Returning Values

Jython scripts, if they were to return a value, must store the value
into a special variable `_res` . Apparently scripts can not return, so 
a custom hack is in place for returning.

### Installing Packages 

One can install Python packages by the following.
First, install `pip` as follows:
Go to the `libs/deps` directory and run the command:

```shell
java -jar jython-standalone-2.7.3.jar -m ensurepip 
```
This will install the `pip` .
Now, say you want to install `requests` module:

```shell
java -jar jython-standalone-2.7.3.jar -m pip install requests 
```
To test that the module runs - you run the following:

```shell
java -jar jython-standalone-2.7.3.jar 
```

And then simply try:
```python
# imports request 
import requests
```
This should be error free.
Now any python script will be able to import `requests` module.

## JavaScript Usage

Rhino gets used as the underlying engine. 
Rhino got a bug which does not allow it to print to console, hence `Test.print()` and `Test.printe()`
to be used for now.


### Installing Packages
`require()` is supported in JavaScript, thus one can simply import any javascript file which is hosted
inside the `lib/js/` directory of the project.

See the file `app/samples/hello/hello.js` :

```javascript
let add = require( "./demo.js")
_log.info( "10 + 20 is {}", add(10,20) )
```
where `demo.js` is situated at `lib/js/demo.js` location for the `hello` app.


## References

1. JSR 223 - https://en.wikipedia.org/wiki/Scripting_for_the_Java_Platform 
2. Script Engines - https://en.wikipedia.org/wiki/List_of_JVM_languages 
3. Rhino - https://github.com/mozilla/rhino 
4. Jython - https://www.jython.org 
5. Groovy - https://groovy-lang.org 
6. ZoomBA - https://gitlab.com/non.est.sacra/zoomba/ 
7. Kotlin Scripting - https://github.com/Kotlin/kotlin-script-examples/blob/master/jvm/jsr223/jsr223.md 
8. Bindings - https://docs.oracle.com/javase/9/docs/api/javax/script/Bindings.html 
9. Routes - https://sparkjava.com/documentation#routes 
10. Request - https://sparkjava.com/documentation#request 
11. Response - https://sparkjava.com/documentation#response 
12. Filters - https://sparkjava.com/documentation#filters 
13. Forward Proxy - https://en.wikipedia.org/wiki/Proxy_server 
