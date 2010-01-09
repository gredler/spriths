package net.gredler.spriths;

import static java.lang.Boolean.valueOf;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.aop.Advisor;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.Assert;
import org.springframework.web.servlet.handler.AbstractDetectingUrlHandlerMapping;

public class ThreadedDefaultListableBeanFactory extends DefaultListableBeanFactory {

    public ThreadedDefaultListableBeanFactory() {
        super();
    }

    public ThreadedDefaultListableBeanFactory(BeanFactory internalParentBeanFactory) {
        super(internalParentBeanFactory);
    }

    @Override
    public void preInstantiateSingletons() throws BeansException {

        if (this.logger.isInfoEnabled()) {
            this.logger.info("Pre-instantiating singletons in " + this);
        }

        Map< String , BeanInitializer > initializers = new HashMap< String , BeanInitializer >();
        for (String beanName : getBeanDefinitionNames()) {
            if (requiresPreInstantiation(beanName)) {
                addBeanAndDependencies(initializers, beanName, false);
            }
        }

        // AOP advisors appear to have circular dependencies amongst themselves; process them first, serially, so that
        // we don't stall the executor below.
        for (BeanInitializer bi : initializers.values()) {
            if (bi.isAdvisor()) {
                bi.run();
                bi.setStatus(Status.FINISHED);
            }
        }

        // Initialize "normal" beans in parallel; this does not include AOP advisors (above) or beans with dynamic
        // dependencies (below).
        InitializingExecutor executor = new InitializingExecutor(initializers.values());
        executor.run();
        while (!executor.isShutdown()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                this.logger.warn(e.getMessage(), e);
            }
        }

        // Beans with dynamic dependencies (i.e. they determine their dependencies at runtime via introspection of the
        // other beans in the application context) run last, serially.
        for (BeanInitializer bi : initializers.values()) {
            if (bi.isDynamic()) {
                bi.run();
                bi.setStatus(Status.FINISHED);
            }
        }
    }

    private boolean requiresPreInstantiation(String beanName) {
        boolean init = false;
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            if (isFactoryBean(beanName)) {
                Class< ? > beanClass = predictBeanType(beanName, bd, new Class[] { SmartFactoryBean.class });
                if (SmartFactoryBean.class.isAssignableFrom(beanClass)) {
                    SmartFactoryBean factory = (SmartFactoryBean) getBean(FACTORY_BEAN_PREFIX + beanName);
                    init = factory.isEagerInit();
                }
            } else {
                init = true;
            }
        }
        return init;
    }

    // TODO: There are other bean dependency types that could be analyzed and included in the dependency graph; we only
    // handle some of the possibilities; see BeanDefinitionValueResolver.resolveValueIfNecessary(Object, Object).
    private BeanInitializer addBeanAndDependencies(Map< String , BeanInitializer > initializers, String beanName,
        boolean advisor) {
        BeanInitializer bi = initializers.get(beanName);
        if (bi != null) {
            return bi;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Planning to parallelize creation of '" + beanName + "'.");
        }
        Class< ? > beanType = getType(beanName);
        boolean dynamic = AbstractDetectingUrlHandlerMapping.class.isAssignableFrom(beanType);
        bi = new BeanInitializer(beanName, advisor, dynamic);
        initializers.put(beanName, bi);
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        for (PropertyValue pv : bd.getPropertyValues().getPropertyValues()) {
            Object val = pv.getValue();
            if (val instanceof BeanReference) {
                BeanReference ref = (BeanReference) val;
                String refName = ref.getBeanName();
                BeanInitializer dependency = addBeanAndDependencies(initializers, refName, false);
                bi.addDependency(dependency);
            } else if (val instanceof ManagedMap) {
                ManagedMap map = (ManagedMap) val;
                for (Object o : map.values()) {
                    if (o instanceof BeanReference) {
                        BeanReference ref = (BeanReference) o;
                        String refName = ref.getBeanName();
                        BeanInitializer dependency = addBeanAndDependencies(initializers, refName, false);
                        bi.addDependency(dependency);
                    }
                }
            } else if (val instanceof ManagedSet) {
                ManagedSet set = (ManagedSet) val;
                for (Object o : set) {
                    if (o instanceof BeanReference) {
                        BeanReference ref = (BeanReference) o;
                        String refName = ref.getBeanName();
                        BeanInitializer dependency = addBeanAndDependencies(initializers, refName, false);
                        bi.addDependency(dependency);
                    }
                }
            } else if (val instanceof ManagedList) {
                ManagedList list = (ManagedList) val;
                for (Object o : list) {
                    if (o instanceof BeanReference) {
                        BeanReference ref = (BeanReference) o;
                        String refName = ref.getBeanName();
                        BeanInitializer dependency = addBeanAndDependencies(initializers, refName, false);
                        bi.addDependency(dependency);
                    }
                }
            }
        }
        String[] advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, Advisor.class, true, false);
        for (String advisorName : advisorNames) {
            BeanInitializer dependency = addBeanAndDependencies(initializers, advisorName, true);
            bi.addDependency(dependency);
        }
        return bi;
    }

    // http://code.google.com/p/testng/source/browse/trunk/src/main/org/testng/internal/thread/GroupThreadPoolExecutor.java
    // http://beust.com/weblog/archives/000525.html
    // http://beust.com/weblog/archives/000536.html
    // With Hibernate 3.2.6 you can make things faster by eliminating contention on DB connection initialization amongst
    // multiple JPA persistence contexts:
    // -Dhibernate.temp.use_jdbc_metadata_defaults=false (see SettingsFactory.buildSettings(Properties))
    private class InitializingExecutor extends ThreadPoolExecutor {

        private String GRAPHS_DIR = getProperty("spriths.graphs.dir");

        private boolean GRAPHS_INCLUDE_ADVISORS = valueOf(getProperty("spriths.graphs.advisors", "false"));

        private Collection< BeanInitializer > initializers;

        private List< String > dotFiles = new ArrayList< String >();

        private AtomicInteger counter = new AtomicInteger();

        public InitializingExecutor(Collection< BeanInitializer > bis) {
            super(bis.size(), bis.size(), 2, SECONDS, new ArrayBlockingQueue< Runnable >(bis.size()));
            this.setThreadFactory(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    return new Thread(r, "spriths-" + counter.getAndIncrement());
                }
            });
            this.initializers = bis;
        }

        public void run() {
            synchronized (this.initializers) {
                saveDot(GRAPHS_INCLUDE_ADVISORS);
                executeRunnableInitializers();
            }
        }

        @Override
        public void afterExecute(Runnable r, Throwable t) {
            synchronized (this.initializers) {
                BeanInitializer bi = (BeanInitializer) r;
                bi.setStatus(Status.FINISHED);
                if (logger.isDebugEnabled()) {
                    logger.debug("Finished initializing '" + bi.getBeanName() + "'.");
                }
                saveDot(GRAPHS_INCLUDE_ADVISORS);
                int finished = getInitializers(Status.FINISHED, true, false, true, false).size();
                int goal = getInitializers(null, true, false, true, false).size();
                if (finished == goal) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Shutting down the initializing executor.");
                    }
                    shutdown();
                } else {
                    executeRunnableInitializers();
                }
            }
        }

        private void executeRunnableInitializers() {
            Set< BeanInitializer > runnables = getRunnableInitializers();
            if (runnables.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No beans ready to be initialized at the moment.");
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Initializing a new round of beans: " + runnables);
                }
                for (BeanInitializer runnable : runnables) {
                    runnable.setStatus(Status.RUNNING);
                    execute(runnable);
                }
            }
        }

        private Set< BeanInitializer > getRunnableInitializers() {
            Set< BeanInitializer > runnables = new HashSet< BeanInitializer >();
            Set< BeanInitializer > waiting = getInitializers(Status.WAITING, true, false, true, false);
            for (BeanInitializer initializer : waiting) {
                if (initializer.isRunnable()) {
                    runnables.add(initializer);
                }
            }
            return runnables;
        }

        private Set< BeanInitializer > getInitializers(Status status, boolean nonAdvisors, boolean advisors,
            boolean nonDynamic, boolean dynamic) {
            Set< BeanInitializer > matches = new HashSet< BeanInitializer >();
            for (BeanInitializer bi : this.initializers) {
                if (status == bi.getStatus() || status == null) {
                    if ((bi.isAdvisor() && advisors) || (!bi.isAdvisor() && nonAdvisors)) {
                        if ((bi.isDynamic() && dynamic) || (!bi.isDynamic() && nonDynamic)) {
                            matches.add(bi);
                        }
                    }
                }
            }
            return matches;
        }

        private void saveDot(boolean includeAdvisors) {
            if (GRAPHS_DIR == null || GRAPHS_DIR.trim().length() == 0) {
                return;
            }
            String dot = toDot(includeAdvisors);
            this.dotFiles.add(dot);
            int i = this.dotFiles.size();
            try {
                File dir = new File(GRAPHS_DIR);
                if (i == 1) {
                    dir.mkdirs();
                    for (File f : dir.listFiles()) {
                        if (f.getName().endsWith(".dot")) {
                            f.delete();
                        }
                    }
                }
                File f = new File(dir, "" + (i < 10 ? "0" : "") + i + ".dot");
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                bw.append(dot);
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // http://en.wikipedia.org/wiki/DOT_language
        // http://www.graphviz.org/doc/info/command.html
        private String toDot(boolean includeAdvisors) {
            String STYLE_WAITING = "";
            String STYLE_RUNNABLE = "style=filled color=yellow";
            String STYLE_RUNNING = "style=filled color=green";
            String STYLE_FINISHED = "style=filled color=grey";
            StringBuilder sb = new StringBuilder("digraph g {\n");
            Set< BeanInitializer > runnables = getRunnableInitializers();
            for (BeanInitializer bi : getInitializers(Status.WAITING, true, includeAdvisors, true, true)) {
                String style = runnables.contains(bi) ? STYLE_RUNNABLE : STYLE_WAITING;
                String name = getSanitizedBeanName(bi);
                String label = getLabel(bi);
                sb.append("  " + name + "[label=\"" + label + "\" " + style + "]\n");
            }
            for (BeanInitializer bi : getInitializers(Status.RUNNING, true, includeAdvisors, true, true)) {
                String name = getSanitizedBeanName(bi);
                String label = getLabel(bi);
                sb.append("  " + name + "[label=\"" + label + "\" " + STYLE_RUNNING + "]\n");
            }
            for (BeanInitializer bi : getInitializers(Status.FINISHED, true, includeAdvisors, true, true)) {
                String name = getSanitizedBeanName(bi);
                String label = getLabel(bi);
                sb.append("  " + name + "[label=\"" + label + "\" " + STYLE_FINISHED + "]\n");
            }
            for (BeanInitializer bi : getInitializers(null, true, includeAdvisors, true, true)) {
                String name1 = getSanitizedBeanName(bi);
                String dotted = bi.getStatus() == Status.FINISHED ? "style=dotted" : "";
                for (BeanInitializer dependency : bi) {
                    if (!dependency.isAdvisor() || includeAdvisors) {
                        String name2 = getSanitizedBeanName(dependency);
                        sb.append("  " + name1 + " -> " + name2 + " [" + dotted + "]\n");
                    }
                }
            }
            sb.append("}\n");
            return sb.toString();
        }

        private String getSanitizedBeanName(BeanInitializer bi) {
            return bi.getBeanName().replace('.', '_').replace('#', '_');
        }

        private String getLabel(BeanInitializer bi) {
            String s = bi.getBeanName();
            if (bi.getStatus() == Status.FINISHED) {
                s += " (" + bi.getMillis() + " ms)";
            }
            return s;
        }
    }

    private class BeanInitializer implements Runnable, Iterable< BeanInitializer > {

        private String beanName;

        // Advisors are special because they appear to have circular dependencies amongst themselves; we process them
        // first, serially, in order to avoid an immediate deadlock.
        private boolean advisor;

        // The dependencies for some beans can't be analyzed statically, e.g. beans that determine their dependencies at
        // runtime via introspection of the other beans in the application context; we process these last, serially.
        private boolean dynamic;

        private Status status;

        private Set< BeanInitializer > dependsOn;

        private long startTime;

        private long endTime;

        public BeanInitializer(String beanName, boolean advisor, boolean dynamic) {
            Assert.notNull(beanName, "beanName must not be null");
            this.beanName = beanName;
            this.advisor = advisor;
            this.dynamic = dynamic;
            this.status = Status.WAITING;
            this.dependsOn = new HashSet< BeanInitializer >();
        }

        public void addDependency(BeanInitializer bi) {
            this.dependsOn.add(bi);
        }

        public Iterator< BeanInitializer > iterator() {
            return Collections.unmodifiableSet(this.dependsOn).iterator();
        }

        public boolean isRunnable() {
            boolean runnable = (this.status == Status.WAITING);
            if (runnable) {
                for (BeanInitializer dependency : this.dependsOn) {
                    if (dependency.getStatus() != Status.FINISHED) {
                        runnable = false;
                        break;
                    }
                }
            }
            return runnable;
        }

        public void run() {
            this.startTime = System.currentTimeMillis();
            try {
                getBean(this.beanName);
            } finally {
                this.endTime = System.currentTimeMillis();
            }
        }

        public String getBeanName() {
            return this.beanName;
        }

        public boolean isAdvisor() {
            return advisor;
        }

        public boolean isDynamic() {
            return dynamic;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public long getMillis() {
            return this.endTime - this.startTime;
        }

        @Override
        public String toString() {
            return this.beanName;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BeanInitializer)) {
                return false;
            }
            BeanInitializer other = (BeanInitializer) obj;
            return this.beanName.equals(other.beanName);
        }

        @Override
        public int hashCode() {
            return this.beanName.hashCode();
        }
    }

    private enum Status {
        WAITING, RUNNING, FINISHED
    }

    // TODO: Threads bottleneck due to a coarse lock on singleton creation;
    // see http://jira.springframework.org/browse/SPR-5360
    // HACK: Get rid of the coarse lock; this is not thread-safe, but should be OK as long as we do a good job of
    // pre-instantiating all singletons in the correct order.
    // Commented code is the original code from the superclass, just to get an idea of what got left out and where.
    @Override
    public Object getSingleton(String beanName, ObjectFactory singletonFactory) {
        Assert.notNull(beanName, "'beanName' must not be null");
        // synchronized (this.singletonObjects) {
        // Object singletonObject = this.singletonObjects.get(beanName);
        Object singletonObject = null;
        boolean exists = this.containsSingleton(beanName);
        // if (singletonObject == null) {
        if (!exists) {
            // if (this.singletonsCurrentlyInDestruction) {
            // throw new BeanCreationNotAllowedException(beanName,
            // "Singleton bean creation not allowed while the singletons of this factory are in destruction " +
            // "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
            // }
            if (logger.isDebugEnabled()) {
                logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
            }
            beforeSingletonCreation(beanName);
            // boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
            // if (recordSuppressedExceptions) {
            // this.suppressedExceptions = new LinkedHashSet();
            // }
            try {
                singletonObject = singletonFactory.getObject();
            } catch (BeanCreationException ex) {
                // if (recordSuppressedExceptions) {
                // for (Iterator it = this.suppressedExceptions.iterator(); it.hasNext();) {
                // ex.addRelatedCause((Exception) it.next());
                // }
                // }
                throw ex;
            } finally {
                // if (recordSuppressedExceptions) {
                // this.suppressedExceptions = null;
                // }
                afterSingletonCreation(beanName);
            }
            addSingleton(beanName, singletonObject);
        }
        return (singletonObject != NULL_OBJECT ? singletonObject : null);
        // }
    }

}
