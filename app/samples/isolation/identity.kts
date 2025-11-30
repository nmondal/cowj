import spark.Request

val req : Request = bindings["req"] as Request  // do not make it non-nullable
req.body() // return this
