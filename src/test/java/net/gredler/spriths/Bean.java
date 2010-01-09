package net.gredler.spriths;

public class Bean {

    private long initTime;

    private Bean bean1;

    private Bean bean2;

    private Bean bean3;

    private Bean bean4;

    private Bean bean5;

    public Bean() {
        // Empty.
    }

    public Bean(long initTime) throws InterruptedException {
        this.initTime = initTime;
        Thread.sleep(initTime);
    }

    public long getInitTime() {
        return initTime;
    }

    public Bean getBean1() {
        return bean1;
    }

    public void setBean1(Bean bean1) {
        this.bean1 = bean1;
    }

    public Bean getBean2() {
        return bean2;
    }

    public void setBean2(Bean bean2) {
        this.bean2 = bean2;
    }

    public Bean getBean3() {
        return bean3;
    }

    public void setBean3(Bean bean3) {
        this.bean3 = bean3;
    }

    public Bean getBean4() {
        return bean4;
    }

    public void setBean4(Bean bean4) {
        this.bean4 = bean4;
    }

    public Bean getBean5() {
        return bean5;
    }

    public void setBean5(Bean bean5) {
        this.bean5 = bean5;
    }

}
