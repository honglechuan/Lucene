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
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

/** 
 * @ClassName:     ActiveMQTopicPro 
 * @Description:   TODO
 * @author:        HongLC 
 * @date:          2017年12月8日 上午9:54:04 
 *  
 */
public class ActiveMQTopicPro {
	

	    //默认连接用户名
	    private static final String USERNAME = "admin";
	    //默认连接密码
	    private static final String PASSWORD = "admin";
	    //默认连接地址
	    private static final String BROKEURL = "tcp://192.168.0.101:61616";
	    //发送的消息数量
	    private static final int SENDNUM = 10;

	    public static void main(String[] args) {
	        //连接工厂
	        ConnectionFactory connectionFactory;
	        //连接
	        Connection connection = null;
	        //会话 接受或者发送消息的线程
	        Session session;
	        //消息的目的地
	        Destination destination;
	        //消息生产者
	        MessageProducer messageProducer;
	        //实例化连接工厂(连接到ActiveMQ服务器)
	        connectionFactory = new ActiveMQConnectionFactory(USERNAME, PASSWORD, BROKEURL);

	        try {
	            //通过连接工厂获取连接
	            connection = connectionFactory.createConnection();
	            //启动连接
	            connection.start();
	            //创建session
	            session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
	            //创建一个名称为MyTopic的消息队列(生产者生成的消息放在哪)
	            destination = session.createTopic("MyTopic");
	            //创建消息生产者
	            messageProducer = session.createProducer(destination);
	            //发送消息
	            sendMessage(session, messageProducer);

	            session.commit();

	        } catch (Exception e) {
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

	    /**
	     * 发送消息
	     *
	     * @param session
	     * @param messageProducer 消息生产者
	     * @throws Exception
	     */
	    public static void sendMessage(Session session, MessageProducer messageProducer) throws Exception {
	        for (int i = 0; i < SENDNUM; i++) {
	            //创建一条文本消息
	            TextMessage message = session.createTextMessage("ActiveMQ 发送Topic消息" + i);
	            //System.out.println("发送消息：Activemq 发送Topic消息" + i);
	            //通过消息生产者发出消息
	            messageProducer.send(message);
	        }

	    }
	}

