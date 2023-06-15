package com.example

import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.quarkus.test.vertx.RunOnVertxContext
import io.quarkus.test.vertx.UniAsserter
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@QuarkusTest
@RunOnVertxContext
@TestSecurity(user = "helmut")
class FooTest {
    @Inject
    lateinit var securityIdentity: SecurityIdentity

    @Test
    fun test(asserter: UniAsserter) {
        val principal1 = securityIdentity.principal
        println(principal1.name)
        asserter.execute {
            val principal2 = securityIdentity.principal
            println(principal2.name)
        }
        println("Hello")
    }
}
