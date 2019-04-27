package com.sorl.frontend;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.ui.ModelMap;

import com.mongodb.BasicDBObject;
import com.sorl.backend.MyMongoDB;
import com.sorl.attributes.InfoMgdAttributes;
import com.sorl.backend.RunPcServer;;

/**
* 
* 登录控制器
*
* @author  nesc420
* @Date    2019-4-7
* @version 0.1.0
*/
@Controller
//@RequestMapping("/ServerWeb")  //控制器WebController中提到的虚拟子文件夹
public class LoginController{ 
	@RequestMapping(value = "/",method = RequestMethod.GET)//地址
	   public ModelAndView page() {
		   return new ModelAndView("login","command",new Admin());//jsp文件
	   }
	
//	@RequestMapping(value = "/",method = RequestMethod.GET)//地址
//	   public String page() {
//		   return "redirect:/login";
//	   }
	
	//GET:表单中设定的参数和参数值将附加到页面地址的末尾以参数的形式提交
   @RequestMapping(value = "/login",method = RequestMethod.GET)//地址
   public ModelAndView admin() {
	   return new ModelAndView("login","command",new Admin());//jsp文件
   }
   
   //POST：在提交表单时，表达中的参数将作为请求头中的信息发送（显示.../working，和working.jsp没有关系）
   @RequestMapping(value = "/working",method = RequestMethod.POST)  //这里的value指的是地址
   public String login(@ModelAttribute("SpringWeb")Admin admin,ModelMap model) {
	   MyMongoDB infoMongodb = RunPcServer.getInfoDb();
	   //filter
	   BasicDBObject filter = new BasicDBObject();
	   filter.put((String)InfoMgdAttributes.MONGODB_USER_NAME_KEY, (String)admin.getName());
	   filter.put((String)InfoMgdAttributes.MONGODB_USER_KEY_KEY, (String)admin.getKey());
	   System.out.println(filter);
	   Long docIter = infoMongodb.count(filter) ;
	   if(docIter >= 1) {
	      model.addAttribute("name", admin.getName());
	      model.addAttribute("key", admin.getKey());
	      return "working";  //working.jsp
	   }else {
		  return "verifailed";
	   }
   }

}