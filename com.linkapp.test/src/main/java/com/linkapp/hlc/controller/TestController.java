package com.linkapp.hlc.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import com.alibaba.fastjson.JSONObject;
import com.linkapp.hlc.entity.User;

@Controller
@RequestMapping("test")
public class TestController {

	@RequestMapping("/t1")	
	public String insert(HttpServletRequest request,HttpServletResponse response)  throws IOException{
		Map<String,String> map = new HashMap<String,String>();   
        map.put("result", "content");  
		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");
		JSONObject resultJSON = new JSONObject();
		response.setContentType("text/plain");  
		response.setHeader("Pragma", "No-cache");  
		response.setHeader("Cache-Control", "no-cache");  
		response.setHeader("Access-Control-Allow-Origin", "*");//添加跨域访问
		response.setDateHeader("Expires", 0);   
		resultJSON.put("result", "ok123");
		
		try {       
			String str=request.getParameter("dtp1");
			 PrintWriter out = response.getWriter();       
			 out.println("callback"+"("+resultJSON.toJSONString()+")");//返回jsonp格式数据 ，要用callback包装下
			 out.flush();  
			 out.close();  
			 System.out.print("1");
			} catch (Exception e) {
			// TODO Auto-generated catch block
				e.printStackTrace();
				e.getMessage();
			}
				return null;			
	}
	
	@RequestMapping("/test1")
	public String test1( User user){
	
		String a=user.getId();
		String b=user.getName();
		String c=user.getDate();
		
		System.out.println("ok");
		return "index";
	}
	
	@RequestMapping("/login")
	public String login(){
		
		return "MyJsp";
	}
	
	

}
