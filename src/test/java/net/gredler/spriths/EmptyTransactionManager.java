package net.gredler.spriths;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

public class EmptyTransactionManager extends AbstractPlatformTransactionManager {

    private static final long serialVersionUID = 7291894222868497137L;

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        // Empty.
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        // Empty.
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        // Empty.
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        return null;
    }

}
