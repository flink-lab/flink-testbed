package flinkapp.frauddetection;

import flinkapp.frauddetection.function.*;
import flinkapp.frauddetection.rule.DecisionTreeRule;
import flinkapp.frauddetection.rule.FraudOrNot;
import flinkapp.frauddetection.transaction.PrecessedTransaction;
import flinkapp.frauddetection.transaction.Transaction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;


public class RuleEvaluatorV2 {

//    private static String expDir = "/home/flink/workspace/fraud_detector/";
    private static String expDir = "/home/hya/prog/flink-exp";


    public static void main(String[] args) throws Exception {
        final ParameterTool params = ParameterTool.fromArgs(args);
        expDir = params.get("expdir", "/home/hya/prog/flink-exp");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//        env.enableCheckpointing(1000);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.disableOperatorChaining();
        // get transaction
        final DataStream<Transaction> transactionDataStream = getSourceStream(env);
        // some preprocessing needed
        final DataStream<PrecessedTransaction> preprocessedStream = transactionDataStream
                .keyBy((KeySelector<Transaction, String>) Transaction::getCcNum)
                .map(new PreprocessingFunction())
                .name("preprocess")
                .setParallelism(4);
        // start processing data
        DataStream<FraudOrNot> isFraudStream = preprocessedStream
                .keyBy((KeySelector<PrecessedTransaction, String>) transaction -> transaction.originalTransaction.getCcNum())
                .process(new ProcessingFunction(new DecisionTreeRule()))
                .name("dtree")
                .setParallelism(8);

        isFraudStream.addSink(new LatencyWriter(expDir + "/end2end.out"));
        // just print here
        DataStream<Tuple2<String, Integer>> resultStream = isFraudStream.map(
                new MapFunction<FraudOrNot, Tuple2<String, Integer>>() {
                    @Override
                    public Tuple2<String, Integer> map(FraudOrNot fraudOrNot) throws Exception {
                        boolean GT = fraudOrNot.transc.getFeature("is_fraud").equals("1");
                        if (GT == fraudOrNot.isFraud) {
                            if (GT) {
                                return Tuple2.of("TP", 1);
                            } else {
                                return Tuple2.of("TN", 1);
                            }
                        } else {
                            if (GT) {
                                return Tuple2.of("FP", 1);
                            } else {
                                return Tuple2.of("FN", 1);
                            }
                        }
                    }
                })
                .keyBy(0)
                .timeWindow(Time.seconds(1))
                .sum(1)
                .setParallelism(2);

        resultStream
                .addSink(new FileWritingFunction(expDir + "/confusion_matrix.csv"))
                .setParallelism(1);
        System.out.println(env.getExecutionPlan());
        env.execute();
    }

    private static DataStream<Transaction> getSourceStream(StreamExecutionEnvironment env) {
        return env.addSource(
                new FileReadingFunction(expDir + "/credit_card_trans.csv"))
                .uid("sentence-source")
                .setParallelism(1);
    }

}