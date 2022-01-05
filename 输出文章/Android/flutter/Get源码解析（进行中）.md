Get源码解析

#### Controller绑定

##### binding+pageRoute绑定

```dart
class GetPageRoute<T> extends PageRoute<T>{
  ...
  Widget _getChild() {
    ...
	// 将配置的bindings和binding，组合成需要依赖的bindings数组
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

```dart
class HomeBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<IHomeProvider>(() => HomeProvider());
    Get.lazyPut<IHomeRepository>(() => HomeRepository(provider: Get.find()));
    Get.lazyPut(() => HomeController(homeRepository: Get.find()));
  }
}
```

源码跟踪

###### 工厂的缓存

```dart
//  
void lazyPut<S>(InstanceBuilderCallback<S> builder,
      {String? tag, bool fenix = false}) {
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

###### 对象的获取

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
      // 未初始化过 ，创建对象
      i = _startController<S>(tag: name);
      if (_singl[key]!.isSingleton!) {
        // 单例的话，只创建一次，每次
        _singl[key]!.isInit = true;
        if (Get.smartManagement != SmartManagement.onlyBuilder) {
          // 如果管理模式是非onlyBuilder，将controller绑定到对应的PageRoute
          RouterReportManager.reportDependencyLinkedToRoute(_getKey(S, name));
        }
      }
    }
    return i;
  }
  
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

###### 销毁



#### Obx原理

