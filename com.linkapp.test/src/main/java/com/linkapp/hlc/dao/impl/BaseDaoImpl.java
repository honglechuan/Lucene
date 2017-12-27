package com.linkapp.hlc.dao.impl;

import java.util.List;

import javax.annotation.Resource;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import com.linkapp.hlc.dao.BaseDao;
import com.linkapp.hlc.entity.User;

@Repository("BaseDao")
public class BaseDaoImpl implements BaseDao{
	
	@Resource
	private SqlSessionTemplate sqlSessionTemplate;
	
	
	
	

	@Override
	public void save(User user) {
		// TODO Auto-generated method stub
		sqlSessionTemplate.insert("com.linkapp.hlc.dao.BaseDao.save",user);
	}
																									//	
	@Override
	public int select() {
		long starTime=System.currentTimeMillis();
		List<Object> count=sqlSessionTemplate.selectList("com.linkapp.hlc.dao.BaseDao.select");
		
		long endTime=System.currentTimeMillis();
		long time=endTime-starTime;
		System.out.println("执行时间："+time);
		return count.size();
	}

																								   //

	
}
