package com.nesc.Backend;

import java.util.Iterator;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
* 
* 设备服务器工具，包括转发数据给PC上位机。在此之前，需要有TCP连接，即MAP中有上位机同服务器连接的Channel。
*
* @author  nesc418
* @Date    2018-9-7
* @version 0.0.1
*/
public class DeviceServerTools{
	/**
	 * 转发设备信息至PC端上位机
	 * @param temp
	 */
	protected static void send2Pc(ByteBuf temp) {   //这里需要是静态的，非静态依赖对象
		synchronized(RunPcServer.getChSta()) {
			synchronized(RunPcServer.getChMap()) {
				for(Iterator<Map.Entry<String,Integer>> item = RunPcServer.getChSta().entrySet().iterator();item.hasNext();) {
					Map.Entry<String,Integer> entry = item.next();
					//判断是否为实时获取数据的状态
					if(entry.getValue()==RunPcServer.DATA_GET_STA) {
						ByteBuf temp1 = temp.copy();
						ChannelFuture future = RunPcServer.getChMap().get(entry.getKey()).pipeline().writeAndFlush(temp1);
						future.addListener(new ChannelFutureListener(){
							@Override
							public void operationComplete(ChannelFuture f) {
								if(!f.isSuccess()) {
									f.cause().printStackTrace();
								}
							}
						});
					}
			}


			}	
		}
	}
}