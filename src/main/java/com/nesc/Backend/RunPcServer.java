package com.nesc.Backend;

import java.math.BigInteger;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import com.nesc.security.SimpleRsa;

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
* @Date    2018-10-22
* @version 0.2.1
*/
public class RunPcServer implements Runnable{
	private Thread t;
	private String threadName = "PC-Thread";
	private int listenPort = 8080;
	public Channel ch = null;
	//存储PC连接的通道<PC[num],channel>
	private volatile static Map<String,Channel> ch_map = new ConcurrentHashMap<String,Channel>();
	//存储通道的状态
	private volatile static Map<String,Integer> ch_sta = new ConcurrentHashMap<String,Integer>();
	//存储RSA加密算法类
	private volatile static Map<String,SimpleRsa> ch_rsa = new ConcurrentHashMap<String,SimpleRsa>();
	//三个状态，请求连接状态、服务器信任状态、数据实时接收状态
	public final static int REQUEST_CONNECT_STA = 0x01;
	public final static int TRUSTED_STA=0x02;
	public final static int DATA_GET_STA=0x04;
	public final static int MAX_CHANNEL_NUM=100;
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
	public static Map<String,Channel> getChMap(){
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
	 * 获取保存同服务器连接的PC的通道状态
	 * @return {@link Map}
	 */
	public static Map<String,Integer> getChSta(){
		return ch_sta;
	}	
	/**
	 * 获取保存同服务器连接的PC的通道的RSA算法类
	 * @return {@link Map}
	 */
	public static Map<String,SimpleRsa> getChRsa(){
		return ch_rsa;
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
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
	private final static String PC_WANT_LOGIN = "Login";
	private final static String PC_WANT_GET_RTDATA = "GetRtdata";
	/**
	 * 登录管理员，命令"login"
	 * @param msg 登录信息(用户名+密码)，形式(逗号用于隔开同一个信息的加密数值，分号隔开不同信息)：
	 * "第一个字符加密数值,第二个字符加密数值,...;第一个字符加密数值,第二个字符加密数值"
	 * @return false：登录失败；true：登录成功
	 */
	private boolean login(ChannelHandlerContext ctx,String msg) {
		//解析加密数值
		try {
			//转化为字符串
			String[] info_str = msg.split(";");
			
			//提取管理员名称和密码，加密数值的字符串形式
			String[] name_str = info_str[0].split(",");
			String[] key_str = info_str[1].split(",");
//			System.out.println("name:"+name_str[0]);
//			System.out.println("key:"+key_str[0]);
			//创建用于保存加密数值的BigInteger数组
			char[] name_decoded = new char[name_str.length];
			char[] key_decoded = new char[key_str.length];
			int idx=0;
			for(String n_str : name_str) {
				name_decoded[idx] = (char)(RunPcServer.getChRsa().get(ctx.channel().remoteAddress().toString())
						.getDencryptedVal(new BigInteger(n_str)).intValueExact());//如果BigInteger输出超出了char则会抛出异常
				idx++;
			}
			idx=0;
			for(String k_str : key_str) {
				key_decoded[idx] = (char)(RunPcServer.getChRsa().get(ctx.channel().remoteAddress().toString())
						.getDencryptedVal(new BigInteger(k_str)).intValueExact());
				idx++;
			}
			String name = new String(name_decoded);
			String key = new String(key_decoded);
			//显示解码后的字符
			System.out.println("name:"+name);
			//显示解码后的字符
			System.out.println("key:"+key);
			if(name.equals("nesc")&&key.equals("123456")) {
				return true;
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
		
	}
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            String str_in = ((ByteBuf)msg).toString(CharsetUtil.UTF_8);
            System.out.println("Recv from PC:"+str_in);
            //返回给该上位机
            ctx.writeAndFlush(Unpooled.copiedBuffer(str_in+"\n",CharsetUtil.UTF_8));
            //提取命令
            String cmd = str_in.split("\\+")[0];
            String info = "1;1";
            if(str_in.split("\\+").length>1) {
                //提取信息
                info = str_in.split("\\+")[1];    
            }
            switch(cmd) {
            	case TCP_ServerHandler4PC.PC_WANT_LOGIN:
                    if(login(ctx,info)) {
                    	ctx.writeAndFlush(Unpooled.copiedBuffer("Logined\n",CharsetUtil.UTF_8));
                    }
                    else {
                    	ctx.writeAndFlush(Unpooled.copiedBuffer("Login error\n",CharsetUtil.UTF_8));
                    }
                    break;
            	case TCP_ServerHandler4PC.PC_WANT_GET_RTDATA:
            		System.out.println("Request RTDATA!But Have Not Programs This Func!");
            		break;
            	default:
            		System.out.println("Cmd Unkown!");
            		break;
            }

            
        } finally {
            // 抛弃收到的数据
            ReferenceCountUtil.release(msg);//如果不是继承的SimpleChannel...则需要自行释放msg
        }
    }
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	System.out.println("PC "+ctx.channel().remoteAddress()+" connected!");
    	//通道数太多了
    	if(RunPcServer.getChSta().size()>RunPcServer.MAX_CHANNEL_NUM) {
    		ChannelFuture future = ctx.close();
			future.addListener(new ChannelFutureListener(){
				@Override
				public void operationComplete(ChannelFuture f) {
					if(!f.isSuccess()) {
						f.cause().printStackTrace();
					}
				}
			});
			return;
    	}
    	synchronized(RunPcServer.getChMap()) {
    		synchronized(RunPcServer.getChSta()) {
    			synchronized(RunPcServer.getChRsa()) {
	        		//channel使能
	        		RunPcServer.getChMap().put(ctx.channel().remoteAddress().toString(), ctx.channel());
	    			//本channel设置为请求状态
	    			RunPcServer.getChSta().put(ctx.channel().remoteAddress().toString(), RunPcServer.REQUEST_CONNECT_STA);
					//加入该通道的RSA算法
	    			RunPcServer.getChRsa().put(ctx.channel().remoteAddress().toString(), new SimpleRsa(10));
   			
    			}
    		}
    	}
    	
    	ctx.writeAndFlush(Unpooled.copiedBuffer("Connected!\n",CharsetUtil.UTF_8));
    	ctx.writeAndFlush(Unpooled.copiedBuffer("Private key (n,e) = ("
    			+RunPcServer.getChRsa().get(ctx.channel().remoteAddress().toString()).getPublicE().toString()+","
    			+RunPcServer.getChRsa().get(ctx.channel().remoteAddress().toString()).getPublicN().toString()+")"
    			+"\n", CharsetUtil.UTF_8));
        ctx.fireChannelActive();
    }
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		synchronized(RunPcServer.getChSta()) {
			synchronized(RunPcServer.getChMap()) {
				synchronized(RunPcServer.getChRsa()) {
					//删除该通道的状态
					RunPcServer.getChSta().remove(ctx.channel().remoteAddress().toString());
					//删除该通道
					RunPcServer.getChMap().remove(ctx.channel().remoteAddress().toString());
					//删除该通道的RSA算法
					RunPcServer.getChRsa().remove(ctx.channel().remoteAddress().toString());
				}		
			}
		}
		System.out.println("PC "+ctx.channel().remoteAddress().toString()+" disconnected!");
		ctx.fireChannelInactive();
	}
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 当出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }
    
}

