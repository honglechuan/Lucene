/**
 * 
 */
package com.linkapp.hlc.activemq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

/** 
 * @ClassName:     ActiveMQTopicPro 
 * @Description:   topic消费者1
 * @author:        HongLC 
 * @date:          2017年12月8日 上午9:54:04 
 *  
 */
public class ActiveMQTopicCon01 {
	
	 //默认连接用户名
    private static final String USERNAME = "admin";
    //默认连接密码
    private static final String PASSWORD = "admin";
    //默认连接地址
    private static final String BROKEURL = "tcp://192.168.0.101:61616";

    public static void main(String[] args) {
        ConnectionFactory connectionFactory;//连接工厂
        Connection connection = null;//连接

        Session session;//会话 接受或者发送消息的线程
        Destination destination;//消息的目的地

        MessageConsumer messageConsumer;//消息的消费者

        //实例化连接工厂(连接到ActiveMQ服务器)
        connectionFactory = new ActiveMQConnectionFactory(USERNAME, PASSWORD, BROKEURL);

        try {
            //通过连接工厂获取连接
            connection = connectionFactory.createConnection();
            //启动连接
            connection.start();
            //创建session
            session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
            //生产者将消息发送到MyTopic，所以消费者要到MyTopic去取
            destination = session.createTopic("MyTopic");
            //创建消息消费者
            messageConsumer = session.createConsumer(destination);

            Message message = messageConsumer.receive();
            while (message != null) {
                TextMessage txtMsg = (TextMessage) message;
                System.out.println("ActiveMQTopicCon01收到消息：" + txtMsg.getText());
                message = messageConsumer.receive(1000L);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }

    }
}
