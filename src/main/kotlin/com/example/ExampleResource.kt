package com.example

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine

@Path("/")
open class ExampleResource {

    @GET
    @Path("/immediate")
    @Produces(MediaType.TEXT_PLAIN)
    @Intercept1
    @Intercept2
    open suspend fun helloImmediate() = "Hello from RESTEasy Reactive"

    @GET
    @Path("/deferred")
    @Produces(MediaType.TEXT_PLAIN)
    @DoNothing
    @Intercept1
    @Intercept2
    open suspend fun helloDeferred(): String {
        delay(1000)
        return "Hello from RESTEasy Reactive"
    }

    @GET
    @Path("/fail_immediate")
    @Produces(MediaType.TEXT_PLAIN)
    @Intercept1
//    @Intercept2
    open suspend fun helloImmediateFail(): String = throw RuntimeException()

    @GET
    @Path("/fail_deferred")
    @Produces(MediaType.TEXT_PLAIN)
    @Intercept1
//    @Intercept2
    open suspend fun helloDeferredFail(): String {
        delay(1000)
        throw RuntimeException()
    }
}
