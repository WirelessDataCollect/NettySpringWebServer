package com.nesc.NettySpringWebServer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.ui.ModelMap;

@Controller
//@RequestMapping("/ServerWeb")  //控制器WebController中提到的虚拟子文件夹
public class PcController{ 
   @RequestMapping(value = "/login",method = RequestMethod.GET)
   public ModelAndView admin() {
	   return new ModelAndView("login","command",new Admin());
   }
   @RequestMapping(value = "/login",method = RequestMethod.POST)
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