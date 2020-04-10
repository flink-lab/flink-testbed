#!/bin/bash

FLINK_DIR="/home/samza/workspace/flink-extended/build-target"
FLINK_APP_DIR="/home/samza/workspace/flink-testbed"

# clean kafka related data
function cleanEnv() {
    export JAVA_HOME=/home/samza/kit/jdk
    ~/samza-hello-samza/bin/grid stop kafka
    ~/samza-hello-samza/bin/grid stop zookeeper
    rm -r /data/kafka/kafka-logs/
    rm -r /tmp/zookeeper/

    python -c 'import time; time.sleep(20)'

    ~/samza-hello-samza/bin/grid start zookeeper
    ~/samza-hello-samza/bin/grid start kafka
}

# configure parameters in flink bin
function configFlink() {
    sed 's/^\(\s*streamswitch.requirement.latency\s*:\s*\).*/\1'"$L"'/' ${FLINK_DIR}/conf/flink-conf.yaml > tmp
    sed 's/^\(\s*streamswitch.system.l\s*:\s*\).*/\1'"$l"'/' tmp > ${FLINK_DIR}/conf/flink-conf.yaml
    rm tmp
}

# run flink clsuter
function runFlink() {
    rm -rf ${FLINK_DIR}/log
    mkdir ${FLINK_DIR}/log
    ${FLINK_DIR}/bin/start-cluster.sh
}

# run applications
function runApp() {
    ${FLINK_APP_DIR}/submit-nexmark5.sh ${N} 64 ${RATE} ${CYCLE} ${BASE} 0
}

# clsoe flink clsuter
function closeFlink() {
    echo "experiment finished closing it"
    ${FLINK_DIR}/bin/stop-cluster.sh
    mv ${FLINK_DIR}/log ${FLINK_APP_DIR}/nexmark_scripts/draw/logs/${EXP_NAME}
    echo "close finished"
}

# draw figures
function draw() {
    python2 ${FLINK_APP_DIR}/nexmark_scripts/draw/RateAndWindowDelay.py ${EXP_NAME}
    python2 ${FLINK_APP_DIR}/nexmark_scripts/draw/ViolationsAndUsageFromGroundTruth.py ${EXP_NAME}
}

#for L in 1000 4000 16000; do
#    for l in 50 100 150 200; do
#        ALPHA=`echo "$l $L" | awk '{printf "%.5f \n", $1/$2}'`
#        echo run experment with L = $L, ALPHA = $ALPHA
#        configFlink
#        runFlink
#        runApp
#        python -c 'import time; time.sleep(300)'
#
#        closeFlink
#        python -c 'import time; time.sleep(30)'
#    done
#done

L=1000
l=10

QUERY=5
BASE=10000
RATE=25000
CYCLE=60

N=1

EXP_NAME=Q${QUERY}-B${BASE}C${CYCLE}R${RATE}-N${N}-L${l}l${l}

cleanEnv
configFlink
runFlink
runApp

python -c 'import time; time.sleep(600)'

# draw figure
draw

closeFlink

