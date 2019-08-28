Spring Boot深度实践---从Reactive到WebFlux

# 理解Reactive

## 一些说法：

- Reactive是异步非阻塞编程
- Reactive能够提升程序性能
- Reactive能够解决传统编程模型遇到的困境

## Reactive框架

- RxJava: Reactive Extensions

- Reactor: Spring WebFlux Reactive类库

- Flow API:Java 9 Flow API实现



- 反应堆模式（Reactor）----同步非阻塞（多工）
- Proactor模式-------异步非阻塞（多工）
- 观察者模式（Observer）---事件监听（推push的模式：数据由客户端推送到服务端）
- 迭代模式---（拉pull的模式）
- Java并发模型



## 传统编程模型中的某些困境



### [Reactor](https://projectreactor.io/docs/core/release/reference/)认为阻塞可能是浪费的

>### 3.1. Blocking Can Be Wasteful
>
>Modern applications can reach huge numbers of concurrent users, and, even though the capabilities of modern hardware have continued to improve, performance of modern software is still a key concern.
>
>There are broadly two ways one can improve a program’s performance:
>
>1. **parallelize**: use more threads and more hardware resources.
>2. **seek more efficiency** in how current resources are used.
>
>Usually, Java developers write programs using blocking code. This practice is fine until there is a performance bottleneck, at which point the time comes to introduce additional threads, running similar blocking code. But this scaling in resource utilization can quickly introduce contention and concurrency problems.
>
>Worse still, blocking wastes resources. If you look closely, as soon as a program involves some latency (notably I/O, such as a database request or a network call), resources are wasted because a thread (or many threads) now sits idle, waiting for data.
>
>So the parallelization approach is not a silver bullet. It is necessary in order to access the full power of the hardware, but it is also complex to reason about and susceptible to resource wasting.

**观点归纳**

- 阻塞导致性能瓶颈和浪费资源

#### **理解阻塞的弊端**

**阻塞场景-数据顺序加载**

加载流程如下

```sequence
# title: 序列图sequence(示例)
participant load()
participant loadConfigurations()
participant loadUsers()
participant loadOrders()



# note left of A: A左侧说明
# note over B: 覆盖B的说明
# note right of C: C右侧说明

load()->loadConfigurations(): 耗时1ms
loadConfigurations()->loadUsers():耗时2ms
loadUsers()->loadOrders():耗时3ms
# B->>C:实线虚箭头
# B-->>A:虚线虚箭头
```


Java实现

```java
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

```

结论

> 由于加载过程串行执行的关系，导致消耗实现线性累加，串行执行即Blocking模式





#### 理解阻塞并行的复杂



>### 3.2. Asynchronicity to the Rescue?
>
>The second approach (mentioned earlier), seeking more efficiency, can be a solution to the resource wasting problem. By writing *asynchronous*, *non-blocking* code, you let the execution switch to another active task **using the same underlying resources** and later come back to the current process when the asynchronous processing has finished.
>
>But how can you produce asynchronous code on the JVM? Java offers two models of asynchronous programming:
>
>- **Callbacks**: Asynchronous methods do not have a return value but take an extra `callback` parameter (a lambda or anonymous class) that gets called when the result is available. A well known example is Swing’s `EventListener`hierarchy.
>- **Futures**: Asynchronous methods return a `Future<T>` **immediately**. The asynchronous process computes a `T` value, but the `Future` object wraps access to it. The value is not immediately available, and the object can be polled until the value is available. For instance, `ExecutorService` running `Callable<T>` tasks use `Future` objects.
>
>Are these techniques good enough? Not for every use case, and both approaches have limitations.
>
>Callbacks are hard to compose together, quickly leading to code that is difficult to read and maintain (known as "Callback Hell").
>
>Consider an example: showing the top five favorites from a user on the UI or suggestions if she doesn’t have a favorite. This goes through three services (one gives favorite IDs, the second fetches favorite details, and the third offers suggestions with details):

**观点归纳**

- 阻塞导致性能瓶颈和浪费资源
- 增加线程可能会引起资源竞争和并发问题
- 并行的方式不是银弹（不能解决所有问题）

**并行场景-并行数据加载**

加载流程如下

```sequence
# title: 序列图sequence(示例)
participant load()
participant loadConfigurations()
participant loadUsers()
participant loadOrders()



# note left of A: A左侧说明
# note over B: 覆盖B的说明
# note right of C: C右侧说明

load()->loadConfigurations(): 耗时1ms
load()->loadUsers():耗时2ms
load()->loadOrders():耗时3ms
# B->>C:实线虚箭头
# B-->>A:虚线虚箭头
```



Java实现

```java
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

```

结论

>明显的，程序改造为并行加载后，性能和资源利用率得到提升，消耗时间取最大值

**延伸思考**

- 如果阻塞导致性能瓶颈和资源浪费的话，Reactive也能解决这个问题？
- 为什么不直接使用`Future#get()`方法强制所有任务执行完毕，然后再统计总耗时？
- 由于以上三个方法之间没有数据依赖关系，所以执行方式有串行调整为并行后，能够达到性能提升的效果。如果方法之间存在依赖关系，那么提升效果是否还会如此明显，并且如何确保它们的执行顺序？



### Reactive认为异步不一定能够救赎

>### 3.2 Asynchronicity to the Rescue?
>
>The second approach (mentioned earlier), seeking more efficiency, can be a solution to the resource wasting problem. By writing *asynchronous*, *non-blocking* code, you let the execution switch to another active task **using the same underlying resources** and later come back to the current process when the asynchronous processing has finished.
>
>But how can you produce asynchronous code on the JVM? Java offers two models of asynchronous programming:
>
>- **Callbacks**: Asynchronous methods do not have a return value but take an extra `callback` parameter (a lambda or anonymous class) that gets called when the result is available. A well known example is Swing’s `EventListener`hierarchy.
>- **Futures**: Asynchronous methods return a `Future<T>` **immediately**. The asynchronous process computes a `T` value, but the `Future` object wraps access to it. The value is not immediately available, and the object can be polled until the value is available. For instance, `ExecutorService` running `Callable<T>` tasks use `Future` objects.
>
>Are these techniques good enough? Not for every use case, and both approaches have limitations.
>
>Callbacks are hard to compose together, quickly leading to code that is difficult to read and maintain (known as "Callback Hell").
>
>Consider an example: showing the top five favorites from a user on the UI or suggestions if she doesn’t have a favorite. This goes through three services (one gives favorite IDs, the second fetches favorite details, and the third offers suggestions with details):

**观点归纳**

- Callbacks是解决非阻塞的方案，然而他们之间很难组合，并且快速得将代码引至“Callback Hell”
- Futures相对于Callbacks好一点，不过还是无法组合，不过`CompletableFuture`能够提升这方面的不足

# Reactive Streams规范

# Reactor框架应用



# 走向Spring WebFlux