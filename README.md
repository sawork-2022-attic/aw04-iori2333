# AW04 实验报告

## Docker 镜像构建

首先设置`pom.xml`，添加构建所需的插件依赖：

```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>3.2.0</version>
    <configuration>
        <to>
            <image>app-webpos-cached</image>
        </to>
        <allowInsecureRegistries>true</allowInsecureRegistries>
    </configuration>
</plugin>
```

此处的依赖版本按照群内消息设置为了`3.2.0`，以避免由于神秘的原因无法访问`gcr.io`导致构建失败。添加依赖后，执行`compile`任务后即可通过`jib:dockerBuild`构建Docker镜像

## 垂直扩展实验

我们使用Docker构建容器，指定参数`--cpus=0.5,1,2`，在`Test.scala`中指定并发数`20,200,500`多次实验，得到实验结果如表：

| CPUs | 并发数 | 平均延迟 |
| ---- | ------ | -------- |
| 0.5  | 20     | 1037     |
| 0.5  | 200    | 8653     |
| 0.5  | 500    | 18671    |
| 1    | 20     | 869      |
| 1    | 200    | 4231     |
| 1    | 500    | 6184     |
| 2    | 20     | 322      |
| 2    | 200    | 754      |
| 2    | 500    | 3813     |

（原始实验数据可见`test.md`）

对于相同的CPU数指定，并发数越高则平均延迟越大，这与预期是相符的。可见，随着CPUs的横向扩展，延迟数大幅度降低，且并发负载越高，这种降低的程度就更大，这表明并发数高时，性能瓶颈集中于CPU性能上。

实验中，我们发现第一次测试的实验数据并不稳定。推测是由于Spring启动后部分后台服务仍处于加载中，并不稳定。因此每次测试前我们均先进行一次500并发测试，以排除这方面的影响。

原始的`Gatling`测试脚本如下：

```scala
package webpos

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class Test extends Simulation {
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader(
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0");

  val scn = scenario("WebPos JD Test")
    .exec(http("request").get("/"))
    .exec(http("request").get("/add?pid=13284888"))

  setUp(scn.inject(atOnceUsers(500)).protocols(httpProtocol))
}
```

## 水平扩展实验

我们使用Docker固定参数`--cpus=1`开启4个容器，并撰写`haproxy.cfg`。我们仍使用上一节的测试文件，指定并发量为500。

```ini
defaults
    mode tcp
frontend webpos
    bind *:8080
    default_backend servers
backend servers
    balance roundrobin
    server server1 localhost:8081
    server server2 localhost:8082
    server server3 localhost:8083
    server server4 localhost:8084
```

实验结果如表所示：

| 服务器                          | 平均延迟 |
| ------------------------------- | -------- |
| server1+server2                 | 4186     |
| server1+server2+server3         | 2445     |
| server1+server2+server3+server4 | 1825     |

（原始实验数据可见`test.md`）

可见，随着横向扩展，平均延迟大幅度降低，但这种降低不是线性的（6184-4186-2445-1825）。推测是因为当服务器开启过多后，服务器性能瓶颈从CPU转移到多服务器IO时延导致的。

## Redis实验

为项目配置`Redis`与缓存，首先在`pom.xml`添加依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.session</groupId>
<artifactId>spring-session-data-redis</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

配置`application.properties`以连接到`redis`服务器：

```
spring.session.store-type=redis
spring.redis.cache.type=redis
spring.redis.cache.host=localhost
spring.redis.cache.port=6379
```

为WebPos的PosDB配置Cache支持：

```java
@Override
@Cacheable(cacheNames = "product", key = "#productId")
public Product getProduct(String productId) {
    ...
    }

@Override
@Cacheable(cacheNames = "products")
public List<Product> getProducts() {
    ...
    }
```

为WebPos配置App的Cache支持：

```java
@SpringBootApplication
@Configuration
@EnableCaching
@EnableRedisHttpSession
public class WebPosApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebPosApplication.class, args);
    }
}
```

为Controller添加Session：

```java
private HttpSession session;

@Autowired
public void setSession(HttpSession session) {
    this.session = session;
}

public Cart getCart() {
    Cart cart = (Cart) session.getAttribute("cart");
    if (cart == null) {
        cart = new Cart();
        session.setAttribute("cart", cart);
    }
    return cart;
}
```

测试文件仍按照第一节所给出的，得到结果如下：

```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                       1000 (OK=1000   KO=0     )
> min response time                                    317 (OK=317    KO=-     )
> max response time                                   3016 (OK=3016   KO=-     )
> mean response time                                  1577 (OK=1577   KO=-     )
> std deviation                                        694 (OK=694    KO=-     )
> response time 50th percentile                       1491 (OK=1491   KO=-     )
> response time 75th percentile                       2233 (OK=2233   KO=-     )
> response time 95th percentile                       2732 (OK=2732   KO=-     )
> response time 99th percentile                       2926 (OK=2926   KO=-     )
> mean requests/sec                                    250 (OK=250    KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                           102 ( 10%)
> 800 ms < t < 1200 ms                                 346 ( 35%)
> t > 1200 ms                                          552 ( 55%)
> failed                                                 0 (  0%)
================================================================================
```

由于此时并不跑在docker容器，故性能更强，延迟更低。但此时其实存在很严重的Cache-Miss问题，这是因为在原本的配置文件中只访问了一次根目录，这就导致每一次测试时缓存均不会使用，因此此时不仅不会加速程序，而且还会因为需要访问redis添加缓存而是应用程序减速。我们改进测试脚本：

```scala
val scn = scenario("WebPos JD Test")
    .exec(http("request").get("/"))
    .exec(http("request").get("/"))
    .exec(http("request").get("/"))
    .exec(http("request").get("/"))
    .exec(http("request").get("/add?pid=13284888"))

setUp(scn.inject(atOnceUsers(500)).protocols(httpProtocol))
```

此时，我们多添加了几次访问以模拟多次访问，这就使得存储的缓存能够获得实际用处。

测试结果如下：

```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                       2500 (OK=2500   KO=0     )
> min response time                                    285 (OK=285    KO=-     )
> max response time                                   1514 (OK=1514   KO=-     )
> mean response time                                   798 (OK=798    KO=-     )
> std deviation                                        147 (OK=147    KO=-     )
> response time 50th percentile                        785 (OK=785    KO=-     )
> response time 75th percentile                        851 (OK=851    KO=-     )
> response time 95th percentile                       1069 (OK=1069   KO=-     )
> response time 99th percentile                       1275 (OK=1275   KO=-     )
> mean requests/sec                                    500 (OK=500    KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                          1403 ( 56%)
> 800 ms < t < 1200 ms                                1025 ( 41%)
> t > 1200 ms                                           72 (  3%)
> failed                                                 0 (  0%)
================================================================================
```

可见，延迟时间大大降低，这验证了缓存的重大作用。

## Redis集群实验

最后，我们使用多个Redis服务器构成集群进行测试。

根据文档，至少需要6个服务器才可以构成集群，但我们应用较小，不需使用如此之多，因此在`application.properties`中只指定了3个节点。

```
spring.redis.cluster.nodes=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002
```

Redis节点配置信息如下：

```
port 7000
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
```

此处我们也只给出了3台服务器的配置，余下的均是大同小异。需要注意的是，执行时需要保证三个配置文件分别位于三个不同的子文件夹，否则会导致文件`nodes.conf`以及临时文件的冲突。

我们首先分别在配置文件夹中运行：

```shell
redis-server ./redis-700x.conf &
```

最后构建集群：

```shell
redis-cli --cluster create 127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 --cluster-replicas 1
```

此时开启程序，并运行上一小节内的改进后的测试脚本，实验结果为：

```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                       2500 (OK=2500   KO=0     )
> min response time                                     56 (OK=56     KO=-     )
> max response time                                   1100 (OK=1100   KO=-     )
> mean response time                                   649 (OK=649    KO=-     )
> std deviation                                        117 (OK=117    KO=-     )
> response time 50th percentile                        656 (OK=656    KO=-     )
> response time 75th percentile                        706 (OK=705    KO=-     )
> response time 95th percentile                        811 (OK=811    KO=-     )
> response time 99th percentile                        962 (OK=962    KO=-     )
> mean requests/sec                                    625 (OK=625    KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                          2355 ( 94%)
> 800 ms < t < 1200 ms                                 145 (  6%)
> t > 1200 ms                                            0 (  0%)
> failed                                                 0 (  0%)
================================================================================
```

可见性能又获得了极大的提升，为以上所有提升方法中效果最为显著的一项。

# WebPOS

The demo shows a web POS system , which replaces the in-memory product db in aw03 with a one backed by 京东.


![](jdpos.png)

To run

```shell
mvn clean spring-boot:run
```

Currently, it creates a new session for each user and the session data is stored in an in-memory h2 db. 
And it also fetches a product list from jd.com every time a session begins.

1. Build a docker image for this application and performance a load testing against it.
2. Make this system horizontally scalable by using haproxy and performance a load testing against it.
3. Take care of the **cache missing** problem (you may cache the products from jd.com) and **session sharing** problem (you may use a standalone mysql db or a redis cluster). Performance load testings.

Please **write a report** on the performance differences you notices among the above tasks.

