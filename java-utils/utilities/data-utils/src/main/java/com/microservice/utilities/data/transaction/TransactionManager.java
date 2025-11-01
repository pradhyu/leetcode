package com.microservice.utilities.data.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Utility class for programmatic transaction management.
 * Provides convenient methods for executing code within transactions.
 */
@Component
public class TransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(TransactionManager.class);

    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final PlatformTransactionManager platformTransactionManager;

    public TransactionManager(PlatformTransactionManager platformTransactionManager) {
        this.platformTransactionManager = platformTransactionManager;
        
        // Default transaction template
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        
        // Read-only transaction template
        this.readOnlyTransactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    /**
     * Execute code within a transaction
     */
    public <T> T executeInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            try {
                return action.get();
            } catch (Exception e) {
                logger.error("Error in transaction, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("Transaction failed", e);
            }
        });
    }

    /**
     * Execute code within a transaction (void return)
     */
    public void executeInTransaction(Runnable action) {
        transactionTemplate.execute(status -> {
            try {
                action.run();
                return null;
            } catch (Exception e) {
                logger.error("Error in transaction, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("Transaction failed", e);
            }
        });
    }

    /**
     * Execute code within a read-only transaction
     */
    public <T> T executeInReadOnlyTransaction(Supplier<T> action) {
        return readOnlyTransactionTemplate.execute(status -> {
            try {
                return action.get();
            } catch (Exception e) {
                logger.error("Error in read-only transaction", e);
                throw new RuntimeException("Read-only transaction failed", e);
            }
        });
    }

    /**
     * Execute code within a read-only transaction (void return)
     */
    public void executeInReadOnlyTransaction(Runnable action) {
        readOnlyTransactionTemplate.execute(status -> {
            try {
                action.run();
                return null;
            } catch (Exception e) {
                logger.error("Error in read-only transaction", e);
                throw new RuntimeException("Read-only transaction failed", e);
            }
        });
    }

    /**
     * Execute code within a new transaction (requires new)
     */
    public <T> T executeInNewTransaction(Supplier<T> action) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        TransactionTemplate newTransactionTemplate = new TransactionTemplate(platformTransactionManager, def);
        
        return newTransactionTemplate.execute(status -> {
            try {
                return action.get();
            } catch (Exception e) {
                logger.error("Error in new transaction, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("New transaction failed", e);
            }
        });
    }

    /**
     * Execute code within a new transaction (void return)
     */
    public void executeInNewTransaction(Runnable action) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        TransactionTemplate newTransactionTemplate = new TransactionTemplate(platformTransactionManager, def);
        
        newTransactionTemplate.execute(status -> {
            try {
                action.run();
                return null;
            } catch (Exception e) {
                logger.error("Error in new transaction, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("New transaction failed", e);
            }
        });
    }

    /**
     * Execute code with custom transaction definition
     */
    public <T> T executeWithCustomTransaction(TransactionDefinition definition, Supplier<T> action) {
        TransactionTemplate customTemplate = new TransactionTemplate(platformTransactionManager, definition);
        
        return customTemplate.execute(status -> {
            try {
                return action.get();
            } catch (Exception e) {
                logger.error("Error in custom transaction, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("Custom transaction failed", e);
            }
        });
    }

    /**
     * Execute code with custom transaction definition (void return)
     */
    public void executeWithCustomTransaction(TransactionDefinition definition, Runnable action) {
        TransactionTemplate customTemplate = new TransactionTemplate(platformTransactionManager, definition);
        
        customTemplate.execute(status -> {
            try {
                action.run();
                return null;
            } catch (Exception e) {
                logger.error("Error in custom transaction, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("Custom transaction failed", e);
            }
        });
    }

    /**
     * Execute code with manual transaction control
     */
    public <T> T executeWithManualTransaction(TransactionCallback<T> callback) {
        return transactionTemplate.execute(callback);
    }

    /**
     * Execute code with timeout
     */
    public <T> T executeWithTimeout(int timeoutSeconds, Supplier<T> action) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setTimeout(timeoutSeconds);
        
        TransactionTemplate timeoutTemplate = new TransactionTemplate(platformTransactionManager, def);
        
        return timeoutTemplate.execute(status -> {
            try {
                return action.get();
            } catch (Exception e) {
                logger.error("Error in transaction with timeout, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("Transaction with timeout failed", e);
            }
        });
    }

    /**
     * Execute code with specific isolation level
     */
    public <T> T executeWithIsolation(int isolationLevel, Supplier<T> action) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(isolationLevel);
        
        TransactionTemplate isolationTemplate = new TransactionTemplate(platformTransactionManager, def);
        
        return isolationTemplate.execute(status -> {
            try {
                return action.get();
            } catch (Exception e) {
                logger.error("Error in transaction with isolation level, rolling back", e);
                status.setRollbackOnly();
                throw new RuntimeException("Transaction with isolation failed", e);
            }
        });
    }

    /**
     * Execute multiple operations in a single transaction
     */
    public void executeBatch(Runnable... actions) {
        executeInTransaction(() -> {
            for (Runnable action : actions) {
                action.run();
            }
        });
    }

    /**
     * Execute with retry on transaction failure
     */
    public <T> T executeWithRetry(Supplier<T> action, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                return executeInTransaction(action);
            } catch (Exception e) {
                lastException = e;
                attempts++;
                logger.warn("Transaction attempt {} failed, retrying... (max: {})", attempts, maxRetries, e);
                
                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(100 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Transaction retry interrupted", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("Transaction failed after " + maxRetries + " attempts", lastException);
    }

    /**
     * Check if currently in a transaction
     */
    public boolean isInTransaction() {
        try {
            TransactionStatus status = platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
            return !status.isNewTransaction();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get current transaction status
     */
    public TransactionStatus getCurrentTransactionStatus() {
        return platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    /**
     * Create a transaction template with custom settings
     */
    public TransactionTemplate createCustomTemplate(int propagation, int isolation, int timeout, boolean readOnly) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(propagation);
        def.setIsolationLevel(isolation);
        def.setTimeout(timeout);
        def.setReadOnly(readOnly);
        
        return new TransactionTemplate(platformTransactionManager, def);
    }
}