package net.gredler.spriths;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.support.XmlWebApplicationContext;

public class ThreadedXmlWebApplicationContext extends XmlWebApplicationContext {

    protected DefaultListableBeanFactory createBeanFactory() {
        return new ThreadedDefaultListableBeanFactory(getInternalParentBeanFactory());
    }

}
