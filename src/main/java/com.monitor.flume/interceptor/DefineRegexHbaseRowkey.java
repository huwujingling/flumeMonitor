package com.monitor.flume.interceptor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.lang.ArrayUtils;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.apache.zookeeper.CreateMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 此类用于在flume拦截器添加habse中基于flume event中指定索引的字段拼接而成的rowkey，并对数据做一定处理.
 */
public class DefineRegexHbaseRowkey implements Interceptor {
    static Map<String, String> confMap;//配置文件中拦截器配置映射
    private Map<String, String> eventMap;//传输信息到zk
    Gson gson = new Gson();
    private String hbaseRowkey;//配置文件中的拦截器配置
    private String[] habseRowkeyRules;//rowkey连接规则
    private String[] context;//event body
    private String symbol;//配置内容切割符
    private int index[];//拼接索引
    private String confName;//flume配置文件中拦截器的配置参数.提供配置文件的名字
    private String parentNode;//zk父节点
    private String zkHost;//zk足迹
    private String receiver;//收件人
    private String monitorPort;//监控端口
    private String flumeWatchCycle;
    private ZkClient zkClient;
    private String zkData;


    public void initialize() {
        try {
            //獲取並檢查zkHost
            zkHost = confMap.get("zkHost");
            if (zkHost == null || zkHost == "")
                throw new Exception("\n=====================================================================================\n"
                        + "--zkHost is empty ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            try {
                //注册zk服务
                zkClient = new ZkClient(zkHost, 6000, 15000);
            } catch (Exception e) {
                throw new Exception("\n=====================================================================================\n"
                        + "--zkHost:" + zkHost + " format error ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            }

            //获取配置参数
            //获取并检查parentNode
            parentNode = confMap.get("parentNode");
            if (parentNode == null || parentNode == "")
                throw new Exception("\n=====================================================================================\n"
                        + "--parentNode is empty ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            if (!zkClient.exists(parentNode))
                throw new Exception("\n=====================================================================================\n"
                        + "--it's not exist parentNode in zkNodes: " + parentNode + ",please check your flume conf file!!!\n"
                        + "=====================================================================================\n");

            //获取并检查monitorPort
            monitorPort = confMap.get("monitorPort");
            if (monitorPort == null || monitorPort == "")
                throw new Exception("\n=====================================================================================\n"
                        + "--monitorPort is empty ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");

            try {
                Integer.parseInt(monitorPort);
            } catch (NumberFormatException e) {
                throw new Exception("\n=====================================================================================\n"
                        + "--monitorPort:" + monitorPort + " format error ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            }

            //获取并检查receiver
            receiver = confMap.get("receiver");
            if (receiver == null || receiver == "")
                throw new Exception("\n=====================================================================================\n"
                        + "--receiver is empty ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            try {
                String[] receivers = receiver.split(";");
                for (String str : receivers) {
                    if (!str.contains("@") || !str.contains(".com")) {
                        throw new Exception();
                    }
                }
            } catch (Exception e) {
                throw new Exception("\n=====================================================================================\n"
                        + "--receiver: " + receiver + " format error ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            }

            //获取并检查flumeWatchCycle,可以允許為空，採取默認週期進行監控
            flumeWatchCycle = confMap.get("flumeWatchCycle");
            if (flumeWatchCycle != null && flumeWatchCycle != "") {
                int mod;
                try {
                    mod = Integer.parseInt(flumeWatchCycle) % 60000;//线程休眠为long mis
                } catch (NumberFormatException e) {
                    throw new Exception("\n=====================================================================================\n"
                            + "--flumeWatchCycle: " + flumeWatchCycle + " format error ,please check your flume conf file!!!\n"
                            + "=====================================================================================\n");
                }
                if (mod !=0)
                    throw new Exception("\n=====================================================================================\n"
                            + "--flumeWatchCycle: " + flumeWatchCycle + " must be a multiple of 60000  ,please check your flume conf file!!!\n"
                            + "=====================================================================================\n");
            }else
                flumeWatchCycle = 60000 + "";

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }


        //检测confName是否已经使用
        if (confMap.get("confName") == null || confMap.get("confName") == "")
            try {
                throw new Exception("\n=====================================================================================\n"
                        + "--confName is empty ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        else {
            confName = confMap.get("confName");
            List<String> children = zkClient.getChildren(parentNode);
            Map currentNodeMap = new HashMap<String, String>();
            if (children != null) {
                for (String node : children) {
                    String nodePath = parentNode + "/" + node; //拿到node在zookeeper中的路径
                    String zkData = zkClient.readData(nodePath).toString();
                    JsonObject returnData = new JsonParser().parse(zkData).getAsJsonObject();
                    currentNodeMap = gson.fromJson(returnData, HashMap.class);
                    try {
                        if (confName.equals(currentNodeMap.get("confName"))) {
                            throw new Exception("\n=====================================================================================\n"
                                    + "--already exists confName in watching,please check flume conf file; 重复的confName!!!\n"
                                    + "=====================================================================================\n");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(0);
                    }
                }
            }
        }


        //将信息封装到map装成json传输到zk
        eventMap = new HashMap<String, String>();
        //获取并检查currentHost
        try {
            eventMap.put("currentHost", InetAddress.getLocalHost().getHostAddress().toString());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        eventMap.put("confName", confName);
        eventMap.put("receiver", receiver);
        eventMap.put("monitorPort", monitorPort);
        eventMap.put("flumeWatchCycle", flumeWatchCycle);
        zkData = gson.toJson(eventMap);


        String create = zkClient.create(parentNode + "/" + confName, zkData, CreateMode.EPHEMERAL_SEQUENTIAL);

        //自定义hbaseRowkey
        hbaseRowkey = confMap.get("hbaseRowkey");

        try {
            if (hbaseRowkey == null || hbaseRowkey == "")
                throw new Exception("\n=====================================================================================\n"
                        + "--hbaseRowkey is empty ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            try {
                hbaseRowkey.split(",");
            } catch (Exception e) {
                throw new Exception("\n=====================================================================================\n"
                        + "--hbaseRowkey: " + hbaseRowkey + " format error ,please check your flume conf file!!!\n"
                        + "=====================================================================================\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }

        habseRowkeyRules = hbaseRowkey.split(",");
        index = new int[habseRowkeyRules.length - 1];
        symbol = habseRowkeyRules[0];

        //获取自定义索引组成数组
        for (int i = 1; i < habseRowkeyRules.length; i++) {
            try {
                try {
                    index[i - 1] = Integer.valueOf(habseRowkeyRules[i]);
                } catch (Exception e) {
                    throw new Exception("\n=====================================================================================\n"
                            + "--hbaseRowkey: " + hbaseRowkey + " format error ,please check your flume conf file!!!\n"
                            + "=====================================================================================\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public Event intercept(Event event) {
        return event;
    }

    public List<Event> intercept(List<Event> events) {
        List<Event> results = new ArrayList<Event>();
        String eventBody = "";

        //获取event，对每个event进行处理。
        for (Event event : events) {
            String rowkey = "";
            eventBody = new String(event.getBody());
            context = eventBody.split(",");

            //将时间转换格式
            context[4] = context[4].replaceAll("[\\/]", "-");

            //过滤空值
            for (int i = 0; i < context.length; i++) {
                if (context[i] == null || "".equals(context[i]))
                    context[i] = "N/A";
            }

            //根据索引合成rowkey
            for (int i = 0; i < index.length; i++) {
                if (i == index.length - 1) {
                    rowkey += context[index[i]] + symbol + context[4];
                    break;
                }
                rowkey += context[index[i]] + symbol;
            }

            //将rowkey添加到event的第一位
            event.setBody((rowkey + "," + ArrayUtils.toString(context).replaceAll("[\\{\\}]", "")).getBytes());
            System.out.println("====================================");
            System.out.println(new String(event.getBody()).toString());
            results.add(event);
        }

        return results;
    }

    public void close() {

    }

    public static class Builder implements Interceptor.Builder {
        public Interceptor build() {
            return new DefineRegexHbaseRowkey();
        }

        public void configure(Context context) {
            confMap = context.getParameters();
        }
    }
}
