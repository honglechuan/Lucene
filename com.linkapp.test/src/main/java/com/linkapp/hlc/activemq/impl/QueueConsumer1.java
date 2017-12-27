/**
 * 
 */
package com.linkapp.hlc.activemq.impl;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.springframework.stereotype.Service;

/** 
 * @ClassName:     QueueConsumer1 
 * @Description:   TODO
 * @author:        HongLC 
 * @date:          2017年12月7日 下午6:09:22 
 *  
 */
@Service
public class QueueConsumer1 implements MessageListener{
	
	 public void onMessage(Message message) {
          TextMessage textMessage = (TextMessage)message;
          try {
              System.out.println("消费的QueueConsumer1获取消息:"+textMessage.getText());
          } catch (JMSException e) {
              e.printStackTrace();
          }
      }
}
