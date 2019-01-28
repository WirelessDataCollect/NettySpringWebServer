package com.sorl.backend;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.DistinctIterable;
import com.mongodb.async.client.FindIterable;
import com.sorl.attributes.ChannelAttributes;
import com.sorl.security.Md5;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

/**
* 
* 运行TCP服务器，用于连接上位机
*
* @author  nesc418
* @Date    2018-11-16
* @version 0.2.2
*/
public class RunPcServer implements Runnable{
	private Thread t;
	private String threadName = "PC-Thread";
	private int listenPort = 8080;
	public Channel ch = null;
	
	/**
	 * infoDb 从db中获取信息
	 */
	private static MyMongoDB infoDb;
	/**
	 * ch_map 存储PC连接的通道<PC[num],channel>
	 */
	private volatile static Map<String,ChannelAttributes> ch_map = new ConcurrentHashMap<String,ChannelAttributes>();
	/**
	 * 设置线程名称。bean的set方法，bean会自动调用
	 * 
	 * @param port
	 */
	public void setListenPort(int port) {
		listenPort = port;
		System.out.println("Listen port for PC: "+listenPort);
	}
	/**
	 * 设置线程名称。bean的set方法，bean会自动调用。
	 * 
	 * @param port
	 */	
	public void setThreadName(String name) {
		threadName = name;
	}
	/**
	 * 获取保存同服务器连接的PC的通道
	 * @return {@link Map}
	 */
	public static Map<String,ChannelAttributes> getChMap(){
		return ch_map;
	}
	/**
	 * 返回连接服务器的PC个数
	 * @return int
	 */
	public int getPcNum(){
		return ch_map.size();
	}
	/**
	 * 设置用户信息
	 * @param db 存储用户信息的db
	 */
	public void setInfoDb(MyMongoDB db) {
		infoDb = db;
	}
	/**
	 * 返回保存用户信息的DB
	 * @return {@link MyMongoDB} MongoDB数据库
	 */
	public static MyMongoDB getInfoDb() {
		
		return infoDb;
	}

	/**
	 * 删除某个channel所有信息
	 * @return {@link Map}
	 */
	public static synchronized void delCh(ChannelHandlerContext ctx){
		Map<String, ChannelAttributes> ch = RunPcServer.getChMap(); 
		//从通道的map中删除掉这个通道
		ch.remove(ctx.channel().remoteAddress().toString());
		//关闭该通道,并等待future完毕
		TCP_ServerHandler4PC.ctxCloseFuture(ctx);
	}	
	@Override
	public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();        // 用来接收进来的连接，这个函数可以设置多少个线程
        EventLoopGroup workerGroup = new NioEventLoopGroup();    // 用来处理已经被接收的连接
        
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)            // 这里告诉Channel如何接收新的连接
            .childHandler( new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {//起初ch的pipeline会分配一个RunPcServer的出/入站处理器（初始化完成后删除）
                    // 自定义处理类 
                    ch.pipeline().addLast(new TCP_ServerHandler4PC());//如果需要继续添加与之链接的handler，则再次调用addLast即可
                    //ch.pipeline().addLast(new TCP_ServerHandler4PC());//这样会有两个TCP_ServerHandler4PC处理器
                }//完成初始化后，删除RunPcServer出/入站处理器
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);
           
             
            // 绑定端口，开始接收进来的连接
            ChannelFuture cf = b.bind(listenPort).sync();//在bind后，创建一个ServerChannel，并且该ServerChannel管理了多个子Channel 
            // 等待服务器socket关闭
            ch = cf.channel();
            ch.closeFuture().sync();      
            
        } catch (Exception e) {//线程会将中断interrupt作为一个终止请求
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        } finally {
        	 workerGroup.shutdownGracefully();
             bossGroup.shutdownGracefully();      	
        }
    }
	/**
	 * 开始RunPcServer对象的线程
	 * @return none
	 */
	public void start () {
		System.out.println("Starting " +  threadName );
		if (t == null) {
			t = new Thread (this, threadName);
			t.start ();
		}
	}
	/**
	 * 关闭RunPcServer对象的线程(使用interrup关闭，thread.stop不安全)
	 * @return none
	 */
	public void stop () {
		System.out.println("Stopping " +  threadName );
		//当在一个被阻塞的线程(调用sleep或者wait)上调用interrupt时，阻塞调用将会被InterruptedException异常中断
		t.interrupt();
	}
}
/**
* 
* TCP服务器的输入处理器函数(ChannelHandler)
*
* @author  nesc418
* @Date    2018-10-28
* @version 0.1.1
*/
class TCP_ServerHandler4PC  extends ChannelInboundHandlerAdapter {
	//优先级高
	private final static String MONGODB_FIND_DOCS = "MongoFindDocs";//获取mongodb中的集合名称
	private final static String MONGODB_FIND_DOCS_NAMES = "MongoFindDocsNames";//获取mongodb中的集合名称
	//中等优先级
	private final static String PC_WANT_LOGIN = "Login";//登录指令
	private final static String PC_WANT_GET_RTDATA = "GetRtdata";//获取实时数据，必须先login（进入信任区）
	private final static String PC_STOP_GET_RTDATA = "StopGetRtdata";//停止获取实时数据
	private final static String MONGODB_CREATE_COL = "MongoCreateCol";//创建一个数据集合，每次实验都要创建
	//优先级低
	private final static String PC_WANT_DISCONNECT = "Disconnect";//断开连接
	private final static String HEART_BEAT_SIGNAL = "HeartBeat";//心跳包
	
	//用于分割消息的字符
	private final static String SEG_CMD_INFO = "\\+";//分割命令和信息
	private final static String SEG_INFO1_INFON = ";";//分割多个子信息
	private final static String SEG_KEY_VALUE = ":";//分割key和calue
	private final static String SEG_LOWER_UPPER_BOUND = ",";//分割value的上下界
//	private static Md5 md5 = (Md5) App.getApplicationContext().getBean("md5");
	//给某个命令的返回信息
	private final static String DONE_SIGNAL_OK = "OK";//成功
	private final static String SIGNAL_GET = "GET";//表示收到信息
	private final static String DONE_SIGNAL_OVER = "OVER";//结束，一般用于，数据发送
	private final static String DONE_SIGNAL_ERROR = "ERROR";//失败
	private final static String SEG_CMD_DONE_SIGNAL = SEG_KEY_VALUE;//分割Key:Value,如Login:OK，登录成功。如MongoFindDocs:rllllaw
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
        	//转化为string
        	String message = ((ByteBuf)msg).toString(CharsetUtil.UTF_8);
            //输出信息
            System.out.println("Recv from PC:"+message);
        	String[] splitMsg = message.split(TCP_ServerHandler4PC.SEG_CMD_INFO);//将CMD和info分成两段
        	String cmd = splitMsg[0];
//        	System.out.println("SplitMsg Len: "+String.valueOf(splitMsg.length));//输出获取到的信息长度
        	//判断当前上位机状态（未登录、已登录等）
        	if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.DATA_GET_STA) {//实时接收数据的时候不能进行其他操作
        		switch(cmd) {
        			case TCP_ServerHandler4PC.PC_STOP_GET_RTDATA://降级为登录状态
        				TCP_ServerHandler4PC.writeFlushFuture(ctx, TCP_ServerHandler4PC.PC_STOP_GET_RTDATA+
        						TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);//发送完毕收到一个通知
        				RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setStatus(ChannelAttributes.LOGINED_STA);
        				break;
        			default:
        				break;
        		}
         	}else if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.REQUEST_CONNECT_STA) {//已经登录
        		//TODO 将REQUEST_CONNECT_STA改回来
        		//获取存放测试数据的数据库
        		MyMongoDB mongodb = (MyMongoDB)App.getApplicationContext().getBean("myMongoDB");
                //判断cmd类型
                switch(cmd) {
                	case TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES://获取所有的doc的test名称
	                	DistinctIterable<String> disIter = mongodb.collection.distinct(DataProcessor.MONGODB_KEY_TESTNAME, String.class);
	                	disIter.forEach(new Block<String>() {
	    					@Override
	    					public void apply(String name) {
	    						System.out.printf("\nGet One doc name:%s\n",name);
	    						//TODO 后期考虑是否全部缓存再flush
	    						//放到Netty缓存区中，最后在SingleResultCallback中发送
	    						TCP_ServerHandler4PC.writeFlushFuture(ctx, TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+
	    								TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+name);//发送完毕收到一个通知
//	    						ctx.writeAndFlush(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+":"+name+"\n",CharsetUtil.UTF_8));
	    					}
	                	},  new SingleResultCallback<Void>() {
	    					@Override
	    					public void onResult(Void result, Throwable t) {
	    						TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+
	    								TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);
//	    						ctx.writeAndFlush(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+":"+"Over",CharsetUtil.UTF_8));//发给上位机doc名全部发送完毕
	    						System.out.println(TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);	    						
	    					}	
	                	});
	                	break;
                	case TCP_ServerHandler4PC.MONGODB_FIND_DOCS://获取MongoDB中的文档信息，可以使用filter
                		//TODO  to test
                		if(splitMsg.length>1) {//也就是除了cmd还有其他信息（filter信息）
                			//splitMsg[1]格式    |key:info;key:info;......|
                			String[] filtersStr = splitMsg[1].split(TCP_ServerHandler4PC.SEG_INFO1_INFON);//将信息划分为多个filters
//                			System.out.println("filterStr:"+filtersStr[0]);
                 			//filter
                			BasicDBObject filter = new BasicDBObject();
                 			//缓存filter的上下界
                 			int lowerBound=0;int upperBound=0;
                 			for(String filterStr:filtersStr) {//将过滤信息都put到filter中
                 				String[] oneFilter = filterStr.split(TCP_ServerHandler4PC.SEG_KEY_VALUE,2);//eg.{test:test1_201901251324}

                 				switch(oneFilter[0]) {
    	             				case DataProcessor.MONGODB_KEY_TESTNAME://过滤测试名称，test:xxxxx
    	             					filter.put(oneFilter[0], oneFilter[1]);
    	             					break;
    	             				case DataProcessor.MONGODB_KEY_YYYYMMDD://过滤年月日,yyyy_mm_dd:xxxxxx
    	             					lowerBound = Integer.parseInt((oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND))[0]);//小的日期
    	             					upperBound = Integer.parseInt((oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND))[1]);//大的日期
    	             					filter.put(oneFilter[0], new BasicDBObject("$gte",lowerBound).append("$lte", upperBound));//>=和<=
    	             					break;
    	             				case DataProcessor.MONGODB_KEY_HEADTIME://过滤每一天中的ms,headtime:xxxxxx
    	             					lowerBound= Integer.parseInt((oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND))[0]);//小的时间/ms
    	             					upperBound = Integer.parseInt((oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND))[1]);//大的时间/ms
    	             					filter.put(oneFilter[0], new BasicDBObject("$gte",lowerBound).append("$lte", upperBound));//>=和<=
    	             					break;
    	             				default:
    	             					break;
    	             				}//end of case
                 			}//end of for
                 			FindIterable<Document> docIter = mongodb.collection.find(filter) ;
                 			docIter.forEach(new Block<Document>() {
    						    @Override
    						    public void apply(final Document document) {//每个doc所做的操作
//    						    	ctx.writeAndFlush(Unpooled.copiedBuffer((String)document.get("raw_data"),CharsetUtil.UTF_8));//发给上位机原始数据
    						    	ctx.write(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS+":",CharsetUtil.UTF_8));//加入抬头
//    						    	TCP_ServerHandler4PC.writeFlushFuture(ctx,(ByteBuf)document.get(DataProcessor.MONGODB_KEY_RAW_DATA));//发给上位机原始数据
    						    	TCP_ServerHandler4PC.writeFlushFuture(ctx,(String)document.get(DataProcessor.MONGODB_KEY_RAW_DATA));
    						    }}, new SingleResultCallback<Void>() {//所有操作完成后的工作
    						        @Override
    						        public void onResult(final Void result, final Throwable t) {
    						        	//TODO
    						        	TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.MONGODB_FIND_DOCS+
    						        			TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);
//    						        	ctx.writeAndFlush(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS+":"+"Over",CharsetUtil.UTF_8));//发给上位机原始数据
    						            System.out.println(TCP_ServerHandler4PC.MONGODB_FIND_DOCS+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);
    						        }			    	
    						    });
                		}else{//无过滤信息，即把所有到的col全部输出
                			TCP_ServerHandler4PC.writeFlushFuture(ctx,"Please input filter info");
//                			ctx.writeAndFlush(Unpooled.copiedBuffer("Please input filter info",CharsetUtil.UTF_8));//请输入查询过滤器信息
                		}  
                		break;
                	case TCP_ServerHandler4PC.MONGODB_CREATE_COL://创建collection
                		//TODO
                		break;
                	case TCP_ServerHandler4PC.PC_WANT_GET_RTDATA://修改位GetRtData的状态
                		RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setStatus(ChannelAttributes.DATA_GET_STA);
                		TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_GET_RTDATA+
                				TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
                		break;
                	default:
                		break;
                }  
        	}else if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.REQUEST_CONNECT_STA) {//连接但还未登录
        		switch(cmd) {
	            	case TCP_ServerHandler4PC.PC_WANT_LOGIN://PC想要登录
	            		String info = splitMsg[1];
	            		//当前状态时请求连接状态而且用户名和密码匹配成功
	            		loginMd5(ctx,info);
	                    break;
	            	default:
	            		break;
        		}
        	}//end of if elif
        	//不管登录与否，都要处理的命令
        	switch(cmd) {
	        	case TCP_ServerHandler4PC.HEART_BEAT_SIGNAL://心跳包
	        		//TODO 每次更新心跳包的时间，过一段时间检查是否超过时间
	        		TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.HEART_BEAT_SIGNAL+
	        				TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.SIGNAL_GET);
	        		break;
	        	case TCP_ServerHandler4PC.PC_WANT_DISCONNECT://上位机想要断开连接
	        		TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_DISCONNECT+
	        				TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
	        		RunPcServer.delCh(ctx);
	        		break;
	        	default:
	        		break;
        	}
        } 
        catch(Exception e) {
        	e.printStackTrace();
        }
        finally {
            // 抛弃收到的数据
            ReferenceCountUtil.release(msg);//如果不是继承的SimpleChannel...则需要自行释放msg
        }
    }//end of channelRead
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	System.out.println("PC "+ctx.channel().remoteAddress()+" connected!");
    	//通道数太多了
    	if(RunPcServer.getChMap().size()>ChannelAttributes.MAX_CHANNEL_NUM) {
    		TCP_ServerHandler4PC.ctxCloseFuture(ctx);
			return;
    	}
    	//加入该通道
    	RunPcServer.getChMap().put(ctx.channel().remoteAddress().toString(), new ChannelAttributes(ctx));
    	String salt = RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getSalt();
    	TCP_ServerHandler4PC.writeFlushFuture(ctx,"RandStr"+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+salt);
//    	ctx.writeAndFlush(Unpooled.copiedBuffer("RandStr"+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+salt,CharsetUtil.UTF_8));//发送salt
    	System.out.println("RandStr"+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+salt);//打印salt
    	//发送RSA算法的n和e
//    	ctx.writeAndFlush(Unpooled.copiedBuffer("Private key (n,e) = ("
//    			+RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getEncryption().getPublicE().toString()+","
//    			+RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getEncryption().getPublicN().toString()+")"
//    			+"\n", CharsetUtil.UTF_8));
        ctx.fireChannelActive();
    }//end of channelActive
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		RunPcServer.delCh(ctx);
		System.out.println("PC "+ctx.channel().remoteAddress().toString()+" disconnected!");
		ctx.fireChannelInactive();
	}
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 当出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }
	/**
	 * 登录管理员Md5加密
	 * 
	 * 验证通过则将该上位机放到新人驱，如果没有通过则直接断开。
     * info
	 * |---------;---------|
	 *    user     keyHash
	 * @param msg:user;keyHash
	 * @return null
	 */
	private void loginMd5(ChannelHandlerContext ctx,String info) {
		//获取该通道盐值
		String salt = RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getSalt();

		String[] splitInfo = info.split(TCP_ServerHandler4PC.SEG_INFO1_INFON);
		String userStr = splitInfo[0];
//		System.out.println("User Name from pc:"+userStr);//显示从pc获取到的用户名
		String keyHashStr = splitInfo[1];//md5(md5(key)+salt)
		//解析加密数值
		try {
			//BasicDBObject时Bson的实现
			BasicDBObject filter = new BasicDBObject();
			filter.put("user", userStr);
			FindIterable<Document> docIter = RunPcServer.getInfoDb().collection.find(filter) ;
			//forEach：异步操作
			docIter.forEach(new Block<Document>() {
			    @Override
			    public void apply(final Document document) {//每个doc所做的操作
			    	
			    	//先获取db中user的key
			    	String key = (String) document.get("key");//获取密码
			    	System.out.println("\nKey in DB: "+key);//查看查询到的密码明文
			    	//计算得到keyHash
			    	String keyHashStrLocal = Md5.getKeySaltHash(key, salt);
			    	//打印出收到的keyHash和本地计算出来的keyHash
//			    	System.out.println("Key Hash Remot:"+keyHashStr);
//			    	System.out.println("Key Hash Local:"+keyHashStrLocal);
			    	if(keyHashStrLocal.equals(keyHashStr)){
			    		System.out.println("Key Correct!");
                    	//如果当前状态是请求连接状态，才可以进行下一步
                    	if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.REQUEST_CONNECT_STA) {
                        	//设置该通道为信任
                    		RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setStatus(ChannelAttributes.LOGINED_STA);
                    	}
                    	//如果当前已经登录了
                    	else {
                    		//do nothing!
                    	}
			    	}
			    	else {
			    		System.out.println("Key Incorrect!");
			    	}
			    }}, new SingleResultCallback<Void>() {//所有操作完成后的工作
			        @Override
			        public void onResult(final Void result, final Throwable t) {
			        	//查询操作结束后，查看是否登录成功
	                    if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.LOGINED_STA) {//登录成功
	                    	//返回登录信息
	                    	TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_LOGIN+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
//	                    	ctx.writeAndFlush(Unpooled.copiedBuffer("Login"+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK,CharsetUtil.UTF_8));
	                    }
	                    else {//登录失败，已经断开了
	                    	TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_LOGIN+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_ERROR);
	                    	//删除这个通道
	                    	RunPcServer.delCh(ctx);   	
	                    }
			            System.out.println("Mongo Operate Finished!");
			        }			    	
			    });
		}catch(Exception e) {
			e.printStackTrace();
		}

	}
//	/**
//	 * 登录管理员，命令"login"
//	 * @param msg 登录信息(用户名+密码)，形式(逗号用于隔开同一个信息的加密数值，分号隔开不同信息)：
//	 * "第一个字符加密数值,第二个字符加密数值,...;第一个字符加密数值,第二个字符加密数值"
//	 * @return false：登录失败；true：登录成功
//	 */
//	private boolean loginRsa(ChannelHandlerContext ctx,String msg) {
//		//解析加密数值
//		try {
//			//转化为字符串
//			String[] info_str = msg.split(";");
//			
//			//提取管理员名称和密码，加密数值的字符串形式
//			String[] name_str = info_str[0].split(",");
//			String[] key_str = info_str[1].split(",");
////			System.out.println("name:"+name_str[0]);
////			System.out.println("key:"+key_str[0]);
//			//创建用于保存加密数值的BigInteger数组
//			char[] name_decoded = new char[name_str.length];
//			char[] key_decoded = new char[key_str.length];
//			int idx=0;
//			for(String n_str : name_str) {
//				name_decoded[idx] = (char)(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString())
//						.getEncryption().getDencryptedVal(new BigInteger(n_str)).intValueExact());//如果BigInteger输出超出了char则会抛出异常
//				idx++;
//			}
//			idx=0;
//			for(String k_str : key_str) {
//				key_decoded[idx] = (char)(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString())
//						.getEncryption().getDencryptedVal(new BigInteger(k_str)).intValueExact());
//				idx++;
//			}
//			String name = new String(name_decoded);
//			String key = new String(key_decoded);
//			//			//显示解码后的字符
////			System.out.println("Decoded--->");
////			System.out.println("name:"+name);
////			//显示解码后的字符
////			System.out.println("key:"+key);
//			//BasicDBObject时Bson的实现
//			BasicDBObject filter = new BasicDBObject();
//			filter.put("name", name);
//			filter.put("key", key);
//			if(RunPcServer.getInfoDb().count(filter)>0) {
//				System.out.println("Name-Key Matched!!");
//				return true;
//			}
//		}catch(Exception e) {
//			e.printStackTrace();
//		}
//		return false;	
//	}
    /**
     * 发送信息，会受到成功的信息，进而处理（不会阻塞等待）
     * @param ctx 通道ctx
     * @param msg 要发送的String信息
     */
    public static void writeFlushFuture(ChannelHandlerContext ctx,String msg) {
    	ChannelFuture future = ctx.writeAndFlush(Unpooled.copiedBuffer(msg,CharsetUtil.UTF_8));
    	//等待发送完毕
    	future.addListener(new ChannelFutureListener(){
			@Override
			public void operationComplete(ChannelFuture f) {
				if(!f.isSuccess()) {
					f.cause().printStackTrace();
				}
			}
		});
    }
    /**
     * 发送信息并等待成功
     * @param ctx 通道ctx
     * @param msg 要发送的ByteBuf信息
     */
    public static void writeFlushFuture(ChannelHandlerContext ctx,ByteBuf msg) {
    	ChannelFuture future = ctx.writeAndFlush(msg);
    	//发送完毕会返回一个信息
    	future.addListener(new ChannelFutureListener(){
			@Override
			public void operationComplete(ChannelFuture f) {
				if(!f.isSuccess()) {
					f.cause().printStackTrace();
				}
			}
		});
    }
    public static void ctxCloseFuture(ChannelHandlerContext ctx) {
		//关闭该通道
		ChannelFuture future = ctx.close();
    	future.addListener(new ChannelFutureListener(){
			@Override
			public void operationComplete(ChannelFuture f) {
				if(!f.isSuccess()) {
					f.cause().printStackTrace();
				}
			}
		});
    }
    /**
     * 获取ctx的远程地址字符串形式
     * @param ctx
     * @return
     */
    public static String getCtxRmAddrStr(ChannelHandlerContext ctx) {
    	return ctx.channel().remoteAddress().toString();
    }

}

