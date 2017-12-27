package com.linkapp.hlc.test;
/**
 * 
 * @ClassName:     Test02170927 
 * @Description:   TODO
 * @author:        HongLC 
 * @date:          2017年12月6日 下午5:41:22 
 *
 */
public class Test02170927 {
	public static void main(String[] args) {
		System.out.println(isEmpty(""));
	}
	
	public static boolean isEmpty(String str)
	{
		boolean flag = false;
		if(null != str)
		{
			if(str.trim().length()==0)
			{
				flag = true;
			}
		}
		else 
		{
			flag = true;
		}
		return flag;
	}
}
