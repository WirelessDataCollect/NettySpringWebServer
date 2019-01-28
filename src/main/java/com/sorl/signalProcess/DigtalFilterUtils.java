package com.sorl.signalProcess;

/**
* 
* 数字滤波器工具箱
*
* @author  nesc418
* @Date    2018-11-6
* @version 0.0.1
*/
public class DigtalFilterUtils {

	/**
	 * RC低通滤波器
	 * @param currentVal 本次数值
	 * @param lastVal 上一个数值
	 * @param alpha RC系数
	 * @return float 滤波后的数值数值
	 */
	public static float RcFilter(float currentVal,float lastVal,float alpha) {
		return (alpha*currentVal + (1-alpha)*lastVal);
	}
	/**
	 * 限幅滤波，本次数值和上一次的数值之差不超过limit
	 * @param currentVal 本次数值
	 * @param lastVal 上一次的数值
	 * @param limit 门限，正数
	 * @return float 滤波后的数值数值
	 */
	public static float LimitFilter(float currentVal,float lastVal,float limit) {
		assert(limit>0);
		return (Math.abs(currentVal-lastVal)<limit)?currentVal:lastVal;
	}
	/**
	 * 得到限幅滤波的limit参数
	 * @param sps
	 * @param slewRate
	 * @param highestLevel
	 * @return float 返回limit参数
	 */
	public static float getLimit(float sps,float slewRate,float highestLevel) {
		//TODO
		return 400;
	}
}
