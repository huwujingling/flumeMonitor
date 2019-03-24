package com.csot.flume.watchservice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;

import javax.mail.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceNodeUpAndDown {
    Gson gson =new Gson();
    FlumeMetricsMonitor flumeMetricsMonitor =new FlumeMetricsMonitor();
    static ZkClient zk;
    static String parentNode;
    int first = 1;
    HashMap<String, String> nodes = new HashMap<>(); //存放注册的节点名称
    static MailManager mailManager;
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String Recipient;//收件人.
    Map<String, String> eventMap = new HashMap<String, String>();
    String currentHost;//主机
    String confName;//配置文件名
    String receiver;//收件人
    String moduleAlias;//组件别名
    String monitorPort;//监控端口
    double monitorTime;
    Map<String,Map> monitorMap;

    //初始化邮件服务器信息
    static {
        Properties properties = new Properties();
        // 使用InPutStream流读取properties文件
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader("E://config.properties.txt"));//watchservice.properties
            properties.load(bufferedReader);

            //连接zookeeper
            zk = new ZkClient(properties.getProperty("zkClient"));

            //获取parentNode
            parentNode = properties.getProperty("parentNode");

            // 获取key对应的value值
            mailManager = new MailManager(
                    properties.getProperty("host"),
                    properties.getProperty("port"),
                    properties.getProperty("auth"),
                    properties.getProperty("userName"),
                    properties.getProperty("domainUser"),
                    properties.getProperty("passWord")
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * * 监听服务器的 动态上下线通知，并且通过邮件告知信息   
     */
    public void listenerForServerNodes() throws UnsupportedEncodingException, MessagingException {
        System.out.println("listenerForServerNodes()方法调用了");
        monitorMap = new ConcurrentHashMap<>();

        List<String> children = zk.getChildren(parentNode); //返回子节点的名称

        if (children == null || children.size() == 0) { //如果子parentNode 下面没有 服务器节点注册，则发送邮件通知没有
            isDown(children);
            //System.out.println("邮件已经发送，当前没有任何服务器注册");
            mailManager.setToReceiverAry(new String[]{"hujun8@tcl.com"});
            mailManager.SendMail("[INFO][FLUME][BIG_DATA]", "<b>event</b>:当前没有flume服务启动<br>" + "<b>time</b>:" + formatter.format(new Date()));
            //在parentNode的节点写没有接的情况，应该清空节点名称历史。防止新加入的节点

        } else {
            //判断新加入的服务器，并发送邮件通知
            for (String node : children) {
                String nodePath = parentNode + "/" + node; //拿到node在zookeeper中的路径
                String nodeName = nodes.get(node);
                //获取zk中存储的数据
                String zkData = zk.readData(nodePath).toString();
                JsonObject returnData = new JsonParser().parse(zkData).getAsJsonObject();
                eventMap = gson.fromJson(returnData, ConcurrentHashMap.class);
                System.out.println(eventMap.toString());

                receiver = eventMap.get("receiver");
                currentHost = eventMap.get("currentHost");
                confName = eventMap.get("confName");
                moduleAlias=eventMap.get("moduleAlias");
                monitorPort=eventMap.get("monitorPort");

                //存放到监控metrics用于获取数据的map
                monitorMap.put(eventMap.get("confName"),eventMap);
                //存放到用于判断网络是否断开的节点




                mailManager.setToReceiverAry(receiver.split(";"));

                if (nodeName == null) {
                    //发送邮件 通知新加入的节点为 nodeName
                    if (first == 1) {
                        //System.out.println("发送邮件 通知已存在的节点为: " + node);
                        mailManager.SendMail("[INFO][FLUME][BIG_DATA]"
                                , "<b>event</b>: 已存在flume节点" + "<br>" +
                                        "<b>process</b>:" + confName + "<br>" +
                                        "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                        "<b>server</b>:" + currentHost + "<br>"
                        );
                        nodes.put(node, zk.readData(nodePath).toString());
                    } else {
                        //System.out.println("发送邮件 通知新加入的节点为: " + node);
                        mailManager.SendMail("[INFO][FLUME][BIG_DATA]",
                                "<b>event</b>:加入flume节点" + "<br>" +
                                        "<b>process</b>:" + confName + "<br>" +
                                        "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                        "<b>server</b>:" + currentHost + "<br>");
                        nodes.put(node, zk.readData(nodePath).toString());
                    }
                } else {//重新刷新 nodes 中的几点，保证nodes 中存放的都是当前正在运行并且稳定的节点
                    nodes.put(node, zk.readData(nodePath).toString());
                }
            }
            first += 1;
            isDown(children);
        }


        //进行metrics监控
        while(!Thread.currentThread().isInterrupted()){
            //flume下线会重新此方法，可能会为空
            if(monitorMap == null || monitorMap.isEmpty())
                break;
            try {
                for (String conf : monitorMap.keySet()) {
                    int monitorMapSize = monitorMap.size();
                    System.out.println("http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics");

                    //循环过程中会移除网络异常的flume
                    if(monitorMap == null || monitorMap.isEmpty())

                        break;
                    flumeMetricsMonitor.metricsMonitor(zk,parentNode,monitorMap,conf,"r1","c1","k1");

                    if (monitorMap.size() != monitorMapSize)
                        Thread.sleep(60000);
                }

               Thread.sleep(10000);//完成一周期的监控休眠10s
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("当前没有监控");
    }

    /**
     * * 判断宕机的节点
     * * @param children     
     **/
    private void isDown(List<String> children) throws UnsupportedEncodingException, MessagingException {
        //判断哪些节点宕机
        if (nodes.size() != 0) {
            Set<Map.Entry<String, String>> entrySet = nodes.entrySet();
            HashMap<String, String> downNodes = new HashMap<String, String>(); //存放宕机的节点
            // 历史节点与当前存在的节点相比较，判断出哪个节点宕机了，
            for (Map.Entry<String, String> entry : entrySet) {
                String key = entry.getKey();
                boolean is_exists = false; //表示key 是否存在默认不存在，如果存在则为true
                for (String nodeName : children) {
                    if (nodeName.equalsIgnoreCase(key)) {
                        is_exists = true;
                    }
                }

                if (!is_exists) {
                    downNodes.put(key, entry.getValue());
                }
            }

            //准备发送邮件通知宕机的节点
            System.out.println("downNodesSize:" + downNodes.size() + "===========================");
            if (downNodes.size() != 0) {
                for (Map.Entry<String, String> node : downNodes.entrySet()) {
                    //获取zk中存储的数据
                    String[] conf = node.getValue().split(":");
                    //System.out.println("发送邮件通知宕机节点为 : " + node.getKey());
                    mailManager.SendMail("[ERORR][FLUME][BIG_DATA]",
                            "<b>event</b>:flume进程异常退出<br>" +
                                    "<b>process</b>:" + confName + "<br>" +
                                    "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                    "<b>server</b>:" + currentHost + "<br>");
                    nodes.remove(node.getKey()); //移除宕机的节点
                }
                downNodes.clear();
            }
        }
    }

    private void serverNodeListener() throws Exception {
        zk.subscribeChildChanges(parentNode, new IZkChildListener() {
            @Override
            public void handleChildChange(String s, List<String> list) throws Exception {
                listenerForServerNodes();
            }
        });
        listenerForServerNodes();
    }

    public static void main(String[] args) throws Exception {
        ServiceNodeUpAndDown sud = new ServiceNodeUpAndDown();
        sud.serverNodeListener();

        synchronized (sud) {
            sud.wait(); //wait 等待 让出竞争锁
        }
    }
}
