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

#### 溶解特效

使用Alpha。在Graph Setting中勾选Alpha Clip。那么会在Fragment中增加Alpha(设置透明度)，AlphaClip(裁剪阈值)，如果在fragment上，Alpha的值比Alpha的值大，那么就保留，小的话则裁剪