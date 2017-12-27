package com.linkapp.hlc.controller;

import java.io.IOException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.linkapp.hlc.service.BaseService;

@Controller
@RequestMapping("db")
public class BaseController {

	@Resource private BaseService baseService;
	@RequestMapping("/insert")	
	public void insert(HttpServletRequest request,HttpServletResponse response)  throws IOException{
		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");
		System.out.println("1");
		long starTime=System.currentTimeMillis();
		baseService.save();
		long endTime=System.currentTimeMillis();
		long time=endTime-starTime;
		System.out.println();
		response.getWriter().print("执行时间："+time);			
	}
	@RequestMapping("/sel")	
	public void select(HttpServletRequest request,HttpServletResponse response) throws IOException{
		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");
		System.out.println("1");
		long starTime=System.currentTimeMillis();
		int count=baseService.select();
		long endTime=System.currentTimeMillis();
		long time=endTime-starTime;
		System.out.println();
		response.getWriter().print(count+"执行时间："+time);
		//return  String.valueOf(count);
		
		
	}
}
