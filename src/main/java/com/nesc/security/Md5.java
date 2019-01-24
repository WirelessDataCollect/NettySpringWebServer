package com.nesc.security;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/**
* 
* 双MD5加密用户密码和盐值,md5(md5(密码明文)+salt)
*
* @author  nesc418
* @Date    2019-1-24
* @version 0.0.1
*/
public class Md5 {
	public MessageDigest md;//获取md5算法实例
	/**
	 * 初始化MD5对象
	 * @throws NoSuchAlgorithmException
	 */
	public Md5() throws NoSuchAlgorithmException{
        md = MessageDigest.getInstance("MD5");		
	}
	/**
	 * 获取加密后的信息摘要
	 * @return {@link String}
	 */
    public String getKeySaltHash(String userKey,String salt) {
        String digest = null;
        try {
        	//将字符串转化为byte
            byte[] keyByte = userKey.getBytes("UTF-8");
            byte[] saltByte = salt.getBytes("UTF-8");
             //将用户密码进行md5加密
            byte[] userKeyHash = this.md.digest(keyByte);
            //md5(密码明文)+salt，拼接
            byte[] cat = new byte[userKeyHash.length + saltByte.length];
            System.arraycopy(userKeyHash, 0, cat, 0, userKeyHash.length);  
            System.arraycopy(saltByte, 0, cat, userKeyHash.length, saltByte.length);           
            byte[] hash = this.md.digest(cat);
            //byte->string
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            digest = sb.toString();
 
        } catch (UnsupportedEncodingException e) {
        	e.printStackTrace();
        }
        return digest;
    }
}
