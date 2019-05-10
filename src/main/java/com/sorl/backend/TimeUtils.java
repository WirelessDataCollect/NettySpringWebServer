package com.sorl.backend;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
//import java.util.TimeZone;

/**
* 
* 时间获取帮助类
*
* @author  nesc420
* @Date    2019-5-7
* @version 0.1.0
*/
public class TimeUtils {
	/**
	 * 获取当前的yyyy-MM-dd'T'HH:mm:ss时间
	 * @return {@link String}
	 */
	public static String getStrIsoSTime() {
		Calendar calendar = new GregorianCalendar();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); 
		return df.format(calendar.getTime());
	}
	/**
	 * 获取当前的yyyy-MM时间
	 * @return {@link String}
	 */
	public static String getStrIsoMTime() {
		Calendar calendar = new GregorianCalendar();
		DateFormat df = new SimpleDateFormat("yyyy-MM"); 
		return df.format(calendar.getTime());
	}
}
