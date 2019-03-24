package com.csot.flume.watchservice;

import com.csot.flume.domain.flumeChannel;
import com.csot.flume.domain.flumeSink;
import com.csot.flume.domain.flumeSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


//此类用于向flume发送http请求，获取metrics
public class FlumeMetricsMonitor {
    private static CloseableHttpClient httpClient;
    static Gson gson = new Gson();
    private static  int monitorTime = 0 ;//监控次数

    public void metricsMonitor(ZkClient zk,String parentNode, Map<String,Map> monitorMap, String conf, String source, String channel, String sink) throws Exception {
        String url ="http://"+monitorMap.get(conf).get("currentHost")+":"+monitorMap.get(conf).get("monitorPort")+"/metrics";

        System.out.println("-------------------------开始第"+(monitorTime+1)+"次监控-------------------------");
            // System.out.println(url);
        //使用get访问http获取metrics
        Map metricsMap = new ConcurrentHashMap<String,String>();

        //判断网络连接每2s重试一次，超过三次则抛异常
            try{
                String metrics = get(url);
                JsonObject returnData = new JsonParser().parse(metrics).getAsJsonObject();
                metricsMap = gson.fromJson(returnData, Map.class);
            }catch (Exception excption){
                monitorMap.remove(conf);
                zk.delete(parentNode+"/"+conf);
                System.out.println(conf+"節點被刪除");
                Thread.currentThread().notify();
                return;
            }


        System.out.println("网络连接正常");
        System.out.println("开始执行监控指标判断");

        //flume进程与网络端口断开快与zookeeper，这段时间会没有数据
        if(metricsMap ==null || metricsMap.isEmpty()) {
            System.out.println("flume网络断开");
            return;
        }

        flumeSink flumeSink = getModule(metricsMap,"SINK."+sink)==null?null:(com.csot.flume.domain.flumeSink) getModule(metricsMap,"SINK."+sink);
        flumeChannel flumeChannel= getModule(metricsMap,"CHANNEL."+channel)==null?null:(com.csot.flume.domain.flumeChannel) getModule(metricsMap,"CHANNEL."+channel);
        flumeSource flumeSource = getModule(metricsMap,"SOURCE."+source)==null?null:(com.csot.flume.domain.flumeSource) getModule(metricsMap,"SOURCE."+source);

        System.out.println("获取状态结束");
        //获取各组件对象

        //通过指标判断各组件存活状态
        //比如下沉到habse的表被disable
        /*checkModuleStatus(flumeSink.getEventDrainSuccessCount(),"Sink.EventDrainSuccessCount");
        checkModuleStatus(flumeChannel.getEventPutSuccessCount(),"Channel.EventPutSuccessCount");
        checkModuleStatus(flumeSource.getEventReceivedCount(),"Source.EventReceivedCount");*/



        //============================source=================================
        //用于source被关闭的情况，比如执行exec source时，执行tail命令的进程退出
        if(flumeSource != null && Double.parseDouble(flumeSource.getStopTime()) > 0)
            System.out.println("目标源被异常关闭");

        //============================channel=================================
        //用于监控channel的存活状态
        if (flumeChannel != null && Double.parseDouble(flumeChannel.getStopTime()) > 0)
            System.out.println("channel被异常关闭");

        //缓存通道占用百分比低于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率
       if(flumeChannel != null && Double.parseDouble(flumeChannel.getChannelFillPercentage()) > 80)
           System.out.println("通道占用百分比低于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率");


        //============================sink=================================
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


        //============================自定义指标=======================================
        if(monitorTime ++ > 0){
            //用于监控source采集效率与发送到channel的效率
            if ( flumeSource != null && Double.parseDouble(flumeSource.getEventAcceptedCount()) >= Double.parseDouble(flumeSource.getEventReceivedCount()) * 2)
                System.out.println("source采集效率高于source发送到channel");

            //用于监控channel的存储效率与sink的拉取效率
            if(flumeSink!=null &&
                    Double.parseDouble(flumeChannel.getEventPutSuccessCount()) >= Double.parseDouble(flumeChannel.getEventTakeSuccessCount()) * 2)
                System.out.println("chanel存储效率高于sink拉取速度");

            //用于监控source组件状态
            if(flumeSink!=null)
            checkModuleStatus(flumeSource.getEventReceivedCount(),"Source.EventReceivedCount");

            //用于确认chanel的状态
            if(flumeChannel != null)
            checkModuleStatus(flumeChannel.getEventPutSuccessCount(),"Channel.EventPutSuccessCount");

            //监控sink的状态
            if (flumeSink != null)
            checkModuleStatus(flumeSink.getEventDrainSuccessCount(),"Sink.EventDrainSuccessCount");
        }

        System.out.println("--------------------------第"+monitorTime+"次监控完毕，flume状态正常----------------------");
    }

    //获取各组件的对象
    public Object  getModule(Map map, String classic){
        if (map.isEmpty() || map == null)
            return null;

        try {
            if("SINK.k1".equals(classic)){
                flumeSink flumeSink = new flumeSink();

                //刚开始启动时网络已经畅通，但还没获取到metrics数据
                if(map.get("SINK.k1").toString() == null) {
                    try {
                        Thread.sleep(10000);
                        return null;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                String sink = map.get("SINK.k1").toString();
                JsonObject returnSinkData = new JsonParser().parse(sink).getAsJsonObject();
                flumeSink = gson.fromJson(returnSinkData, flumeSink.class);
                return flumeSink;
            }
            if("CHANNEL.c1".equals(classic)){
                flumeChannel flumeChannel =new flumeChannel();

                //刚开始启动时网络已经畅通，但还没获取到metrics数据
                if(map.get("CHANNEL.c1").toString() == null) {
                        return null;
                }

                String channel = map.get("CHANNEL.c1").toString();
                JsonObject returnChannelData = new JsonParser().parse(channel).getAsJsonObject();
                flumeChannel = gson.fromJson(returnChannelData, flumeChannel.class);
                return flumeChannel;
            }
            if("SOURCE.r1".equals(classic)){
                flumeSource flumeSource =new flumeSource();

                if(map.get("SOURCE.r1").toString() == null) {
                        return null;
                }

                String source = map.get("SOURCE.r1").toString();
                JsonObject returnSourceData = new JsonParser().parse(source).getAsJsonObject();
                flumeSource = gson.fromJson(returnSourceData, flumeSource.class);
                return flumeSource;
            }
        } catch (NullPointerException e) {
            return null;
        }
        return null;
    }

    //此方法用于判断sink.EventDrainSuccessCount;Source.EventReceivedCount;Channel.EventPutSuccessCount的值是否变化
    //从而判断各组件是否正常运行
    public void checkModuleStatus(String eventDrainSuccessCount, String propConf){
        Properties properties = new Properties();
        // 使用InPutStream流读取properties文件
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("").getCanonicalPath().toString()+"/src/main/resources/conf/metrics.properties"));//watchservice.properties
            properties.load(bufferedReader);
            String lastEventDrainSuccessCount= properties.getProperty(propConf);
            System.out.println("last "+propConf+":"+lastEventDrainSuccessCount);

            if (eventDrainSuccessCount .equals(lastEventDrainSuccessCount) )
                System.out.println("flume "+ propConf+" Component isNot run or nodata");
            else {
                //修改配置文件中的值
                properties.load(bufferedReader);
                properties.put(propConf, eventDrainSuccessCount);
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(new File("").getCanonicalPath().toString()+"/src/main/resources/conf/metrics.properties"));//watchservice.properties
                properties.store(bufferedWriter,"");
                System.out.println("motify "+propConf +":"+ eventDrainSuccessCount);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    static {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);
        cm.setDefaultMaxPerRoute(20);
        cm.setDefaultMaxPerRoute(50);
        httpClient = HttpClients.custom().setConnectionManager(cm).build();
    }


    //使用get请求获取数据
    public String get(String url) {
        CloseableHttpResponse response = null;
        BufferedReader in = null;
        String result = "";
        try {
            HttpGet httpGet = new HttpGet(url);
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(30000).setConnectionRequestTimeout(30000).setSocketTimeout(30000).build();
            httpGet.setConfig(requestConfig);
            httpGet.setConfig(requestConfig);
            httpGet.addHeader("Content-type", "application/json; charset=utf-8");
            httpGet.setHeader("Accept", "application/json");
            response = httpClient.execute(httpGet);
            in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuffer sb = new StringBuffer("");
            String line = "";
            String NL = System.getProperty("line.separator");
            while ((line = in.readLine()) != null) {
                sb.append(line + NL);
            }
            in.close();
            result = sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
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
