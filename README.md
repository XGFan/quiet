Quiet 是一个简单，轻巧，快速的博客/Wiki程序。

+ 提前加载目录下所有markdown文件
+ 文件发生修改或者变动，自动更新
+ 无需数据库


#### 安装

编译打包

```shell
mvn clean package
```

把`target\quiet-xxx-jar-with-dependencies.jar` 复制到任意目录。

新建一个`quiet.properties`，填入`siteName`和`siteUrl`以及端口。

```shell
java -jar quiet-xxxx.jar
```

就会根据同一层级下的`markdown`文件夹内的内容生成一个网站了。

Done！


##### 演示站点
https://xulog.com/


#### 计划

+ 增加一款**简单**主题
+ 增加生成静态网站的功能


----

由于是前端弱渣，主题修(luan)改(gai)了 [Trii Hsia](https://github.com/txperl) 的 Moricolor ，在此表示感谢。

