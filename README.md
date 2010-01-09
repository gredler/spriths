Spriths
=======

**SPRI**ng **TH**readed **S**tartup

An experiment in [Spring](http://www.springsource.org/about/) application startup optimization.

*NOT FOR PRODUCTION USE! THIS IS JUST AN EXPERIMENT!*

Introduction
------------

Applications built around Spring tend to spend most of their startup time initializing singleton beans. These beans are instantiated serially on a single thread. However, computers have started to scale in terms of CPU cores, rather than in terms of CPU speed. As a result, it is very likely that computer upgrades going forward will exhibit only marginal performance improvements in terms of Spring application startup time.

The solution is to parallelize the initialization of all eager-init Spring singleton beans in order to take advantage of all available CPU cores. This quick hack is an experiment in such an optimization, based heavily on the TestNG test [parallelization](http://beust.com/weblog/archives/000525.html) [work](http://beust.com/weblog/archives/000536.html) [done](http://code.google.com/p/testng/source/browse/trunk/src/main/org/testng/internal/thread/GroupThreadPoolExecutor.java) previously by Cedric Beust.

Approach
--------

My main design goal was to keep the code as unobtrusive and isolated as possible (no pulling the Spring codebase and refactoring it). Thus, I was constrained to working with the Spring codebase as it currently exists, subclassing the standard Spring classes.

The actual implementation looks very similar to Cedric's TestNG solution: a _ThreadPoolExecutor_ which performs a topological sort, re-evaluating the graph state every time a node is processed. I also borrowed the idea of generating a sequence of graph images as a way of visualizing the initialization process.

The implementation was complicated by the fact that there are so many different types of dependencies between Spring beans: "normal" bean references, parent bean references, references to AOP aspects created by Spring internally, bean references inside of collections, references determined dynamically at runtime via introspection of the other beans in the application context, etc.

Another issue is that _DefaultSingletonBeanRegistry_ has a very coarse lock guarding its _getSingleton(...)_ method variants. I had to override one of these methods and remove the lock, making it non-threadsafe, in order to get any performance wins. Though this change doesn't affect the very controlled tests I ran, it's not something you would want out in the wild. However, bug [SPR-5360](http://jira.springframework.org/browse/SPR-5360) has already been raised concerning this lock, so maybe something a little more thread-friendly will replace it in the official distribution.

Step by step
------------

The images below illustrate the effects of parallelizing the initialization of eager-init singleton Spring beans, as well as the effects of the _getSingleton(...)_ lock. They're screenshots of the thread visualization available in the (very excellent) [YourKit](http://yourkit.com/) profiler. Each row represents a thread; threads named _"spriths-xxx"_ are bean initialization threads spawned by Spriths. The green portions of the bars represent time spent by a thread actually running code; red portions indicate that a thread was blocked, waiting for another thread to release a lock somewhere.

The first image illustrates the _status quo ante_. This is how Spring currently behaves: a single thread, chugging away at the work.

![no extra threads](http://github.com/gredler/spriths/raw/master/doc/no-extra-threads.png)

In the second image, Spriths is being used to farm the work out to different threads, but we've left the _getSingleton(...)_ lock in the code. This image shows lots of lock contention (red) and not much concurrent initialization (green).

![threads with coarse lock](http://github.com/gredler/spriths/raw/master/doc/threads-with-coarse-lock.png)

The final image illustrates the situation with Spriths after removing the lock. Spriths' threads are no longer blocking each other and are free to do useful work; there's much more green and much less red. As a result, the graph is also much shorter along the horizontal axis (less total time spent on initialization). Mission accomplished!

![threads without coarse lock](http://github.com/gredler/spriths/raw/master/doc/threads-without-coarse-lock.png)

Results
-------

So is something like this worth the effort? Well, probably; it'll depend on the specific usecase. The sample application context contained in this project loads in about 3.2 seconds on a 2.8 GHz Core 2 Duo (two CPU cores) using the standard _ClassPathXmlApplicationContext_, and in about 1.8 seconds using the custom _ThreadedClassPathXmlApplicationContext_ provided by this project. That's a savings of about 40%.

However, using this in a couple of real-world applications showed gains more in the 15% to 20% range. Specifically, these applications were using Hibernate for JPA; JPA persistence context initialization was triggering retrieval of metadata from the database on startup. The network I/O generated by a single bean was consuming most of the startup time.

In general, a solution like this would pay greater dividends for application contexts with an equitable work distribution amongst the various beans; if one bean is consuming most of the startup time, it might be more productive to try to optimize that single bean's performance.

Can I try this myself?
----------------------

Yep. Just pull the code and run:

    mvn test
    dot2png
    png2html

The first step runs a test, loading a sample Spring application context in both the standard _ClassPathXmlApplicationContext_ and in the custom _ThreadedClassPathXmlApplicationContext_ provided by this project. The run times of each load will be printed to the console.

The test also generates a series of graphs illustrating the bean initialization process. Each graph shows the dependencies between the various beans, as well as the state of each bean. White nodes represent beans that are waiting to be initialized but are waiting on their dependent beans to be initialized; yellow nodes represent beans that are waiting to be initialized and which are not blocked by any uninitialized dependencies; green nodes represent beans that are in the middle of being initialized; finally, grey nodes represent beans which have been successfully initialized.

The second step above converts these graphs into PNG images, and the third step incorporates these images into an HTML file. This overview file will be available at _target/graphs/graphs.html_. There's a sample overview file in the _sample_ directory.

**NOTE:** The second step requires that you have [Graphviz](http://www.graphviz.org/) installed.
