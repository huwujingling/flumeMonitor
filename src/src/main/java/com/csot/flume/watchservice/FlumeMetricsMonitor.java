package com.csot.flume.watchservice;

import com.csot.flume.domain.FlumeChannel;
import com.csot.flume.domain.FlumeSink;
import com.csot.flume.domain.FlumeSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


//此类用于向flume发送http请求，获取metrics
public class FlumeMetricsMonitor{ //extends Thread
    private static CloseableHttpClient httpClient;
    static Gson gson = new Gson();
    private static  int monitorTime  ;//监控次数
    private Map metricsMap;
    private JsonObject returnData;
    private JsonObject returnSourceData;
    private JsonObject returnChannelData;
    private JsonObject returnSinkData;
    private FlumeSink flumeSink;
    private FlumeSource flumeSource;
    private MailManager mailManager;
    private SimpleDateFormat formatter;
    boolean fisrtRun;//是否第一次运行
    private static BufferedReader bufferedReader;
    private  BufferedWriter bufferedWriter;
    private Properties properties = new Properties();
    //http所需对象
    private CloseableHttpResponse response ;
    private BufferedReader in;
    private String result;//http响应结果
    private HttpGet httpGet;
    private String lastEventDataCount;
    private  StringBuilder sb;
    private String line;
    private String NL;
    private String[] aliasArray;

    public FlumeMetricsMonitor(MailManager mailManager, SimpleDateFormat formatter) {
        this.mailManager= mailManager;
        this.formatter = formatter;
        this.fisrtRun = true;
    }

    static {
        //初始化http
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);
        cm.setDefaultMaxPerRoute(20);
        cm.setDefaultMaxPerRoute(50);
        httpClient = HttpClients.custom().setConnectionManager(cm).build();

        //初始化配置文件
        try {
            //获取metrics所在对象
            bufferedReader = new BufferedReader(new FileReader(new File("").getCanonicalPath().toString()+"/src/main/resources/conf/metrics.properties"));//watchservice.properties
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run(ZkClient zk, String parentNode, Map<String,Map> monitorMap){
        //新启服务清空之前的内容
        try {
            properties.load(bufferedReader);
            properties.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //周期进行监控
        while(!Thread.currentThread().isInterrupted()){
            try {
                System.out.println("----------------------------开始第"+(++monitorTime)+"次监控----------------------------");
                System.out.println("Thread.name:"+Thread.currentThread().getName()+"-----Thread.id:"+Thread.currentThread().getId());

                for (String conf : monitorMap.keySet()) {
                    //循环过程中会移除网络异常的flume,對應monitorMap中的key
                    if(monitorMap == null || monitorMap.isEmpty())
                        break;
                    System.out.println("http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics");
                    System.out.println("Thread.name:"+Thread.currentThread().getName()+"-----Thread.id:"+Thread.currentThread().getId());

                    aliasArray = monitorMap.get(conf).get("moduleAlias").toString().split(",");

                    metricsMonitor(zk,parentNode,monitorMap,conf,aliasArray[0],aliasArray[1],aliasArray[2]);
                }

                //flume下线会重新此方法，可能会为空
                if(monitorMap == null || monitorMap.isEmpty()){
                    //如果启动多线程在此关闭线程
                    System.out.println("当前没有可监听的对象");
                }

                if(fisrtRun){
                    System.out.println("---------------------------第"+ monitorTime +"次监控初始化完毕-----------------------");
                }

                else
                    System.out.println("--------------------第"+ monitorTime +"次运行监控，flume状态记录完毕-------------------");

                System.out.println();
                System.out.println();

                Thread.sleep(600000);//完成一周期的监控休眠600s
                fisrtRun = false;
            } catch (Exception e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void metricsMonitor(ZkClient zk,String parentNode, Map<String,Map> monitorMap, String conf, String source, String channel, String sink) throws Exception {
        //使用get访问http获取metrics
        String url ="http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics";

        metricsMap = new ConcurrentHashMap<String,String>();

        //int tryTime=0;//网络异常重试次数
        //判断网络连接每5s重试一次，超过2次则抛异常
        try{
            String metrics = get(url);
            returnData = new JsonParser().parse(metrics).getAsJsonObject();
            metricsMap = gson.fromJson(returnData, Map.class);

        }catch (Exception excption){
           System.out.println(url+",网络连接异常");
           mailManager.SendMail("[ERORR][FLUME][BIG_DATA]",
                   "<b>event</b>:flume httpMonitor http网络连接异常<br>" +
                           "<b>url</b>: " + "http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics" + "<br>" +
                           "<b>process</b>:" + conf + "<br>" +
                           "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>" +
                           "<b>time</b>:" + formatter.format(new Date()) + "<br>" );
           System.out.println(conf+"被删除,可能是该节点被删除或服务器连接不通");
           monitorMap.remove(conf);
           return;
        }


        System.out.println("网络连接正常");
        System.out.println("开始执行监控指标判断");

        //flume进程与网络端口断开快与zookeeper，这段时间会没有数据
        if(metricsMap ==null || metricsMap.isEmpty()) {
            mailManager.SendMail("[ERORR][FLUME][BIG_DATA]",
                    "<b>event</b>:flume进程关闭或所在服务器网络断开<br>" +
                            "<b>url</b>:" + "http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics" + "<br>" +
                            "<b>process</b>:" + conf + "<br>" +
                            "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                            "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>");
            System.out.println("flume进程关闭或所在服务器网络断开");
            return;
        }

        //获取各组件对象
        FlumeSink flumeSink = getModule(metricsMap,"SINK."+sink,source,sink,channel)==null?null:(FlumeSink) getModule(metricsMap,"SINK."+sink,source,sink,channel);
        FlumeChannel flumeChannel= getModule(metricsMap,"CHANNEL."+channel,source,sink,channel)==null?null:(FlumeChannel) getModule(metricsMap,"CHANNEL."+channel,source,sink,channel);
        FlumeSource flumeSource = getModule(metricsMap,"SOURCE."+source,source,sink,channel)==null?null:(FlumeSource) getModule(metricsMap,"SOURCE."+source,source,sink,channel);

        //通过指标判断各组件存活状态
        //比如下沉到habse的表被disable

        //============================source=================================
        //用于source被关闭的情况，比如执行exec source时，执行tail命令的进程退出
        if(flumeSource != null && Double.parseDouble(flumeSource.getStopTime()) > 0)
            System.out.println("目标源被异常关闭");

        //============================channel================================
        //用于监控channel的存活状态
        if (flumeChannel != null && Double.parseDouble(flumeChannel.getStopTime()) > 0)
            System.out.println("channel被异常关闭");

        //缓存通道占用百分比低于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率
        if(flumeChannel != null && Double.parseDouble(flumeChannel.getChannelFillPercentage()) > 80)
            System.out.println("通道占用百分比低于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率");


        //============================sink===================================
        //用于确认sink的存活状态
        if (flumeSink !=null && Double.parseDouble(flumeSink.getStopTime()) > 0)
            System.out.println("sink异常关闭");

        //sink连接失败，需要找原因
        //比如file sink时下沉文件夹被删掉时，"ConnectionCreatedCount":"1",
        // "ConnectionClosedCount":"1","ConnectionFailedCount":"28"
        // 但此时并不会中断进程
        if(flumeSink!=null && Double.parseDouble(flumeSink.getConnectionFailedCount()) > 0)
            System.out.println("sink连接失败");

        //sink批量写入是否有写入溢出
        if (flumeSink!=null && Double.parseDouble(flumeSink.getBatchUnderflowCount()) > 0)
            System.out.println("sink批量写入溢出，影响性能");


        //============================自定义指标==============================
        //用于监控source采集效率与发送到channel的效率
        if ( flumeSource != null && Double.parseDouble(flumeSource.getEventAcceptedCount()) >= Double.parseDouble(flumeSource.getEventReceivedCount()) * 2)
            System.out.println("source采集效率高于source发送到channel");

        //用于监控channel的存储效率与sink的拉取效率
        if(flumeSink != null &&
                Double.parseDouble(flumeChannel.getEventPutSuccessCount()) >= Double.parseDouble(flumeChannel.getEventTakeSuccessCount()) * 2)
            System.out.println("chanel存储效率高于sink拉取速度");

        //用于监控source组件状态
        if(flumeSource !=null) {
            boolean flumeSourceStatus =checkModuleStatus(flumeSource.getEventReceivedCount(),conf+".Source.EventReceivedCount",monitorMap,conf);
        }


        //用于确认chanel的状态
        if(flumeChannel != null){
            boolean flumeChannelStatus = checkModuleStatus(flumeChannel.getEventPutSuccessCount(),conf+".Channel.EventPutSuccessCount", monitorMap, conf);
        }


        //监控sink的状态
        if (flumeSink != null){
            boolean flumeSinkStatus =checkModuleStatus(flumeSink.getEventDrainSuccessCount(),conf+".Sink.EventDrainSuccessCount", monitorMap, conf);
        }

    }

    //获取各组件的对象
    public Object  getModule(Map map, String classic,String sour,String sin,String chan){
        if (map.isEmpty() || map == null)
            return null;

        try {
            if(("SINK."+sin).equals(classic)){
                flumeSink = new FlumeSink();

                //刚开始启动时网络已经畅通，但还没获取到metrics数据
                if(map.get("SINK."+sin).toString() == null) {
                    try {
                        Thread.sleep(10000);
                        return null;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                String sink = map.get("SINK."+sin).toString();
                returnSinkData = new JsonParser().parse(sink).getAsJsonObject();
                flumeSink = gson.fromJson(returnSinkData, FlumeSink.class);
                return flumeSink;
            }
            if(("CHANNEL."+chan).equals(classic)){
                FlumeChannel flumeChannel =new FlumeChannel();

                //刚开始启动时网络已经畅通，但还没获取到metrics数据
                if(map.get("CHANNEL."+chan).toString() == null) {
                    return null;
                }

                String channel = map.get("CHANNEL."+chan).toString();
                returnChannelData = new JsonParser().parse(channel).getAsJsonObject();
                flumeChannel = gson.fromJson(returnChannelData, FlumeChannel.class);
                return flumeChannel;
            }
            if(("SOURCE."+sour).equals(classic)){
                flumeSource =new FlumeSource();

                if(map.get("SOURCE."+sour).toString() == null) {
                    return null;
                }

                String source = map.get("SOURCE."+sour).toString();
                returnSourceData = new JsonParser().parse(source).getAsJsonObject();
                flumeSource = gson.fromJson(returnSourceData, FlumeSource.class);
                return flumeSource;
            }
        } catch (NullPointerException e) {
            return null;
        }
        return null;
    }

    //此方法用于判断sink.EventDrainSuccessCount;Source.EventReceivedCount;Channel.EventPutSuccessCount的值是否变化
    //从而判断各组件是否正常运行
    public boolean checkModuleStatus(String eventCount, String propConf, Map<String, Map> monitorMap, String conf){
        // 使用InPutStream流读取properties文件
        try {
            properties.load(bufferedReader);
            lastEventDataCount= properties.getProperty(propConf);
            System.out.println("--last "+propConf+":"+lastEventDataCount);

            if (!fisrtRun && eventCount .equals(lastEventDataCount) && properties.getProperty(propConf) != null) {
                mailManager.SendMail("[ERORR][FLUME][BIG_DATA]",
                        "<b>event</b>: flume " +propConf+ "数据异常<br>" +
                                "<b>url</b>: " + "http://" + monitorMap.get(conf).get("currentHost") + ":" + monitorMap.get(conf).get("monitorPort") + "/metrics" + "<br>" +
                                "<b>EventClassic</b>:" + propConf + "<br>" +
                                "<b>lastEventData</b>:" + properties.getProperty(propConf) + "<br>" +
                                "<b>currentEventData</b>:" + eventCount + "<br>" +
                                "<b>process</b>:" + conf + "<br>" +
                                "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>");
                System.out.println("motify "+propConf +":"+ eventCount);
                return false;
            }

            else {
                //修改配置文件中的值
                properties.load(bufferedReader);
                properties.put(propConf, eventCount);
                bufferedWriter = new BufferedWriter(new FileWriter(new File("").getCanonicalPath().toString()+"/src/main/resources/conf/metrics.properties"));//watchservice.properties
                properties.store(bufferedWriter,"");
                System.out.println("motify "+propConf +":"+ eventCount);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    //使用get请求获取数据
    public String get(String url) throws IOException {
        try {
            httpGet = new HttpGet(url);
            //設置連接超時
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(50000).setConnectionRequestTimeout(50000).setSocketTimeout(30000).build();
            httpGet.setConfig(requestConfig);
            httpGet.setConfig(requestConfig);
            httpGet.addHeader("Content-type", "application/json; charset=utf-8");
            httpGet.setHeader("Accept", "application/json");
            response = httpClient.execute(httpGet);
            in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            sb = new StringBuilder("");
            line = "";
            NL = System.getProperty("line.separator");
            while ((line = in.readLine()) != null) {
                sb.append(line + NL);
            }
            in.close();
            result = sb.toString();
        } catch (IOException e) {
           throw e;
        } finally {
            try {
                if (null != response) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}