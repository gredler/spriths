package net.gredler.spriths;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ThreadedClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {

    public ThreadedClassPathXmlApplicationContext() {
        super();
    }

    public ThreadedClassPathXmlApplicationContext(ApplicationContext parent) {
        super(parent);
    }

    public ThreadedClassPathXmlApplicationContext(String configLocation) throws BeansException {
        super(configLocation);
    }

    public ThreadedClassPathXmlApplicationContext(String[] configLocations) throws BeansException {
        super(configLocations);
    }

    public ThreadedClassPathXmlApplicationContext(String[] configLocations, ApplicationContext parent)
        throws BeansException {
        super(configLocations, parent);
    }

    public ThreadedClassPathXmlApplicationContext(String[] configLocations, boolean refresh) throws BeansException {
        super(configLocations, refresh);
    }

    public ThreadedClassPathXmlApplicationContext(String[] configLocations, boolean refresh, ApplicationContext parent)
        throws BeansException {
        super(configLocations, refresh, parent);
    }

    public ThreadedClassPathXmlApplicationContext(String path, Class< ? > clazz) throws BeansException {
        super(path, clazz);
    }

    public ThreadedClassPathXmlApplicationContext(String[] paths, Class< ? > clazz) throws BeansException {
        super(paths, clazz);
    }

    public ThreadedClassPathXmlApplicationContext(String[] paths, Class< ? > clazz, ApplicationContext parent)
        throws BeansException {
        super(paths, clazz, parent);
    }

    @Override
    protected DefaultListableBeanFactory createBeanFactory() {
        return new ThreadedDefaultListableBeanFactory(getInternalParentBeanFactory());
    }

}
