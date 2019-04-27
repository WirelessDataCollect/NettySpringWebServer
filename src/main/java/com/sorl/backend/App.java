package com.sorl.backend;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
/**
* 
* 后端主程序
*
* @author  nesc420
* @Date    2018-9-7
* @version 0.1.0
*/
public class App{
	private static ApplicationContext context;
	private TestTools test;//工具
	private RunPcServer pc_server;//面向PC的进程
	private RunDeviceServer device_server;//面向设备的进程
    public App() { 
    	/*获取context*/
    	context = new ClassPathXmlApplicationContext("beans.xml");
    	/*获取RunPcServer類*/
    	pc_server = (RunPcServer)context.getBean("runPcServer");
    	pc_server.start();
    	/*获取RunDeviceServer類*/
    	device_server = (RunDeviceServer)context.getBean("runDeviceServer");
    	device_server.start();  
    	/*获取TestTools類*/
    	test = (TestTools)context.getBean("testTools");
    	test.start();
    }
	/**
	 * 获取bean的一个应用上下文
	 * @return context
	 */
	public static ApplicationContext getApplicationContext() {
		return context;
	}
	/**
	 * 获取TestTools
	 * @return test
	 */
	public TestTools getTest() {
		return this.test;
	}
	/**
	 * 获取面向PC的进程
	 * @return pc_server
	 */
	public RunPcServer getPcServer() {
		return this.pc_server;
	}
	/**
	 * 获取面向设备的进程
	 * @return device_server
	 */
	public RunDeviceServer getDeviceServer() {
		return this.device_server;
	}
}


