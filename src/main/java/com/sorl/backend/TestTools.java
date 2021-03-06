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
	private final static Logger logger = Logger.getLogger(TestTools.class);
	private RunDeviceServer runDeviceServer;
	private MyMongoDB dataMgd;
	@Override
	public void run() {
		try {
			runDeviceServer = (RunDeviceServer) App.getApplicationContext().getBean("runDeviceServer");
			dataMgd = (MyMongoDB) App.getApplicationContext().getBean("myMongoDB");
			packsNum = runDeviceServer.getPacksNum();  //获取packsnums
			runDeviceServer.resetPacksNum();  //packsnums = 0
			logger.info(String.format("Periodic Packs : %d",packsNum));
			logger.info(String.format("Data's Db.Col  : %s.%s\r\n", dataMgd.getDbName(),dataMgd.getColName()));
		}catch (Exception e) {
			logger.error("",e);
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
	 * @return none
	 */
	public void stop () {
		logger.info("Stopping TestTools");
		t.interrupt();
	}
}
