# Auth

[TOC]

Cowj supports `auth` using `JCasbin`.

## Design Ideas

Design goal of this has been to do authorisation verification for  `Request`.
After much thought we have also put authentication.

### Options

## Special  Location for Auth

If anyone wants to use auth, it has to be inside the `auth` folder, 
the special designated folder must hold all auth definitions.

Suppose, then we have the `auth` folder pointing to `/something/auth`, 
then the designated auth file is : `/something/auth/auth.yaml`.

This was done in purpose, to ensure fixed location for `auth`. 



Although that is not really the design, technically one can override the `auth()` function in the model to move auth to any other file location.

For this manual we point to the `samples/auth_demo` project. 

## Configuration

```yaml
# The standard location auth file
# is it enabled?
disabled: false

provider:
  type: "auth-jwt"
  secret-key: "42"
  issuer: "test"
  expiry: 60000 # 1 minute
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

In the main app's yaml configuration file we must define the auth provider as follows:

```yaml
# define plugins 
plugins:
  cowj.plugins:
    auth-jdbc : StorageAuthenticator::JDBC
    auth-redis: StorageAuthenticator::REDIS
    auth-gs   : StorageAuthenticator::GOOGLE_STORAGE
    auth-jwt  : JWTAuthenticator::JWT # jwt based authentication

```

#### Disabled

Is the auth to be disabled - so that it won't be used it `true`. Default value `false`.

We have 2 types of Authentication providers.

### Authentication Configuration 

#### Storage Based 

This is how one can define storage based auth. There are 3 types, but they are to be defined in the plugins section. 

```yaml
provider:
  type: "auth-jdbc" # type to be defined in the plugins as above shown 
  storage: "jdbc-ds-name" # name of the  data source to be used 
  token: "body:token" # defines how to extract the token, from body or header 
  query: "select * from tokens where token = '%s'" # query which would get back the user row from tokens table 
  user: "user" # in the resultant row name of the users column 
  expiry: "expiry" # resultant row name of the expiry column 
  risks: # bunch of allowed paths - which does not require authentication 
   - /path1
   - /path2 
```

System would pick up the token using the `token` expression, it is `xpath` expression with a name space either `header` or `body`. In this case, we are specifically telling body of the request would have a field called `token` and we should extract that. 

#### JWT Based 

```yaml
provider:
  type: "auth-jwt" # type of the provider - define in the plugin 
  secrets: "my-secret-provider" # a secrets manager 
  secret-key: "my-secret-key" # key to the secret key in the secrets manager  
  issuer: "issuer-identity" # key to the issuer identity in the secrets manager 
  expiry: 60000 # in ms, how long the token would be valid by default 
  risks:
    - "/token" # bunch of risky routes 

```

To make this work, create a JWT token as follows in the `/tokens` route:

```scala
// get provider
jwt_provider = _ds["ds:auth"] ?? ""
panic( empty(jwt_provider), "JWT Provider does not exist!")
// create whatever token - presumably after auth
jwt_token = jwt_provider.jwt("bar")
// respond
jstr(  { "tok" :  jwt_token.toString() } )
```

And then we can use the token simply by adding them to `Authorization` header:

```shell
curl -H "Authorization: Bearer <insert-jwt-here>" localhost:6044/hello 
```

See the `app/samples/auth_jwt` folder.

### Authorisation Configurations 

#### User Header

The header key which shall have the user's id. Default is `username` .

#### Casbin Model

There is a fixed location `auth/model.conf` is the location of the model file.

```toml
# auth/model.conf
# https://github.com/php-casbin/casbin-tutorials/blob/master/tutorials/RBAC-with-Casbin.md
[request_definition]
r = sub, obj, act

[policy_definition]
p = sub, obj, act

#  the definition for the RBAC role inheritance relations
[role_definition]
g = _, _

[policy_effect]
e = some(where (p.eft == allow))

[matchers]
m = g(r.sub, p.sub) && keyMatch2(r.obj, p.obj) && regexMatch(r.act, p.act)
```

#### Casbin Policy Adapter

This comes under policy, plan to support some adapters, specifically the Google Storage and S3 adapters.
Currently only supports `file` adapter, which must have `file` key to point to the policy file, which must 
be present in the `auth` folder itself.



#### Message

Used to deliver the un-auth message to the user.



### Example

The Cowj configuration file is shown:

```yaml
# auth_demo/auth_demo.yaml

port: 6042

# path mapping
routes:
  post:
    /entity: _/create.zm
  get:
    /entity/:entityId : _/fetch.zm

```

The policy file can be found here: `auth_demo/auth/policy.csv` 

```csv
g, alice, admin
g, bob, member
p, member, /entity/:id, GET
g, admin, member
p, admin, /entity, POST
```

It says `alice` is admin, and `bob` is a member in the first two lines.

Next the policy access : member can access the route `/entity:id`  via `GET`

Admin is also a member.

Finally, admin can access `/entity` route via `POST`.

```shell
curl -XPOST localhost:6042/entity -H "u:bob" 
```

Will fail with `403` error. while

```shell
curl -XPOST localhost:6042/entity -H "u:alice" 
```

will run through. At the same time:

```shell
curl -XGET localhost:6042/entity/111 -H "u:bob" 
```

would work, but :

```shell
curl -XGET localhost:6042/entity/111
```

will give `401` due to not having the header `u`.  At the same time:

```shell
curl -XGET localhost:6042/hello.json  
```

because it is loaded from `static` - it is exempt - so anyone would be able to access it.


## References

1. Casbin :  https://casbin.org 

2. JCasbin : [GitHub - casbin/jcasbin: An authorization library that supports access control models like ACL, RBAC, ABAC in Java](https://github.com/casbin/jcasbin)

3. Casbin Adapters : https://casbin.org/docs/adapters 

4. Access Control : [Computer access control - Wikipedia](https://en.wikipedia.org/wiki/Computer_access_control) 

5. RBAC : [Role-based access control - Wikipedia](https://en.wikipedia.org/wiki/Role-based_access_control)  

6. ABAC :  [Attribute-based access control - Wikipedia](https://en.wikipedia.org/wiki/Attribute-based_access_control) 
   
   
