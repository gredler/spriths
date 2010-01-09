package net.gredler.spriths;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SprithsTest {

    private final static Log LOG = LogFactory.getLog(SprithsTest.class);

    @Test
    public void test() {

        long start = System.currentTimeMillis();
        new ClassPathXmlApplicationContext("classpath:application-context.xml");
        long end = System.currentTimeMillis();
        long elapsed = end - start;
        LOG.info("Loaded application context with ClassPathXmlApplicationContext in " + elapsed + " ms");

        long start2 = System.currentTimeMillis();
        new ThreadedClassPathXmlApplicationContext("classpath:application-context.xml");
        long end2 = System.currentTimeMillis();
        long elapsed2 = end2 - start2;
        LOG.info("Loaded application context with ThreadedClassPathXmlApplicationContext in " + elapsed2 + " ms");
    }

}
