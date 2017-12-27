/**
 * 
 */
package com.linkapp.hlc.activemq.impl;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

/** 
 * @ClassName:     TopicConsumer2 
 * @Description:   TODO
 * @author:        HongLC 
 * @date:          2017年12月7日 下午6:11:52 
 *  
 */
public class TopicConsumer2 {
	
	 public void onMessage(Message message) {
          TextMessage textMessage = (TextMessage)message;
          try {
              System.out.println("消费的TopicConsumer2获取消息:"+textMessage.getText());
         } catch (JMSException e) {
              e.printStackTrace();
         }
      }
}
