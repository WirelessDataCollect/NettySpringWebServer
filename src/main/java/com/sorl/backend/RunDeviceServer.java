package com.sorl.backend;

import java.util.Scanner;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.apache.log4j.Logger;
/**
* 
* 运行UDP/TCP服务器，用于连接硬件设备
*
* 设备参考输入：12121212000000011212120102012022（只适用于固定adc_length为16）
* @author  nesc418
* @Date    2018-10-22
* @version 0.2.1
*/
public class RunDeviceServer implements Runnable{
	private String protocol = "UDP";
	private int listenPort = 5001;
	private Thread t;
	private String threadName = "Device-Thread";
	private volatile int packsNum = 0;
	private static final Logger logger = Logger.getLogger(RunDeviceServer.class);
	/**
	 * 设置连接设备的端口。bean的set方法，bean会自动调用
	 * 
	 * @param port
	 */
	public void setListenPort(int port) {
		listenPort = port;
	}
	/**
	 * 设置连接设备的协议。bean的set方法，bean会自动调用
	 * 
	 * @param proto
	 */
	public void setProtocol(String proto) {
		protocol = proto;
	}
	/**
	 * 设置线程名称。bean的set方法，bean会自动调用
	 * 
	 * @param name
	 */
	public void setThreadName(String name) {
		threadName = name;
	}	
	/**
	* 清除packsNum1s。
	*
	* @throws none
	*/	
	public void resetPacksNum() {
		this.packsNum = 0;
	}
	/**
	* 增加packsNum1s。
	*
	* @throws none
	*/	
	public void incPacksNum() {
		this.packsNum ++;
	}	
	/**
	* 获取packsNum的数值。
	*
	* @throws none
	*/	
	public int getPacksNum() {
		return this.packsNum;
	}	
	/**
	* 获取UDP还是TCP协议，获取端口号。
	*
	* @throws none
	*/	
	public void getProtocolInfo(){
		logger.info("Choose TCP or UDP?");
		Scanner scan = new Scanner(System.in);
        while (scan.hasNextLine()) {
        	protocol = scan.nextLine().toUpperCase();//支持大小写混写
            if(protocol.equals("TCP")||protocol.equals("UDP")) {
            	logger.info("Choose port from 5000~9000...");
            	while (scan.hasNextLine()) {
            		try{
            			listenPort = Integer.parseInt(scan.nextLine());
            			if(listenPort<5000||listenPort>9000) {//如果超出了这个port界限，则要重新输入
            				logger.error("Please input port number from 5000~9000!");
            				continue;
            			}
            			else {
            				logger.info(String.format("Port for Node : %d", this.listenPort));
            				break;//退出while(得到port)
            			}		
            		}catch(NumberFormatException nfe) {
            			logger.error("Please input port number from 5000~9000!");
            		}
            	}
                if(scan!=null) {
                	scan.close();//关闭scanner
                }
            	return;//getProtocolInfo结束
            }
            else {
            	logger.error("Error:Please input \"TCP\" or \"UDP\"!");
            }

        }
        if(scan!=null) {
        	scan.close();//关闭scanner
        }
	}
	/**
	* 运行UDP连接设备。
	*
	* @param port 面向设备的端口
	* @throws none
	*/
	private void runUdp(int port){
		EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			
			Bootstrap bootstrap = new Bootstrap();//引导启动 
			bootstrap.group(eventLoopGroup)
			.channel(NioDatagramChannel.class)
			.option(ChannelOption.SO_BROADCAST, true)
			.handler(new ChannelInitializer<DatagramChannel>() {
			@Override
			public void initChannel(DatagramChannel ch) throws Exception {
				ch.pipeline().addLast(new UDP_ServerHandler());
			}
		});//业务处理类,其中包含一些回调函数
			 
		ChannelFuture cf= bootstrap.bind(port).sync();
			cf.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			logger.error("",e);
		} finally {
			eventLoopGroup.shutdownGracefully();//最后一定要释放掉所有资源,并关闭channle
		}
	}
	/**
	* 运行TCP连接设备。
	*
	* @param port 面向设备的端口
	* @throws none
	*/
	private void runTcp(int port){
	    EventLoopGroup bossGroup = new NioEventLoopGroup();        // 用来接收进来的连接，这个函数可以设置多少个线程
	    EventLoopGroup workerGroup = new NioEventLoopGroup();    // 用来处理已经被接收的连接
	
	    try {
	    	ServerBootstrap b = new ServerBootstrap();
	    	b.group(bossGroup, workerGroup)
	    	 .channel(NioServerSocketChannel.class)            // 这里告诉Channel如何接收新的连接
	    	 .childHandler( new ChannelInitializer<SocketChannel>() {
		    	 @Override
		    	 protected void initChannel(SocketChannel ch) throws Exception {
		        // 自定义处理类
		    		 ch.pipeline().addLast(new TCP_ServerHandler());//如果需要继续添加与之链接的handler，则再次调用addLast即可
		    	 }	
	    	 })
		    .option(ChannelOption.SO_BACKLOG, 128)
		    .childOption(ChannelOption.SO_KEEPALIVE, true);
	   
	     
	    	// 绑定端口，开始接收进来的连接
	    	ChannelFuture cf = b.bind(port).sync();//在bind后，创建一个ServerChannel，并且该ServerChannel管理了多个子Channel 
	    	// 等待服务器socket关闭
	        cf.channel().closeFuture().sync();              
	    } catch (Exception e) {
	        workerGroup.shutdownGracefully();
	        bossGroup.shutdownGracefully();
	    }
	    finally {
	    	 workerGroup.shutdownGracefully();
	         bossGroup.shutdownGracefully();      	
	    }
	}  
	@Override
	public void run() {	
		logger.info("Protocol for devices: "+protocol);
		logger.info(String.format("Listen port for devices: ", listenPort));
        switch(protocol) {
        case "UDP":
        	runUdp(listenPort);
        	break;
        case "TCP":
        	runTcp(listenPort);
        	break;
        default:
        	logger.error("Bad protocal");
        }  			
	}
	public void start () {
		logger.info("Starting " +  threadName);
		if (t == null) {
			t = new Thread (this, threadName);
			t.start ();
		}
	}
	/**
	 * 关闭RunDeviceServer对象的线程
	 * @return none
	 */
	public void stop () {
		logger.info("Stopping " +  threadName);
		t.interrupt();
	}	
}