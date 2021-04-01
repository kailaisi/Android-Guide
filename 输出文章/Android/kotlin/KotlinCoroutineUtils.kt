package com.honeywell.hch.mobilesubphone.uitl

import org.eclipse.jetty.io.nio.AsyncConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import kotlin.coroutines.*

/**
 * 描述:
 * <p/>作者：wu
 * <br/>创建时间：2021/4/1 15:03
 */

suspend fun <T> AsyncScope.await(block: () -> Call<T>) = suspendCoroutine<T> { continuation ->
    val call = block()
    call.enqueue(object :Callback<T>{
        override fun onResponse(call: Call<T>, response: Response<T>) {
            if (response.isSuccessful){
                response.body().let { continuation::resume }
            }else{
                continuation.resumeWithException(HttpException(response))
            }
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            continuation.resumeWithException(t)
        }

    })

}

interface AsyncScope

fun asyncInfo(context: CoroutineContext = EmptyCoroutineContext, block: suspend AsyncScope.() -> Unit) {
    var asyncConnection = AsyncCoroutine(context)
    block.startCoroutine(asyncConnection, asyncConnection)
}

class AsyncCoroutine(override val context: CoroutineContext = EmptyCoroutineContext) : Continuation<Unit>, AsyncScope {
    override fun resumeWith(result: Result<Unit>) {
        result.getOrThrow()
    }

}