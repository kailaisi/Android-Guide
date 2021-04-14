### Flutter搭建

简介：编程语言Dart

#### 设置镜像

```
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
```

#### SDK配置

##### 下载

下载地址：https://flutter.dev/docs/development/tools/sdk/releases?tab=macos#windows。

##### 解压缩

##### 设置环境变量

##### 更改环境变量。

1. path路径增加flutter的bin目录
2. 用户变量下增加PUB_HOSTED_URL和FLUTTER_STORAGE_BASE_URL。

##### 检测

在bash中通过 *flutter doctor* 检测环境是否正确

#### IDE配置

IDE推荐：VSCode

**插件安装**

1. 启动 VS Code
2. 调用 **View>Command Palette…**
3. 输入 ‘install’, 然后选择 **Extensions: Install Extension** action
4. 在搜索框输入 `flutter` , 在搜索结果列表中选择 ‘Flutter’, 然后点击 **Install**
5. 选择 ‘OK’ 重新启动 VS Code



#### 工程创建

* 通过命令：flutter create xxx
* ~~通过vscode：Command Palette-->Flutter:new Application Project(不推荐，坑多)~~

#### 工程运行

* 通过命令：flutter run

#### 常用的控件

`**Flutter**`中拥有30多种预定义的布局`widget`，常用的有**`Container`**、**`Padding`**、**`Center`**、**`Flex`**、**`Row`**、**`Colum`**、**`ListView`**、**`GridView`**。按照《**Flutter技术入门与实战**》上面来说的话，大概分为四类

- **基础布局组件**：**Container**(容器布局)，**Center**(居中布局)，**Padding**(填充布局)，**Align**(对齐布局)，**Colum**（垂直布局），**Row**（水平布局），**Expanded**（配合Colum，Row使用），**FittedBox**（缩放布局），**Stack**（堆叠布局），**overflowBox**(溢出父视图容器)。
- **宽高尺寸处理**：**SizedBox**（设置具体尺寸），**ConstrainedBox**（限定最大最小宽高布局），**LimitedBox**（限定最大宽高布局），**AspectRatio**（调整宽高比），**FractionallySizedBox**（百分比布局）
- **列表和表格处理**：**ListView**（列表），**GridView**（网格），**Table**（表格）
- **其它布局处理**：Transform（矩阵转换），Baseline（基准线布局），Offstage（控制是否显示组件），Wrap（按宽高自动换行布局）

三方：https://pub.dev/packages



#### 参考：

[贾鹏辉技术博客](https://www.devio.org/tags/#Flutter)

[Flutter中文网](https://flutterchina.club/tutorials/layout/#approach)

[Flutter开发者文档](https://flutter.cn/docs/get-started/install/windows)

[Flutter实战](https://book.flutterchina.club/chapter2/first_flutter_app.html)

[闲鱼Flutter系列](https://www.yuque.com/xytech/flutter)

[Dart Flutter入门实战视频教程](https://www.bilibili.com/video/BV1S4411E7LY?p=31&t=1842)

https://github.com/CarGuo/gsy_flutter_book

[实战携程网App](https://coding.imooc.com/class/321.html)