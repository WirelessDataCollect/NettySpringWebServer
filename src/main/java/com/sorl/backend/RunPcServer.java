package com.sorl.backend;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.Document;
import org.bson.types.*;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.client.result.DeleteResult;
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
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import com.sorl.attributes.InfoMgdAttributes;
import org.apache.log4j.Logger;
/**
* 
* 运行TCP服务器，用于连接上位机
*
* @author  nesc420
* @Date    2018-11-16
* @version 0.2.2
*/
public class RunPcServer implements Runnable{
	private Thread t;
	private String threadName = "PC-Thread";
	private int listenPort = 8080;
	public Channel ch = null;
	public final static int MAX_CHANNEL_NUM = 50;	
	private static final Logger logger = Logger.getLogger(RunPcServer.class);
	//分割报文
	private static final String MsgSPL = "\t";
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
		logger.info(String.format("Listen port for PC: %d", listenPort));
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
	public static synchronized Map<String,ChannelAttributes> getChMap(){
		return ch_map;
	}
	/**
	 * 返回连接服务器的PC个数
	 * @return int
	 */
	public synchronized int  getPcNum(){
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
		try {
			Map<String, ChannelAttributes> ch = RunPcServer.getChMap();
			//从通道的map中删除掉这个通道
			ch.remove(ctx.channel().remoteAddress().toString());
			//关闭该通道,并等待future完毕
			TCP_ServerHandler4PC.ctxCloseFuture(ctx);			
		}catch(Exception e) {
			logger.error("",e);
		}

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
                    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(64 * 1024, Unpooled.copiedBuffer(MsgSPL.getBytes())))//换行解码器
                    .addLast(new TCP_ServerHandler4PC());//如果需要继续添加与之链接的handler，则再次调用addLast即可
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
        	logger.info("",e);
        } finally {
        	 workerGroup.shutdownGracefully().awaitUninterruptibly();
             bossGroup.shutdownGracefully().awaitUninterruptibly();      	
        }
    }
	/**
	 * 开始RunPcServer对象的线程
	 * @return none
	 */
	public void start () {
		logger.info("Starting " +  threadName);
		if (t == null) {
			//运行(this)这个runnable接口下的对象
			t = new Thread (this, threadName);
			t.start ();
		}
	}
	/**
	 * 关闭RunPcServer对象的线程(使用interrup关闭，thread.stop不安全)
	 * @return none
	 */
	public void stop () {
		logger.info("Stopping " +  threadName);
		//当在一个被阻塞的线程(调用sleep或者wait)上调用interrupt时，阻塞调用将会被InterruptedException异常中断
		t.interrupt();
	}
}
/**
* 
* TCP服务器的输入处理器函数(ChannelHandler)
*
* @author  nesc420
* @Date    2018-10-28
* @version 0.1.1
*/
class TCP_ServerHandler4PC  extends ChannelInboundHandlerAdapter {
	//优先级高
	private final static String PC_START_ONE_TEST = "StartTest";
	private final static String MONGODB_FIND_DOCS = "MongoFindDocs";//获取mongodb中的集合名称
	private final static String MONGODB_FIND_DOCS_NAMES = "MongoFindDocsNames";//获取mongodb中的集合名称
	//中等优先级
	private final static String PC_WANT_LOGIN = "Login";//登录指令
	private final static String PC_WANT_GET_TEST_CONFIG = "GetTestConfig";//获取测试配置文件
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
	private final static String SEG_LIST_BOUND = ",";//分割value的列表，如dataType:CAN,ADC
	private final static String SEG_TOW_PACK = "\n";
//	private static Md5 md5 = (Md5) App.getApplicationContext().getBean("md5");
	//给某个命令的返回信息
	private final static String DONE_SIGNAL_OK = "OK";//成功
	private final static String DONE_SIGNAL_OVER = "OVER";//结束，一般用于，数据发送
	private final static String DONE_SIGNAL_ERROR = "ERROR";//失败
	private final static String SEG_CMD_DONE_SIGNAL = SEG_KEY_VALUE;//分割Key:Value,如Login:OK，登录成功。如MongoFindDocs:rllllaw
	//存储测试配置信息
	public static MyMongoDB testInfoMongdb = (MyMongoDB)App.getApplicationContext().getBean("testConfMongoDB");
	//数据类型
	public final static String TESTINFOMONGODB_KEY_TESTNAME = DataProcessor.MONGODB_KEY_TESTNAME;
	public final static String TESTINFOMONGODB_KEY_ISODATE = "isodate";
	public final static String TESTINFOMONGODB_KEY_INSERT_ISO_DATE = DataProcessor.MONGODB_KEY_INSERT_ISO_DATE;
	public final static String TESTINFOMONGODB_KEY_TESTCONF = "config";
	public final static String TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA = "dataCol";
	
	private static final Logger logger = Logger.getLogger(TCP_ServerHandler4PC.class);
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
        	BasicDBObject projections = null;
        	//转化为string
        	String message = ((ByteBuf)msg).toString(CharsetUtil.UTF_8);
        	String[] splitMsg = message.split(TCP_ServerHandler4PC.SEG_CMD_INFO,2);//将CMD和info分成两段
        	String cmd = splitMsg[0];
        	logger.info("Got Cmd : "+ message);
        	logger.debug("Msg Len: "+String.valueOf(splitMsg.length));
        	//判断当前上位机状态（未登录、已登录等）
        	if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus().equals(ChannelAttributes.DATA_GET_STA)) {//实时接收数据的时候不能进行其他操作
        		switch(cmd) {
        			case TCP_ServerHandler4PC.PC_STOP_GET_RTDATA://降级为登录状态
        				TCP_ServerHandler4PC.writeFlushFuture(ctx, TCP_ServerHandler4PC.PC_STOP_GET_RTDATA+
        						TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);//发送完毕收到一个通知
        				RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setTestName("");
        				RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setStatus(ChannelAttributes.LOGINED_STA);
        				break;
        			default:
        				break;
        		}
         	}else if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus().equals(ChannelAttributes.LOGINED_STA)) {//已经登录REQUEST_CONNECT_STA) {LOGINED_STA
        		//TODO 将REQUEST_CONNECT_STA改回来
        		//获取存放测试数据的数据库
        		MyMongoDB mongodb = (MyMongoDB)App.getApplicationContext().getBean("myMongoDB");
                //判断cmd类型
                switch(cmd) {
                	case TCP_ServerHandler4PC.PC_START_ONE_TEST:
                		BasicDBObject filterName = new BasicDBObject();
                		if(splitMsg.length>1) {//也就是除了cmd还有其他信息（filter信息）
                			//将信息划分为多个filters，实验名称;配置文件长度;配置文件
                			String[] infoStr = splitMsg[1].split(TCP_ServerHandler4PC.SEG_INFO1_INFON,3);
                			if(infoStr.length < 3) {
                				break;
                			}
                			try {
                				String testName = infoStr[0];
                				String isoDate  = (testName.split("/", 2))[1];
                				int configFileLen = Integer.parseInt(infoStr[1]);
                    			String testConfigFile = infoStr[2];
                    			//长度不正确
                    			if(configFileLen != testConfigFile.length()) {
                    				logger.warn(String.format("Test(\"%s\")'s Config File Len Error: Abandoned", testName));
                    				TCP_ServerHandler4PC.writeFlushFuture(ctx, TCP_ServerHandler4PC.PC_START_ONE_TEST+
    	    								TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_ERROR);
                    				break;
                    			}else {
	                    			filterName.put(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME, testName);
	                    			//删掉重复的（之前已经有的）
	                    			TCP_ServerHandler4PC.testInfoMongdb.collection.deleteMany(filterName, new SingleResultCallback<DeleteResult>() {
	                    				@Override
										public void onResult(final DeleteResult result, final Throwable t) {
	                    					logger.info(String.format("Find Existed Test(\"%s\")'s Config File : Deleted\r\n", testName));
	                    				}
	                    			});
	                    			//给该PC设置测试名称
	                    			RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setTestName(testName);
	                    			
	                    			Document doc = new Document(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME,testName)
	                    					.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_ISODATE, isoDate)
	                    					//当前插入的系统时间
	                    					.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_INSERT_ISO_DATE, TimeUtils.getStrIsoSTime())
	                    					//当前插入的月份，指向一个数据存储集合
	                    					.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA, TimeUtils.getStrIsoMTime())
	                    					.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTCONF, testConfigFile);
	                    			TCP_ServerHandler4PC.testInfoMongdb.insertOne(doc, new SingleResultCallback<Void>() {
	                					@Override
	                				    public void onResult(final Void result, final Throwable t) {
	                						logger.info(String.format("Test(\"%s\") Config File Saved", testName));
	                						logger.info(String.format("-Config File Detail : \r\n  %s", testConfigFile));
	                				    	TCP_ServerHandler4PC.writeFlushFuture(ctx, TCP_ServerHandler4PC.PC_START_ONE_TEST+
	        	    								TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
	                				    }});
                    			}
                			}catch(Exception e) {
                				logger.error("",e);
                			}
                		}
                		break;
                	case TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES://获取所有的doc的test名称
                		//filter
            			BasicDBObject filters = new BasicDBObject();
                		if(splitMsg.length>1) {//也就是除了cmd还有其他信息（filter信息）
                			//splitMsg[1]格式    |key:info;key:info;......|
                			String[] filtersStr = splitMsg[1].split(TCP_ServerHandler4PC.SEG_INFO1_INFON);//将信息划分为多个filters              			
                 			//缓存filter的上下界
                 			String lowerBound;String upperBound;
                 			for(String filterStr:filtersStr) {//将过滤信息都put到filter中
                 				String[] oneFilter = filterStr.split(TCP_ServerHandler4PC.SEG_KEY_VALUE,2);//eg.{test:test1_201901251324}，没有参数也可以
                 				switch(oneFilter[0]) {
    	             				case TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME://过滤测试名称，test:xxxxx
    	             					filters.put(oneFilter[0], oneFilter[1]);
    	             					break;
    	             				//过滤年月日,yyyy-MM-ddTHH:mm:ss
    	             				case TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_INSERT_ISO_DATE://配置文件插入的时间（以服务器时间为准）
    	             				case TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_ISODATE://配置文件插入的时间（以上位机时间为准，从测试名称中提取得到）
    	             					String[] lowerUpperData = oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND);
    	             					if(lowerUpperData.length > 1) {
    	             						lowerBound = (oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND,2))[0];//小的日期
        	             					upperBound = (oneFilter[1].split(TCP_ServerHandler4PC.SEG_LOWER_UPPER_BOUND,2))[1];//大的日期
        	             					filters.put(oneFilter[0], new BasicDBObject("$gte",lowerBound).append("$lte", upperBound));//>=和<=
    	             					}else{
    	             						//nothing to do
    	             					}
    	             					break;
    	             				}//end of case
                 			}//end of for
                		}
                		//根据过滤信息来获取实验名称
                		projections = new BasicDBObject();
						projections.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME, 1).append("_id", 0);
                		FindIterable<Document> findIter = TCP_ServerHandler4PC.testInfoMongdb.collection.find(filters).projection(projections);	                	
                		findIter.forEach(new Block<Document>() {
                            @Override
                            public void apply(Document doc) {
                                try {
                                    //没有超过水位
                                    if(!ctx.channel().isWritable()){
                                        ctx.flush();
                                    }
                                    ctx.write(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+
                                    		TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+
                                    		(String)doc.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME)+
                            				TCP_ServerHandler4PC.SEG_TOW_PACK, CharsetUtil.UTF_8));//发给上位机原始数据
                                }catch(Exception e) {
                                    logger.error("",e);
                                }
                            }
                        },  new SingleResultCallback<Void>() {
                            @Override
                            public void onResult(final Void result, final Throwable t) {
                                ctx.write(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+
                                		TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+
                                		TCP_ServerHandler4PC.DONE_SIGNAL_OVER+
                                		TCP_ServerHandler4PC.SEG_TOW_PACK,CharsetUtil.UTF_8));
                                ctx.flush();
                                logger.debug(TCP_ServerHandler4PC.MONGODB_FIND_DOCS_NAMES+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);
                            }
                        });
	                	break;
	                	//获取MongoDB中的文档信息，可以使用filter
	                	//!!!!!只支持查询一次测试
                	case TCP_ServerHandler4PC.MONGODB_FIND_DOCS:
                		//filter
            			BasicDBObject filterDocs = new BasicDBObject();
                		//TODO  to test
                		if(splitMsg.length >= 2) {//也就是除了cmd还有其他信息（filter信息）
                			//splitMsg[1]格式    |key:info;key:info;......|
                			String[] filtersStr = splitMsg[1].split(TCP_ServerHandler4PC.SEG_INFO1_INFON);//将信息划分为多个filters
                			if(filtersStr.length >= 1) {
                				String[] oneFilter = filtersStr[0].split(TCP_ServerHandler4PC.SEG_KEY_VALUE,2);
                				if(oneFilter.length >= 2) {
                					filterDocs.put(oneFilter[0], oneFilter[1]);
                				}
                			}
                		} 
                		//获取数据所存在的集合
                		testInfoMongdb.collection.find(filterDocs).first(new SingleResultCallback<Document>() {//所有操作完成后的工作 	
								@Override
								public void onResult(final Document result, final Throwable t) {				
									try {
										String[] yyyy_MM = new String[2];
										yyyy_MM[0] = (String)result.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA);
										if(yyyy_MM[0] == null) {
											logger.warn("Cannot get colName");
											return;
										}
										//获取得到下一个月String
										SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM");
										Date date =sdf.parse(yyyy_MM[0]);
										Calendar calendar = Calendar.getInstance();
										calendar.setTime(date);
										calendar.add(Calendar.MONTH, 1);
										yyyy_MM[1] = sdf.format(calendar.getTime());
										if(yyyy_MM[1] == null) {
											logger.warn("Cannot get colName");
											return;
										}
										//查询集合和下一个月的集合
										//最多查询两个月，时间跨度不能太大
//										for(String colName : yyyy_MM) {
										String colName = yyyy_MM[0];
										MyMongoDB generalMgdIf = (MyMongoDB)App.getApplicationContext().getBean("generalMgdInterface");
										//指向相应的集合
										generalMgdIf.resetCol(colName);
										BasicDBObject projections = new BasicDBObject();
										projections.append(DataProcessor.MONGODB_KEY_RAW_DATA, 1).append("_id", 0);
				                		FindIterable<Document> docIter = mongodb.collection.find(filterDocs).projection(projections) ;
				             			docIter.forEach(new Block<Document>() {
										    @Override
										    public void apply(final Document document) {//每个doc所做的操作
										    	try {
                                                    //没有超过水位
                                                    if(!ctx.channel().isWritable()){
                                                        ctx.flush();
                                                    }
                                                    ctx.write(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL,CharsetUtil.UTF_8));//加入抬头
                                                    Binary rawDataBin = (Binary)document.get(DataProcessor.MONGODB_KEY_RAW_DATA);
                                                    byte[] rawDataByte = rawDataBin.getData();
                                                    ctx.write(Unpooled.wrappedBuffer(rawDataByte));//发给上位机原始数据
										    	}catch(Exception e) {
										    		logger.error("",e);
										    	}	
										    }}, new SingleResultCallback<Void>() {//所有操作完成后的工作 	
										        @Override
										        public void onResult(final Void result, final Throwable t) {
	                                                ctx.write(Unpooled.copiedBuffer(TCP_ServerHandler4PC.MONGODB_FIND_DOCS+
	                                                		TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER+
	                                                		TCP_ServerHandler4PC.SEG_TOW_PACK,CharsetUtil.UTF_8));
	                                                ctx.flush();
										        	logger.debug(TCP_ServerHandler4PC.MONGODB_FIND_DOCS+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);
										        }			    	
										    });
//										}
									} catch (ParseException e) {
										logger.info("",e);
									}
								}			    	
						    });
                		break;
                	case TCP_ServerHandler4PC.PC_WANT_GET_TEST_CONFIG:
                		try {
	                		String testName = splitMsg[1].trim();
	                		BasicDBObject filter = new BasicDBObject();
	            			filter.put(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME, testName);
	            			//查找第一个满足testname满足要求的配置文件
							TCP_ServerHandler4PC.testInfoMongdb.collection.find(filter).first(new SingleResultCallback<Document>() {
		    					@Override
		    					public void onResult(final Document doc, final Throwable t) {
		    						if(doc == null) {
		    							return;
		    						}
		    						TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_GET_TEST_CONFIG+
		                    				TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+doc.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTCONF));
		    						TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_GET_TEST_CONFIG+
		    								TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OVER);
		    					}	
		                	});
                		}catch(Exception e) {
                			logger.error("",e);
                		}
                		break;
                	case TCP_ServerHandler4PC.MONGODB_CREATE_COL://创建collection
                		//TODO
                		break;
                	case TCP_ServerHandler4PC.PC_WANT_GET_RTDATA://修改位GetRtData的状态
                		try {
                			String testName = splitMsg[1].trim();
                			RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setTestName(testName);
                    		RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setStatus(ChannelAttributes.DATA_GET_STA);
                    		TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_GET_RTDATA+
                    				TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
                		}catch(Exception e){
                			logger.error("",e);
                		}
            			
                		break;
                	default:
                		break;
                }  
        	}else if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus().equals(ChannelAttributes.REQUEST_CONNECT_STA)) {//连接但还未登录
        		switch(cmd) {
	            	case TCP_ServerHandler4PC.PC_WANT_LOGIN://PC想要登录
	            		String info = splitMsg[1];
	            		logger.debug("Info : "+info);
	            		//当前状态时请求连接状态而且用户名和密码匹配成功
	            		loginMd5(ctx,info);
	                    break;
	            	default://不认识的命令，强制断开
	            		RunPcServer.delCh(ctx);
	            		break;
        		}
        	}//end of if elif
        	//不管登录与否，都要处理的命令
        	switch(cmd) {
	        	case TCP_ServerHandler4PC.HEART_BEAT_SIGNAL://心跳包
	        		//TODO 每次更新心跳包的时间，过一段时间检查是否超过时间
	        		TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.HEART_BEAT_SIGNAL+
	        				TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
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
        	logger.error("",e);
        }
        finally {
            // 抛弃收到的数据
            ReferenceCountUtil.release(msg);//如果不是继承的SimpleChannel...则需要自行释放msg
        }
    }//end of channelRead
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		//设置消息队列流量水位，不能太多，否则会造成积压
		ctx.channel().config().setWriteBufferHighWaterMark(50 * 1024 * 1024);//50MB
		logger.info(String.format("PC %s connected!",ctx.channel().remoteAddress()));
    	//通道数太多了
    	Map<String,ChannelAttributes> chMapTemp = RunPcServer.getChMap();
    	synchronized(chMapTemp) {//拥有ch map的锁
        	if(chMapTemp.size()>RunPcServer.MAX_CHANNEL_NUM) {
        		channelInactive(ctx);//关闭通道
    			return;
        	}
        	//加入该通道
        	chMapTemp.put(ctx.channel().remoteAddress().toString(), new ChannelAttributes(ctx));  	
    	}
    	logger.info(String.format("Channel Num : %d", chMapTemp.size()));
    	String salt = chMapTemp.get(ctx.channel().remoteAddress().toString()).getSalt();
    	TCP_ServerHandler4PC.writeFlushFuture(ctx,"RandStr"+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+salt);
    	logger.debug("Salt : "+salt);
    	ctx.fireChannelActive();
    }//end of channelActive
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		RunPcServer.delCh(ctx);
		logger.info(String.format("PC %s disconnected!",ctx.channel().remoteAddress().toString()));
		logger.info(String.format("Channel Num : %d", RunPcServer.getChMap().size()));
		ctx.fireChannelInactive();
	}
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 当出现异常就关闭连接
    	logger.error("",cause);
        RunPcServer.delCh(ctx);
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
		String keyHashStr = splitInfo[1];//md5(md5(key)+salt)
		//解析加密数值
		try {
			BasicDBObject projections = new BasicDBObject();
			projections.append(InfoMgdAttributes.MONGODB_USER_KEY_KEY, 1).append("_id", 0);
			//BasicDBObject时Bson的实现
			BasicDBObject filter = new BasicDBObject();
			filter.put(InfoMgdAttributes.MONGODB_USER_NAME_KEY, userStr);
			FindIterable<Document> docIter = RunPcServer.getInfoDb().collection.find(filter).projection(projections) ;
			//forEach：异步操作
			docIter.forEach(new Block<Document>() {
			    @Override
			    public void apply(final Document document) {//每个doc所做的操作
			    	
			    	//先获取db中user的key
			    	String key = (String) document.get(InfoMgdAttributes.MONGODB_USER_KEY_KEY);
			    	//查看查询到的密码明文
			    	logger.debug("Key in DB : "+key);
			    	//计算得到keyHash
			    	String keyHashStrLocal = Md5.getKeySaltHash(key, salt);
			    	//打印出收到的keyHash和本地计算出来的keyHash
			    	logger.debug("Key Hash Remot : "+keyHashStr);
			    	logger.debug("Key Hash Local : "+keyHashStrLocal);
			    	if(keyHashStrLocal.toUpperCase().equals(keyHashStr.toUpperCase())){
			    		logger.info(ctx.channel().remoteAddress().toString()+"'s Key Correct!");
                    	//如果当前状态是请求连接状态，才可以进行下一步
                    	if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.REQUEST_CONNECT_STA) {
                        	//设置该通道为信任
                    		RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).setStatus(ChannelAttributes.LOGINED_STA);
                    	}else {                	//如果当前已经登录了
                    		//do nothing!
                    	}
			    	}else {
			    		logger.info(ctx.channel().remoteAddress().toString()+"'s Key Incorrect!");
			    	}
			    }}, new SingleResultCallback<Void>() {//所有操作完成后的工作
			        @Override
			        public void onResult(final Void result, final Throwable t) {
			        	//查询操作结束后，查看是否登录成功
	                    if(RunPcServer.getChMap().get(ctx.channel().remoteAddress().toString()).getStatus()==ChannelAttributes.LOGINED_STA) {//登录成功
	                    	//返回登录信息
	                    	TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_LOGIN+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK);
//	                    	ctx.writeAndFlush(Unpooled.copiedBuffer("Login"+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_OK,CharsetUtil.UTF_8));
	                    }else {//登录失败，已经断开了
	                    	TCP_ServerHandler4PC.writeFlushFuture(ctx,TCP_ServerHandler4PC.PC_WANT_LOGIN+TCP_ServerHandler4PC.SEG_CMD_DONE_SIGNAL+TCP_ServerHandler4PC.DONE_SIGNAL_ERROR);
	                    	//删除这个通道
	                    	RunPcServer.delCh(ctx);   	
	                    }
			        }			    	
			    });
		}catch(Exception e) {
			logger.error("",e);
		}

	}
    /**
     * 发送信息，会受到成功的信息，进而处理（不会阻塞等待）
     * 
     * 消息末尾加上一个'\n'结束符
     * @param ctx 通道ctx
     * @param msg 要发送的String信息
     */
    public static void writeFlushFuture(ChannelHandlerContext ctx,String msg) {
    	//如果还没有断开
    	if(ctx.channel().isActive()) {
    		//如果这个channel没有到达水位的话，还可以写入
        	//水位在active时设置
        	if(ctx.channel().isWritable()) {
            	ChannelFuture future = ctx.writeAndFlush(Unpooled.copiedBuffer(msg + TCP_ServerHandler4PC.SEG_TOW_PACK,CharsetUtil.UTF_8));
              	//等待发送完毕
            	future.addListener(new ChannelFutureListener(){
        			@Override
        			public void operationComplete(ChannelFuture f) {
        				if(!f.isSuccess()) {
        					logger.error("",f.cause());
        				}
        			}
        		});    		
        	}    		
    	}else {
    		RunPcServer.delCh(ctx);
    	}
    }

    /**
     * 发送信息并等待成功
     * @param ctx 通道ctx
     * @param msg 要发送的ByteBuf信息
     */
    public static void writeFlushFuture(ChannelHandlerContext ctx,ByteBuf msg) {
    	//如果这个channel没有到达水位的话，还可以写入
    	//水位在active时设置
    	if(ctx.channel().isWritable()) {
    	ChannelFuture future = ctx.writeAndFlush(msg);
	    	//发送完毕会返回一个信息
	    	future.addListener(new ChannelFutureListener(){
				@Override
				public void operationComplete(ChannelFuture f) {
					if(!f.isSuccess()) {
						RunPcServer.delCh(ctx);
						logger.error("",f.cause());
					}
				}
			});
    	}
    }
    /**
     * 关闭ctx
     * @param ctx
     */
    public static void ctxCloseFuture(ChannelHandlerContext ctx) {
		try {
			ChannelFuture future = ctx.close();
			logger.info("Got In Close : "+ctx.pipeline().channel().toString());
	    	future.addListener(new ChannelFutureListener(){
				@Override
				public void operationComplete(ChannelFuture f) {
					if(!f.isSuccess()) {
						logger.error("",f.cause());
					}
				}
			});
		}catch(Exception e){
			logger.error("",e);
		}
		
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

