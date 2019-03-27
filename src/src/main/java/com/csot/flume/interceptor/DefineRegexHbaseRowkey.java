package com.csot.flume.interceptor;

import com.google.gson.Gson;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.lang.ArrayUtils;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.apache.zookeeper.CreateMode;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 此类用于在flume拦截器添加habse中基于flume event中指定索引的字段拼接而成的rowkey，并对数据做一定处理.
 */
public class DefineRegexHbaseRowkey implements Interceptor {
    static Map<String,String> map;//配置文件中拦截器配置映射
    static Map<String,String> eventMap ;//传输信息到zk
    Gson gson = new Gson();
    static String hbaseRowkey;//配置文件中的拦截器配置
    static String[] habseRowkeyRules;//rowkey连接规则
    static String[] context;//event body
    static String symbol;//配置内容切割符
    static int index[] ;//拼接索引
    static String confName;//flume配置文件中拦截器的配置参数.提供配置文件的名字
    static String parentNode ;//zk父节点
    static String zkHost;//zk足迹
    static String receiver;//收件人
    private String moduleAlias;//组件别名
    private String monitorPort;//监控端口
    String zkData;


    public void initialize() {
        try{
            //获取配置参数
            hbaseRowkey=map.get("hbaseRowkey");
            parentNode=map.get("parentNode");
            confName = map == null?"a1.sources.r1.interceptors.i1.confName命名出错":map.get("confName");
            moduleAlias = map.get("moduleAlias");
            monitorPort = map.get("monitorPort");
            zkHost=map.get("zkHost");
            receiver=map.get("receiver");
            //注册zk服务
            ZkClient zkClient = new ZkClient(zkHost);

            //将信息封装到map装成json传输到zk
            eventMap = new HashMap<String, String>();
            eventMap.put("currentHost",InetAddress.getLocalHost().getHostAddress().toString());
            eventMap.put("confName",confName);
            eventMap.put("receiver",receiver);
            eventMap.put("moduleAlias",moduleAlias);
            eventMap.put("monitorPort",monitorPort);
            zkData = gson.toJson(eventMap);


            String create = zkClient.create(parentNode + "/"+confName ,zkData,CreateMode.EPHEMERAL_SEQUENTIAL);
        } catch (Exception e) {
            e.printStackTrace();
        }



        if (hbaseRowkey == null)
            throw new RuntimeException("*.sources.*.interceptors.*.habseRowkey error");

        habseRowkeyRules = hbaseRowkey.split(",");
        index = new int[habseRowkeyRules.length-1];
        symbol = habseRowkeyRules[0];

        //获取自定义索引组成数组
        for (int i = 1;i < habseRowkeyRules.length;i++){
            if (habseRowkeyRules[i] != null || !"".equals(habseRowkeyRules[i])){
                index[i-1] = Integer.valueOf(habseRowkeyRules[i]);
            } else {
                throw new RuntimeException("*.sources.*.interceptors.*.habseRowkey error");
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
            eventBody=new String(event.getBody());
            context = eventBody.split(",");

            //将时间转换格式
            context[4] = context[4].replaceAll("[\\/]","-");

            //过滤空值
            for (int i = 0; i < context.length; i++) {
                if(context[i] == null || "".equals(context[i]))
                    context[i] = "N/A";
            }

            //根据索引合成rowkey
            for (int i = 0;i < index.length;i++){
                if (i == index.length-1) {
                    rowkey += context[index[i]]+ symbol+context[4];
                    break;
                }
                rowkey += context[index[i]] + symbol;
            }

            //将rowkey添加到event的第一位
            event.setBody((rowkey+","+ ArrayUtils.toString(context).replaceAll("[\\{\\}]","")).getBytes());
            System.out.println("====================================");
            System.out.println(new String(event.getBody()).toString());
            results.add(event);
        }

        return results;
    }

    public void close() {

    }

    public static class Builder implements Interceptor.Builder{
        public Interceptor build() {
            return new DefineRegexHbaseRowkey();
        }
        public void configure(Context context) {
            map = context.getParameters();
        }
    }
}
