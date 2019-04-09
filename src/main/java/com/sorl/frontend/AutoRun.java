package com.sorl.frontend;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.sorl.backend.App;
/**
* 
* Servlet监听接口
*
* @author  nesc420
* @Date    2019-4-7
* @version 0.1.0
*/
public class AutoRun implements ServletContextListener{
	/**
	 * tomcat启动后自动运行的方法
	 * 
	 * @param arg0 事件
	 * @return none
	 */
	public App app;
	public void contextInitialized(ServletContextEvent arg0) {
		app = new App();//开几个线程
	}
	/**
	 * tomcat关闭后自动运行的方法
	 * 
	 * @param arg0 事件
	 * @return none
	 */
    public void contextDestroyed(ServletContextEvent arg0){
    	app.getDeviceServer().stop();
    	app.getPcServer().stop();
    	app.getTest().stop();
    }
}
