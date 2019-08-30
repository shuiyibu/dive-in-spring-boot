package com.imooc.spring.reactive.loader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @Auther: langdylan
 * @Date: 2019-08-29 10:59
 * @Description:
 */
public class FutureBlockingDataLoader extends DataLoader {
    public static void main(String[] args) {
        /**
         * [Thread: pool-1-thread-1] loadConfigurations() costs: 1003 ms
         * [Thread: pool-1-thread-2] loadUsers() costs: 2001 ms
         * [Thread: pool-1-thread-3] loadOrders costs: 3002 ms
         * load() total cost: 6044 ms
         */
        new FutureBlockingDataLoader().load();
    }

    private void runCompletely(Future<?> future) {
        try {
            future.get();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void doLoad() {
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        runCompletely(executorService.submit(super::loadConfigurations, null)); // 耗时 >= 1s
        runCompletely(executorService.submit(super::loadUsers, null));// 耗时 >= 2s
        runCompletely(executorService.submit(super::loadOrders, null));// 耗时 >= 3s

        executorService.shutdown();
    }
}
