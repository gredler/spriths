package net.gredler.spriths;

import org.springframework.transaction.annotation.Transactional;

@Transactional
public class Bean2 extends Bean {

    public Bean2() {
        // Empty.
    }

    public Bean2(long initTime) throws InterruptedException {
        super(initTime);
    }

    public void doSomething() {
        // Empty.
    }

}
