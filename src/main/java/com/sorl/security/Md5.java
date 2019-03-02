package com.sorl.security;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.lang3.RandomStringUtils; 
/**
* 
* 双MD5加密用户密码和盐值,md5(md5(密码明文)+salt)
*
* @author  nesc418
* @Date    2019-1-25
* @version 0.0.1
*/
public class Md5 {
	/**
	 * 获取随机字符串
	 * @return String
	 */
	public static String getRandStr() {
		return RandomStringUtils.randomAlphanumeric(20);
	}
	/**
	 * 获取加密后的信息摘要，默认大写
	 * @return {@link String}
	 */
    public static String getKeySaltHash(String userKey,String salt) {
    	String digest = null;
        try {
            byte[] keyByte = userKey.getBytes("UTF-8");
            byte[] saltByte = salt.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
             
            byte[] userKeyHash = md.digest(keyByte);
//            StringBuilder sb1 = new StringBuilder(2 * userKeyHash.length);
//            for (byte b : userKeyHash) {
//                sb1.append(String.format("%02x", b & 0xff));
//            }     
//            digest = sb1.toString();
            
            byte[] cat = new byte[userKeyHash.length + saltByte.length];
            System.arraycopy(userKeyHash, 0, cat, 0, userKeyHash.length);  
            System.arraycopy(saltByte, 0, cat, userKeyHash.length, saltByte.length); 
            StringBuilder sb = new StringBuilder(2 * cat.length);
            for (byte b : cat) {
                sb.append(String.format("%02x", b & 0xff));
            }     
            digest = sb.toString();            
//            System.out.println("Key+salt:"+digest);
//            String keyHashCatSalt = userKeyHash.toString().concat(salt);//把salt接到后面
            byte[] hash = md.digest(cat);
            //converting byte array to Hexadecimal String
            sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
 
            digest = sb.toString();
 
        }catch (NoSuchAlgorithmException ex) {
            //Logger.getLogger(StringReplace.class.getName()).log(Level.SEVERE, null, ex);
        }catch (UnsupportedEncodingException e) {
        	e.printStackTrace();
        }
        return digest.toUpperCase();
    }
}
