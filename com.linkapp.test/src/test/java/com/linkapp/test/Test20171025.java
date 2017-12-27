package com.linkapp.test;

public class Test20171025 {
	public static void main(String[] args) {
		new B().b();
	}
	
	
	static class B extends C{
		public static void b(){
			System.out.println("b");
			c();
		};
	}
	
	static class C{
		public static void c(){
			System.out.println("c");
		}
	}
}
