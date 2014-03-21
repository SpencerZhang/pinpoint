package com.nhn.pinpoint.profiler.monitor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.nhn.pinpoint.profiler.monitor.codahale.gc.GarbageCollector;
import com.nhn.pinpoint.profiler.monitor.codahale.gc.GarbageCollectorFactory;
import com.nhn.pinpoint.thrift.dto.TAgentStat;
import com.nhn.pinpoint.thrift.dto.TJvmGc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhn.pinpoint.common.util.PinpointThreadFactory;
import com.nhn.pinpoint.profiler.sender.DataSender;

/**
 * AgentStat monitor
 * 
 * @author harebox
 * 
 */
public class AgentStatMonitor {

    private static final long DEFAULT_INTERVAL = 1000 * 5;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, new PinpointThreadFactory("Pinpoint-stat-monitor", true));

	private final DataSender dataSender;
	private final String agentId;

	public AgentStatMonitor(DataSender dataSender, String agentId) {
        if (dataSender == null) {
            throw new NullPointerException("dataSender must not be null");
        }
        if (agentId == null) {
            throw new NullPointerException("agentId must not be null");
        }
        this.dataSender = dataSender;
        this.agentId = agentId;
	}


	public void start() {
		CollectJob job = new CollectJob(dataSender);
		// FIXME 설정에서 수집 주기를 가져올 수 있어야 한다.
		long interval = DEFAULT_INTERVAL;
		long wait = 0;
		
		executor.scheduleAtFixedRate(job, wait, interval, TimeUnit.MILLISECONDS);
		logger.info("AgentStat monitor started");
	}

	public void stop() {
		executor.shutdown();
        try {
            executor.awaitTermination(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("AgentStat monitor stopped");
	}

    private class CollectJob implements Runnable {

		private final DataSender dataSender;
		private final GarbageCollector garbageCollector;
        private final String agentId;

		public CollectJob(DataSender dataSender) {
            if (dataSender == null) {
                throw new NullPointerException("dataSender must not be null");
            }
            this.dataSender = dataSender;
            this.agentId = AgentStatMonitor.this.agentId;

			// GarbageCollectorFactory 타입을 확인한다.
			this.garbageCollector = GarbageCollectorFactory.createGarbageCollector();
			if (logger.isInfoEnabled()) {
				logger.info("found : {}", this.garbageCollector);
			}
		}



        public void run() {
			try {
                // TAgentStat 객체를 준비한다.
                // TODO TAgentStat을 재활용시 datasender가 별도의 thread이기 때문에. multithread문제가 생길수 있음.
                final TAgentStat agentStat = new TAgentStat();
                agentStat.setAgentId(agentId);
				agentStat.setTimestamp(System.currentTimeMillis());
                final TJvmGc gc = this.garbageCollector.collect();
                agentStat.setGc(gc);

                this.dataSender.send(agentStat);
			} catch (Exception ex) {
				logger.warn("AgentStat collect failed. Caused:{}", ex.getMessage(), ex);
			}
		}
	}

}
