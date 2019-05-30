package com.sorl.frontend;
/**
* 
* 由客户端发送的管理员信息
*
* @author  nesc420
* @Date    2019-4-7
* @version 0.1.0
*/
public class Admin {
   private String name;
   private String key;
   public void setName(String name) {
      this.name = name;
   }
   public String getName() {
      return name;
   }
   public void setKey(String key) {
      this.key = key;
   }
   public String getKey() {
      return key;
   }
}
