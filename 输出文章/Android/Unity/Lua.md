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

当修改表数据的时候，使用的方法。只有原来的键不存在的时候，才会调用改方法，如果原来的key存在，则不会调用

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
    __newindex=newtable
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