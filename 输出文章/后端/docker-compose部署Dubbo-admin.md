##  docker-compose部署Dubbo-admin

在我们之前的环境中，已经部署过一段Zookeeper的集群了，以后想玩玩Dubbo的，现在在云服务上部署一套dubbo-admin

1. 打包dubbo-admin

   照原来的处理，所有的文件下载都是的/usr/local中进行处理

   ```
   cd /usr/local;
   git clone -b master https://github.com/apache/incubator-dubbo-ops.git
   mvn clean mvn clean package -Dmaven.test.skip=true
   ```

   

2. 制作dubbo-admin镜像文件

   在dubbo-admin下面增加Dockerfile文件，内容如下

   ```
   FROM openjdk:8-jdk-alpine
   MAINTAINER yangxianyu
   VOLUME /tmp
   ADD ./target/dubbo-admin-0.0.1-SNAPSHOT.jar app.jar
   ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
   ```

   在Dockerfile文件所在的目录下执行如下代码

   ```
   docker build -t dubbo-admin:1.0 .
   ```

   执行完毕以后，可以看到生成了一个名叫 **dubbo-admin-0.0.1-SNAPSHOT.jar** 的文件。

3. 编写docker-compose 文件

   回到 /user/local目录下，编写docker-compose文件

   ```
   version: '3'
   services:
     dubbo-admin:
       image: dubbo-admin:1.0
       container_name: dubbo-admin
       ports:
         - 7001:7001
       restart: always
   ```

   最后执行

   ```
   docker-compose up -d
   ```

   程序执行起来了。。

   现在我们访问 **http://IP:7001/** ，输入账号：root  密 码：root。就可以看到我们的监控页面了。