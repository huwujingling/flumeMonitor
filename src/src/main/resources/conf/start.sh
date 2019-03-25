#!/bin/bash
# /opt/cloudera/parcels/CDH/bin/flume-ng  a1 /data/flume_test/conf/flume_test.properties "/data/flume_test/define_habse_rowkey-1.0-SNAPSHOT.jar:"

num=$#
echo $num

prograPath=$1
agentName=$2
confPath=$3
classPath=$4

#获取随机数
function rand(){
    min=$1
    max=$(($2-$min+1))
    num=$(($RANDOM+1000000000)) #增加一个10位的数再求余
    echo $(($num%$max+$min))
}

rndPort=$(rand 30000 32678)

#获取端口
while((1<100))
do
rndPort=$(rand 30000 32678)
echo $rndPort
port=`lsof -i:$rndPort`
line=`echo "$port" | wc -l`

if [[ $line  -ge 2 ]];then
echo 'ok'
else
echo 'false'
break
fi
done

#给conf中的的拦截器配置中的monitorPort赋值
a=`cat /$confPath |grep monitorPort`
b="a1.sources.r1.interceptors.i1.monitorPort = "$rndPort
echo "$b"

sed -i "s/$a/$b/g" $confPath
c=`cat $confPath |grep monitorPort`
echo "monitorPort:$c"

#判断是否修改成功
if [[ $b -ne $c ]];then
echo "monitorPort not successfully modified"
exit 0
fi


/opt/cloudera/parcels/CDH/bin/flume-ng agent -n $agentName -c conf -f $confPath --classpath $classPath -Xmx1024m  -Dflume.monitoring.type=http -Dflume.monitoring.port=$rndPort

exit 0