# Type System

Cowj supports appending a type system for input schema validation as of now.

## Schema Location 

If anyone wants to use a schema, it has to be inside the `static` folder, 
the special designated folder must hold all type definitions.

Suppose, then we have a static folder pointing to `/something/my_static`, 
then the designated schema file is : `/something/my_static/types/schema.yaml`

This `types` folder would host now all `json-schema`.
We support upto draft-07 of JSON Schema ( https://json-schema.org ).

## Schema Definitions

Take a typical app which creates person and gets the person back.

```yaml
#########################
# Schema validation test bed
# Also, using _shared
#########################

port: 5042
# path mapping
routes:
  post:
    /person: _/create_person.zm
  get:
    /person/:personId : _/fetch_person.zm
```

Corresponding schema is defined as:

```yaml
#####################
# Defines the Schemas for routes
# https://json-schema.org/learn/miscellaneous-examples.html
#####################

/person:
    post:
      in: Person.json
      ok: RetVal.json
      err: RetVal.json

/person/*:
  get:
    ok: Person.json
    err: RetVal.json

```
As one can see, we invert the `routes` with `path` in front, and then use the verbs.
As each of these paths can be accessed with multiple `verb` we invert it.

## Type Definitions

These are examples from the same app:

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
          "$id": "#/properties/personId",
          "type": "string"
        }},"required":["personId"]
    },
    {
      "properties": {
        "error": {
          "$id": "#/properties/error",
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

Once we turn on the schema validation, then, the system automatically validates the schema
and parsed JSON Object gets added to the `Request` as an attribute `_body` , which then 
the route script can use as follows:

```scala
// ZoomBA 
assert( "_body" @ req.attributes() , "How come req.body failed to verify and come here?" , 409 )
payload = req.attribute("_body") // this should already have the parsed data
```

### On Validation Error 

Validation errors are responded with `409` as discussed in [SO here](https://stackoverflow.com/questions/7939137/what-http-status-code-should-be-used-for-wrong-input) - along with the validation error.

## Output Schema Valiation
There is really no need for this, as of now.

## Schema View

Given the `static` folder is mapped, one can simply browse to : `<host>:<port>/types/schema.yaml`
to see the schema mapping, along with other files:
`<host>:<port>/types/RetVal.json`

This makes the schema publicly exposed.
