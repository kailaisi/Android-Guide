Kotlin的协程原理
回调转写成挂起函数方式

* 使用suspendCoroutine获取挂起函数的Continuation
* 回调成功的分支使用Continuation.resume（value）进行回调处理
* 失败的使用Continuation.resumeWithException(e)进行处理

```
    try {
        val user=github.getUser("..")
        showUser(user)
    }catch (e:Exception){
        showError(e)
    }

suspend fun getUser(name:String)=suspendCoroutine<User>{ continuation->
    github.getUserCallback(name).enqueue(object:Callback<User>{
        override fun onFailure(call: Call<User>, t: Throwable) {
            continuation.resumeWithException(t)
        }

        override fun onResponse(call: Call<User>, response: Response<User>) {
            return continuation.resume(response.body()!!)
        }

    })
}
```



协程的创建：

* 协程是一段可执行的程序
* 创建通常需要一个函数：suspend fun 
* 创建需要API：startCoroutine和createCoroutine
* 