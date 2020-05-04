package flinkapp.wordcount;

import Nexmark.sinks.DummySink;
import flinkapp.wordcount.sources.RateControlledSourceFunction;
import flinkapp.wordcount.sources.RateControlledSourceFunctionKV;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.GenericTypeInfo;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class StatefulWordCount {

	public static void main(String[] args) throws Exception {

		// Checking input parameters
		final ParameterTool params = ParameterTool.fromArgs(args);

		// set up the execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		env.setStateBackend(new MemoryStateBackend(100000000));

		// make parameters available in the web interface
		env.getConfig().setGlobalJobParameters(params);
		env.disableOperatorChaining();

		final int srcRate = params.getInt("srcRate", 100000);
		final int srcCycle = params.getInt("srcCycle", 60);
		final int srcBase = params.getInt("srcBase", 0);
		final int srcWarmUp = params.getInt("srcWarmUp", 100);
		final int sentenceSize = params.getInt("sentence-size", 100);

		final DataStream<Tuple2<String, String>> text = env.addSource(
				new RateControlledSourceFunctionKV(
						srcRate, srcCycle, srcBase, srcWarmUp*1000, sentenceSize))
				.uid("sentence-source")
				.setParallelism(params.getInt("p1", 1))
				.setMaxParallelism(params.getInt("mp1", 64))
				.keyBy(0);

		// split up the lines in pairs (2-tuples) containing:
		// (word,1)
		DataStream<Tuple2<String, Long>> counts = text
				.flatMap(new Tokenizer())
				.name("Splitter FlatMap")
				.uid("flatmap")
				.setParallelism(params.getInt("p2", 1))
				.setMaxParallelism(params.getInt("mp1", 64))
				.keyBy(0)
				.flatMap(new CountWords())
				.name("Count")
				.uid("count")
				.setParallelism(params.getInt("p3", 1))
				.setMaxParallelism(params.getInt("mp1", 64));

		GenericTypeInfo<Object> objectTypeInfo = new GenericTypeInfo<>(Object.class);
		// write to dummy sink
		counts.transform("Latency Sink", objectTypeInfo,
				new DummySink<>())
				.uid("dummy-sink")
				.setParallelism(params.getInt("p3", 1));

		// execute program
		env.execute("Stateful WordCount");
	}

	// *************************************************************************
	// USER FUNCTIONS
	// *************************************************************************

	public static final class Tokenizer implements FlatMapFunction<Tuple2<String, String>, Tuple2<String, Long>> {
		private static final long serialVersionUID = 1L;

		@Override
		public void flatMap(Tuple2<String, String> value, Collector<Tuple2<String, Long>> out) throws Exception {
			// normalize and split the line
			String[] tokens = value.f1.toLowerCase().split("\\W+");

			long start = System.nanoTime();
			while (System.nanoTime() - start < 100000) {}

			// emit the pairs
			for (String token : tokens) {
				if (token.length() > 0) {
					out.collect(new Tuple2<>(token, 1L));
				}
			}
		}
	}

	public static final class CountWords extends RichFlatMapFunction<Tuple2<String, Long>, Tuple2<String, Long>> {

		private transient ReducingState<Long> count;

		@Override
		public void open(Configuration parameters) throws Exception {

			ReducingStateDescriptor<Long> descriptor =
					new ReducingStateDescriptor<Long>(
							"count", // the state name
							new Count(),
							BasicTypeInfo.LONG_TYPE_INFO);

			count = getRuntimeContext().getReducingState(descriptor);
		}

		@Override
		public void flatMap(Tuple2<String, Long> value, Collector<Tuple2<String, Long>> out) throws Exception {
			count.add(value.f1);

			long start = System.nanoTime();
			while (System.nanoTime() - start < 10000) {}

			out.collect(new Tuple2<>(value.f0, count.get()));
		}

		public static final class Count implements ReduceFunction<Long> {

			@Override
			public Long reduce(Long value1, Long value2) throws Exception {
				return value1 + value2;
			}
		}
	}

}
