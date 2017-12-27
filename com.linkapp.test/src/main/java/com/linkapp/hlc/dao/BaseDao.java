package com.linkapp.hlc.dao;

import com.linkapp.hlc.entity.User;



public interface BaseDao {

	public void save(User user);
	
	public int select();
}
