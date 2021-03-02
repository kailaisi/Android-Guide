Binder线程

Binder线程是一个普通的线程，只是因为用来执行Binder的实体业务，所以才叫做Binder线程。**一个线程成为一个Binder线程，只需要开启一个监听Binder驱动（实际就是Binder字符设备）的Loop线程即可。**

以我们的SystemService为例，当线程执行到最厚的时候，会Loop监听Binder设备，变成一个死循环的线程。

```c
extern "C" status_t system_init()
{
    ...
    ALOGI("System server: entering thread pool.\n");
  	//启动线程
    ProcessState::self()->startThreadPool();
  	//将其注册到Binder驱动
    IPCThreadState::self()->joinThreadPool();
    ALOGI("System server: exiting thread pool.\n");
    return NO_ERROR;
}
```

这里会通过joinThreadPool，将线程注册为Binder线程

```c
//注册为Binder线程
void IPCThreadState::joinThreadPool(bool isMain){
    //写入标志位，标明线程是主动还是被动注册为Binder线程
    mOut.writeInt32(isMain ? BC_ENTER_LOOPER : BC_REGISTER_LOOPER);
    status_t result;
    do {
        //重点方法。获取到指令并不断的处理指令
        result = getAndExecuteCommand();
    } while (result != -ECONNREFUSED && result != -EBADF);
    mOut.writeInt32(BC_EXIT_LOOPER);
    talkWithDriver(false);
}
```

注册为Binder线程时，会不断的获取Binder驱动的命令并执行

```c
status_t IPCThreadState::getAndExecuteCommand()
{
    status_t result;
    int32_t cmd;

    result = talkWithDriver();
    if (result >= NO_ERROR) {
        //读取要执行的指令
        cmd = mIn.readInt32();
        //执行指令
        result = executeCommand(cmd);
    }
}
```

**executeCommand**会对不同的指令进行处理，我们这里只关心当收到Binder驱动发送过来的Client端请求信息的处理分支。

```c
//IPCThreadState.cpp
status_t IPCThreadState::executeCommand(int32_t cmd)
{
    BBinder* obj;
    switch ((uint32_t)cmd) {
    case BR_TRANSACTION://client端将请求发送给驱动，然后驱动将请求发送给server端，server端就会收到这个请求指令
        {
            binder_transaction_data& tr = tr_secctx.transaction_data;
            //将数据读到tr中
            result = mIn.read(&tr, sizeof(tr));
                tr_secctx.secctx = 0;
            }
            //buffer是给Server端的上层使用的，
            Parcel buffer;
            buffer.ipcSetDataReference(...);
        		//返回给客户端的数据
            Parcel reply;
                if (reinterpret_cast<RefBase::weakref_type*>(tr.target.ptr)->attemptIncStrong(this)) {
                  //这里是两个方法的合集。
                  //1.reinterpret_cast<BBinder*>(tr.cookie)。用于取出tr中的Binder的实体，
                  //2.BBinder的->transact方法来将数据传输给上层进行处理，交给上层的时候，有code，buffer和replay，上层处理完成之后，会将返回的数据通过reply返回回来
                    error = reinterpret_cast<BBinder*>(tr.cookie)->transact(tr.code, buffer,
                            &reply, tr.flags);
                }
            //将数据发送给Binder驱动，返回给client端
            sendReply(reply, 0);
        }
        break;

```



BBinder会通过JNI发起调用，调用Java层的execTransact()函数。也就是AIDL的Stub类中的数据

