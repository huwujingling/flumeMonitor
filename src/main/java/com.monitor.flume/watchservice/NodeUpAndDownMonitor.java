package com.monitor.flume.watchservice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;

import javax.mail.*;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeUpAndDownMonitor {
    private Gson gson ;
    private ZkClient zk;
    private String parentNode;
    private boolean firstRun ;
    private HashMap<String, String> nodes; //存放注册的节点名称
    private MailManager mailManager;
    private SimpleDateFormat formatter;
    private String currentHost;//主机
    private String confName;//配置文件名
    private String receiver;//收件人
    private String monitorPort;//监控端口
    private String flumeWatchCycle;//监控周期
    private ConcurrentHashMap<String, Map> monitorMap;
    private int watchCycle;//monitorMetrics监控周期
    private ConcurrentHashMap cycleMap ;//用来记录各监控对象的监控周期


    public NodeUpAndDownMonitor(MailManager mailManager, ZkClient zk, String parentNode, int watchCycle
            , SimpleDateFormat formatter,ConcurrentHashMap<String, Map> monitorMap,ConcurrentHashMap cycleMap) {
        this.mailManager = mailManager;
        this.zk = zk;
        this.parentNode = parentNode;
        this.watchCycle = watchCycle;
        this.formatter = formatter;
        this.monitorMap = monitorMap;
        this.cycleMap = cycleMap;
        this.nodes = new HashMap<>();
        this.gson = new Gson();
        this.firstRun =true;
    }


    /**
     * * 监听服务器的 动态上下线通知，并且通过邮件告知信息
     */
    public void listenerForServerNodes(List<String> children) throws UnsupportedEncodingException, MessagingException {
        //System.out.println("Thread.name:"+Thread.currentThread().getName()+"-----Thread.id:"+Thread.currentThread().getId());
        System.out.println("listenerForServerNodes()方法调用了");

        //如果子parentNode下面没有服务器节点注册，则发送邮件通知没有
        if (children == null || children.size() == 0) {
            isDown(children);
            //System.out.println("邮件已经发送，当前没有任何服务器注册");
            mailManager.setToReceiverAry(new String[]{"hujun8@tcl.com"});
            mailManager.SendMail("[INFO][FLUME][BIG_DATA]",
                    "<b>event</b>:当前没有flume服务启动<br>" +
                            "<b>time</b>:" + formatter.format(new Date()));
            //在parentNode的节点写没有接的情况，应该清空节点名称历史。防止新加入的节点
        } else {
            Map<String, String> eventMap = new ConcurrentHashMap<String, String>();

            //判断新加入的服务器，并发送邮件通知
            for (String node : children) {
                String nodePath = parentNode + "/" + node; //拿到node在zookeeper中的路径
                String nodeName = nodes.get(node);

                //与之前的存储集合比较
                if (nodeName == null) {
                    System.out.println(nodeName);

                    //获取zk中存储的数据
                    String zkData = zk.readData(nodePath).toString();
                    JsonObject returnData = new JsonParser().parse(zkData).getAsJsonObject();

                    eventMap = gson.fromJson(returnData, ConcurrentHashMap.class);
                    System.out.println("===================================================================");
                    System.out.println("zkData: " + zkData);
                    System.out.println("node: " + node);
                    System.out.println("nodeName: " + nodeName);
                    System.out.println("eventMap: " + eventMap.toString());
                    System.out.println("===================================================================");

                    receiver = eventMap.get("receiver");
                    currentHost = eventMap.get("currentHost");
                    confName = eventMap.get("confName");
                    monitorPort = eventMap.get("monitorPort");
                    flumeWatchCycle = eventMap.get("flumeWatchCycle");

                    if (flumeWatchCycle == null || flumeWatchCycle == "")
                        eventMap.put("flumeWatchCycle", watchCycle + "");

                    //用于monitor判断是否新加入
                    if (monitorMap.get("confName") == null)
                        eventMap.put("initialization", "true");

                    //存放到监控metrics用于获取数据的map
                    monitorMap.put(eventMap.get("confName"), eventMap);

                    //初始化每个进程的监控周期
                    //if ("true".equals(monitorMap.get("confName").get("initialization")))
                    cycleMap.put(eventMap.get("confName"), 0);

                    //发送邮通知新加入的节点为nodeName
                    if (firstRun) {
                        mailManager.setToReceiverAry(monitorMap.get(confName).get("receiver").toString().split(";")).SendMail(
                                "[INFO][FLUME][BIG_DATA]"
                                , "<b>event</b>: 已存在flume节点" + "<br>" +
                                        "<b>process</b>:" + confName + "<br>" +
                                        "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                        "<b>server</b>:" + currentHost + "<br>"
                        );
                        nodes.put(node, zk.readData(nodePath).toString());
                    } else {
                        mailManager.setToReceiverAry(monitorMap.get(confName).get("receiver").toString().split(";")).SendMail(
                                "[INFO][FLUME][BIG_DATA]",
                                "<b>event</b>:加入flume节点" + "<br>" +
                                        "<b>process</b>:" + confName + "<br>" +
                                        "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                        "<b>server</b>:" + currentHost + "<br>");
                        nodes.put(node, zk.readData(nodePath).toString());
                    }
                } else {//重新刷新nodes中的节，保证nodes中存放的都是当前正在运行并且稳定的节点
                    nodes.put(node, zk.readData(nodePath).toString());
                }
            }

            isDown(children);
            firstRun = firstRun?false:false;
        }
    }

    /**
     * * 判断宕机的节点
     * * @param children? ? ?
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
                Map<String, String> downMap = new ConcurrentHashMap<String, String>();

                for (Map.Entry<String, String> node : downNodes.entrySet()) {
                    //获取zk中存储的数据
                    JsonObject returnData = new JsonParser().parse(node.getValue()).getAsJsonObject();
                    downMap = gson.fromJson(returnData, ConcurrentHashMap.class);
                    System.out.println("==============================================================");
                    System.out.println(" node.getValue(): " + node.getValue());
                    System.out.println("downMap: " + downMap.toString());
                    System.out.println("==============================================================");
                    //System.out.println("发送邮件通知宕机节点为 : " + node.getKey());

                    mailManager.setToReceiverAry(downMap.get("receiver").toString().split(";")).SendMail(
                            "[ERORR][FLUME][BIG_DATA]",
                            "<b>event</b>:flume进程异常退出<br>" +
                                    "<b>process</b>:" + downMap.get("confName") + "<br>" +
                                    "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                    "<b>server</b>:" + downMap.get("currentHost") + "<br>");

                    nodes.remove(node.getKey()); //移除宕机的节点
                    monitorMap.remove(downMap.get("confName"));
                    cycleMap.remove(downMap.get("confName"));
                }
                downNodes.clear();
            }
        }
    }

    public void serverNodeListener() throws Exception {
        zk.subscribeChildChanges(parentNode, new IZkChildListener() {
            @Override
            public void handleChildChange(String s, List<String> list) throws Exception {
                listenerForServerNodes(list);
            }
        });
        listenerForServerNodes(zk.getChildren(parentNode));
    }
}
