package com.sorl.backend;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import org.apache.log4j.Logger;

/**
* 
* TCP服务器的输入处理器函数
*
* @author  nesc420
* @Date    2018-9-7
* @version 0.0.1
*/
public class TCP_ServerHandler extends ChannelInboundHandlerAdapter {
	private static final Logger logger = Logger.getLogger(TCP_ServerHandler.class);
	private DataProcessor processor;
	TCP_ServerHandler(){
		processor =(DataProcessor) App.getApplicationContext().getBean("dataProcessor");//获取一个数据处理器
	}
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {	
        	RunDeviceServer runDeviceServer = //获取某一个端口的数据信息
    			(RunDeviceServer) App.getApplicationContext().getBean("runDeviceServer");
        	runDeviceServer.incPacksNum();//n秒钟内的包++
    		ByteBuf temp = (ByteBuf)msg;
    		DeviceServerTools.send2Pc(temp);
    		processor.dataProcess(temp);
        } finally {
            // 抛弃收到的数据
            ReferenceCountUtil.release(msg);//如果不是继承的SimpleChannel...则需要自行释放msg
        }
    }
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {   //1
		logger.info("Device TCP channel " + ctx.channel().toString() + " created");
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	logger.error("",cause);
        ctx.close();
    }
}