# NettySpringWebServer
## 预期目标
服务器主要包括四个方面的功能

1.从设备端接收原始数据并进行解析

2.将解析后的数据转发给PC上位机

3.将解析后的数据存储在服务器的MongoDB数据库中

4.通过web查看数据的情况

## 运行流程
### 上位机和服务器的连接
1、上位机连接服务器

上位机加入服务器channel map，并设置为请求连接状态

2、服务器产生RSA算法密钥，并发送公钥(n,e)给上位机

3、上位机加密管理员账户和密码

以ASCII码形式转换

如用户名：A(65)B(66)，则将'A'以数值65加密(RSA公式：f(C) = C\^e &nbsp mod &nbsp N),B以数值66加密，加密后为f('A'),f('B')

数值f('A')和f('B')均转化为String——SA和SB再发送。

用户名和密码(假设也为SA和SB)发送形式："SA,Sb;SA,Sb"。用户名和密钥用分号";"分开，用户名和密钥内部数值用逗号","分开。

4、服务器接收来自上位机的用户名和密钥

5、服务器使用私钥解密

6、服务器提取数据库中的用户名和密钥和上位机发送过来的用户和密钥对比。

7、如果对比成功，则将该channel加入到信任区

如果对比失败，则将该通道删除

### 正式测试流程

注：还没有做多地同时测量

1、上位机已经进入服务器的信任区(### 上位机和服务器的连接)

2、上位机发送本次测试地点名称（如果不设置测试地点，则认为需求只有远程读取mongodb数据）

3、服务器注册该地点，其他测试地点不能再使用这个名称

4、上位机设置本次测试名称+时间戳到服务器

5、上位机启动设备（包括发送测试地点名称）

设备帧头需要发送地点名称

### 服务器启动运行
1、启动初始化

2、连接测试地点的上位机

3、验证管理员和密码，并用RSA加密

验证通过

4、上位机发送过来测试地点，服务器查看测试地点是否存在

如果存在，回复已存在的命令

如果不存在，则加入该测试地点

5、测试开始后，服务器需要分析数据的测试地点

将数据存放在测试地点+测试名称+时间戳作为集合col的名称

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
服务器主要包括三个方面的功能——从设备端接收原始数据并进行解析；将解析后的数据转发给PC上位机；将解析后的数据存储在服务器的MongoDB数据库中。

本服务器基于Netty和Spring框架。

* 数据接收

设备通过TCP或者UDP（可手动设置）连接服务器，并传输数据到服务器。

* 数据转发

PC上位机通过TCP连接服务器8081端口，实施接受经过服务器转发的设备采集数据。

* 数据存储

服务器接受设备数据，并存储在MongoDB数据库中。

存储格式：

```Json
{
    "_id" : ObjectId("5b90c70aa7986c262ff73c34"),
    "wifi_client_id" : 48,
    "yyyy_mm_dd" : NumberLong(842084913),
    "headtime" : NumberLong(842084913),
    "adc_count_short" : NumberLong(2),
    "io1" : 48,
    "io2" : 48,
    "adc_val" : {
        "ch1" : [
            787,
            771
        ],
        "ch2" : [
            787,
            771
        ],
        "ch3" : [
            787,
            803
        ],
        "ch4" : [
            771,
            803
        ]
    }
}
```

# 上位机和服务器的交互
|上位机命令|信息|服务器返回|结束|
|-|-|-|-|
|MongoFindDocsNames|none|MongoFindDocsNames:xxx|MongoFindDocsNames:Over|
|-|-|-|-|
|MongoFindDocs|none|MongoFindDocsNames:xxx|MongoFindDocsNames:Over|

```
eg.查询测试名称：test1_20190121，从日期8245810到8245820的数据
MongoFindDocs+test:test1_20190121;yyyy_mm_dd:8245810,8245820

eg.查询测试名称：test1_20190121，从那一天的8245840到8245840的数据（即==8245840）
MongoFindDocs+test:test1_20190121;headtime:8245840,8245840
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


