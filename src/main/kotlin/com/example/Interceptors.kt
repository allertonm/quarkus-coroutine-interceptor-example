package com.example

import io.quarkus.logging.Log
import jakarta.annotation.Priority
import jakarta.inject.Inject
import jakarta.interceptor.*
import org.jboss.logging.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

@InterceptorBinding
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class DoNothing

@InterceptorBinding
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class Intercept1

@InterceptorBinding
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class Intercept2

abstract class CoroutineInterceptor {
    abstract val log: Logger

    protected fun interceptSuspending(ctx: InvocationContext): Any? {
        val ctxParameters: Array<Any?> = ctx.parameters
        @Suppress("UNCHECKED_CAST")
        val outerContinuation = ctxParameters.lastOrNull() as? Continuation<Any?>
        if (outerContinuation != null) {
            Log.info("incoming continuation $outerContinuation")
            try {
                val chainedContinuation =
                    ChainedContinuation(outerContinuation, log)
                val newParameters: Array<Any?> = ctxParameters.clone()
                newParameters[newParameters.size - 1] = chainedContinuation
                // this should work, but will not if our interceptor is not first in the chain
                ctx.parameters = newParameters
                // This will work for any position in the chain, but... yeesh.
                // setParameters(ctx, newParameters)
                log.info("proceeding")
                val result = ctx.proceed()
                if (result != COROUTINE_SUSPENDED) {
                    log.info("Returned immediately with $result")
                    chainedContinuation.resumeWith(Result.success(result))
                }
            } catch (t: Throwable) {
                log.error("ctx.proceed threw $t")
                outerContinuation.resumeWith(Result.failure(t))
            }

            return COROUTINE_SUSPENDED
        } else {
            return ctx.proceed()
        }
    }

    class ChainedContinuation<ResultType>(val outerContination: Continuation<ResultType>, val log: Logger): Continuation<ResultType> {
        override val context: CoroutineContext
            get() = outerContination.context

        override fun resumeWith(result: Result<ResultType>) {
            log.info("ChainedContinuation.resumeWith $result")
            outerContination.resumeWith(result);
        }
    }

    private fun setParameters(ctx: InvocationContext, parameters: Array<Any?>) {
        ctx.parameters = parameters

        if (ctx.javaClass.name == "io.quarkus.arc.impl.AroundInvokeInvocationContext\$NextAroundInvokeInvocationContext") {
            try {
                val innerThisField = ctx.javaClass.getDeclaredField("this$0")
                innerThisField.isAccessible = true
                val underlying = innerThisField[ctx] as InvocationContext
                underlying.parameters = parameters
            } catch (e: Exception) {
                log.error(e)
            }
        }
    }
}

@DoNothing
@Interceptor
@Priority(1)
class DoNothingInterceptor @Inject constructor(override val log: Logger)
    : CoroutineInterceptor() {

    @AroundInvoke
    fun intercept1(ctx: InvocationContext): Any? {
        log.info("proceeding")
        return ctx.proceed()
    }
}

@Intercept1
@Interceptor
@Priority(2)
class Interceptor1 @Inject constructor(override val log: Logger)
    : CoroutineInterceptor() {

    @AroundInvoke
    fun intercept1(ctx: InvocationContext): Any? = interceptSuspending(ctx)
}

@Intercept2
@Interceptor
@Priority(3)
class Interceptor2 @Inject constructor(override val log: Logger)
    : CoroutineInterceptor() {

    @AroundInvoke
    fun intercept2(ctx: InvocationContext): Any? = interceptSuspending(ctx)
}
