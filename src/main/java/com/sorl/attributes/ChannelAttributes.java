package com.sorl.attributes;
import com.sorl.security.Md5;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;


/**
* 
* 通信通道信息
*
* @author  nesc420
* @Date    2018-11-2
* @version 0.0.1
*/
public class ChannelAttributes {
	//三个状态，请求连接状态、服务器信任状态(已登录状态)、数据实时接收状态
	public final static int REQUEST_CONNECT_STA = 0x01;
	public final static int LOGINED_STA=0x02;
	public final static int DATA_GET_STA = 0x40;
	private boolean ALLOW_SEND_DOCS;
	private final Channel channel;//通道,初始化后不可改变
	private final ChannelHandlerContext context;
	private String encryption;//加密算法
	private String enrypt_salt;
	private String testName;
	Integer status;//通道状态
	
	/**
	 * PC连接服务器时，初始化该通道
	 * @param ctx
	 */
	public ChannelAttributes(ChannelHandlerContext ctx){
		this.context = ctx;
		this.channel = ctx.channel();//保存通道信息
		this.status = ChannelAttributes.REQUEST_CONNECT_STA;//设置为请求连接状态
		this.encryption = "Md5";//保存RSA加密算法信息
		this.enrypt_salt = Md5.getRandStr();//随机初始化salt
		this.testName = "";
		this.allowSendDocs();
	}
	/**
	 * 返回该通道的状态
	 * @return Integer 通道的状态
	 */
	public Integer getStatus() {
		return this.status;
	}
	/**
	 * 可以发送数据
	 */
	public void allowSendDocs() {
		this.ALLOW_SEND_DOCS = true;
	}
	/**
	 * 可以发送数据？
	 */
	public boolean isAllowSendDocs() {
		return this.ALLOW_SEND_DOCS;
	}
	/**
	 * 不允许发送数据
	 */
	public void stopSendDocs() {
		this.ALLOW_SEND_DOCS = false;
	}
	/**
	 * 设置该通道的状态
	 * @param sta 通道状态
	 */
	public void setStatus(Integer sta) {
		this.status = sta;
	}
	/**
	 * 返回该通道的加密算法
	 * @return SimpleRsa 加密算法
	 */
	public String getEncryption() {
		return this.encryption;
	}	
	/**
	 * 返回该通道的salt
	 * @return String 盐值字符串
	 */
	public String getSalt() {
		return this.enrypt_salt;
	}	
	/**
	 * 返回该通道的Channel类
	 * @return Channel 通道类
	 */
	public Channel getChannel() {
		return this.channel;
	}	
	/**
	 * 返回该通道的ChannelHandlerContext类
	 * @return Channel 通道类
	 */
	public ChannelHandlerContext getContext() {
		return this.context;
	}	
	/**
	 * 返回该通道所做的测试名称
	 * @return Channel 通道类
	 */
	public String getTestName() {
		return this.testName;
	}	
	/**
	 * 设置测试名称
	 * @param name 测试名称
	 * @return None
	 */
	public void setTestName(String name) {
		this.testName = name;
	}	
}
