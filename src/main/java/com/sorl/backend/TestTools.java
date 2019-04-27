package com.sorl.backend;
import org.apache.log4j.Logger;
/**
* 
* 测试工具
*
* @author  nesc420
* @Date    2019-4-27
* @version 0.3.1
*/
public class TestTools implements Runnable{
	private Thread t;//线程
	private int packsNum;
	private final static int SLEEP_MS = 5000;
	private final static Logger logger = Logger.getLogger(TestTools.class);
	private RunDeviceServer runDeviceServer;
	@Override
	public void run() {
		while(true) {
			runDeviceServer = (RunDeviceServer) App.getApplicationContext().getBean("runDeviceServer");
			packsNum = runDeviceServer.getPacksNum();  //获取packsnums
			runDeviceServer.resetPacksNum();  //packsnums = 0
			logger.info(String.format("%d Packs/5s\r\n",packsNum));
			try {//休息5s
				Thread.sleep(TestTools.SLEEP_MS);//阻塞当前进程
			} catch (InterruptedException e) {
				logger.error("",e);
			} 
		}
	}
	/**
	 * 开始线程
	 */
	public void start () {
		logger.info("Starting TestTools thread");
		if (t == null) {
			t = new Thread (this, "TestTools");
			t.start ();
		}
	}
	/**
	 * 关闭TestTools对象的线程
	 * 
	 *	
	 * @return none
	 */
	public void stop () {
		logger.info("Stopping TestTools");
		t.interrupt();
	}
}
