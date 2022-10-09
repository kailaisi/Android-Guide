### 前言
大约在去年11月份，Google将官方网站上推荐的MVVM架构悄悄替换成了MVI架构。参考了官方和许多相关分享，便有了此文章。

### 官方简介
![去骗](https://img-blog.csdnimg.cn/img_convert/d2a6dbdb571ba384fac26e7ccff1a2da.png)

![](https://img-blog.csdnimg.cn/20201031200026625.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3ZpdGF2aXZh,size_16,color_FFFFFF,t_70#pic_center)

* View：Activit、Fragment等展示的信息
* Model：可以理解为状态。可以根据状态信息去更新我们的View
* I：Intent，意图。代表了用户点击交互的相关信息。

用户操作以Intent的形式通知Model => Model基于Intent更新State => View接收到State变化刷新UI。数据永远在一个环形结构中单向流动，不能反向流动：

### 代码展示


Intent代表了我们用户的相关意图，比如我们可能需要获取新闻信息；获取句子数据。
```
sealed class EngLishIntent {
    //获取英语句子数据
    data class DoLoadingEnglish(val num:Int):EngLishIntent()
    //获取新闻数据
    object DoLoadingNews:EngLishIntent()
}
```

再定义一个和Intent差不多的封装类state。代表我们View状态，加载前，加载中，加载后，加载新闻信息成功等等
```
sealed class EnglishState {
    object BeforeLoading:EnglishState()
    object Loading:EnglishState()
    object FinishLoading:EnglishState()

    data class EnglishData(val list:List<EnglishKey>):EnglishState()
    data class NewsData(val list:List<NewsListKey>):EnglishState()

    data class ErrorData(val error:String):EnglishState();


}
```
View层中做对应的意图调用处理。
```
class MVIEnglishActivity :BaseActivity() {
    val viewModel :EnglishVM by viewModels<EnglishVM>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent(R.layout.act_mvi_english_class)
        setTitle("MVI学习")
        btnLoadingNews.setOnClickListener {
            tvClass addText "btnLoadingNews 被点击"
            doLaunch{
                tvClass addText "send(EngLishIntent.DoLoadingNews)"
                viewModel.englishIntent.send(EngLishIntent.DoLoadingNews)
            }
        }
        btnLoadingEnglish.setOnClickListener {
            tvClass addText "btnLoadingEnglish 被点击"
            doLaunch{
                tvClass addText "send(EngLishIntent.DoLoadingEnglish)"

                viewModel.englishIntent.send(EngLishIntent.DoLoadingEnglish(5))
            }
        }
//这里注意改成有生命周期的lifecycleScope 否则网络请求回来这里管道就销毁了
        lifecycleScope.launch {
            viewModel.state.collect {
                when(it){
                    is EnglishState.BeforeLoading->{
                        tvClass addText "初始化页面"

                    }
                    is EnglishState.Loading ->{
                        tvClass addText "加载中..."

                    }
                    is EnglishState.FinishLoading ->{
                        tvClass addText "加载完毕..."

                    }
                    is EnglishState.EnglishData->{
                        for (key in it.list){
                            tvClass addText key.english addText key.chinese

                        }

                    }
                    is EnglishState.NewsData->{
                        for (key in it.list){
                            tvClass addText "标题：${key.title}" addText "摘要：${key.summary}" addText "省份:${key.provinceName} 时间:${key.updateTime}"


                        }
                    }
                }
            }
        }

    }
    fun doLaunch(block: suspend CoroutineScope.() -> Unit){
        GlobalScope.launch {
            block.invoke(this)
        }
    }
    infix fun  TextView.addText(text: String) :TextView{
       this.text = "${this.text?.toString()}$text\n";
        return this
    }
}
```
对应的ViewModel层来处理Intent和State的变更逻辑
```
class EnglishVM : BaseViewModel() {
    val englishIntent = Channel<EngLishIntent>(Channel.UNLIMITED)
    private val _state = MutableStateFlow<EnglishState>(EnglishState.BeforeLoading)
    val state: StateFlow<EnglishState>
        get() = _state
    init {
        handleIntent();
    }
    private fun handleIntent() {
        viewModelScope.launch {
            englishIntent.consumeAsFlow().collect {
                //这两种写法太冗余了
//                    is EngLishIntent.DoLoadingEnglish -> loadingEnglish()
//                    is EngLishIntent.DoLoadingNews -> loadingEnglish()
                commentLoading(it)
            }
        }
    }
   suspend fun intentToState(intent:EngLishIntent):EnglishState{
        when (intent) {
            //加载英语句子
            is EngLishIntent.DoLoadingEnglish ->
                return EnglishState.EnglishData(getClient.invoke().getEnglishWordsByLaunch(5))
            //加载新闻句子
            is EngLishIntent.DoLoadingNews ->
                return EnglishState.NewsData(getClient.invoke().getNewsListKeyByLaunch())
        }
    }

    private fun commentLoading(intent:EngLishIntent) {
        viewModelScope.launch(context = (errorContext {
            _state.value = EnglishState.FinishLoading
            _state.value = EnglishState.ErrorData(it.message?:"请求异常")
        } + Dispatchers.Main)) {
            _state.value = EnglishState.Loading
            _state.value = intentToState(intent)
            _state.value = EnglishState.FinishLoading
        }
    }
}
```


### 优缺点
* 优点
  * UI的所有变化来自State，所以只需聚焦State，架构更简单、易于调试
  * 数据单向流动，很容易对状态变化进行跟踪和回溯
  * state实例都是不可变的，确保线程安全
  * UI只是反应State的变化，没有额外逻辑，可以被轻松替换或复用

* 缺点
  * 所有的操作最终都会转换成State，所以当复杂页面的State容易膨胀
  * state是不变的，每当state需要更新时都要创建新对象替代老对象，这会带来一定内存开销
  * 有些事件类的UI变化不适合用state描述，例如弹出一个toast或者snackbar

### 后续意图
谷歌之所以推广MVI架构，是为了Comopose做铺垫，在Compose中，更加适合MVI架构的使用。当State状态流变更，自动驱动UI的重组。而无需像现在代码这样，再去手动的更新View层的视图变化。用户只需要关注页面的实现即可。

### 参考
[android MVI到底是什么](https://www.jianshu.com/p/353e882ee410)
[MVI架构快速入门](https://blog.csdn.net/vitaviva/article/details/109406873)