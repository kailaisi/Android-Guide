Notificaton通知栏

通知栏

安卓8

通知

##### 通知栏消息

![image-20201227094832666](http://cdn.qiniu.kailaisii.com/typora/202012/27/094833-279728.png)

用户可以在状态栏向下滑动以打开抽屉通知栏，并显示更多详情i以及通知的执行操作。

![image-20201227095009582](http://cdn.qiniu.kailaisii.com/typora/202012/27/095022-989845.png)

##### 提醒式通知

从Android5.0开始，通知栏可以短暂的显示在浮动窗口，被称之为提醒式通知。这种一般用来适用于用户应该立即知道的重要通知，而且仅在设备未锁定时才显示。

![image-20201227095809189](http://cdn.qiniu.kailaisii.com/typora/202012/27/095912-580253.png)

提醒式通知在应用发出通知后会立即显示，稍后就会消失，仍然照常显示在抽屉式通知栏中。

提醒式通知的触发条件：

* 通知优先级很高，Android7.1及更低版本上使用铃声或震动
* Android8.0及以上的设备，通知渠道的重要程度比较高。

##### 锁屏显示

从Android5.0开始，通知可以显示在锁定屏幕上。

![image-20201227101223421](http://cdn.qiniu.kailaisii.com/typora/202012/27/101225-576610.png)



程序设置方式：

```kotlin
    var builder = NotificationCompat.Builder(this, CHANNEL_ID)
            ...
            .setFullScreenIntent(fullScreenPendingIntent, true)
```

除此之外，**用户可以通过系统设置来选择锁屏通知的可见等级，包括停用该功能**。Android8.0开始，用户可以选择按照通知渠道的锁定屏幕通知了。

##### 角标

在Android8.0及以上设备中。通知信息可以通过角标来显示出来。

![image-20201227102844237](http://cdn.qiniu.kailaisii.com/typora/202012/27/102847-434466.png)

角标可以显示通知栏的数量，长按显示对应的通知消息等（这个功能大厂很多阉割了）。

##### 通知栏

通知栏样式一般由系统设定，应用只需要定义模板中各个部分的内容即可。

![img](http://cdn.qiniu.kailaisii.com/typora/202012/27/103745-51011.png)

上图展示了通知最常见的几个部分，具体的如下：

1. 小图标：必须提供，通过 `setSmallIcon()` 进行设置。
2. 应用名称：由系统提供。
3. 时间戳：由系统提供，但您可以通过 `setWhen()` 将其替换掉或者通过 `setShowWhen(false)` 将其隐藏。
4. 大图标：可选内容（通常仅用于联系人照片，请勿将其用于应用图标），通过 `setLargeIcon()` 进行设置。
5. 标题：可选内容，通过 `setContentTitle()` 进行设置。
6. 文本：可选内容，通过 `setContentText()` 进行设置。

##### 展开式通知

在默认情况下，通知的文字内容会被截断放在一行（`国内部分厂商手机可能是2行，也有的可以手动设置1行或者2行`）显示，然后剩余的显示...。如果需要长一些的通知，可以通过应用自定义模板启动。

![image-20201227105202166](http://cdn.qiniu.kailaisii.com/typora/202012/27/105202-75090.png)

##### 通知渠道

从Android8.0开始，必须为所有的通知分配渠道，否则通知就不会显示。可以将通知归类为不同的渠道，用户也可以根据渠道的重要程度来进行区分对待（这些都可以在设置中完成）。而在Android7.1及更低版本的设备商，用户仅可以按照应用来管理通知信息。

![image-20201227111103741](http://cdn.qiniu.kailaisii.com/typora/202012/27/111106-778360.png)

安卓8.0以上系统。

![image-20201227122733496](http://cdn.qiniu.kailaisii.com/typora/202012/27/122735-731094.png)

安卓5.1.1

```
★注意：界面将渠道称作“类别”。
```

##### 通知兼容性

Android的通知系统页面以及相关的API一直在发展。谷歌推荐使用`NotificationCompat` 及其子类，以及 `NotificationManagerCompat`来进行旧设备的兼容处理。

**Android 4.1，API 级别 16**

- 推出了展开式通知模板（称为通知样式），可以提供较大的通知内容区域来显示信息。用户可以通过单指向上/向下滑动的手势展开通知。
- 还支持以按钮的形式向通知添加其他操作。
- 允许用户在设置中按应用关闭通知。

**Android 4.4，API 级别 19 和 20**

- 向 API 中添加了通知监听器服务。
- API 级别 20 中新增了 Android Wear（现已更名为 Wear OS）支持。

**Android 5.0，API 级别 21**

- 推出了锁定屏幕和提醒式通知。

- 用户现在可以将手机设为勿扰模式，并配置允许哪些通知在设备处于优先模式时打扰他们。

- 向 API 集添加了通知是否在锁定屏幕上显示的方法 (`setVisibility()`)，以及指定通知文本的“公开”版本的方法。

- 添加了 `setPriority()` 方法，告知系统通知的“干扰性”（例如，将其设为“高”可使通知以提醒式通知的形式显示）。

**Android 7.0，API 级别 24**

- 重新设置了通知模板的样式以强调主打图片和头像。
- 添加了三个通知模板：一个用于短信应用，另外两个用于借助展开式选项和其他系统装饰来装饰自定义内容视图。
- 向手持设备（手机和平板电脑）添加了对通知组的支持。使用与 Android 5.0（API 级别 21）中推出的 Android Wear（现已更名为 Wear OS）通知堆栈相同的 API。
- 用户可以使用内嵌回复功能直接在通知内进行回复（他们输入的文本将转发到通知的父级应用）。

**Android 8.0，API 级别 26**

- 现在必须将各个通知放入特定渠道中。
- 现在，用户可以按[渠道](https://developer.android.google.cn/guide/topics/ui/notifiers/notifications#ManageChannels)关闭通知，而非关闭来自某个应用的所有通知。
- 包含有效通知的应用将在主屏幕/启动器屏幕上相应应用图标的上方显示通知“标志”。
- 现在，用户可以从抽屉式通知栏中暂停某个通知。您可以为通知设置自动超时时间。
- 您还可以设置通知的背景颜色。
- 部分与通知行为相关的 API 从 `Notification` 移至了 `NotificationChannel`。例如，在搭载 Android 8.0 及更高版本的设备中，使用 `NotificationChannel.setImportance()`，而非 `NotificationCompat.Builder.setPriority()`。

#### 创建通知

最基本、最精简形式的通知会显示一个图标、一个标题和少量内容文本。

##### 通知内容

创建一个最基本通知，需要设置如下内容：

- 小图标，通过 `setSmallIcon()` 设置。这是所必需的唯一用户可见内容。
- 标题，通过 `setContentTitle()` 设置。
- 正文文本，通过 `setContentText()` 设置。
- 通知优先级，通过 `setPriority()` 设置。优先级确定通知在 Android 7.1 和更低版本上的干扰程度。（对于 Android 8.0 和更高版本，必须设置渠道重要性，如下一节中所示。）

```kotlin
val notification: NotificationCompat.Builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("消息栏通知")
            .setContentText("这是一条很长很长的消息栏通知详情。也不知道能不能全部都能显示出来。也不知道能不能全部都能显示出来。也不知道能不能全部都能显示出来。也不知道能不能全部都能显示出来。也不知道能不能全部都能显示出来。")
            .setSmallIcon(R.drawable.notification_logo)//小图标
```

对于Android8.0及以上的系统，必须设置通知的渠道才可。

常用的设置：

```kotlin
       val notification: NotificationCompat.Builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("消息栏通知")//标题栏
            .setContentText("这是一条很长很长的消息栏通知详情。")//详情
            .setContentIntent(pendingIntent)//设置意图，比如说跳转到某个页面等等
            .setSmallIcon(R.drawable.notification_logo)//小图标
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.notification_logo))//大图标。如果有大图标，则大图标在右侧小图标在左侧。如果没有大图标，则只在左侧显示小图标。
            .setPriority(NotificationCompat.PRIORITY_HIGH)//优先级
            .setFullScreenIntent(pendingIntent, true)//锁屏通知下的通知
            .setCategory(Notification.CATEGORY_CALL)
            .setColor(resources.getColor(android.R.color.holo_red_light))
            .setNumber(0)//显示的角标数字
            .setAutoCancel(true)//用户点击面板的时候是否自动取消
            .setDefaults(Notification.DEFAULT_ALL)//设置通知的默认效果。即设置和系统保持一致。可以设置ALL，Notification#DEFAULT_SOUND、link Notification#DEFAULT_VIBRATE、Notification#DEFAULT_LIGHTS、Notification#DEFAULT_ALL
     
```

##### 创建渠道并设置重要性

必须先通过向 `createNotificationChannel()` 传递 `NotificationChannel` 的实例在系统中注册应用的通知渠道，然后才能在 Android 8.0 及更高版本上提供通知。

```kotlin
    private fun createNotificationChannel(channelID: String, channelNAME: String, level: Int): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelID, channelNAME, level)//有优先级信息
            manager.createNotificationChannel(channel)//注册渠道信息
            channelID
        } else {
            ""
        }
    }
```

由于渠道的功能，只在Android8.0以上版本才有，所以这里根据系统进行了判断。创建了渠道`id`和`名称`等。

**由于需要先创建渠道和对应的优先级，然后才可以在Android8.0及以上进行通知的创建，所以这段代码可以在应用启动的时候进行调用。（这里有个坑，后面讲）**

##### 显示紧急消息

对于一些特别紧急的消息，可以通过创建锁屏通知来进行显示处理。这时候，对于用户的体验是：

- 如果用户设备被锁定，会显示全屏 Activity，覆盖锁屏。
- 如果用户设备处于解锁状态，通知以展开形式显示，其中包含用于处理或关闭通知的选项。

```kotlin
    val fullScreenIntent = Intent(this, ImportantActivity::class.java)
    val fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
        fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

    var builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("My notification")
            .setContentText("Hello World!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setFullScreenIntent(fullScreenPendingIntent, true)
```

##### 更新通知

如需在发出此通知后对其进行更新，请再次调用 `NotificationManagerCompat.notify()`，并将之前使用的具有同一 ID 的通知传递给该方法。如果之前的通知已被关闭，则系统会创建一个新通知。

您可以选择性调用 `setOnlyAlertOnce()`，这样通知只会在通知首次出现时打断用户（通过声音、振动或视觉提示），而之后更新则不会再打断用户。

**注意**：Android 会在更新通知时应用速率限制。如果您过于频繁地发布对某条通知的更新（不到一秒内发布多条通知），系统可能会丢弃部分更新。

##### 移除通知

除非发生以下情况之一，否则通知仍然可见：

- 用户关闭通知。
- 用户点击通知，且您在创建通知时调用了 `setAutoCancel()`。
- 您针对特定的通知 ID 调用了 `cancel()`。此方法还会删除当前通知。
- 您调用了 `cancelAll()` 方法，该方法将移除之前发出的所有通知。
- 如果您在创建通知时使用 `setTimeoutAfter()` 设置了超时，系统会在指定持续时间过后取消通知。如果需要，您可以在指定的超时持续时间过去之前取消通知。

代码：

```kotlin

    /**
     * Notification通知
     */
    fun sendNotifiCation(alarmContent: String, context: Context, channelID: String, channelNAME: String, level: Int) {
        //点击发送广播，然后在广播中打开应用
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val channelId: String = createNotificationChannel(channelID, channelNAME,level)
        val notification: NotificationCompat.Builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(resources.getString(R.string.enroll_phld_gatewayname))
            .setContentText(alarmContent)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(0)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(notificationId++, notification.build())
    }

    fun createNotificationChannel(context: Context, channelID: String, channelNAME: String, level: Int): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelID, channelNAME, level)
            channel.setShowBadge(true)
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
            channelID
        } else {
            ""
        }
    }
```

#### 遇到的问题

在对接国内各个手机的时候，总是遇到一些奇葩的问题。

##### VIVO手机创建通知消息后总收不到消息

对于渠道消息，默认都是打开的。但是国内厂商真奇葩。这个默认的通知，在部分vivo手机上是关闭的。所以需要用户去手动打开。

解决方案：在应用启动的时候创建渠道名称，然后在启动页检测是否打开了通知栏和渠道的通知功能，如果没有，引导用户去设置页面。

```kotlin
    /**
     * 跳转到通知权限设置界面
     */
    fun open(context: Context) {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra("app_package", context.packageName)
            intent.putExtra("app_uid", context.applicationInfo.uid)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", context.packageName, null)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.action = Intent.ACTION_VIEW
            intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails")
            intent.putExtra("com.android.settings.ApplicationPkgName", context.packageName)
        }
        context.startActivity(intent)
    }

    /**
     * 可以通过NotificationManagerCompat 中的 areNotificationsEnabled()来判断是否开启通知权限。NotificationManagerCompat 在 android.support.v4.app包中，是API 22.1.0 中加入的。而 areNotificationsEnabled()则是在 API 24.1.0之后加入的。
     * areNotificationsEnabled 只对 API 19 及以上版本有效，低于API 19 会一直返回true
     */
    fun isNotificationEnabled(context: Context?): Boolean {
        val notificationManagerCompat = NotificationManagerCompat.from(context!!)
        val areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //8.0以上需要同时打开渠道通知和通知。
            val notificationChannels = notificationManagerCompat.notificationChannels
            for (channel in notificationChannels) {
                //检测渠道通知功能被关系
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    return false
                }
            }
        }
        //只需要打开通知即可
        return areNotificationsEnabled
    }
```

#### 总结

* 对于通知栏，现在可以按照渠道来设置。
* 安卓8.0及以后通知栏是否显示同时受：应用通知权限+渠道通知权限来控制。如果发现收不到了，就去检查这两个权限。
* 安卓原生通知的应用角标是小圆点，而国内对这部分定制比较多。有的是数字，有的是圆点。
* 对于通知，最好按照对应的重要等级来设置优先级，防止滥用。

> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)