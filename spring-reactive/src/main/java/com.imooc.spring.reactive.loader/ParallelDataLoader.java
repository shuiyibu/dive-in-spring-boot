package com.imooc.spring.reactive.loader;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Auther: langdylan
 * @Date: 2019-08-28 20:07
 * @Description: 并行数据加载
 */
public class ParallelDataLoader extends DataLoader {
    public static void main(String[] args) {
        /**
         * [Thread: pool-1-thread-1] loadConfigurations() costs: 1000 ms
         * [Thread: pool-1-thread-2] loadUsers() costs: 2003 ms
         * [Thread: pool-1-thread-3] loadOrders costs: 3006 ms
         * load() total cost: 3081 ms
         */
        new ParallelDataLoader().load();
    }

    @Override
    protected void doLoad() {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        CompletionService completionService = new ExecutorCompletionService(executorService);

        completionService.submit(super::loadConfigurations, null); // 耗时 >= 1s
        completionService.submit(super::loadUsers, null);// 耗时 >= 2s
        completionService.submit(super::loadOrders, null);// 耗时 >= 3s

        int count = 0;
        while (count < 3) {//等待三个任务完成
            if (completionService.poll() != null) {
                count++;
            }
        }
        executorService.shutdown();
    }
}
