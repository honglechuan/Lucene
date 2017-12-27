package com.linkapp.hlc.test;

public class Thread02 implements Runnable{


	@Override
	public void run() {
		
        for (int i = 0; i < 26; i++) {
            System.out.print((char) (97 + i));
        }
	}
}
