## Redis 实战の投票方案

该文章仅用于记录Reids实战中的文章投票方案记录：

需求如下：

1. 发布文章以后，用户能够对文章进行投票操作，仅允许发布一个星期以内进行投票
2. 如果投票超过200张，就算作推荐阅读的文章
3. 要将推荐阅读的文章放到文章列表的前面
4. 文章列表每页只展示100条

设计方法如下：

1.3种表，

	> 一个存储文章的相关信息,使用Hash（每个文章都会有一个表）  key:article:id     value:hash
	>
	> 一个文章投票人的记录表，使用set（每个文章都会有一个表）    key:voted:id      value:  user:userId
	>
	> 一个存储文章的发布时间，使用zset     key:time             value:文章ID->文章发布时间
	>
	> 一个存储文章的投票分数，使用szet     key:score             value:文章ID->文章评分

![image-20200301082123041](http://cdn.qiniu.kailaisii.com/typora/20200301082157-779004.png)![image-20200301082202777](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200301082202777.png)

设计方案：

1. 每点一个投票，判断发布时间是否在一周之内，如果不在，则返回
2. 如果在一周之内，需要更新文章信息里面的投票数，更新投票分数表，更新投票人记录表