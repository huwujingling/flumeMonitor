package com.monitor.flume.watchservice;

import com.csot.flume.domain.FlumeChannel;
import com.csot.flume.domain.FlumeSink;
import com.csot.flume.domain.FlumeSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.I0Itec.zkclient.ZkClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.mail.MessagingException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


//此类用于向flume发送http请求，获取metrics
public class FlumeMetricsMonitor{ //extends Thread
    static Gson gson = new Gson();
    private static  int monitorTime  ;//监控次数
    private Map metricsMap;
    private JsonObject returnData;
    private JsonObject returnSourceData;
    private JsonObject returnChannelData;
    private JsonObject returnSinkData;
    private FlumeSink flumeSink;
    private FlumeSource flumeSource;
    private  FlumeChannel flumeChannel;
    private MailManager mailManager;
    private SimpleDateFormat formatter;
    boolean fisrtRun;//是否第一次运行
    private static BufferedReader bufferedReader;
    private  BufferedWriter bufferedWriter;
    private Properties properties = new Properties();
    //http所需对象
    private static CloseableHttpClient httpClient;
    private CloseableHttpResponse response ;
    private BufferedReader in;
    private String result;//http响应结果
    private HttpGet httpGet;
    private String lastEventDataCount;
    private  StringBuilder sb;
    private String line;
    private String NL;
    private String[] aliasArray;
    //用于自定义检查各组件所需对象
    private boolean flumeSourceStatus;
    private boolean flumeChannelStatus;
    private boolean flumeSinkStatus;
    private String flumeSourceLastEventData;
    private String flumeSourceCurrentEventData;
    private String flumeChannelLastEventData;
    private String flumeChannelCurrentEventData;
    private String flumeSinkLastEventData;
    private String flumeSinkCurrentEventData;
    private String flumeSourceTitle;
    private String flumeChannelTitle ;
    private String flumeSinkTitle ;
    private String flumeSourceEventClassic ;
    private String flumeChannelEventClassic ;
    private String flumeSinkEventClassic;
    private String sourceAlias = "";
    private String sinkAlias = "";
    private String channelAlias = "";
    //private Map cycleMap ;//用来记录各监控对象的监控周期
    private  boolean cycleMonitorExists;//记录周期内是否存在监控对象

    public FlumeMetricsMonitor(MailManager mailManager, SimpleDateFormat formatter) {
        this.mailManager= mailManager;
        this.formatter = formatter;
        this.fisrtRun = true;
        this.cycleMonitorExists = false;
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

    public void run(ZkClient zk, String parentNode, Map<String,Map> monitorMap,ConcurrentHashMap<String,Integer> cycleMap,int watchCycle){
        //新启服务清空之前的内容
        try {
            properties.load(bufferedReader);
            properties.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SimpleDateFormat df =null;
        //周期进行监控
        while(!Thread.currentThread().isInterrupted()){
            df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
            System.out.println(df.format(new Date()));// new Date()为获取当前系统时间

            try {
                System.out.println("-------------------------开始第"+(++monitorTime)+"次监控-----------------------------");


                for (String conf : monitorMap.keySet()) {

                    //循环过程中会移除网络异常的flume,對應monitorMap中的key
                    if(monitorMap == null || monitorMap.isEmpty()){
                        cycleMonitorExists = false;
                        break;
                    }


                    //flumeWatchCycle = -1 则不监控
                    if (Integer.parseInt(monitorMap.get(conf).get("flumeWatchCycle").toString()) < 0){
                        monitorMap.remove(conf);
                        continue;
                    }

                    //判断是否新加入
                    if (monitorMap.get(conf).get("initialization") == "true"){
                        monitorMap.get(conf).put("initialization","false");
                    }

                    System.out.println("--Integer.parseInt(cycleMap.get("+conf+").toString()) :"+Integer.parseInt(cycleMap.get(conf).toString()));
                    System.out.println("Integer.parseInt(monitorMap.get("+conf+").get(\"flumeWatchCycle\").toString():"+Integer.parseInt(monitorMap.get(conf).get("flumeWatchCycle").toString()));

                    //到达预定周期才开始监控
                    if(Integer.parseInt(cycleMap.get(conf).toString()) % Integer.parseInt(monitorMap.get(conf).get("flumeWatchCycle").toString())  == 0){
                        System.out.println("http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics");
                        System.out.println("Thread.name:"+Thread.currentThread().getName()+"-----Thread.id:"+Thread.currentThread().getId());
                        metricsMonitor(zk,parentNode,monitorMap,cycleMap,conf);
                        cycleMap.put(conf,0);
                        cycleMonitorExists = true;
                    }

                    cycleMap.put(conf,Integer.parseInt(cycleMap.get(conf).toString()) + watchCycle);
                }

                System.out.println("cycleMap: "+cycleMap);

                //flume下线会重新此方法，可能会为空
                if(monitorMap == null || monitorMap.isEmpty() || cycleMonitorExists == false){
                    //如果启动多线程在此关闭线程
                    System.out.println("当前没有可监听的对象aaa");
                }

                if(fisrtRun)
                    System.out.println("------------------------第"+ monitorTime +"次监控初始化完毕------------------------");
                else
                    System.out.println("--------------------第"+ monitorTime +"次运行监控，flume状态记录完毕-----------------");

                System.out.println();
                System.out.println();

                Thread.sleep(watchCycle);//完成一周期的监控休眠20min
                fisrtRun = false;
            } catch (Exception e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    //核心方法
    public void metricsMonitor(ZkClient zk,String parentNode, Map<String,Map> monitorMap,ConcurrentHashMap<String,Integer> cycleMap, String conf) throws Exception {
        //使用get访问http获取metrics
        String url ="http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics";

        metricsMap = new ConcurrentHashMap<String,String>();

        //检查url连接状态
        if (checkURLStatus(monitorMap, cycleMap, conf, url))
            return;

        System.out.println("网络连接正常");
        System.out.println("开始执行监控指标判断");

        //flume进程与网络端口断开快与zookeeper，这段时间会没有数据
        if(metricsMap ==null || metricsMap.isEmpty()) {
            mailManager.setToReceiverAry(monitorMap.get(conf).get("receiver").toString().split(";")).SendMail(
                    "[ERORR][FLUME][BIG_DATA]",
                    "<b>event</b>:flume进程关闭或所在服务器网络断开<br>" +
                            "<b>url</b>:" + "http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics" + "<br>" +
                            "<b>process</b>:" + conf + "<br>" +
                            "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                            "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>");
            System.out.println("flume进程关闭或所在服务器网络断开");
            return;
        }

        //获取各组件对象
        flumeSink = getModule(metricsMap,sinkAlias,sourceAlias,sinkAlias,channelAlias)==null?null:(FlumeSink) getModule(metricsMap,sinkAlias,sourceAlias,sinkAlias,channelAlias);
        flumeChannel = getModule(metricsMap,channelAlias,sourceAlias,sinkAlias,channelAlias)==null?null:(FlumeChannel) getModule(metricsMap,channelAlias,sourceAlias,sinkAlias,channelAlias);
        flumeSource = getModule(metricsMap,sourceAlias,sourceAlias,sinkAlias,channelAlias)==null?null:(FlumeSource) getModule(metricsMap,sourceAlias,sourceAlias,sinkAlias,channelAlias);

        //通过指标判断各组件存活状态
        //比如下沉到habse的表被disable

        //============================source=================================
        //用于source被关闭的情况，比如执行exec source时，执行tail命令的进程退出
        if(flumeSource != null && Double.parseDouble(flumeSource.getStopTime()) > 0)
            System.out.println("目标源被异常关闭");

        //检查channel的状态
        checkChannelStatus(monitorMap, conf);

        //检查sink的状态
        checkSinkStaus(conf);

        //检查自定义的指标
        checkDefineItem(monitorMap, conf);

    }

    private void checkChannelStatus(Map<String, Map> monitorMap, String conf) throws MessagingException, UnsupportedEncodingException {
        //============================channel================================
        //用于监控channel的存活状态
        if (flumeChannel != null && Double.parseDouble(flumeChannel.getStopTime()) > 0)
            System.out.println("channel被异常关闭");

        System.out.println(flumeChannel.getChannelFillPercentage().concat("00").substring(0,5));
        //缓存通道占用百分比低于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率
        if(flumeChannel != null && Double.parseDouble(flumeChannel.getChannelFillPercentage().concat("00").substring(0,5)) > 80) {
            mailManager.setToReceiverAry(monitorMap.get(conf).get("receiver").toString().split(";")).SendMail(
                    "[ERORR][FLUME][BIG_DATA]",
                    "<b>event</b>:CHANNEL通道占用高于80%，可能会导致通道溢出<br>" +
                            "<b>url</b>:" + "http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics" + "<br>" +
                            "<b>ChannelCapacity</b>:" + flumeChannel.getChannelCapacity() + "<br>" +
                            "<b>ChannelSize</b>:" + flumeChannel.getChannelSize() + "<br>" +
                            "<b>ChannelFillPercentage</b>:" + flumeChannel.getChannelFillPercentage() + "<br>" +
                            "<b>process</b>:" + conf + "<br>" +
                            "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                            "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>");
            System.out.println("channel通道占用百分比高于于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率");
        }
    }

    private void checkSinkStaus(String conf) throws IOException {
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
        if (flumeSink!=null && Double.parseDouble(flumeSink.getBatchUnderflowCount()) > 0){
            if (properties.getProperty(conf+".Sink.BatchUnderflowCount") ==null ||
                    Double.parseDouble(flumeSink.getBatchUnderflowCount()) > Double.parseDouble(properties.getProperty(conf+".Sink.BatchUnderflowCount"))){
               /* //发送邮件
                mailManager.setToReceiverAry(monitorMap.get(conf).get("receiver").toString().split(";")).SendMail(
                                "[ERORR][FLUME][BIG_DATA]",
                                "<b>event</b>:sink批量写入溢出<br>" +
                                "<b>url</b>: " + "http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics" + "<br>" +
                                "<b>lastBatchUnderflowCount</b>:" +  properties.get(conf+".Sink.BatchUnderflowCount") + "<br>" +
                                "<b>currentBatchUnderflowCount</b>:" + flumeSink.getBatchUnderflowCount() + "<br>" +
                                "<b>process</b>:" + conf + "<br>" +
                                "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                                "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>");
                System.out.println("sink批量写入溢出，影响性能");*/

                //修改配置文件中的值
                System.out.println("--last "+conf+".Sink.BatchUnderflowCount" +" = "+ properties.get(conf+".Sink.BatchUnderflowCount"));
                System.out.println("motify "+conf+".Sink.BatchUnderflowCount" +" = "+ flumeSink.getBatchUnderflowCount());

                try {
                    properties.load(bufferedReader);
                    properties.put(conf+".Sink.BatchUnderflowCount",flumeSink.getBatchUnderflowCount());
                    bufferedWriter = new BufferedWriter(new FileWriter(new File("").getCanonicalPath().toString()+"/src/main/resources/conf/metrics.properties"));//watchservice.properties
                    properties.store(bufferedWriter,"");

                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    bufferedWriter.close();
                }
            }
        }
    }

    private void checkDefineItem(Map<String, Map> monitorMap, String conf) throws MessagingException, UnsupportedEncodingException {
        //============================自定义指标==============================
        //用于监控source采集效率与发送到channel的效率
        if ( flumeSource != null && Double.parseDouble(flumeSource.getEventAcceptedCount()) > Double.parseDouble(flumeSource.getEventReceivedCount()) * 2)
            System.out.println("source采集效率高于source发送到channel");

        //用于监控channel的存储效率与sink的拉取效率
        if(flumeSink != null &&
                Double.parseDouble(flumeChannel.getEventPutSuccessCount()) > Double.parseDouble(flumeChannel.getEventTakeSuccessCount()) * 2)
            System.out.println("chanel存储效率高于sink拉取速度");

        //用于监控source组件状态
        if(flumeSource != null) {
            flumeSourceLastEventData =  "<b>lastEventData</b>:" + properties.getProperty(conf+".Source.EventReceivedCount") + "<br>" ;

            flumeSourceStatus =checkModuleStatus(flumeSource.getEventReceivedCount(),conf+".Source.EventReceivedCount",monitorMap,conf);

            flumeSourceCurrentEventData =  "<b>currentEventData</b>:" + flumeSource.getEventReceivedCount() + "<br>" ;
        }
        //用于确认chanel的状态
        if(flumeChannel != null){
            flumeChannelLastEventData =  "<b>lastEventData</b>:" + properties.getProperty(conf+".Channel.EventPutSuccessCount") + "<br>" ;

            flumeChannelStatus = checkModuleStatus(flumeChannel.getEventPutSuccessCount(),conf+".Channel.EventPutSuccessCount", monitorMap, conf);

            flumeChannelCurrentEventData =  "<b>currentEventData</b>:" + flumeChannel.getEventPutSuccessCount() + "<br>" ;
        }
        //用于确认sink的状态
        if (flumeSink != null){
            flumeSinkLastEventData =  "<b>lastEventData</b>:" + properties.getProperty(conf+".Sink.EventDrainSuccessCount") + "<br>" ;

            flumeSinkStatus =checkModuleStatus(flumeSink.getEventDrainSuccessCount(),conf+".Sink.EventDrainSuccessCount", monitorMap, conf);

            flumeSinkCurrentEventData =  "<b>currentEventData</b>:" + flumeSink.getEventDrainSuccessCount() + "<br>" ;
        }
        //根据checkStatus的结果是否发邮件以及邮件指标
        if (!flumeSourceStatus || !flumeChannelStatus || !flumeSinkStatus){
            flumeSourceTitle = flumeSourceStatus?"":"Source";
            flumeChannelTitle = flumeChannelStatus?"":"Channel";
            flumeSinkTitle = flumeSinkStatus?"":"Sink";

            flumeSourceEventClassic = flumeSourceStatus?"":"<b>EventType</b>:" + conf+".Source.EventReceivedCount" + "<br>";
            flumeChannelEventClassic = flumeChannelStatus?"":"<b>EventType</b>:" + conf+".Channel.EventPutSuccessCount" + "<br>";
            flumeSinkEventClassic = flumeSinkStatus?"":"<b>EventType</b>:" + conf+".Sink.EventDrainSuccessCount" + "<br>";

            mailManager.setToReceiverAry(monitorMap.get(conf).get("receiver").toString().split(";")).SendMail(
                    "[ERORR][FLUME][BIG_DATA]",
                    "<b>event</b>: flume " +flumeSourceTitle+" "+flumeChannelTitle+" "+flumeSinkTitle+ " 没有数据<br>" +
                            "<b>url</b>: " + "http://" + monitorMap.get(conf).get("currentHost") + ":" + monitorMap.get(conf).get("monitorPort") + "/metrics" + "<br>" +
                            flumeSourceEventClassic +
                            flumeSourceLastEventData +
                            flumeSourceCurrentEventData +
                            flumeChannelEventClassic +
                            flumeChannelLastEventData +
                            flumeChannelCurrentEventData +
                            flumeSinkEventClassic +
                            flumeSinkLastEventData +
                            flumeSinkCurrentEventData +
                            "<b>process</b>:" + conf + "<br>" +
                            "<b>time</b>:" + formatter.format(new Date()) + "<br>" +
                            "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>");
        }
    }

    private boolean checkURLStatus(Map<String, Map> monitorMap, ConcurrentHashMap<String, Integer> cycleMap, String conf, String url) throws MessagingException, UnsupportedEncodingException {
        //int tryTime=0;//网络异常重试次数
        //判断网络连接每5s重试一次，超过2次则抛异常
        String subject ="flume httpMonitor http网络连接异常";

        try{
            String metrics = getResult(url);
            returnData = new JsonParser().parse(metrics).getAsJsonObject();
            metricsMap = gson.fromJson(returnData, Map.class);

            System.out.println("================================================");
            System.out.println("================================================");
            System.out.println(metricsMap.toString());

            for (Object metricsKey : metricsMap.keySet()) {
                try {
                    System.out.println(metricsKey);
                    if(metricsKey != null && metricsKey != "" && metricsKey.toString().contains("SOURCE"))
                        sourceAlias=metricsKey.toString();
                    if(metricsKey != null && metricsKey != "" && metricsKey.toString().contains("SINK"))
                        sinkAlias=metricsKey.toString();
                    if(metricsKey != null && metricsKey != "" && metricsKey.toString().contains("CHANNEL"))
                        channelAlias=metricsKey.toString();
                } catch (Exception e) {
                    subject="获取source,sink，channel的别名失败";
                }
            }
            System.out.println("================================================");
            System.out.println("================================================");
        }catch (Exception excption){
            System.out.println(url+subject);

            mailManager.setToReceiverAry(monitorMap.get(conf).get("receiver").toString().split(";")).SendMail(
                    "[ERORR][FLUME][BIG_DATA]",
                    "<b>event</b>:"+subject+"<br>" +
                            "<b>url</b>: " + "http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics" + "<br>" +
                            "<b>process</b>:" + conf + "<br>" +
                            "<b>server</b>:" + monitorMap.get(conf).get("currentHost") + "<br>" +
                            "<b>time</b>:" + formatter.format(new Date()) + "<br>" );
            System.out.println(conf+"被删除,可能是该节点被删除或服务器连接不通");

            if (monitorMap.containsKey(conf))
                monitorMap.remove(conf);

            if (cycleMap.containsKey(conf))
                cycleMap.remove(conf);

            return true;
        }
        return false;
    }

    //获取各组件的对象
    public Object  getModule(Map map, String classic,String sour,String sin,String chan){
        if (map.isEmpty() || map == null)
            return null;

        try {
            if((sin).equals(classic)){
                flumeSink = new FlumeSink();

                //刚开始启动时网络已经畅通，但还没获取到metrics数据
                if(map.get(sin).toString() == null) {
                    try {
                        Thread.sleep(10000);
                        return null;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                String sink = map.get(sin).toString();
                returnSinkData = new JsonParser().parse(sink).getAsJsonObject();
                flumeSink = gson.fromJson(returnSinkData, FlumeSink.class);
                return flumeSink;
            }
            if((chan).equals(classic)){

                //刚开始启动时网络已经畅通，但还没获取到metrics数据
                if(map.get(chan).toString() == null) {
                    return null;
                }

                String channel = map.get(chan).toString();
                returnChannelData = new JsonParser().parse(channel).getAsJsonObject();
                flumeChannel = gson.fromJson(returnChannelData, FlumeChannel.class);
                return flumeChannel;
            }
            if((sour).equals(classic)){
                flumeSource =new FlumeSource();

                if(map.get(sour).toString() == null) {
                    return null;
                }

                String source = map.get(sour).toString();
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

            if (!fisrtRun && eventCount.equals(lastEventDataCount) && properties.getProperty(propConf) != null) {
                System.out.println("current "+propConf +":"+ eventCount);
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
        }finally {
            try {
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    //使用get请求获取数据
    public String getResult(String url) throws IOException {
        try {
            httpGet = new HttpGet(url);
            //设置连接超时
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