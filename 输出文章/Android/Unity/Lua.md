### Lua学习笔记



#### 元表

**扩展了普通表的行为**

对于table，只有一些固定的方法，比如说遍历，比如说访问不存在的方法，比如说两表相加。这种也可以实现，但是每次都需要我们手动的去处理，这样比较麻烦，所以可以采用元表，其实是对表的一种方法的扩展，实现了对表的一种方法扩展。

注意

> 如果元表中存在__metatable键值，那么setmetatable会失效。而且getmetatable不会返回元表。
>
> 这样就可以保护元表。防止外部修改元表数据

##### 元方法

###### __index

当键不存在是的处理方式。既可以是一个方法，也可以是一个表结构。

```lua
mytable={"Lua","Java","C++"}
mymetatable={
    __index=function (tab,key)
        print("调用了Index方法"..key)
    end
}
print(mytable[1])
print(mytable[10])
print(mytable[7])
```

__index也可以使用一个表结构

```lua
mytable={"Lua","Java","C++"}
newtable={}
newtable[7]="Lua"
newtable[8]="Php"
mymetatable={
    __index=newtable       
}
mytable=setmetatable(mytable,mymetatable)

print(mytable[1])
print(mytable[10])
print(mytable[7])
```

###### __newIndex

当修改表数据的时候，使用的方法。只有原来的键不存在的时候，才会调用改方法，如果原来的key存在，则不会调用。如果不设置的话，会修改原来的表结构，而不会修改元表

```lua
mytable={"Lua","Java","C++"}
mymetatable={
    __newindex=function (tab,key,value)
        print("我们需要修的键是"..key..",对应的值为："..value)
        rawset(tab,key,value)  --需要使用该方法进行更新表数据
    end
}
mytable=setmetatable(mytable,mymetatable)
print(mytable[1])   --输出lua
mytable[1]="C#"    -- 1存在，所以不会调用__newindex方法
mytable[5]="Lua"  
输出=>
Lua
我们需要修的键是5,对应的值为：Lua
```

也可以使用表结构

```lua
mytable={"Lua","Java","C++"}
newtable={}
mymetatable={
    __newindex=newtable   -- 这样当设置不存在的key的数据的时候，会把数据保存到newtable中
}
mytable=setmetatable(mytable,mymetatable)
mytable[1]="C#"
mytable[5]="Lua"  --5这个数据不存在，那么调用newindex方法，会将对应的键值对保存到元表中
print(mytable[1])  -- mytable中存在，可以输出c#
print(mytable[5])  --因为5保存到了newtable中，而且没有使用__index设置。所以mytable[5]其实是不存在的
print(newtable[5])

--> 输出
C#
nil
Lua
```

##### add元方法
可以通过add方法，定义两个表的加法运算
其中两个相加的表设置了add元方法即可。

```lua
mytable={"Lua","C#","PHP"}
mymetatable={
    __add=function(tab,newtab)
        local mi=0
        for k, v in pairs(tab) do
            if k > mi then
                mi=k
            end
        end
        for k, v in pairs(newtab) do
            mi=mi+1
            table.insert(tab,mi,v)
        end
        return tab
    end
}
mytable=setmetatable(mytable,mymetatable)
newtable={"LUA","Python"}
v=mytable+newtable
v2=newtable+mytable
for i, v in pairs(v) do
    print(k,v)
end
```


##### call元方法
当把table作为方法调用的时候 ，使用的方法
##### tostring方法
当print表的时候，回调用string方法。

#### 协同

可以理解为让某个function在半路暂停（挂起），然后在需要的时候继续执行。

```lua
-- 定义协同函数
co=coroutine.create(
    function (a,b)
        print(a+b)
    end
)
-- 启动协同函数
coroutine.resume(co,30,20)
```

#### 面向对象

通过table中的属性和方法来实现面向对象的功能。但是对于具有相同属性和方法的对象，不能直接使用多个table的创建来处理。

##### 封装

这时候可以通过创建构造函数，用于构造拥有相同属性和函数的对象。

```lua
-- 面向对象编程。使用table+function
person={name="person",age=12}
function person:eat()
    print(self.name.."在吃饭")
end

-- 创建一个构造函数

function person:new()
    -- 创建一个新的表。使用local，防止外部访问
    local t={}
    -- 将index设置为self，调用一个属性的时候，如果t中不存在，那么就会在self所制定的table中查找，也就是self
    -- 这里的self就是person表数据。从而能够实现person中的name和age是可以获取到的
    setmetatable(t,{__index=self}) 
    return t
end
-- 创建新对象
person1=person:new()
-- 创建新对象
person2=person:new()
-- __index只影响获取值的时候的处理。当我们设置name的值的时候，会把name放入到t中，而不是元表中。所以不会互相影响
person1.name="person1"
print(person1.name)   -- 打印出来的是“person1”
print(person2.name)   -- 打印出来的仍然是“person”
```

##### 继承实现

```lua
-- 面向对象编程。使用table+function
person={name="person",age=12}
function person:eat()
    print(self.name.."在吃饭")
end

-- 创建一个构造函数

function person:new()
    -- 创建一个新的表。使用local，防止外部访问
    local t={}
    -- 将index设置为self，调用一个属性的时候，如果t中不存在，那么就会在self所制定的table中查找，也就是self
    -- 这里的self就是person表数据。从而能够实现person中的name和age是可以获取到的
    setmetatable(t,{__index=self}) 
    return t
end
-- Student的元表是person。
Student=person.new()
-- 在person基础上扩展了grade属性。
Student.grade=1
-- student里面是没有new方法的，所以会调用元表里面的new方法。
-- 这里调用的new方法，里面使用的self，其实是Student，所以创建的是Student类
-- stu1的元表是Student，Student的元表是person。
stu1=Student:new()
```

#### 垃圾回收

lua采用自动内存管理。lua运行了一个**垃圾收集器**来收集所有死对象来完成自动内存管理工作。

lua实现了增量标记-扫描收集器。 

```lua
mytable={"apple","orange","banana"}
-- 打印当前程序占用的内存空间
print(collectgarbage("count"))
mytable=nil
print(collectgarbage("count"))
-- 强制执行垃圾回收
print(collectgarbage("collect"))
print(collectgarbage("count"))

====>输出
72.47265625
72.5078125
0
45.384765625
```

