Get源码解析 第一弹 依赖注入

#### 基础

##### 依赖注入

依赖注入是指在系统运行时**动态的向某个对象提供它所需要的其他对象**。有如下实现方式（维基百科）：

* 基于 set 方法。实现特定属性的public set方法，来让外部容器调用传入所依赖类型的对象。
* 基于构造函数。实现特定参数的构造函数，在新建对象时传入所依赖类型的对象。
* 基于注解。基于Java的注解功能，在私有变量前加“@Autowired”等注解，不需要显式的定义以上三种代码，便可以让外部容器传入对应的对象。该方案相当于定义了public的set方法，但是因为没有真正的set方法，从而不会为了实现依赖注入导致暴露了不该暴露的接口（因为set方法只想让容器访问来注入而并不希望其他依赖此类的对象访问）。

在平时的java应用开发中，我们要实现某一个功能或者说是完成某个业务逻辑时至少需要两个或以上的对象来协作完成，在没有使用Spring的时候，每个对象在需要使用他的合作对象时，自己均要使用像new object() 这样的语法来将合作对象创建出来，这个合作对象是由自己主动创建出来的，创建合作对象的主动权在自己手上，自己需要哪个合作对象，就主动去创建，创建合作对象的主动权和创建时机是由自己把控的，而这样就会使得对象间的耦合度高了，A对象需要使用合作对象B来共同完成一件事，A要使用B，那么A就对B产生了依赖，也就是A和B之间存在一种耦合关系，并且是紧密耦合在一起，而使用了Spring之后就不一样了，创建合作对象B的工作是由Spring来做的，Spring创建好B对象，然后存储到一个容器里面，当A对象需要使用B对象时，Spring就从存放对象的那个容器里面取出A要使用的那个B对象，然后交给A对象使用，至于Spring是如何创建那个对象，以及什么时候创建好对象的，A对象不需要关心这些细节问题(你是什么时候生的，怎么生出来的我可不关心，能帮我干活就行)，A得到Spring给我们的对象之后，两个人一起协作完成要完成的工作即可。

**控制反转IoC(Inversion of Control)是说创建对象的控制权进行转移，以前创建对象的主动权和创建时机是由自己把控的，而现在这种权力转移到第三方**

Android 上有 [Dagger](https://links.jianshu.com/go?to=https%3A%2F%2Fdeveloper.android.com%2Ftraining%2Fdependency-injection%2Fdagger-basics%3Fhl%3Dzh-cn) 和[Hilt](https://links.jianshu.com/go?to=https%3A%2F%2Fdeveloper.android.com%2Ftraining%2Fdependency-injection%2Fhilt-android%3Fhl%3Dzh-cn) ，后台有Spring，都能够实现自动注入， 而GetX 也为我们提供了相应的功能。

Getx注入的GetXController都是由GetX框架自己来维护的。使用Getx的依赖注入，能极大的简化代码。

Get有一个简单而强大的依赖管理器，它允许你只用1行代码就能检索到 Controller 或者需要依赖的类，不需要提供上下文。

#### Get源码

注入依赖：

```dart
Get.put<PutController>(PutController());
Get.lazyPut<PutController>(()->PutController());
```

获取依赖：

```dart
Get.find<PutController>();
```

源码跟踪

###### lazyPut

懒加载方式，只有在使用的时候才会实力话。

```dart
//
Get.lazyPut<S>(
  // 必须：当你的类第一次被调用时，将被执行的方法。
  InstanceBuilderCallback<S> builder,
  
  // 可选：和Get.put()一样，当你想让同一个类有多个不同的实例时，就会用到它。
  // 必须是唯一的
  {String tag,

  // 可选：下次使用时是否重建，
  // 当不使用时，实例会被丢弃，但当再次需要使用时，Get会重新创建实例，
  // 就像 bindings api 中的 "SmartManagement.keepFactory "一样。
  // 默认值为false
  bool fenix = false
  }
){
  GetInstance().lazyPut<S>(builder, tag: tag, fenix: fenix);
}

  void lazyPut<S>(
    InstanceBuilderCallback<S> builder, {
    String? tag,
    bool? fenix,
    bool permanent = false,
  }) {
    _insert(
      isSingleton: true,
      name: tag,
      permanent: permanent,
      builder: builder,
      fenix: fenix ?? Get.smartManagement == SmartManagement.keepFactory,
    );
  }

  void _insert<S>({
    bool? isSingleton,
    String? name,
    bool permanent = false,
    required InstanceBuilderCallback<S> builder,
    bool fenix = false,
  }) {
    // 根据返回的类型和name，找到所对应的key值
    final key = _getKey(S, name);
    // 检测构造器是否已经存在
    if (_singl.containsKey(key)) {
      final dep = _singl[key];
      if (dep != null && dep.isDirty) {
        // 如果对应的构造工厂已经dirty，则重新进行设置
        _singl[key] = _InstanceBuilderFactory<S>(
          isSingleton,
          builder,
          permanent,
          false,
          fenix,
          name,
          lateRemove: dep as _InstanceBuilderFactory<S>, // 将原有的构造工厂设置为后面移除
        );
      }
    } else {
      // 放入到对应的hashmap中
      _singl[key] = _InstanceBuilderFactory<S>(
        isSingleton,
        builder,
        permanent,
        false,
        fenix,
        name,
      );
    }
  }
```

总结：

* 全局的数据处理都是在GetInstance中来进行
* 全局的数据都存在_singl中，这是个Map，
  * key：对象的runtimeType或者类的Type + tag
  * value：_InstanceBuilderFactory类，我们传入builder对象会存入这个类中
* singl 这个map存值的时候，不是用的put，而是用的putIfAbsent
  - 如果map中有key和传入key相同的数据，传入的数据将不会被存储
  - 也就是说相同类实例的对象，传入并不会被覆盖，只会存储第一条数据，后续被放弃
* 最后使用find方法，返回传入的实例

###### 实例的获取

```dart
  //
  S find<S>({String? tag}) => GetInstance().find<S>(tag: tag);

  // GetInstance
  S find<S>({String? tag}) {
    final key = _getKey(S, tag);
    if (isRegistered<S>(tag: tag)) {
      final dep = _singl[key];
      if (dep == null) {
        if (tag == null) {
          throw 'Class "$S" is not registered';
        } else {
          throw 'Class "$S" with tag "$tag" is not registered';
        }
      }
      // 初始化，并创建对象
      final i = _initDependencies<S>(name: tag);
      return i ?? dep.getDependency() as S;
    } else {
      throw '"$S" not found. You need to call "Get.put($S())" or "Get.lazyPut(()=>$S())"';
    }
  }

  S? _initDependencies<S>({String? name}) {
    final key = _getKey(S, name);
    final isInit = _singl[key]!.isInit;
    S? i;
    if (!isInit) {
      // 重点方法1 未初始化过 ，创建对象
      i = _startController<S>(tag: name);
      if (_singl[key]!.isSingleton!) {
        // 单例的话，只创建一次
        _singl[key]!.isInit = true;
        if (Get.smartManagement != SmartManagement.onlyBuilder) {
          // 重点方法2  如果管理模式是非onlyBuilder，将controller绑定到对应的Route
          RouterReportManager.reportDependencyLinkedToRoute(_getKey(S, name));
        }
      }
    }
    return i;
  }

```

重点方法1:

```dart
  S _startController<S>({String? tag}) {
    final key = _getKey(S, tag);
    // 调用传入的builderFunc来创建对象，
    final i = _singl[key]!.getDependency() as S;
    if (i is GetLifeCycleBase) {
      // 如果创建的类是GetLifeCycleBase的子类，则执行onStart->onInit()方法
      i.onStart();
      if (tag == null) {
        Get.log('Instance "$S" has been initialized');
      } else {
        Get.log('Instance "$S" with tag "$tag" has been initialized');
      }
      if (!_singl[key]!.isSingleton!) {
        // 非单例模式
        RouterReportManager.appendRouteByCreate(i);
      }
    }
    return i;
  }
```

重点方法2:

```dart
class RouterReportManager<T> {
  /// Route所持有的Instance对象
  static final Map<Route?, List<String>> _routesKey = {};

  /// Route销毁时需要执行的方法（主要是controller中的onClose()方法）
  static final Map<Route?, HashSet<Function>> _routesByCreate = {};

  static void reportDependencyLinkedToRoute(String depedencyKey) {
    if (_current == null) return;
    if (_routesKey.containsKey(_current)) {
      _routesKey[_current!]!.add(depedencyKey);
    } else {
      _routesKey[_current] = <String>[depedencyKey];
    }
  }
```

总结：

- 先判断 _singl 中是否含有该key的数据，有则取，无则抛异常
- 
- 如果创建的对象是生命周期相关，则会绑定到对应的Route页面
- 关键代码： **_singl[key]!.getDependency() as S** ，直接通过key去map取值就行了

###### 销毁

1. 覆写Route方法

在Getx中，Dialog、BottomSheet、GetPageRoute都重写了**dispose()**方法，在内部调用了**RouterReportManager.reportRouteDispose(this)**方法。

```dart
mixin PageRouteReportMixin<T> on Route<T> {
  @override
  void dispose() {
    super.dispose();
    // 通知Route销毁
    RouterReportManager.reportRouteDispose(this);
  }
}

class GetDialogRoute<T> extends PopupRoute<T> {
  @override
  void dispose() {
    RouterReportManager.reportRouteDispose(this);
    super.dispose();
  }
}

class GetModalBottomSheetRoute<T> extends PopupRoute<T> {
  @override
  void dispose() {
    RouterReportManager.reportRouteDispose(this);
    super.dispose();
  }
}
```

2. 数据销毁

```dart
  static void reportRouteDispose(Route disposed) {
    if (Get.smartManagement != SmartManagement.onlyBuilder) {
      WidgetsBinding.instance!.addPostFrameCallback((_) {
        // 移除所有绑定到Route上的依赖
        _removeDependencyByRoute(disposed);
      });
    }
  }

  static void _removeDependencyByRoute(Route routeName) {
    final keysToRemove = <String>[];
    _routesKey[routeName]?.forEach(keysToRemove.add);
    if (_routesByCreate.containsKey(routeName)) {
      for (final onClose in _routesByCreate[routeName]!) {
        // 重点方法1 调用onClose() 方法
        onClose();
      }
      _routesByCreate[routeName]!.clear();
      _routesByCreate.remove(routeName);
    }

    for (final element in keysToRemove) {
      // 重点方法2 调用delete方法，从内存中移除
      final value = GetInstance().delete(key: element);
      if (value) {
        _routesKey[routeName]?.remove(element);
      }
    }

    keysToRemove.clear();
  }

// GetInstance
  bool delete<S>({String? tag, String? key, bool force = false}) {
    final newKey = key ?? _getKey(S, tag);
    final dep = _singl[newKey];
    final _InstanceBuilderFactory builder;
    builder = dep;
    ...
    final i = builder.dependency;
    // Service类型不销毁
    if (i is GetxServiceMixin && !force) {
      return false;
    }
    // 调用onDelete()方法
    if (i is GetLifeCycleBase) {
      i.onDelete();
      Get.log('"$newKey" onDelete() called');
    }

    if (builder.fenix) {
      // 对象如果支持下次find的时候重新创建。 则只删除对象，不删除工厂。
      builder.dependency = null;
      builder.isInit = false;
      return true;
    } else {
      if (dep.lateRemove != null) {
        //
        dep.lateRemove = null;
        Get.log('"$newKey" deleted from memory');
        return false;
      } else {
        // 直接移除_InstanceBuilderFactory，清除对象，清除对象创建工厂。
        _singl.remove(newKey);
        if (_singl.containsKey(newKey)) {
          Get.log('Error removing object "$newKey"', isError: true);
        } else {
          Get.log('"$newKey" deleted from memory');
        }
        return true;
      }
    }
  }
```

###### 其他

对于对象的注入，除了lazyPut，还有很多种方法。

```
Get.put<S>(
  // 必备：要注入的类。
  // 注：" S "意味着它可以是任何类型的类。
  S dependency

  // 可选：想要注入多个相同类型的类时，可以用这个方法。
  // 比如有两个购物车实例，就需要使用标签区分不同的实例。
  // 必须是唯一的字符串。
  {String tag,

  // 可选：默认情况下，get会在实例不再使用后进行销毁
  // （例如：一个已经销毁的视图的Controller)
  // 如果需要这个实例在整个应用生命周期中都存在，就像一个sharedPreferences的实例。
  // 默认值为false
  bool permanent = false,

  // 可选：允许你在测试中使用一个抽象类后，用另一个抽象类代替它，然后再进行测试。
  // 默认为false
  bool overrideAbstract = false,

  // 可选：允许你使用函数而不是依赖（dependency）本身来创建依赖。
  // 这个不常用
  InstanceBuilderCallback<S> builder,}
) {
    _insert(
        isSingleton: true,
        name: tag,
        permanent: permanent,
        builder: builder ?? (() => dependency));//自动帮我们写入了builder方法
    // 相当于lazyPut+find
    return find<S>(tag: tag);
  }
```



#### Bindings类

通过Get能实现依赖的注入和使。但是当我们需要和Widget的生命周期进行绑定时，仍然还是需要手动来管理。如果想要实现自动注入，则需要使用Bindings类。

```dart
class TestBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut(() => TestController());
  }
}

```

###### 使用

```dart
配合命名路由
    GetPage(
      name: Routes.TEST,
      page: () => TestPage(),
      binding:TestBinding(),
    ),
或者  
Get.to(InjectSimplePage(), binding: InjectSimpleBinding());
```

###### 源码

在get中，如果需要进行页面跳转，使用的是Get.toName

binding+pageRoute绑定

```dart
class GetPageRoute<T> extends PageRoute<T>{
  ...
  Widget _getChild() {
    ...
	// 将配置的bindings和binding，组合成需要依赖的数组
    final localbindings = [
      if (bindings != null) ...bindings!,
      if (binding != null) ...[binding!]
    ];
    final bindingsToBind = middlewareRunner.runOnBindingsStart(localbindings);
    if (bindingsToBind != null) {
      for (final binding in bindingsToBind) {
        // 依次调用dependencies方法，执行依赖的相关准备工作
        binding.dependencies();
      }
    }
    ...
  }

```

使用demo





#### 问题点

循环依赖

```dart
    final isInit = _singl[key]!.isInit;
    S? i;
    if (!isInit) {
      // 创建对象
      i = _startController<S>(tag: name);
      if (_singl[key]!.isSingleton!) {
        // 对象创建完成，isInit才设置为true
        _singl[key]!.isInit = true;
```



#### Obx原理







参考：

[控制反转和依赖注入的理解](https://blog.csdn.net/sinat_21843047/article/details/80297951)

[[依赖注入的三种方式以及优缺点。](https://www.cnblogs.com/zoro-zero/p/13490459.html)

[Flutter状态管理终极方案GetX第三篇——依赖注入](https://www.jianshu.com/p/62764349f9e1)

[Flutter GetX深度剖析](https://blog.csdn.net/CNAD666/article/details/118721062)

[Flutter GetX框架状态管理源码原理分析](https://blog.csdn.net/qq_24856205/article/details/121678619)
