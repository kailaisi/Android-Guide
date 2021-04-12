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