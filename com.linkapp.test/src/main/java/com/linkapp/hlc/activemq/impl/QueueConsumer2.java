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
 * @ClassName:     QueueConsumer2
 * @Description:   TODO
 * @author:        HongLC 
 * @date:          2017年12月7日 下午6:09:22 
 *  
 */
@Service
public class QueueConsumer2 implements MessageListener{
	
	 public void onMessage(Message message) {
          TextMessage textMessage = (TextMessage)message;
          try {
              System.out.println("消费的QueueConsumer2获取消息:"+textMessage.getText());
          } catch (JMSException e) {
              e.printStackTrace();
          }
      }
}
