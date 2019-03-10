# NettySpringWebServer
## 预期目标
服务器主要包括四个方面的功能

1.从设备端接收原始数据并进行解析

2.将原数据直接（命令驱动）转发给PC上位机

3.将解析后的数据存储在服务器的MongoDB数据库中

4.上位机连接服务器，通过命令调取测试名称、测试数据等

*5.通过web查看某些特性*

## 运行流程
### 上位机和服务器的连接
1、上位机连接服务器

上位机加入服务器channel map，并设置为请求连接状态

2、服务器单独为每个通道（即上位机）生成一个盐值（salt）

发送给上位机，数据格式为RandStr:具体的盐值，即```RandStr:j7sjbwwDxzoCYybveyk6```

3、上位机加密管理员账户和密码

用户密码(UTF-8字符串)  \-\-\-MD5\-\-\-\>  密码密文结合salt  \-\-\-MD5\-\-\-\>  最终密文

以Login指令登录，具体见命令表。

4、服务器接收来自上位机的用户名和密码密文

5、服务器从数据库中获取相应用户的密码

*后期考虑是否使用密文存储密码*

具体密文存储方案：每个密码都结合一个固定字符串（服务器和上位机相同）后使用MD5计算出密文。

6、服务器提取数据库中的用户名和密码，根据生成的盐值计算出最终密文，和上位机发送过来的最终密文对比。

7、如果对比成功，则将该channel加入到信任区

如果对比失败，则将该通道删除

### 上位机请求实时数据
1、上位机发送请求命令（前提是上位机已经处于信任区）

2、服务器接收到命令后判断是否信任，是则加入“实时数据请求区”

不信任则忽略

### 上位机退出“实时数据请求区”

1、上位机发送请求命令（前提是上位机已经处于“实时数据请求区”）

2、服务器接收到命令后判断是否“实时数据请求区”，是则退出“实时数据请求区”

不信任则忽略

## 介绍
**已实现**

### 前端
* 用户登录

### 后端
服务器主要包括三个方面的功能——从设备端接收原始数据并进行解析；将（实时或者历史）原数据转发给PC上位机；将解析后的数据和原数据存储在服务器的MongoDB数据库中。

本服务器基于Netty和Spring框架。

* 数据接收

设备通过TCP或者UDP（可手动设置）连接服务器，并传输数据到服务器。

* 数据转发

PC上位机通过TCP连接服务器8089端口，实施接受经过服务器转发的设备采集数据。

* 数据存储

服务器接受设备数据，并存储在MongoDB数据库中。

单个数据包大小：<1KB；每秒钟14-15个包

存储格式：

```Json
{
	"_id" : ObjectId("5c831c7503047748eb06632b"),
	"nodeId" : 3,
	"yyyy_mm_dd" : NumberLong(0),
	"headtime" : NumberLong(1298),
	"adc_count_short" : NumberLong(67),
	"io1" : 0,
	"io2" : 0,
	"test" : "Default Name",
	"adc_val" : {
		"ch1" : [
			6297,
			6293,
			...
		],
		"ch2" : [
			2,
			2,
			...
		],
		"ch3" : [
			2051,
			2052,
			...
		],
		"ch4" : [
			11507,
			11507,
			...
		]
	},
	"raw_data" : BinData(0,"AAAAABIFAAAYAgAAAwAAEkRlZmF1bHQgTmFtZQAAAAAAAAAAAAAAAAAAAAAAAAAAGJkAAggDLPMYlQACCAQs8xiUAAEIBizzGJcAAggHLPMYmQACCAos8xiXAAIIDizzGJYAAggQLPMYmQACCBQs9BiXAAIIFyz0GJgAAggZLPMYlQACCBos8xiYAAIIGizzGJcAAggYLPMYlgACCBUs8xiZAAIIEyzzGJYAAggPLPMYlQACCAws8xiYAAIICCzzGJgAAggGLPMYlQABCAQs8xiJAAIIAiz0GJYAAggDLPMYmQACCAUs9BiTAAIICCzzGJkAAggKLPMYmAACCA8s8xibAAIIESzzGJcAAggVLPMYlQACCBcs8xicAAIIGSzzGJcAAggaLPMYkgACCBos8xiZAAIIFyz0GJYAAggWLPMYlgACCBMs8xiYAAIIDyzzGJYAAggMLPMYkgACCAos8xiTAAIIBizzGJEAAggELPQYlwACCAMs8xiVAAIIAyz0GJUAAggFLPMYlQABCAgs8xiTAAIICSzyGJQAAggOLPQYkgACCBEs8xiUAAIIFCzyGJUAAggYLPMYlwABCBks8hiTAAIIGizzGJEAAggaLPMYlQACCBgs9BiRAAIIFSzzGJIAAggTLPMYkwACCA8s8xiYAAIIDSz0GJQAAggJLPMYlAABCAcs8xiTAAIIBCzzGJUAAQgDLPQYlwABCAMs9BiUAAIIBSzzGJYAAggILPMYlQACCAos8xiXAAEIDiz0GJYAAggRLPM=")
}

```

# 上位机和服务器的交互
### 信息中不可包含的字符
"+"：用于分割命令和信息，CMD+INFO

";"：用于分割INFO，将INFO(info1;info2;...)分割为多个子info

":"：用于分割子info的key:value（或者大小关系）

","：用于分割子info中的value(多用于分割上下界，如year:2019,2033，表示年份从2019到2033)

"\\n"：一个包的结尾，用于解析TCP包粘包问题。

```
MongoFindDocs+test:test1_20190121;headtime:8245840,8245840
```

**备注：**

在java中需要转义的几个字符：

```
( [ { \ ^ - $ ** } ] ) ? * + .
```
### 数据库查询
|上位机命令|信息|服务器返回|结束|说明|
|-|-|-|-|-|
|MongoFindDocsNames|key1:value1;key2:value2;...|MongoFindDocsNames:xxx\\n|MongoFindDocsNames:OVER\\n|查询所有的doc名称|
|MongoFindDocs|key1:value1;key2:value2;...|MongoFindDocs:xxx|MongoFindDocs:OVER\\n|根据条件查询doc，并发送给上位机|

```
eg.查询所有的doc名称
MongoFindDocsNames

eg.查询"yyyy_mm_dd == 8245840"的数据实验名称（test）
MongoFindDocsNames+yyyy_mm_dd:8245840

其他参数类似，可叠加
```

```
eg.获取所有的doc
MongoFindDocs

eg.获取"测试名称：test1_20190121"，"从日期8245810到8245820的数据"
MongoFindDocs+test:test1_20190121;yyyy_mm_dd:8245810,8245820

eg.获取"yyyy_mm_dd == 8245840"的数据
MongoFindDocs+yyyy_mm_dd:8245840

eg.获取"测试名称：test1_20190121"，"从那一天的8245840的数据"
MongoFindDocs+test:test1_20190121;headtime:8245840

其他参数类似，可叠加

```

### 指令
|上位机命令|信息|服务器返回|说明|
|-|-|-|-|
|Login|登录用户名;MD5加密数据|Login:OK\\n|登录用户|
|GetRtdata|none|GetRtdata:OK\\n|获取实时数据|
|StopGetRtdata|none|StopGetRtdata:OK\\n|停止获取实时数据|
|HeartBeat|none|HeartBeat:GET\\n|心跳包|
|Disconnect|none|Disconnect:OK\\n|断开连接|

**说明**

* 关于登录(Login)

```Login+登录用户名;MD5加密数据``` 的MD5加密数据是经过两次MD5加密的。

用户密码(UTF-8字符串)  \-\-\-MD5\-\-\-\>  密码密文结合salt  \-\-\-MD5\-\-\-\>  最终密文

# 数据库操作

快速添加一个数据

```
eg.
db.data.insert({test:"test1_20190121",wifi_client_id:0,yyyy_mm_dd:8245810,headtime:8245850,adc_count_short:2,io1:0,io2:0,adc_val:{ch1:[2676,2726],ch2:[2676,2726],ch3:[2676,2726],ch4:[2676,2726]},raw_data:"rllllaw datasslklks"})
```

# 参考

[Netty实战精髓-w3cSchool](https://www.w3cschool.cn/essential_netty_in_action/ "Netty实战精髓-w3cSchool")

[Netty实战-何平译](https://book.douban.com/subject/27038538/ "Netty实战-何平译")

[对Netty组件的理解](http://neyzoter.cn/2018/09/07/Netty-EventLoopGroup-EventLoop-Channel-Channle-ChannlePipeline-et/ "对Netty组件的理解（Channel、Pipeline、EventLoop等）")

[Netty笔记](http://neyzoter.cn/wiki/Netty/ "Netty笔记")

[Java菜鸟教程](http://www.runoob.com/java/java-tutorial.html "Java菜鸟教程")

[Java笔记](http://neyzoter.cn/wiki/Java/ "Java笔记")

[Maven笔记](http://neyzoter.cn/wiki/MAVEN/ "Maven笔记")

[MongoDB菜鸟教程](http://www.runoob.com/mongodb/mongodb-tutorial.html "MongoDB菜鸟教程")

[MongoDB笔记](http://neyzoter.cn/wiki/MongoDB/ "MongoDB笔记")

[Spring笔记](http://neyzoter.cn/wiki/Spring/ "Spring笔记")


