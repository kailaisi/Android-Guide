### Android应用进程的创建

在之前的[Android启动流程]()中，我们最后提到了会通过**ActivityManagerService**的**startProcess**方法来进行应用进程的创建。本篇文章我们就从这里开始着手，来进行相关源码的解析工作。

#### ActivityManagerService#startProcess

```java
    //ActivityManagerService#LocalService.java
 	public void startProcess(String processName, ApplicationInfo info,
            boolean knownToBeDead, String hostingType, ComponentName hostingName) {
        try {
            //同步操作，避免死锁
            synchronized (ActivityManagerService.this) {
                //调用startProcessLocked方法, Process的start，最终到ZygoteProcess的attemptUsapSendArgsAndGetResult()
                // 用来fork一个新的Launcher的进程
                startProcessLocked(processName, info, knownToBeDead, 0 /* intentFlags */,new HostingRecord(hostingType, hostingName),false /* allowWhileBooting */, false /* isolated */,true /* keepIfLarge */);
            }
        ...
    }
    
    final ProcessRecord startProcessLocked(String processName,
            ApplicationInfo info, boolean knownToBeDead, int intentFlags,
            HostingRecord hostingRecord, boolean allowWhileBooting,
            boolean isolated, boolean keepIfLarge) {
        return mProcessList.startProcessLocked(processName, info, knownToBeDead, intentFlags,
                hostingRecord, allowWhileBooting, isolated, 0 /* isolatedUid */, keepIfLarge,
                null /* ABI override */, null /* entryPoint */, null /* entryPointArgs */,
                null /* crashHandler */);
    }
```

这里最终调用了ProcessList的**startProcessLocked**方法。这里的ProcessList类的主要作用是用来处理进程。

#### ProcessList#startProcessLocked

```java
    //启动进程
    @GuardedBy("mService")
    boolean startProcessLocked(ProcessRecord app, HostingRecord hostingRecord,
            boolean disableHiddenApiChecks, boolean mountExtStorageFull,
            String abiOverride) {
        //已经启动，则直接返回
        if (app.pendingStart) {
            return true;
        }
        //启动时间
        long startTime = SystemClock.elapsedRealtime();
        ...
            //设置程序的入口
            final String entryPoint = "android.app.ActivityThread";
            //***重点方法****
            return startProcessLocked(hostingRecord, entryPoint, app, uid, gids,
                    runtimeFlags, mountExternal, seInfo, requiredAbi, instructionSet, invokeWith,
                    startTime);
       ...
    }
```

调用了一个重载方法，**注意我们这里的一个参数entryPoint，这个是我们的进程启动以后的入口类，当我们fork出进程以后，会调用这个类中的main方法来启进程。**

```java
    boolean startProcessLocked(HostingRecord hostingRecord,
            String entryPoint,
            ProcessRecord app, int uid, int[] gids, int runtimeFlags, int mountExternal,
            String seInfo, String requiredAbi, String instructionSet, String invokeWith,
            long startTime) {
            ...
                    //***重点方法***
                    final Process.ProcessStartResult startResult = startProcess(app.hostingRecord,
                            entryPoint, app, app.startUid, gids, runtimeFlags, mountExternal,
                            app.seInfo, requiredAbi, instructionSet, invokeWith, app.startTime);
        ...
    }

    private Process.ProcessStartResult startProcess(HostingRecord hostingRecord, String entryPoint,
            ProcessRecord app, int uid, int[] gids, int runtimeFlags, int mountExternal,
            String seInfo, String requiredAbi, String instructionSet, String invokeWith,
            long startTime) {
                //*****重点方法*****最终调用的创建进程的方法
                startResult = Process.start(entryPoint,
                        app.processName, uid, uid, gids, runtimeFlags, mountExternal,
                        app.info.targetSdkVersion, seInfo, requiredAbi, instructionSet,
                        app.info.dataDir, invokeWith, app.info.packageName,
                        new String[] {PROC_START_SEQ_IDENT + app.startSeq});
    }
```

最后将进程的创建交给**Process**类来进行处理，通过start方法创建，然后返回了**ProcessStartResult**启动的结果。

#### Process#start

```java
    public static ProcessStartResult start(@NonNull final String processClass,
                                           @Nullable final String niceName,
                                           int uid, int gid, @Nullable int[] gids,
                                           int runtimeFlags,
                                           int mountExternal,
                                           int targetSdkVersion,
                                           @Nullable String seInfo,
                                           @NonNull String abi,
                                           @Nullable String instructionSet,
                                           @Nullable String appDataDir,
                                           @Nullable String invokeWith,
                                           @Nullable String packageName,
                                           @Nullable String[] zygoteArgs) {
        //processClass为"android.app.ActivityThread"，表示程序的入口类
        return ZYGOTE_PROCESS.start(processClass, niceName, uid, gid, gids,
                    runtimeFlags, mountExternal, targetSdkVersion, seInfo,
                    abi, instructionSet, appDataDir, invokeWith, packageName,
                    /*useUsapPool=*/ true, zygoteArgs);
    }
```

调用ZygoteProcess的start方法

#### ZygoteProcess#start

```java
    //启动一个新的进程
    public final Process.ProcessStartResult start(@NonNull final String processClass,
                                                  final String niceName,
                                                  int uid, int gid, @Nullable int[] gids,
                                                  int runtimeFlags, int mountExternal,
                                                  int targetSdkVersion,
                                                  @Nullable String seInfo,
                                                  @NonNull String abi,
                                                  @Nullable String instructionSet,
                                                  @Nullable String appDataDir,
                                                  @Nullable String invokeWith,
                                                  @Nullable String packageName,
                                                  boolean useUsapPool,
                                                  @Nullable String[] zygoteArgs) {
        try {
            //***重点方法****
            return startViaZygote(processClass, niceName, uid, gid, gids,
                    runtimeFlags, mountExternal, targetSdkVersion, seInfo,
                    abi, instructionSet, appDataDir, invokeWith, /*startChildZygote=*/ false,
                    packageName, useUsapPool, zygoteArgs);
        }
        ...
    }

    private Process.ProcessStartResult startViaZygote(@NonNull final String processClass,
                                                      @Nullable final String niceName,
                                                      final int uid, final int gid,
                                                      @Nullable final int[] gids,
                                                      int runtimeFlags, int mountExternal,
                                                      int targetSdkVersion,
                                                      @Nullable String seInfo,
                                                      @NonNull String abi,
                                                      @Nullable String instructionSet,
                                                      @Nullable String appDataDir,
                                                      @Nullable String invokeWith,
                                                      boolean startChildZygote,
                                                      @Nullable String packageName,
                                                      boolean useUsapPool,
                                                      @Nullable String[] extraArgs)
                                                      throws ZygoteStartFailedEx {
        ArrayList<String> argsForZygote = new ArrayList<>();
        //这是一些创建进程时候的参数信息
        ...
        //这个是程序的入口类，设置的是"android.app.ActivityThread"
        argsForZygote.add(processClass);
	    ...
        synchronized(mLock) {
            //***重点方法***
            return zygoteSendArgsAndGetResult(openZygoteSocketIfNeeded(abi),
                                              useUsapPool,
                                              argsForZygote);
        }
    }


```

**openZygoteSocketIfNeeded** 会创建一个和Zygote的socket连接。

```java
    //如果初始的Zygote的连接不存在或者未连接。则创建一个Socket连接，并将相关信息封装为ZygoteState
    @GuardedBy("mLock")
    private void attemptConnectionToPrimaryZygote() throws IOException {
        //如果没有连接
        if (primaryZygoteState == null || primaryZygoteState.isClosed()) {
            primaryZygoteState = ZygoteState.connect(mZygoteSocketAddress, mUsapPoolSocketAddress);
        }
    }
```

如果之前没有建立过和Zygote之间的连接，那么会通过**connect()**方法进行连接

```java
        static ZygoteState connect(@NonNull LocalSocketAddress zygoteSocketAddress,@Nullable LocalSocketAddress usapSocketAddress)throws IOException {
            DataInputStream zygoteInputStream;
            BufferedWriter zygoteOutputWriter;
            final LocalSocket zygoteSessionSocket = new LocalSocket();
            try {
                //进行连接
                zygoteSessionSocket.connect(zygoteSocketAddress);
                //创建DataInputStream
                zygoteInputStream = new DataInputStream(zygoteSessionSocket.getInputStream());
                //创建BufferedWriter
                zygoteOutputWriter = new BufferedWriter(new OutputStreamWriter(zygoteSessionSocket.getOutputStream()),Zygote.SOCKET_BUFFER_SIZE);
            ...
            //封装为ZygoteState对象
            return new ZygoteState(zygoteSocketAddress, usapSocketAddress,
                                   zygoteSessionSocket, zygoteInputStream, zygoteOutputWriter,
                                   getAbiList(zygoteOutputWriter, zygoteInputStream));
        }
```

 所以openZygoteSocketIfNeeded的主要作用是**保证和Zygote的socket连接的存在**。当连接存在以后就可以通过socket进行消息的传输了。

#### ZygoteProcess#zygoteSendArgsAndGetResult

通过socket连接Zygote，并发送对应的fork进程所需要的信息

```java
    private Process.ProcessStartResult zygoteSendArgsAndGetResult(
            ZygoteState zygoteState, boolean useUsapPool, @NonNull ArrayList<String> args) throws ZygoteStartFailedEx {
        //****重点方法**** 尝试fork子线程
        return attemptZygoteSendArgsAndGetResult(zygoteState, msgStr);
    }	
	
    private Process.ProcessStartResult attemptZygoteSendArgsAndGetResult(
            ZygoteState zygoteState, String msgStr) throws ZygoteStartFailedEx {
        try {
            //传入的zygoteState为openZygoteSocketIfNeeded()，里面会通过abi来检查是第一个zygote还是第二个
            final BufferedWriter zygoteWriter = zygoteState.mZygoteOutputWriter;
            final DataInputStream zygoteInputStream = zygoteState.mZygoteInputStream;
            //将参数的信息写给Zygote进程，包括前面的processClass ="android.app.ActivityThread"
            zygoteWriter.write(msgStr);
            //刷数据，全部写入Zygote进程，处于阻塞状态
            zygoteWriter.flush();
            //从socket中得到zygote创建的应用pid，赋值给 ProcessStartResult的对象
            Process.ProcessStartResult result = new Process.ProcessStartResult();
            //从socket中读取创建的进程的pid
            result.pid = zygoteInputStream.readInt();
            result.usingWrapper = zygoteInputStream.readBoolean();
            //如果pid<0，表示创建失败
            if (result.pid < 0) {
                throw new ZygoteStartFailedEx("fork() failed");
            }
            return result;
        } 
        ...
    }
```
最后会通过socket连接到Zygote进程，将对应的参数发送给Socket的Server端以后，由Server端来进行进程的fork操作，操作完成以后将创建的进程id返回。

那么Zygote的Server端又是如何创建的呢？

### Zygote启动监听

这个就涉及了Zygote的启动过程了。这部分我们后续可以详细分析，这里只提一下大体的流程。

Zygote会先fork出SystemServer进程，然后会进入循环等待，用来接收Socket发来的消息，用来fork出其他应用所需要的进程信息。

```java
    //ZygoteInit.java
	public static void main(String argv[]) {
        ZygoteServer zygoteServer = null;
        Runnable caller;
        try {
            //创建一个ZygoteServer对象，这个对象创建一个socket服务端，能够接收连接并且孵化对应的子进程
            zygoteServer = new ZygoteServer(isPrimaryZygote);
            if (startSystemServer) {
                //Fork出第一个进程  SystemServer服务所需的进程
                Runnable r = forkSystemServer(abiList, zygoteSocketName, zygoteServer);
                if (r != null) {
                    //启动SystemServer服务
                    r.run();
                    return;
                }
            }
            //***重点方法***   这里会进入循环等待，用来接收Socket发来的消息，用来fork出其他应用所需要的进程信息。并且返回fork出的进程的启动函数
            caller = zygoteServer.runSelectLoop(abiList);
        ....
        if (caller != null) {
            //调用caller的run方法，启动子进程（run方法会调用子进程的启动程序的main方法，也就是ActivityThread.java的main()方法）
            caller.run();
        }
    }
```

### 连接的处理

我们这里看一下**runSelectLoop**这个方法如何监听Socket连接以及接收消息的

```java
    Runnable runSelectLoop(String abiList) {
        ArrayList<FileDescriptor> socketFDs = new ArrayList<FileDescriptor>();
        ArrayList<ZygoteConnection> peers = new ArrayList<ZygoteConnection>();
        //socketFDs[0]是socketServer。
        socketFDs.add(mZygoteSocket.getFileDescriptor());
        peers.add(null);
        //死循环
        while (true) {
            {
                //这里会进入阻塞，当有pollFDs事件到来的时候，则继续往下执行
                Os.poll(pollFDs, -1);
            } catch (ErrnoException ex) {
                throw new RuntimeException("poll failed", ex);
            }
            while (--pollIndex >= 0) {
                if (pollIndex == 0) {
                    //采用I/O 多路复用机制,index==0表示selcet接收到的是Zygote的socket连接的事件.
                    // 客户端第一次请求服务端，服务端会调用accept方法与客户端建立连接，客户端在zygote以ZygoteConnection对象表示
                    ZygoteConnection newPeer = acceptCommandPeer(abiList);
                    peers.add(newPeer);
                    socketFDs.add(newPeer.getFileDescriptor());
                } else if (pollIndex < usapPoolEventFDIndex) {
                    //当连接以后，能够接收指令，这里根据
                    try {
                        //peers.get(index)取得发送数据客户端的ZygoteConnection对象。这个就是多路复用的机制了
                        ZygoteConnection connection = peers.get(pollIndex);
                        //收到Socket发来的消息，进行fork的创建工作。返回的command是MethodAndArgsCaller类
                        //其run方法，会调用通过socket接收到的启动类的main方法
                        final Runnable command = connection.processOneCommand(this);
                        ....
    }
```

当启动循环以后，会一直遍历等待，等待接收Socket发来的连接以及消息请求。当获取到对应的客户端的ZygoteConnection对象以后，这里会调用processOneCommand指令来进行处理。

到这里的话，就可以和我们刚才讲的创建Socket连接关联起来了。

我们看一下**processOneCommand**这个方法是如何对发送的相关fork进程的参数来进行处理的。

```java
    Runnable processOneCommand(ZygoteServer zygoteServer) {
        String args[];
        ZygoteArguments parsedArgs = null;
        FileDescriptor[] descriptors;
        try {
            //读取socket传来的参数信息
            args = Zygote.readArgumentList(mSocketReader);
        ...
        parsedArgs = new ZygoteArguments(args);
        fd = null;
        //*****重点方法**** fork一个子进程，得到一个对应的进程pid
        pid = Zygote.forkAndSpecialize(parsedArgs.mUid, parsedArgs.mGid, parsedArgs.mGids,
                parsedArgs.mRuntimeFlags, rlimits, parsedArgs.mMountExternal, parsedArgs.mSeInfo,
                parsedArgs.mNiceName, fdsToClose, fdsToIgnore, parsedArgs.mStartChildZygote,
                parsedArgs.mInstructionSet, parsedArgs.mAppDataDir, parsedArgs.mTargetSdkVersion);

        try {
            if (pid == 0) {
                //当pid=0，说明是fork的子进程
                zygoteServer.setForkChild();
                zygoteServer.closeServerSocket();
                IoUtils.closeQuietly(serverPipeFd);
                serverPipeFd = null;
                //****重点方法*****  处理子进程
                return handleChildProc(parsedArgs, descriptors, childPipeFd,
                        parsedArgs.mStartChildZygote);
		...
    }
```

我们将这个方法分为3个大内容来处理

1. 将接收到的数据进行解析处理，生成ZygoteArguments对象，这个对象里面包含了我们设置的进程创建以后的入口类（即启动类）
2. 通过**forkAndSpecialize**方法fork出一个子进程
3. 通过**handleChildProc**方法对fork出的子进程进行处理

我们分别对上面的3部分进行分析：

##### 参数的解析

这里接收的参数，是在我们的socket的client端来进行创建的。

```java
//ZygoteProcess.java #startViaZygote方法
		ArrayList<String> argsForZygote = new ArrayList<>();
        // --runtime-args, --setuid=, --setgid=,
        // and --setgroups= must go first
        //这是一些创建进程时候的参数信息
        argsForZygote.add("--runtime-args");
        argsForZygote.add("--setuid=" + uid);
        argsForZygote.add("--setgid=" + gid);
        argsForZygote.add("--runtime-flags=" + runtimeFlags);
        if (mountExternal == Zygote.MOUNT_EXTERNAL_DEFAULT) {
            argsForZygote.add("--mount-external-default");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_READ) {
            argsForZygote.add("--mount-external-read");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_WRITE) {
            argsForZygote.add("--mount-external-write");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_FULL) {
            argsForZygote.add("--mount-external-full");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_INSTALLER) {
            argsForZygote.add("--mount-external-installer");
        } else if (mountExternal == Zygote.MOUNT_EXTERNAL_LEGACY) {
            argsForZygote.add("--mount-external-legacy");
        }

        argsForZygote.add("--target-sdk-version=" + targetSdkVersion);
        argsForZygote.add(processClass);
        
        //ZygoteProcess.java # zygoteSendArgsAndGetResult方法
	    String msgStr = args.size() + "\n" + String.join("\n", args) + "\n";

```

这里的参数的样式是"--setuid=1"，行与行之间以"\r"、"\n"或者"\r\n"分割，最后一个参数是进程的入口类。在解析的时候，会按照格式进行拆分。

##### 子进程的创建

对于子进程的fork，是通过**Zygote.forkAndSpecialize**来处理的。

```java
    //Zygote.java
	//fork一个子进程，如果这是子节点则返回0；如果这是父进程，则返回子进程的pid；发生异常则返回-1。
    public static int forkAndSpecialize(int uid, int gid, int[] gids, int runtimeFlags,
            int[][] rlimits, int mountExternal, String seInfo, String niceName, int[] fdsToClose,
            int[] fdsToIgnore, boolean startChildZygote, String instructionSet, String appDataDir,
            int targetSdkVersion) {
        ...
        //调用native方法，fork出一个子进程。具体的位置在com_android_internal_os_Zygote.cpp
        int pid = nativeForkAndSpecialize(uid, gid, gids, runtimeFlags, rlimits, mountExternal, seInfo, niceName, fdsToClose,fdsToIgnore, startChildZygote, instructionSet, appDataDir);
        ...
        return pid;
    }
```

这里调用了一个native方法来进行线程的fork操作。由于采用copy on write方式，这里执行一次，会返回两次。

##### 创建结果的处理

当通过native进行了子进程的fork操作以后，会返回pid。这里的pid根据具体的值表示的是不同的类型

* pid=0：表示处于子进程
* pid>0：表示处于Zygote进程
* pid<0：表示子进程的创建失败

这里我们只关心子进程的处理。也就是**handleChildProc**方法。

```java
    //ZygoteConnection.java
	//进程创建完成后的处理工作，适当的关闭socket，适当的重新打开stdio，返回成功或者失败信息等
    //返回的Runnable是一个封装了创建进程时，socket传进来的程序入口的方法以及对应的参数的类。其run方法会通过反射调用类的main方法
    private Runnable handleChildProc(ZygoteArguments parsedArgs, FileDescriptor[] descriptors,FileDescriptor pipeFd, boolean isZygote) {
 
        //当执行到这的时候，connection已经关闭了关闭socket，用/dev/null替换它们。
        closeSocket();
        ...
                //这个里面会通过反射创建socket传递的启动程序的入口类（ActivityThread），然后调用其main方法进行启动
                return ZygoteInit.childZygoteInit(parsedArgs.mTargetSdkVersion,
                        parsedArgs.mRemainingArgs, null /* classLoader */);
            }
        }
    }
```

这个方法在最后会调用**childZygoteInit**方法，然后返回一个Runnable对象。

```java
    static final Runnable childZygoteInit(int targetSdkVersion, String[] argv, ClassLoader classLoader) {
        //根据argv获取到对应的运行的相关参数
        RuntimeInit.Arguments args = new RuntimeInit.Arguments(argv);
        return RuntimeInit.findStaticMain(args.startClass, args.startArgs, classLoader);
    }
```

这里根据解析后的参数信息，生成了**Arguments**对象。

```java
        //RuntimeInit.java
        Arguments(String args[]) throws IllegalArgumentException {
            parseArgs(args);
        }
        
        //进行参数的解析
        private void parseArgs(String args[]) throws IllegalArgumentException {
            int curArg = 0;
            for (; curArg < args.length; curArg++) {
                String arg = args[curArg];
                if (arg.equals("--")) {
                    curArg++;
                    break;
                } else if (!arg.startsWith("--")) {
                    break;
                }
            }
            //在传递参数的时候，最后一项传递的是程序入口类的信息
            startClass = args[curArg++];
            //参数信息
            startArgs = new String[args.length - curArg];
            System.arraycopy(args, curArg, startArgs, 0, startArgs.length);
        }
    }

```

方法中会根据传入的参数确定了fork的子进程启动时的类**startClass**以及对应的参数**startArgs**。

```java
     //ZygoteInit.java
	//调用传入的className所对应的类的main方法
    protected static Runnable findStaticMain(String className, String[] argv, ClassLoader classLoader) {
         Class<?> cl;
         //反射创建类
         cl = Class.forName(className, true, classLoader);
         Method m;
		//获取类的main方法
          m = cl.getMethod("main", new Class[] { String[].class });
         //封装一个Runnable类，将方法和参数都传递给了该类，其run方法会通过反射调用main方法
         return new MethodAndArgsCaller(m, argv);
    }

   static class MethodAndArgsCaller implements Runnable {
        //调用的方法
        private final Method mMethod;
        //方法的参数
        private final String[] mArgs;

        public MethodAndArgsCaller(Method method, String[] args) {
            mMethod = method;
            mArgs = args;
        }

        public void run() {
            try {
                mMethod.invoke(null, new Object[] { mArgs });
            } 
            ...
        }
    }
```

handleChildProc会根据将传入的参数信息，返回子进程启动时所使用的方法通过反射获取到，并放在一个Runnable的run方法中去执行。

那么这个Runnable的启动是在哪儿呢？

```java
//ZygoteInit.java
    public static void main(String argv[]) {
    	...
           caller = zygoteServer.runSelectLoop(abiList);
        ...
        if (caller != null) {
            //调用caller的run方法，启动子进程（run方法会调用子进程的启动程序的main方法，也就是ActivityThread.java的main()方法）
            caller.run();
        }
```

当通过**runSelectLoop**方法fork完对应的子进程以后，会将这个**MethodAndArgsCaller**返回并执行。我们一开始传入的**ActivityThread**的main方法就调用并执行了。

### 总结

1. 在fork子进程之后，直接执行了**ActivityThread**的main方法来启动的。
2. 在系统启动时，开启了Zygote的Socket的Server端来监听，当需要创建进程时，直接通过Socket连接，然后传递参数来创建。
3. fork采用了copy on write方式。
4. Server端对于连接的处理，采用了**I/O 多路复用**机制。具体的这个机制，这个机制回头可以延伸一下。

[源码解析项目地址](https://github.com/kailaisi/android-29-framwork.git)

> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)



