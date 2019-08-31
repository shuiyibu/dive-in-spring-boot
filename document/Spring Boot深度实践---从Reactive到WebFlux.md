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

#### 理解"Callback Hell"

```java
package com.imooc.spring.reactive.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static java.lang.System.out;

/**
 * @Auther: langdylan
 * @Date: 2019-08-29 10:27
 * @Description:
 */
public class JavaGUI {
    /**
     * [Thread: main]
     * [Thread: AWT-EventQueue-0] mouse click coordinate(X: 150, Y: 78)
     * [Thread: AWT-EventQueue-0] mouse click coordinate(X: 224, Y: 128)
     * [Thread: AWT-EventQueue-0] close jFrame...
     * [Thread: AWT-EventQueue-0] exit jFrame...
     *
     * @param args
     */
    public static void main(String[] args) {
        JFrame jFrame = new JFrame("GUI 示例");
        jFrame.setBounds(500, 300, 400, 300);
        LayoutManager layoutManager = new BorderLayout(400, 300);
        jFrame.setLayout(layoutManager);
        jFrame.addMouseListener(new MouseAdapter() { //callback 1
            @Override
            public void mouseClicked(MouseEvent e) {
                out.printf("[Thread: %s] mouse click coordinate(X: %d, Y: %d)\n", currentThreadName(), e.getX(), e.getY());
            }
        });
        jFrame.addWindowListener(new WindowAdapter() { //callback 2
            @Override
            public void windowClosing(WindowEvent e) {
                out.printf("[Thread: %s] close jFrame...\n", currentThreadName());
                jFrame.dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                out.printf("[Thread: %s] exit jFrame...\n", currentThreadName());
                System.exit(0);
            }
        });
        out.printf("[Thread: %s] \n", currentThreadName());
        jFrame.setVisible(true);
    }

    private static String currentThreadName() {
        return Thread.currentThread().getName();
    }
}

```

**结论**

>Java GUI以及事件/监听模式基本采用匿名内置类实现，即回调实现。从本例中可以得出，鼠标的点击确实没有被其他线程阻塞，不过当监听的维度增多时，Callback实现也随之增多，同时，事件/监听者模式的并发模式可以为同步或异步。
>
>>回顾
>>
>>- Spring事件/监听器（同步或异步）
>>  - 事件：`ApplicationEvent`
>>  - 事件监听器：`ApplicationListener`
>>  - 事件广播器：`ApplicationEventListenerMulticaster`
>>  - 事件发布器：`ApplicationEventPublisher`
>>- Servlet事件/监听
>>  - 同步
>>    - 事件：`ServletContextEvent`
>>    - 事件监听器：`ServletContextListener`
>>  - 异步
>>    - 事件：`AsyncEvent`
>>    - 事件监听器：`AsyncListener`

#### 理解`Future`阻塞问题

如果`DataLoader`的`loadOrders()`方法依赖于`loadUsers()`的结果，而`loadUsers()`又依赖于`loadConfiguration()`，调整实现：

```java
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

```

**结论**

>`Future#get())`方法不得不等待任务执行完成，换言之，如果多个任务提交后，返回的多个Future逐一调用`get()`方法时，将会依次blocking，任务的执行从并行变为串行，这也是之前“延伸思考”问题2的答案

#### 理解`Future`链式问题

由于`Future`无法实现异步执行结果链式处理，尽管`FutureBlockingDataLoader`能够解决方法数据依赖以及顺序执行的问题，不过它们并行执行带回了阻塞（串行）执行，所以，它不是一个理想实现，不过`CompletableFuture`可以帮助提升`Future`的限制

```java
package com.imooc.spring.reactive.loader;

import java.util.concurrent.CompletableFuture;

import static java.lang.System.out;

/**
 * @Auther: langdylan
 * @Date: 2019-08-29 11:09
 * @Description:
 */
public class ChainDataLoader extends DataLoader {
    /**
     * [Thread: ForkJoinPool.commonPool-worker-3] loadConfigurations() costs: 1005 ms
     * [Thread: ForkJoinPool.commonPool-worker-3] loadUsers() costs: 2004 ms
     * [Thread: ForkJoinPool.commonPool-worker-3] loadOrders costs: 3004 ms
     * [Thread: ForkJoinPool.commonPool-worker-3] Load completed.
     * load() total cost: 6072 ms
     *
     * @param args     */

    public static void main(String[] args) {
        new ChainDataLoader().load();
    }

    @Override
    protected void doLoad() {
        CompletableFuture
                .runAsync(super::loadConfigurations)// 耗时 >= 1s
                .thenRun(super::loadUsers)// 耗时 >= 2s
                .thenRun(super::loadOrders)// 耗时 >= 3s
                .whenComplete((result, throwable) -> {//完成回掉
                    out.printf("[Thread: %s] Load completed.\n", Thread.currentThread().getName());
                })
                .join();//等待完成
    }
}

```

**图解**



```sequence
# title: 序列图sequence(示例)
participant main thread
participant CompletableFuture thread
participant loadConfigurations()
participant loadUsers()
participant loadOrders()
participant whenComplete()



# note left of A: A左侧说明
# note over B: 覆盖B的说明
# note right of C: C右侧说明
main thread->CompletableFuture thread: 线程切换
CompletableFuture thread->loadConfigurations(): 耗时1ms
loadConfigurations()->loadUsers():耗时2ms
loadUsers()->loadOrders():耗时3ms
loadOrders()->whenComplete():执行完成时回调
CompletableFuture thread->main thread: 等待CompletableFuture \n 线程切换结束


# B->>C:实线虚箭头
# B-->>A:虚线虚箭头
```

**结论**

- 如果阻塞导致性能瓶颈和资源浪费的话，Reactive也能解决这个问题？
- `CompletableFuture`属于异步操作，如果强制等待结束的话，又回到了阻塞编程的方式，那么Reactive也会面临同样的问题吗？
- `CompletableFuture`让我我们理解到非阻塞不一定提升性能，那么Reactive也会这样吗？



### [Reactive Stream JVM](https://github.com/reactive-streams/reactive-streams-jvm)认为异步系统和资源消费需要特殊处理

>Handling streams of data—especially “live” data whose volume is not predetermined—requires special care in an asynchronous system. The most prominent issue is that resource consumption needs to be carefully controlled such that a fast data source does not overwhelm the stream destination. Asynchrony is needed in order to enable the parallel use of computing resources, on collaborating network hosts or multiple CPU cores within a single machine.

**观点归纳：**

- 流式数据容量难以预判
- 异步编程复杂
- 数据源和消费端之间资源消费难以平衡



**Reactive要解决以上所有问题吗？**



## **思考**

- Reactive到底是什么？
- Reactive的使用场景在哪里？
- Reactive存在怎样限制/不足？











# Reactive Programming定义

## The Reactive Manifesto

>Reactive systems  are Responsive, Resilient, Elastic and Message Driven

关键字：

- Responsive  响应的
- Resilient  适应性强的
- Elastic 弹性的
- Message Driven 消息驱动的



侧重点：

- 面向Reactive系统
- Reactive系统原则



## [WIKI](https://en.wikipedia.org/wiki/Reactive_programming)

>**Reactive programming** is a declarative [programming paradigm](https://en.wikipedia.org/wiki/Programming_paradigm) concerned with [data streams](https://en.wikipedia.org/wiki/Dataflow_programming) and the propagation of change. With this paradigm it is possible to express static (e.g., arrays) or dynamic (e.g., event emitters) *data streams* with ease, and also communicate that an inferred dependency within the associated *execution model* exists, which facilitates the automatic propagation of the changed data flow
>
>

关键字：

- 数据流 data stream
- 传播变化 propagation of change

侧重点

- 数据结构
  - 数组 array
  - 事件发射器 event emitters
- 数据变化

技术连接

- 数据流 Java 8 `stream`
- 传播变化 java `Observable`/ `Observer`
- 事件 Java `EventObject` / `EventListener`



## [Spring Framework](https://docs.spring.io/spring-framework/docs/5.0.0.M1/spring-framework-reference/html/web-reactive.html)

>In plain terms reactive programming is about non-blocking applications that are asynchronous and event-driven and require a small number of threads to scale. A key aspect of that definition is the concept of backpressure which is a mechanism to ensure producers don’t overwhelm consumers. For example in a pipeline of reactive components that extends from the database to the HTTP socket when the HTTP client is slow the data repository slows down or stops until capacity frees up.



关键字

- 变化响应 reacting to change
- 非阻塞 non-blocking

侧重点

- 响应通知
  - 操作完成 operations complete
  - 数据可用 data becomes avalible
- 技术连接
  - 非阻塞 Servlet 3.1  `ReadListener / WriteListener`
  - 响应通知 Servlet 3.0 `AsyncListener`



## [ReactiveX](http://reactivex.io/intro.html)

>ReactiveX extends [the observer pattern](http://en.wikipedia.org/wiki/Observer_pattern) to support sequences of data and/or events and adds operators that allow you to compose sequences together declaratively while abstracting away concerns about things like low-level threading, synchronization, thread-safety, concurrent data structures, and non-blocking I/O.

关键字：

- 观察者模式 observer pattern
- 数据 / 事件序列 sequences of data
- 序列操作符 operators
- 屏蔽并发细节 abstracting away

侧重点

- 设计模式
- 数据结构
- 数据模型
- 并发模型

技术连接

- 观察者模式 Java `Observable` / `Observer`
- 数据 / 事件序列 Java8 `Stream` 
- 序列操作符 Java8 `Stream`
- 屏蔽并发细节 `Executor` `Future` `Runnable`

## [Reactor](https://projectreactor.io/docs/core/release/reference/#getting-started-introducing-reactor)

> The reactive programming paradigm is often presented in object-oriented languages as an extension of the Observer design pattern. One can also compare the main reactive streams pattern with the familiar Iterator design pattern, as there is a duality to the `Iterable`-`Iterator` pair in all of these libraries. One major difference is that, while an Iterator is **pull**-based, reactive streams are **push**-based.









## @andrestaltz





设计模式

- 扩展模式：观察者（push）
  - 缺点
    - 主要只能满足非阻塞
    - 并没有强调异步
- 对立模式：迭代器（pull）
- 混合模式：反应堆、
  - 共同点：非阻塞
  - 不同点：R-同步；P-异步

# Reactive Streams规范

# Reactor框架应用

# Reactive Programming特性
## 编程模型（Programming Models)
- 响应式编程
- 函数式编程

# 走向Spring WebFlux



> 编程模型：同步异步
>
> 线程模型：阻塞非阻塞

