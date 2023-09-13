/*
* Awesomeness of the Kotlin folks, do not ask
* Fix from here, this should work
* https://discuss.kotlinlang.org/t/add-bindings-to-kotlin-jsr-223-engine/20317/2
* https://github.com/Kotlin/kotlin-script-examples/blob/master/jvm/jsr223/jsr223-main-kts/testData/import-middle.main.kts
* https://github.com/spring-projects/spring-framework/blob/main/spring-webflux/src/test/resources/org/springframework/web/reactive/result/view/script/kotlin/eval.kts
* This is definitely not working ...
* https://github.com/JetBrains/kotlin/blob/master/libraries/scripting/jsr223-test/test/kotlin/script/experimental/jsr223/test/KotlinJsr223ScriptEngineIT.kt
*
* */
import spark.Request
//val req : Request = bindings["req"] as Request  // do not make it non-nullable
println(req)
"hello, world!" // last line is the response value


