UnityShader 学习

### 矩阵变换

在进行各种空间的变换处理中，主要分为以下几部分：

1. 矩阵的平移：

![image-20220404223407076](http://cdn.qiniu.kailaisii.com/typora/image-20220404223407076.png)

平移并不会对矢量的方向产生任何的影响。

2. 矩阵缩放：
   ![image-20220404223751907](http://cdn.qiniu.kailaisii.com/typora/image-20220404223751907.png)

矩阵的缩放，是对相应的x，y，z轴进行相应的比例调整。对于角度不会有任何的影响。

3. 矩阵旋转：

   旋转分为3种，分别为绕x,y,z进行旋转。每种的处理方式都不相同

   

![image-20220404223947989](http://cdn.qiniu.kailaisii.com/typora/image-20220404223947989.png)

4. 复合变换

​	复合变换是上述三种变换的结合。一般按照约定的顺序：**先缩放，再旋转，最后平移。**

### Unity中的空间

#### 模型空间

模型空间，是和某个模型（或者对象）有关的。也称为对象空间或者局部空间。会以某个对象为参考的原点，然后按照：前（+z），上（+y），右（+x）来定义其他对象所在的位置。

比如说以农场中的一只小羊为坐标原点，它鼻子的位置为：（0,2,4,1）。

#### 世界坐标

世界坐标是一个特殊的坐标系，可以用来描述绝对位置。我们可以通过调整Transform组件的Position来改变模型的位置。

**顶点变换第一步：将顶点坐标从模型空间变换到世界空间中**

比如说我们的小羊，在Transform中的设置如下：

![image-20220404230008084](http://cdn.qiniu.kailaisii.com/typora/image-20220404230008084.png)

可以知道在世界空间中，进行了（2，2，2）的缩放，然后继续你给你了（0，150，0）的旋转以及（5，0，25）的平移。所以可以构建出变换矩阵：

![image-20220404230119772](http://cdn.qiniu.kailaisii.com/typora/image-20220404230119772.png)

这时候，我们就可以知道小羊鼻子在世界坐标中的位置为：

![image-20220404230242769](/Users/yunzhanghu1154/Library/Application%20Support/typora-user-images/image-20220404230242769.png)

#### 观察空间

观察空间，也被称为摄像机空间。**观察空间是个特例，使用的是右手坐标系**。

**顶点变换的第二步，就是将顶点坐标从世界空间变换到观察空间中。**所以如果我们想知道小羊在摄像机的位置，首先需要知道世界坐标的顶点在观察空间的位置。

按照之前的方法，我们可以通过相机的Transform来得到观察空间在世界坐标的变换矩阵，然后求逆矩阵来得到从世界空间到观察空间的变换矩阵。另一种方式，可以想像，平移整个观察空间，让摄像机原点位于世界坐标的原点，并三个坐标轴重合就可以得到对应的矩阵。

“由Transform组件可以知道，摄像机在世界空间中的变换是先按(30, 0, 0)进行旋转，然后按(0, 10, −10)进行了平移。那么，为了把摄像机重新移回到初始状态（这里指摄像机原点位于世界坐标的原点、坐标轴与世界空间中的坐标轴重合），我们需要进行逆向变换，即先按(0, −10, 10)平移，以便将摄像机移回到原点，再按(−30, 0, 0)进行旋转，以便让坐标轴重合。”

![image-20220404232500038](http://cdn.qiniu.kailaisii.com/typora/image-20220404232500038.png)

由于是右手坐标系，所以需要对z轴进行取反操作。

![image-20220404232536784](http://cdn.qiniu.kailaisii.com/typora/image-20220404232536784.png)

所以这时候就可以通过Mview得到小羊鼻子在观察空间中的位置了。

![image-20220404232800072](http://cdn.qiniu.kailaisii.com/typora/image-20220404232800072.png)

#### 裁剪空间

当确定了观察空间之后，就需要进行一定的裁剪，将位于视图中中的图元进行保留，而外部的则被剔除，而不会被渲染。

裁剪分为两种方式：正交投影和透视投影。其中正交投影，对于正方形，展示出来的效果仍然是正方形，而透视投影有近大远小的真实世界效果。

![image-20220405102619071](http://cdn.qiniu.kailaisii.com/typora/image-20220405102619071.png)

其所对应的摄像头裁剪方式如下：

![image-20220405102647637](http://cdn.qiniu.kailaisii.com/typora/image-20220405102647637.png)

##### 透视投影

透视投影是视锥体，

![image-20220405103228798](http://cdn.qiniu.kailaisii.com/typora/image-20220405103228798.png)

其中，对于2个裁剪面，我们可以计算其高度：

![image-20220405110447132](http://cdn.qiniu.kailaisii.com/typora/image-20220405110447132.png)

横向信息，可以通过摄像机纵横比（即Viewport Rect中摄像机占用的比例）来得到。我们定义纵横比：

![image-20220405110648958](http://cdn.qiniu.kailaisii.com/typora/image-20220405110648958.png)

可以根据已知的参数获取透视投影的投影矩阵：

![image-20220405110718356](http://cdn.qiniu.kailaisii.com/typora/image-20220405110718356.png)

**一个顶点和上述投影矩阵相乘，可以由观察空间变换到裁剪空间中。**

![image-20220405112204992](http://cdn.qiniu.kailaisii.com/typora/image-20220405112204992.png)

可以通过结果看，这个投影矩阵的本质是对x，y，z分量进行了不同程度的缩放，缩放是为了方便裁剪。**顶点的w分量不再是1，而是原先z分量的取反结果**。

如果一个顶点在视锥体内，那么它变换后的坐标必须满足：

![image-20220405112551648](http://cdn.qiniu.kailaisii.com/typora/image-20220405112551648.png)



![img](http://cdn.qiniu.kailaisii.com/typora/156.png)

##### 正交投影

正交投影是6个裁剪平面

![image-20220405104033291](http://cdn.qiniu.kailaisii.com/typora/image-20220405104033291.png)

正交投影对应的变换矩阵是：

![img](http://cdn.qiniu.kailaisii.com/typora/158.gif)

继续之前小羊的样子。我们可以查看摄像机的参数如下：

![image-20220405111749019](http://cdn.qiniu.kailaisii.com/typora/image-20220405111749019.png)

可以看到，采用的是透视投影，Fov=60度，Near为5，Far为40，Aspect为4/3=1.33333。根据透视矩阵

![img](http://cdn.qiniu.kailaisii.com/typora/162.gif)

通过这个矩阵把小羊的鼻子从观察空间变化到裁剪空间中。

![img](http://cdn.qiniu.kailaisii.com/typora/163.gif)

通过对比得到，鼻子满足下面的不等式：

![image-20220405112043673](http://cdn.qiniu.kailaisii.com/typora/image-20220405112043673.png)

##### 屏幕空间

当经过投影矩阵变换后，会进行裁剪工作。当完成了所有的裁剪工作之后，就需要进行真正的投影了。也就是需要**把视锥体投影到屏幕空间中**。

首先需要做的就是**标准齐次除法**，得到的坐标有时候也称作**归一化的设备坐标**（NDC）。齐次出发和屏幕映射的过程可以使用下面的公式。

![img](http://cdn.qiniu.kailaisii.com/typora/166.gif)

在上一步的变化中，我们知道了小羊鼻子的位置是（11.691,15.311,23.692,27.31）。按照屏幕像素宽400，高300来计算。首先按照齐次除法，把裁剪空间坐标投影到NDC中，然后再映射到屏幕中。

![img](http://cdn.qiniu.kailaisii.com/typora/168.gif)

由此，我们可以计算出小羊鼻子在屏幕中的位置坐标（285.617，234。096）。

![img](http://cdn.qiniu.kailaisii.com/typora/169.jpg)

整个渲染流水线中的顶点空间变换过程就是如上所示了。

![img](http://cdn.qiniu.kailaisii.com/typora/170.jpg)



### Shader

##### 语义概要

在Unity中，我们可以创建Shader文件。其中我们看下最简单的语义。

```glsl
Shader "Unlit/MySimpleShader"
{
    Properties {}
    SubShader
    {

        Pass
        {
            CGPROGRAM
            // 编译指令，告诉unity，哪个函数包含了顶点着色器的代码
            //哪个函数包含了片源着色器的代码
            //vertex是顶点着色器，ver是对应的函数名称
            #pragma vertex vert
            // fragment 是片源着色器，对应的函数名称是frag
            #pragma fragment frag
            // 顶点着色器代码，逐个顶点都会执行，POSITION指定V这个顶点的位置
            // 返回值float4是这个顶点在裁剪空间的位置。
            // POSITION和SV_POSITION都是CG/HLSL语义，这些语义告诉系统，用户需要哪些输入值，以及会有哪些输出
            // 这里的POSITION告诉模型，将模型的顶点坐标填充到输入参数v中。
            // SV_POSITION告诉模型，顶点着色器的输出是裁剪空间中的顶点坐标
            // 
            float4 vert(float4 v:POSITION):SV_POSITION
            {
                // UNITY_MATRIX_MVP是矩阵，是unity内置的模型观察投影。
                // 该句的的意思是将顶点坐标从模型空间转换到裁剪空间
                return mul(UNITY_MATRIX_MVP,v);
            }

            // 没有输入，SV_TARGET是HLSL语言的语义。把用户的输出颜色存储到一个渲染目标中，这里将输出到默认的帧缓存中。
            fixed4 frag():SV_TARGET
            {
                // 返回了一个固定的白色的fix4类型数据。
                return fixed4(1.0, 1.0, 1.0, 1.0);
            }
            ENDCG
        }
    }
}
```

##### 模型数据来源

在一些场景中，我们需要获得顶点着色器中的纹理坐标和法线方向。这时候，则需要定义一个结构体来存储对应的信息。

```glsl
Shader "Unlit/Shader2"
{
    Properties
    {
        _MainTex ("Texture", 2D) = "white" {}
    }
    SubShader
    {


        Pass
        {
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            // 使用一个结构体来定义顶点着色器的输入。对于顶点着色器，Unity支持的语义有：POSITION, TANGENT，NORMAL，TEXCOORD0，TEXCOORD1，TEXCOORD2，TEXCOORD3，COLOR等。
            // 这些具体的数据，是由使用该着色器的MeshRender来提供的。
            struct a2v
            {
                float4 vertex : POSITION; // 用POSITION告诉unity，用模型空间的顶点坐标来填充vertex对象
                float3 normal:NORMAL; //模型空间的法线方向NORMLAL来填充normal对象
                float4 texcoord:TEXCOORD; //用模型的第一套纹理来填充texcoord对象
            };

            float4 vert(a2v v):SV_POSITION
            {
                // UNITY_MATRIX_MVP是矩阵，是unity内置的模型观察投影
                return mul(UNITY_MATRIX_MVP,v.vertex);
            }

            // 没有输入，SV_TARGET是HLSL语言的语义。把用户的输出颜色存储到一个渲染目标
            fixed4 frag():SV_TARGET
            {
                return fixed4(0.0, 1.0, 1.0, 1.0);
            }
            ENDCG
        }
    }
}
```

对于顶点着色器，Unity支持的语义有：POSITION, TANGENT，NORMAL，TEXCOORD0，TEXCOORD1，TEXCOORD2，TEXCOORD3，COLOR等。**这些具体的数据，是由使用该着色器的Mesh Render来提供的**。在每帧调用Draw Call的时候，Mesh Render组件会把它负责渲染的模型数据发送给Unity Shader。**每个模型包含一组三角面片，每个三角面片由3个顶点构成，而每个顶点又包含了一些数据，例如顶点位置、法线、切线、纹理坐标、顶点颜色等。**

##### 顶点着色器/片元着色器通信

在一些实践中，我们希望从顶点着色器中输出一些数据，然后传递给片元着色器。这就涉及到了二者之间的通信。