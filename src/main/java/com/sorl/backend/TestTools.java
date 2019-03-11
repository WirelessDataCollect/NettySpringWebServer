package com.sorl.backend;

/**
* 
* 测试工具
*
* @author  nesc418
* @Date    2018-10-22
* @version 0.2.1
*/
public class TestTools implements Runnable{
	private Thread t;//线程
	private int packsNum;
	private final static int SLEEP_MS = 5000;
	@Override
	public void run() {
		while(true) {
//			System.out.printf("Str(\"8245810\") to int test : %d \n",Integer.parseInt("8245810"));
			RunDeviceServer runDeviceServer = (RunDeviceServer) App.getApplicationContext().getBean("runDeviceServer");
			packsNum = runDeviceServer.getPacksNum();  //获取packsnums
			runDeviceServer.resetPacksNum();  //packsnums = 0
			System.out.printf("%d Packs/5s\n",packsNum);			
			try {//休息5s
				Thread.sleep(TestTools.SLEEP_MS);//阻塞当前进程
			} catch (InterruptedException e) {
				e.printStackTrace();
			} 
		}
	}
	/**
	 * 开始线程
	 */
	public void start () {
		System.out.println("Starting TestTools thread");
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
		System.out.println("Stopping TestTools" );
		t.interrupt();
	}
}
