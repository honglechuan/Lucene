package com.linkapp.hlc.test;

public class Thread01 implements Runnable{
	
	public static void main(String[] args) throws InterruptedException {
		Thread01 t=new Thread01();
		Thread02 t2=new Thread02();
		
		Thread t1=new Thread(t);
		t1.sleep(1000);
		Thread t3=new Thread(t2);
		
		t1.start();
		t3.sleep(1000);
		t3.start();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		for (int i = 0; i < 26; i++) {
			
            System.out.print((char) (65 + i));
        }
      
	}
}
