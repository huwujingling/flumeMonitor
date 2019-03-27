package com.csot.flume.watchservice;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

/**
 * 该类为邮件封装类
 * 设置邮件收发人，内容，日期，以及发送功能
 *
 * @author Yangy
 */
public class MailManager {

    private String host;
    private String port;
    private String auth;
    private String username;
    private String domainUser;
    private String password;
    //获取接收人 "csot_50043671@tcl.com","dangxueting@tcl.com","denganzhi01@tcl.com"
    private String[] toReceiverAry;

    public MailManager(String host, String port, String auth, String username, String domainUser, String password) {
        this.host = host;
        this.auth = auth;
        this.username = username;
        this.domainUser = domainUser;
        this.password = password;
        this.port = port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    public void setDomainUser(String domainUser) {
        this.domainUser = domainUser;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public MailManager setToReceiverAry(String[] toReceiverAry) {
        this.toReceiverAry = toReceiverAry;
        return this;
    }

    /**
     * 发送邮件
     *
     * @param subject 标题
     * @param msgtext 内容
     * @return
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public boolean SendMail(String subject, String msgtext) throws MessagingException, UnsupportedEncodingException {
        boolean flag = false;

        Properties properties = new Properties();
        //设置smtp协议
        properties.put("mail.transport.protocol", "SMTP");
        //设置服务器连接地址
        properties.put("mail.smtp.host", host);
        //设置TLS保护连接，默认为false
        properties.put("mail.smtp.starttls.enable", "false");
        //设置身份校验
        properties.put("mail.smtp.auth", "true");
        //设置默认端口号
        properties.put("mail.smtp.port", port);

        //使用环境属性和授权信息，创建邮件会话
        Session session = Session.getDefaultInstance(properties);
        //控制台打印日志
        session.setDebug(true);
        //创建邮件消息
        Message msg = new MimeMessage(session);

        //设置发件人
        InternetAddress from = new InternetAddress(username);
        msg.setFrom(from);

        if (toReceiverAry.length > 0) {
            InternetAddress[] toAddr = new InternetAddress[toReceiverAry.length];
            for (int i = 0; i < toReceiverAry.length; i++) {
                toAddr[i] = new InternetAddress(toReceiverAry[i]);
            }


            //设置收件人的邮箱
            msg.setRecipients(Message.RecipientType.TO, toAddr);
            //设置邮件标题
            msg.setSubject(subject);
            //设置发送时间
            msg.setSentDate(new Date());
            //设置邮件的内容体
            BodyPart mbp = new MimeBodyPart();
            mbp.setContent(msgtext, "text/html;charset=utf-8");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(mbp);
            msg.setContent(multipart);

            msg.saveChanges();
            //建立邮件传输对象
            Transport transport = session.getTransport("smtp");
            //与服务端建立连接
            transport.connect(host, from.toString().split("@")[0], password);
            //发送邮件
            transport.sendMessage(msg, msg.getAllRecipients());
            //关闭
            transport.close();

            flag = true;
        } else {
            System.out.println("接收用户为空");
        }

        return flag;
    }
}
