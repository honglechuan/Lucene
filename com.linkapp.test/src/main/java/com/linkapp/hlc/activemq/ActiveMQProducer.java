/**
 * 
 */
package com.linkapp.hlc.activemq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

/** 
 * @ClassName:     ActiveMQProducer 
 * @Description:   消息生产者
 * @author:        HongLC 
 * @date:          2017年12月7日 下午5:34:12 
 *  
 */
public class ActiveMQProducer {

	 @Test
      public void testProduceMsg() throws Exception{
		// 连接工厂
		ConnectionFactory factory;
		// 连接实例
		Connection connection = null;
		// 收发的线程实例
		Session session;
		// 消息发送目标地址
		Destination destination;
		// 消息创建者
		MessageProducer messageProducer;
		try {
			// 默认地址用户密码
			factory = new ActiveMQConnectionFactory();
			// 获取连接实例
			connection = factory.createConnection();
			// 启动连接
			connection.start();
			// 创建接收或发送的线程实例（创建session的时候定义是否要启用事务，且事务类型是Auto_ACKNOWLEDGE也就是消费者成功在Listern中获得消息返回时，会话自动确定用户收到消息）
			session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			// 创建队列（返回一个消息目的地）
			destination = session.createQueue("hlcQuene");
			// 创建消息生产者
			messageProducer = session.createProducer(destination);
			// 创建TextMessage消息实体
			/*TextMessage message = session
					.createTextMessage("我是hlc,这是我的第一个消息！"+0);*/
			for (int i = 0; i < 10; i++) {
				messageProducer.send(session
						.createTextMessage("我是hlc,这是我的第一个消息....！"+i));
			}
		
			session.commit();
		} catch (JMSException e) {
			e.printStackTrace();
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}
		}
     }
}
