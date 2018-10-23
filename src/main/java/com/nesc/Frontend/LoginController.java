package com.nesc.Frontend;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.ui.ModelMap;

@Controller
//@RequestMapping("/ServerWeb")  //控制器WebController中提到的虚拟子文件夹
public class LoginController{ 
	//GET:表单中设定的参数和参数值将附加到页面地址的末尾以参数的形式提交
   @RequestMapping(value = "/login",method = RequestMethod.GET)//地址
   public ModelAndView admin() {
	   return new ModelAndView("login","command",new Admin());//jsp文件
   }
   
   //POST：在提交表单时，表达中的参数将作为请求头中的信息发送
   @RequestMapping(value = "/working",method = RequestMethod.POST)  //这里的value指的是地址
   public String login(@ModelAttribute("SpringWeb")Admin admin,ModelMap model) {
	   if(admin.getName().equals("song") && admin.getKey().equals("123456")) {
		      model.addAttribute("name", admin.getName());
		      model.addAttribute("key", admin.getKey());
		      return "working";  //result视图从service方法中返回，呈现result.jsp  
	   }
	   else {
		   return "verifailed";
	   }
   }
}