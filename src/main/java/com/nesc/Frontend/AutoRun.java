package com.nesc.Frontend;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.nesc.Backend.App;

public class AutoRun implements ServletContextListener{
	/**
	 * tomcat启动后自动运行的方法
	 * 
	 * @param arg0 事件
	 * @return none
	 */
	public void contextInitialized(ServletContextEvent arg0) {
		App app = new App();//开几个线程
		System.out.println("Mainfunc is running");
	}
	/**
	 * tomcat关闭后自动运行的方法
	 * 
	 * @param arg0 事件
	 * @return none
	 */
    public void contextDestroyed(ServletContextEvent arg0){
    	System.out.println("Mainfunc stopped");
    }
}
