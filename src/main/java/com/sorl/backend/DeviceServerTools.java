package com.sorl.backend;

import java.util.Iterator;
import java.util.Map;

import com.sorl.attributes.ChannelAttributes;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
* 
* 设备服务器工具，包括转发数据给PC上位机。在此之前，需要有TCP连接，即MAP中有上位机同服务器连接的Channel。
*
* @author  nesc420
* @Date    2018-9-7
* @version 0.0.1
*/
public class DeviceServerTools{
	/**
	 * 转发设备信息至PC端上位机
	 * @param temp
	 */
	protected static void send2Pc(ByteBuf temp) {   //这里需要是静态的，非静态依赖对象
		synchronized(RunPcServer.getChMap()) {
				String testName = DataProcessor.getFrameHeadTestName(temp);
				for(Iterator<Map.Entry<String,ChannelAttributes>> item = RunPcServer.getChMap().entrySet().iterator();item.hasNext();) {
					Map.Entry<String,ChannelAttributes> entry = item.next();
					//判断是否为实时获取数据的状态,且和测试名称对应
					if((entry.getValue().getStatus()==ChannelAttributes.DATA_GET_STA) && (entry.getValue().getTestName().equals(testName)) || entry.getValue().getTestName().equals("all")) {
						//发送数据
						TCP_ServerHandler4PC.writeFlushFuture(entry.getValue().getContext(),Unpooled.copiedBuffer(temp));
					}
			}
		}
	}
}