# Type System

[TOC]

Cowj supports appending a type system for input schema validation as of now.

## Design Ideas

Design goal of this has been to do automatic input verification for for `Request` body. 

Sans that, the developer has to assume a lot about how input data would be structured. Consider the POJO:

```java
record class Person( String firstName, String lastName, int age)
```

That something is of this type is an open problem. We can argue that it must have at least one of the attributes. But what works for business?

### Options

The real verification is always hidden deep behind actual verification done, post receiving the actual object.

For example, what about the `Rule` that first name can not be more than 32 chars?

Or age must be between 0 to 150? 

#### Data Compression Formats

These are `thrift` , `avro`, `protobuf` and likes. They can define schema, but the trouble is about rules. Their design goal is compression.

Admittedly they are superior at that, and they also mandate both server and the client to `compile` to generate the actual stub, they take care of serialization problem. 

Admittedly Avro is better at this than the rest, but there are others.

#### REST Standards

These are `Open API`, `RAML`  and `JSON-Schema`.  There are intrinsic trouble with open api because of - it is post facto, once the response is actually coded in, one should `automagically` produce the response.

My own opinionated remark on this - this is not good. 

Code should be done based on interfaces, and auto generating schema is a terrible idea for things which are distributed in nature by definition.

`RAML` is much better than Open API, but stems from the problem of over engineering.

The only trouble is, suitable schema validator is missing for the same. Same goes for Open-API, people are so interested in auto generation, they do not want to validate payload.

JSON-Schema is massively popular among the `Node` enthusiasts, and thus, validations are terribly easy to find. We picked up the fastest java implementation for the same.

#### Theme

1. Develop the schema first for:
   
   1. input 
   
   2. output 
   
   3. error

2. Be Optionally typed  

3. Current live schema version in use should be publicly available in live instance 

And then automatically verify the input coming from clients, optionally, if need be. 

It is in [3] that the Swagger is better - because of auto generation the output schema should be in sync.

The problem remains, however, what sort of validations are put into it?

## Special  Location for Schema

If anyone wants to use a schema, it has to be inside the `static` folder, 
the special designated folder must hold all type definitions.

Suppose, then we have a static folder pointing to `/something/my_static`, 
then the designated schema file is : `/something/my_static/types/schema.yaml`

This `types` folder would host now all `json-schema`.

This was done in purpose, to ensure public availability of the interfacing contract. 

## Schema Definitions

We support up-to draft-07 of JSON Schema ( [https://json-schema.org](https://json-schema.org) ).

Take a typical app which creates person and gets the person back `prod` app:

```yaml
port: 5042
# path mapping
routes:
  post:
    /person: _/create_person.zm
  get:
    /person/:personId : _/fetch_person.zm
```

Corresponding schema is defined as: `app/samples/prod/types/schema.yaml`

```yaml
#####################
# Defines the Schemas for routes
# https://json-schema.org/learn/miscellaneous-examples.html
#####################
labels: # how system knows which label to invoke 
   ok:  "resp.status == 200" # when response status is 200 
   err:  "resp.status != 200" # when it is not 

verify:
   in: true # verify input schema 
   out: true # verify output schema, and log errors 

routes:
   /person: # the route 
       post:
         in: Person.json # the input body schema 
         ok: RetVal.json
         err: RetVal.json

   /person/*:
     get:
       ok: Person.json
       err: RetVal.json
```

### Labels

Special case is of input schema `in`, for the rest, how to know which output schema to map it from?
This is done by `expression` labels. Under the hood system runs an expression evaluator.

```java
interface StatusLabel extends BiPredicate<Request,Response> {
   String name() ;
   String expression() ;
}
```

This way, way more specific schema mapping can be done & checked with the validator.
`name()` corresponds to the left hand side, for example `ok` is a name.
`expression()` is the right hand side, which when evaluated to `true` corresponding schema will be applied.
This name against schema is stored in the routes. 

### Verify

This turns `on` and `off` input and output schema verification.
Once a schema is attached the default configuration is follows:

```yaml
verify:
   in: true # verify input schema 
   out: false # do not verify output schema - classic someone else's problem
```

This whole schema verification technically can be done at the proxy API gateway layer.
Validation takes a little amount, initially from `20ms` to load the schema, on a proper run it would take around 
0 to 2 ms. 

### Routes

As one can see, we invert the `routes` with `path` in front, and then use the verbs.
As each of these paths can be accessed with multiple `verb` we invert it.

## Type Definitions

These are examples from the same app `prod`  which is available in the `app/samples` directory:

### RetVal Type

This comes from `types/RetVal.json` :

```json
{
  "$id": "https://github.com/nmondal/cowj/prod/retval.schema.json",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "oneOf" : [
    {
      "properties": {
        "personId": {
          "type": "string"
        }},"required":["personId"]
    },
    {
      "properties": {
        "error": {
          "type": "string"
        }},"required":["error"]
    }
  ]
}
```

### Person Type

This comes from `types/Person.json` :

```json
{
  "$id": "https://github.com/nmondal/cowj/prod/person.schema.json",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Person",
  "type": "object",
  "properties": {
    "firstName": {
      "type": "string",
      "description": "The person's first name."
    },
    "lastName": {
      "type": "string",
      "description": "The person's last name."
    },
    "age": {
      "description": "Age in years which must be equal to or greater than zero.",
      "type": "integer",
      "minimum": 0,
      "maximum" : 150
    },
    "personId": {
      "description": "System Generated Person Id",
      "type": "string"
    }
  },
  "required": ["firstName", "lastName" ]
}
```

## Post Processing

### On Successful

Once we turn on the schema validation, then, the system automatically validates the schema and parsed JSON Object gets added to the `Request` as an attribute `_body` , which then  the route script can use as follows:

```scala
// ZoomBA 
assert( "_body" @ req.attributes() , "How come req.body failed to verify and come here?" , 409 )
payload = req.attribute("_body") // this should already have the parsed data
```

### On Validation Error

Validation errors are responded with `409` as discussed in [SO here](https://stackoverflow.com/questions/7939137/what-http-status-code-should-be-used-for-wrong-input) - along with the validation error.

## Output Schema Validation

On success, nothing, except time taken gets logged. 
On failure, the error gets logged, server keeps on running.

## Schema View

Given the `static` folder is mapped, one can simply browse to :

 `<host>:<port>/types/schema.yaml`

to see the schema mapping, along with other files:
`<host>:<port>/types/RetVal.json`

This makes the schema publicly exposed.

## References

1. Thrift :  [Thrift: The Missing Guide](http://diwakergupta.github.io/thrift-missing-guide/)

2. Avro : [IDL Language | Apache Avro](https://avro.apache.org/docs/1.11.1/idl-language/) 

3. Protobuf :  [Language Guide (proto 3) | Protocol Buffers Documentation](https://protobuf.dev/programming-guides/proto3/)

4. JSON Schema : https://json-schema.org 

5. RAML : https://github.com/raml-org/raml-spec/blob/master/versions/raml-10/raml-10.md/ 

6. Open API ( Swagger ) :   https://swagger.io/specification/https://swagger.io/specification/

7. On Type Systems:
   
   1. [Clojure vs. The Static Typing World](https://ericnormand.me/article/clojure-and-types) 
   
   2. [Data exchange - Wikipedia](https://en.wikipedia.org/wiki/Data_exchange) 

8. Static Vs Dynamic Typing : 
   
   1.  https://inst.eecs.berkeley.edu/~cs61bl/su15/materials/guides/static-dynamic.pdf
   
   2.  https://www.ics.uci.edu/~jajones/INF102-S18/readings/23_hanenberg.pdf
   
   3. [(PDF) Static Typing Where Possible, Dynamic Typing When Needed: The End of the Cold War Between Programming Languages](https://www.researchgate.net/publication/213886116_Static_Typing_Where_Possible_Dynamic_Typing_When_Needed_The_End_of_the_Cold_War_Between_Programming_Languages) 
   
   
