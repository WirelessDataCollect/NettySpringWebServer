package com.nesc.Backend;

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

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(true) {
			packsNum = RunDeviceServer.getPacksNum();  //获取packsnums
			RunDeviceServer.resetPacksNum();  //packsnums = 0
			System.out.printf("Packs num: %d\t"+packsNum);			
			try {//休息10s
				Thread.sleep(5000);//阻塞当前进程
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
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
