package com.linkapp.hlc.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Test;

public class Test20171012 {

	
	public static void main (String[] args) {
		String url="http://127.0.0.1:8080/com.linkapp.test/db/insert.do";
		String param="1";

		try {
			String res=sendPost(url,param);
			System.out.println("返回值为"+res);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public static String sendPost(String url, String param)  throws Exception{
	    PrintWriter out = null;
	    BufferedReader in = null;
	    String result = "";
	    try {
	        URL realUrl = new URL(url);
	        URLConnection conn = realUrl.openConnection();
	        conn.setConnectTimeout(5000);
	        conn.setReadTimeout(10*1000);
	        conn.setDoOutput(true); // 发送POST请求必须设置如下两行
	        conn.setDoInput(true);
	        out = new PrintWriter(conn.getOutputStream());
	        out.print(param);
	        out.flush();
	        in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	        String line;
	        while ((line = in.readLine()) != null) {
	            result += line;
	        }
	    } catch (Exception e) {
	        //logger.error("HTTP请求路径时错误：" + url, e);
	       
	        throw e; // 异常外抛
	    } finally{
	        try{
	            if(out!=null)out.close();
	            if(in!=null) in.close();
	        }
	        catch(Exception ex){
	        }
	    }
	    return result;
	}
}
