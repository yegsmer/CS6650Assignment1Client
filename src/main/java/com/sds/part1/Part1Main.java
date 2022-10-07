package com.sds.part1;

import com.sds.model.LiftRideEvent;
import com.sds.model.Counter;

import java.sql.Timestamp;
import java.util.concurrent.*;

public class Part1Main {

    private final static int TOTAL_REQUESTS = 200000;
    private final static LiftRideEvent POISON = new LiftRideEvent();
    private final static int PHASE_ONE_THREADS = 32;
    private final static int PHASE_TWO_THREADS = 224;
    private final static int MAX_REQUEST_ACCEPT_PHASE_ONE = 1000;
    private final static int MAX_REQUEST_ACCEPT_PHASE_TWO = 1200;
    private final static String BASE_PATH = "http://34.219.57.136:8080/Lab2_war/skiers_Web/";

    public static void main(String[] args) throws InterruptedException {

        Timestamp startTime = new Timestamp(System.currentTimeMillis());

        Counter passedRequests = new Counter();
        Counter failedRequests = new Counter();

        BlockingQueue<LiftRideEvent> blockingQueue = new LinkedBlockingDeque<>();

        // Start a single producer thread to put events in the the blocking queue and the associated countdown latch
        CountDownLatch producerLatch = new CountDownLatch(1);
        Runnable producer = new Producer(blockingQueue,PHASE_TWO_THREADS + PHASE_ONE_THREADS , POISON,
            TOTAL_REQUESTS,producerLatch);
        new Thread(producer).start();

        producerLatch.await();

        // 32 consumer threads fot phase 1
        CountDownLatch consumerLatch = new CountDownLatch(PHASE_ONE_THREADS + PHASE_TWO_THREADS);
        for (int i = 0; i < PHASE_ONE_THREADS; i++) {
            new Thread(new Consumer(BASE_PATH,blockingQueue, POISON, MAX_REQUEST_ACCEPT_PHASE_ONE, passedRequests, failedRequests, consumerLatch)).start();
        }

        // Additional threads for phase 2
        for (int i = 0; i < PHASE_TWO_THREADS; i++) {
            new Thread(new Consumer(BASE_PATH,blockingQueue, POISON, MAX_REQUEST_ACCEPT_PHASE_TWO, passedRequests, failedRequests, consumerLatch)).start();
        }


        consumerLatch.await();

        Timestamp endTime = new Timestamp(System.currentTimeMillis());
        long latency = endTime.getTime() - startTime.getTime();

        System.out.println();
        System.out.println("----------- Part One Performance Calculation -----------");
        System.out.println("The number of successful requests: " + passedRequests.getVal());
        System.out.println("The number of failed requests: " + failedRequests.getVal());
        System.out.println("The total run time (wall time): " + latency + "ms");
        System.out.println("The throughput in requests per second " + (int) ((TOTAL_REQUESTS * 1000) / latency));
        System.out.println("------------------------ The End ------------------------");

    }
}

