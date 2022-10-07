package com.sds.part2;

import com.sds.model.LiftRideEvent;
import com.sds.model.Counter;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.api.SkiersApi;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class Consumer implements Runnable {
    private final static Integer MAX_TRIES = 5;

    private final BlockingQueue<LiftRideEvent> inputQueue;
    private final LiftRideEvent poison;
    private final SkiersApi apiInstance;
    private final int numOfRequests;
    private final Counter numPassedRequests;
    private final Counter numFailedRequests;
    private final CountDownLatch latch;
    private final SharedFileWriter sharedFileWriter;
    private final Queue<Metric> metrics;


    public Consumer(String base_path, BlockingQueue<LiftRideEvent> inputQueue, LiftRideEvent poison,
                        int numOfRequests, Counter numPassedRequests, Counter numFailedRequests, CountDownLatch latch, SharedFileWriter sharedFileWriter, Queue<Metric> metrics) {
        this.inputQueue = inputQueue;
        this.poison = poison;
        this.numOfRequests = numOfRequests;
        this.apiInstance = new SkiersApi();
        apiInstance.getApiClient().setBasePath(base_path);
        this.numFailedRequests = numFailedRequests;
        this.numPassedRequests = numPassedRequests;
        this.latch = latch;
        this.sharedFileWriter = sharedFileWriter;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        // Consumers take events from the blocking queue as long as it is not empty
        System.out.println("Consumer Thread " + Thread.currentThread().getName() + " Started");
        int counter = 0;
        ArrayList<String> data = new ArrayList<>();
        try {
            for(int i = 0; i < numOfRequests; i++) {
                LiftRideEvent event = inputQueue.take();

                if (event == poison ) {
                    break;
                }

                long startTime = new Timestamp(System.currentTimeMillis()).getTime();
                String startTimeViewer = new Timestamp(System.currentTimeMillis()).toString();
                int statusCode = this.retryRequests(event,apiInstance);
                long endTime = new Timestamp(System.currentTimeMillis()).getTime();
                long latency = endTime - startTime;
                Metric metric = new Metric(startTimeViewer,"POST",latency,statusCode);
                String fileLine =  metric.toString();
                metrics.add(metric);
                data.add(fileLine);
                counter++;
            }
        } catch (InterruptedException e) {
            System.err.println("The thread is interrupted");
            e.printStackTrace();
        } catch (ApiException e){
            this.numFailedRequests.inc(1);
            System.err.println("APIException when calling writeNewLiftRide");
            e.printStackTrace();
        }
        this.sharedFileWriter.addData(data);
        this.numPassedRequests.inc(counter);
        System.out.println("Consumer Thread " + Thread.currentThread().getName() + " Finished");
        this.latch.countDown();
    }
    private int retryRequests(LiftRideEvent event, SkiersApi apiInstance) throws ApiException{
        int count = 0;
        int maxTries = MAX_TRIES;
        while(true) {
            try {
                ApiResponse<Void> response = apiInstance.writeNewLiftRideWithHttpInfo(event.getBody(), event.getResortID(), event.getSeasonID(), event.getDayID(), event.getSkierID());
                return response.getStatusCode();
            } catch (ApiException e) {
                if (++count == maxTries) throw e;
            }
        }
    }


}