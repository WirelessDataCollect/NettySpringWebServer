package com.nesc.Backend;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;;

/**
* 
* 后端主程序
*
* @author  nesc528
* @Date    2018-9-7
* @version 0.1.0
*/
public class App{
	public static ApplicationContext context;
    public App() { 
    	context = new ClassPathXmlApplicationContext("beans.xml");
    	TestTools test = new TestTools();
    	test.start();	
    	RunPcServer pc_server = (RunPcServer)context.getBean("runPcServer");
    	pc_server.start();
    	RunDeviceServer device_server = (RunDeviceServer)context.getBean("runDeviceServer");
    	device_server.start();   	
    }
	/**
	 * 获取bean的一个应用上下文
	 * @return context
	 */
	public static ApplicationContext getApplicationContext() {
		return context;
	}
}


