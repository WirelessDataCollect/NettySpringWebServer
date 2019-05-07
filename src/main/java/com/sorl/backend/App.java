package com.sorl.backend;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
/**
* 
* 后端主程序
*
* @author  nesc420
* @Date    2019-5-6
* @version 0.1.1
*/
public class App{
	private static ApplicationContext context;
	private RunPcServer pc_server;//面向PC的进程
	private RunDeviceServer device_server;//面向设备的进程
	private ScheduledExecutorService testExecutor;
	
    public App() { 
    	// 获取context
    	context = new ClassPathXmlApplicationContext("beans.xml");
    	// 获取RunPcServer類
    	pc_server = (RunPcServer)context.getBean("runPcServer");
    	pc_server.start();
    	// 获取RunDeviceServer類
    	device_server = (RunDeviceServer)context.getBean("runDeviceServer");
    	device_server.start();  
    	// 获取TestTools类
    	testExecutor = Executors.newSingleThreadScheduledExecutor();
    	testExecutor.scheduleAtFixedRate((TestTools)context.getBean("testTools"),
                5, 5, TimeUnit.SECONDS);
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
	public ScheduledExecutorService getTestExecutor() {
		return this.testExecutor;
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
	/**
	 * 释放App这个类和线程
	 * @return none
	 */
	public void destroyThreads() {
		this.getDeviceServer().stop();
		this.getPcServer().stop();
		this.getTestExecutor().shutdown();
	}
}


