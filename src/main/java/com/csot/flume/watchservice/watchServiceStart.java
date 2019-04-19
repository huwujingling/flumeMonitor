package com.csot.flume.watchservice;

import org.I0Itec.zkclient.ZkClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class watchServiceStart {
    private static Properties properties;
    private static MailManager mailManager;
    private static ZkClient zk;
    private static String parentNode;
    private static int watchCycle;
    private static SimpleDateFormat formatter ;
    private static ConcurrentHashMap<String, Map> monitorMap ;
    private static ConcurrentHashMap cycleMap ;//用来记录各监控对象的监控周期




    public static void main(String[] args) throws Exception {
        init();

        //zkEventThread参数赋值
        NodeUpAndDownMonitor sud = new NodeUpAndDownMonitor(mailManager,zk,parentNode,watchCycle,formatter,monitorMap,cycleMap);

        FlumeMetricsMonitor flumeMetricsMonitor = new FlumeMetricsMonitor(mailManager, formatter);

        //开启节点状态的监控
        sud.serverNodeListener();

        //开始监控metrics,这是主线程,传参
        flumeMetricsMonitor.run(zk, parentNode,monitorMap,cycleMap,watchCycle);

        System.out.println("监控结束");

       /* synchronized (sud) {
            sud.wait(); //wait等待让出竞争锁
        }*/
        /*flumeMetricsMonitor.start();
        System.out.println("输出任意键结束程序");
        new BufferedReader(new InputStreamReader(System.in)).readLine();*/
    }

    private static void init() {
        try {
            formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            monitorMap = new ConcurrentHashMap<>();
            cycleMap = new ConcurrentHashMap<String, Integer>();

            properties = new Properties();
            // 使用InPutStream流读取properties文件
            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("").getCanonicalPath().toString() + "/src/main/resources/conf/configuration.properties"));
            properties.load(bufferedReader);

            // 初始化邮件对象
            mailManager = new MailManager(
                    properties.getProperty("host"),
                    properties.getProperty("port"),
                    properties.getProperty("auth"),
                    properties.getProperty("userName"),
                    properties.getProperty("domainUser"),
                    properties.getProperty("passWord")
            );

            //连接zookeeper
            if (properties.getProperty("zkClient") == "" || properties.getProperty("zkClient") == null)
                throw new Exception("\n=====================================================================================\n"
                        + "--zkClient: " + properties.getProperty("zkClient") + " format error ,please check your conf file!!!\n"
                        + "=====================================================================================\n");

            zk = new ZkClient(properties.getProperty("zkClient"));
            //获取parentNode
            parentNode = properties.getProperty("parentNode");

            if (parentNode == "" || parentNode == null || !parentNode.contains("/"))
                throw new Exception("\n=====================================================================================\n"
                        + "--parentNode: " + parentNode + " format error ,please check your conf file!!!\n"
                        + "=====================================================================================\n");

            System.out.println(properties.getProperty("watchCycle"));
            watchCycle = Integer.parseInt(properties.getProperty("watchCycle").replaceAll(" ", ""));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("程序初始化失败,退出");
            System.exit(0);
        }

        System.out.println("程序初始化结束");
    }
}
