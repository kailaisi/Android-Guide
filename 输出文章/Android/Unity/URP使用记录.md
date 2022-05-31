##### URP使用记录

#### 网格

网格是 GameObject 的 3D 骨架。Unity 中的每个 GameObject 都有一个网格。它是对象的几何元素。

着色器和材质告诉 Unity 您希望如何将 3D 游戏对象的网格从 3D 场景[渲染](https://learn.unity.com/tutorial/get-started-on-your-guided-project#619fbedaedbc2a5554f0b95b)到屏幕上的 2D 图像。

Mesh Filter 组件指向您项目中的网格坐标数据。

Mesh Renderer 组件是您指定如何渲染网格的地方。

着色器负责计算网格的渲染方式。

材质是包含定义网格外观的颜色、图像和其他属性的资产（与您的项目一起存储并在项目文件夹中管理）。

着色器定义了表面的*外观*，而材质定义了它*的*外观。

纹理是环绕 3D 对象的 2D 贴图，以创建颜色、反射率和其他属性的变化。

由 Autodesk® 3ds Max® 和 Maya® 或 Blender® 等建模应用程序制作的网格会生成它们自己的称为**UV 坐标的 2D 坐标集**。UV 坐标类似于常规 2D 空间中的 XY 坐标，但它们被称为 UV 以将它们与环境坐标系 (XYZ) 区分开来。UV 坐标相对于网格，而不是场景中的 3D 空间。

https://connect-prd-cdn.unity.com/20211122/learn/images/7b0e005e-a171-4abf-947f-fcf0eb5f6f5f_EllenUnwrap.gif._gif_.mp4

##### 

#### 透明效果

##### 使用 Alpha 通道的透明度。

**纯色**

SurfaceType：Transparent

BlendMode：Alpha

RenderFace：Both

Smoothness:0.5~1

Metallic：调整具体值

**非纯色**

使用有透明度的Map来设置

##### Alpha剪裁

点击Alpha Clip。可以进行一定的剪裁。将小于透明Alpha的裁剪掉。

#### 凹凸贴图

实现凹凸效果的，主要使用两种类型的贴图：法线贴图和高度贴图。

法线和高度贴图可以在不使用太多计算能力的情况下为您的表面添加逼真的物理细节。通常，法线贴图在没有高度贴图的情况下使用。

高度图表示网格中每个像素的相对高度。这些是单通道（灰度）图，其中每个像素值表示与网格表面的相对距离。当您使用 RGB 图像作为高度图时，着色器仅读取绿色通道。

#### 其他

**遮挡**，在 3D 图形中，是物体对光线的遮挡。人行道上的裂缝和握紧拳头手指之间的细暗阴影线是遮挡的例子，OcclusionMap。



仔细观察具有光滑表面的现实世界物体，例如智能手机上的玻璃，上面覆盖着指纹，或者您最喜欢的咖啡杯被划伤和磨损。如果您想对这些项目进行建模并包含指纹或划痕等细节，您可以使用**微表面映射**来添加在基本贴图或法线贴图中无法捕获的细节级别。**Detail Inputs**部分（在您工作的**Surface Inputs**部分下方），找到**Normal Map** 即可设置



#### 溶解特效

使用Alpha。在Graph Setting中勾选Alpha Clip。那么会在Fragment中增加Alpha(设置透明度)，AlphaClip(裁剪阈值)，如果在fragment上，Alpha的值比Alpha的值大，那么就保留，小的话则裁剪