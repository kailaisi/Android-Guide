## 数据持久化

本文来自于Unity官方学习资料：[链接](https://learn.unity.com/tutorial/implement-data-persistence-between-sessions)

#### Scene之间持久化

单例：

```c#
public class MainManager : MonoBehaviour
{
    public static MainManager Instance;
    public Color TeamColor;
    private void Awake()
    {
        Debug.Log(gameObject);
        if (Instance != null)
        {
            Destroy(gameObject);
            return;
        }
        Instance = this;
        DontDestroyOnLoad(gameObject);
    }
}

```

1. 通过单例实现在各个类中都可以调用，在切换Scene时，仍然保存在内存中，可以实现多场景切换时仍然适用。

> 注意，在其他场景中使用时，要进行null的判断处理。防止在其他Scene中直接Play调试时，因为null导致的程序异常

2. DontDestroyOnLoad保证挂在的gameObject在切换场景时，不被销毁。

> 注意，当从A切到B，再切回A时，可能会导致DontDestroyOnLoad设置的gameObject存在多个，所以需要根据单例是否存在进行销毁。



**最佳实践：创建一个单独的Scene，在其中将所有不被销毁的gameObject进行创建，并且不会再重复进入到该Scene中，这样就不再需要处理多次创建问题。**

####  数据序列化

##### 简介

当应用关闭或者启动时，如果需要进行数据的持久化，需要将关键数据进行序列话并保存到本地。

![img](https://connect-cdn-public-prd.unitychina.cn/h1/20210602/learn/images/197dfde6-d842-4bce-bffa-a0fab3140687_0.jpg)

##### JSON：

JSON小巧轻便，在各种语言中都能很好地进行解析。

Unity 有一个名为[ JsonUtility](https://docs.unity3d.com/Manual/JSONSerialization.html?_ga=2.31525622.974222808.1647314494-2067007065.1645154224) 的辅助类，它允许您获取一个可序列化的类并将其转换为它的 JSON 数据。

```c#
[Serializable]
public class PlayerData
{
    public int level;
    public Vector3 position;
    public string playerName;
}

// 序列化方式
string json = JsonUtility.ToJson(myData)
// 反序列化
PlayerData myData = JsonUtility.FromJson<PlayerData>(json);
```

##### JsonUtility的缺陷

* JsonUtility不适用于私有属性，Arrays，Lists，以及字典。
* JsonUtility仅对支持序列化的类型有效。可以通过[Serializable]注解来使类支持序列化，但是其中的属性（第一条中的属性）如果不支持序列化，则可能该属性无法保存

##### 数据持久化的难点

因为JsonUtility的一些缺点，对于一些不支持序列话的类或者属性，则需要进行特殊的处理。



**代码事例：**

```c#

    [System.Serializable]
    class SaveData
    {
        public Color TeamColor;
    }

		public void SaveColor()
    {
        SaveData data = new SaveData();
        data.TeamColor = TeamColor;
        string json = JsonUtility.ToJson(data);
        File.WriteAllText(Application.persistentDataPath + "/savefile.json", json);
    }

    public void LoadColor()
    {
        string path = Application.persistentDataPath + "/savefile.json";
        if (File.Exists(path))
        {
            string json = File.ReadAllText(path);
            SaveData data = JsonUtility.FromJson<SaveData>(json);
            TeamColor = data.TeamColor;
        }
    }

```

