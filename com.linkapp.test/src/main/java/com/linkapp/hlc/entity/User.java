package com.linkapp.hlc.entity;

public class User {

	private String id;
	private String name;
	private String date;
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public User(String id, String name, String date) {
		super();
		this.id = id;
		this.name = name;
		this.date = date;
	}
	public User() {
		super();
	}
	
	
}
