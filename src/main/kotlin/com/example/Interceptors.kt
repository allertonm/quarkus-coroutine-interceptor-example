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
            val chainedContinuation =
                ChainedContinuation(outerContinuation, log)
            // this works but depends on knowledge of how AroundInvokeInterceptor is implemented
            ctx.parameters[ctx.parameters.size - 1] = chainedContinuation

            // this is the standard way of doing this, but is broken (see https://github.com/quarkusio/quarkus/issues/34001)
            // val newParameters: Array<Any?> = ctxParameters.clone()
            // newParameters[newParameters.size - 1] = chainedContinuation
            // ctx.parameters = newParameters

            // This reflection-based workaround will work for any position in the chain, but... yeesh.
            // setParameters(ctx, newParameters)
            try {
                log.info("proceeding")
                val result = ctx.proceed()
                // the callee can return a result immediately, and in this case we could just return this result
                // directly to the caller rather than resuming the continuation (Kotlin generates code in the caller
                // to handle this immediate return) - but for the sake of simplicity we will always resume the
                // continuation here.
                // (In practice our invocation of ctx.proceed may also be deferred, so it's probably not worth optimizing
                // for this case.)
                if (result != COROUTINE_SUSPENDED) {
                    log.info("Returned immediately with $result")
                    chainedContinuation.resumeWith(Result.success(result))
                }
            } catch (t: Throwable) {
                // similarly to the immediate success case, the callee can throw an exception immediately
                log.error("ctx.proceed threw $t")
                chainedContinuation.resumeWith(Result.failure(t))
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
@Priority(2)
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
@Priority(1)
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
