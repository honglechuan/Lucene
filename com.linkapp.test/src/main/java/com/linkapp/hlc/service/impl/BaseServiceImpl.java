package com.linkapp.hlc.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import javax.annotation.Resource;

import org.junit.Test;
import org.springframework.stereotype.Service;

import com.linkapp.hlc.dao.BaseDao;
import com.linkapp.hlc.entity.User;
import com.linkapp.hlc.service.BaseService;

@Service("baseService")
public class BaseServiceImpl implements BaseService {

	@Resource
	private BaseDao baseDao;
	
	public static int i=0; 
	
	@Override
	@Test
	public void save() {
		/*for (int i = 0; i < 4; i++) {
			Thread t=new Thread(new BaseServiceImpl());
			t.start();
		}*/
		/*Thread t=new Thread(new BaseServiceImpl());
		t.start();*/
		final int count=10;
		for (int i = 0; i < count; i++) {
			new Thread(new Runnable(){

				@Override
				public void run() {
					// TODO Auto-generated method stub
					for (int j = 0; j < 200/count; j++) {
						User u=new User(new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()), ""+j, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
						baseDao.save(u);
					}
					
				}
				
			});
			
		}
		
	}
	
	
	@Override
	public int select() {
		System.out.println("2");
		return baseDao.select();
	}
		
}
