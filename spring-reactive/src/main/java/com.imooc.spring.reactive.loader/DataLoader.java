package com.imooc.spring.reactive.loader;

import java.util.concurrent.TimeUnit;

import static java.lang.System.out;

/**
 * @Auther: langdylan
 * @Date: 2019-08-28 19:50
 * @Description: 阻塞串行数据加载
 */
public class DataLoader {
    public static void main(String[] args) {
        /**
         * [Thread: main] loadConfigurations() costs: 1005 ms
         * [Thread: main] loadUsers() costs: 2004 ms
         * [Thread: main] loadOrders costs: 3005 ms
         * load() total cost: 6036 ms
         */
        new DataLoader().load();
    }

    public final void load() {
        long startTime = System.currentTimeMillis();
        doLoad();
        long costTime = System.currentTimeMillis() - startTime;
        out.println("load() total cost: " + costTime + " ms");

    }

    protected void doLoad() {
        loadConfigurations();
        loadUsers();
        loadOrders();
    }

    protected final void loadConfigurations() {
        loadMock("loadConfigurations()", 1);

    }

    protected final void loadUsers() {
        loadMock("loadUsers()", 2);

    }

    protected final void loadOrders() {
        loadMock("loadOrders", 3);
    }

    private void loadMock(String source, int seconds) {
        try {
            long startTime = System.currentTimeMillis();
            long milliseconds = TimeUnit.SECONDS.toMillis(seconds);
            Thread.sleep(milliseconds);
            long costTime = System.currentTimeMillis() - startTime;
            out.printf("[Thread: %s] %s costs: %d ms\n", Thread.currentThread().getName(), source, costTime);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
