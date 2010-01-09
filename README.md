Spriths
=======

**SPRI**ng **TH**readed **S**tartup

An experiment in [Spring](http://www.springsource.org/about/) application startup optimization.

*NOT FOR PRODUCTION USE! THIS IS JUST AN EXPERIMENT!*

Introduction
------------

Applications built around Spring tend to spend most of their startup time initializing singleton beans. These beans are instantiated serially on a single thread. However, computers have started to scale in terms of CPU cores, rather than in terms of CPU speed. As a result, it is very likely that computer upgrades going forward will exhibit only marginal performance improvements in terms of Spring application startup time.

The solution is to parallelize the initialization of all eager-init Spring singleton beans in order to take advantage of all available CPU cores. This quick hack is an experiment in such an optimization, based heavily on the TestNG test [parallelization](http://beust.com/weblog/archives/000525.html) [work](http://beust.com/weblog/archives/000536.html) [done](http://code.google.com/p/testng/source/browse/trunk/src/main/org/testng/internal/thread/GroupThreadPoolExecutor.java) previously by Cedric Beust.

Can I run this myself?
----------------------

Yep. Just pull the code and run:

    mvn test
    dot2png
    png2html

The first step runs a test, loading a sample Spring application context in both the standard _ClassPathXmlApplicationContext_ and in the custom _ThreadedClassPathXmlApplicationContext_ provided by this project. The run times of each load will be printed to the console.

The test also generates a series of graphs illustrating the bean initialization process. Each graph shows the dependencies between the various beans, as well as the state of each bean. White nodes represent beans that are waiting to be initialized but are waiting on their dependent beans to be initialized; yellow nodes represent beans that are waiting to be initialized and which are not blocked by any uninitialized dependencies; green nodes represent beans that are in the middle of being initialized; finally, grey nodes represent beans which have been successfully initialized.

The second step above converts these step-by-step graphs into PNG images, and the third step incorporates these images into an HTML file. This overview file will be available at _target/graphs/graphs.html_. There's a sample overview file in the _sample_ directory.

**NOTE:** The second step requires that you have [Graphviz](http://www.graphviz.org/) installed.
