package fraud;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import model.Transaction;
import model.Transaction.Type;

public class FraudDetector implements Runnable {

    private static final double LARGE_TXN_THRESHOLD = 10000.0;
    private static final int    RAPID_WITHDRAW_LIMIT   = 3;
    private static final long   RAPID_WINDOW_MS        = 5000;

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> withdrawalTracker
            = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Transaction> transactionLog;

    private int lastSeenCount = 0;

    public FraudDetector(ConcurrentHashMap<String, Transaction> transactionLog) {
        this.transactionLog = transactionLog;
    }

    @Override
    public void run() {
        System.out.println("[FraudDetector] Monitoring started (daemon)...");
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                scanNewTransactions();
                Thread.sleep(500); // scan every 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag
                break;
            }
        }

        System.out.println("[FraudDetector] Shutting down.");
    }

    private void scanNewTransactions() {
        int currentCount = transactionLog.size();
        if (currentCount == lastSeenCount) return; // nothing new

        transactionLog.values().forEach(this::inspect);
        lastSeenCount = currentCount;
    }

    public void inspect(Transaction t) {
        checkLargeTransaction(t);
        if (t.getType() == Type.WITHDRAW) {
            checkRapidWithdrawals(t);
        }
    }

    private void checkLargeTransaction(Transaction t) {
        if (t.getAmount() >= LARGE_TXN_THRESHOLD) {
            System.out.println("\n[FRAUD ALERT] Large transaction detected!"
                    + "\n  → Type:   " + t.getType()
                    + "\n  → Amount: ₹" + t.getAmount()
                    + "\n  → ID:     " + t.getTransactionId()
                    + "\n  → Time:   " + t.getTimestamp()
                    + "\n  → Status: " + t.getStatus() + "\n");
        }
    }

    private void checkRapidWithdrawals(Transaction t) {
        // Use transactionId as proxy for accountId (real system would track per account)
        String key = "withdraw-monitor";

        withdrawalTracker.putIfAbsent(key, new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<Long> timestamps = withdrawalTracker.get(key);

        long now = System.currentTimeMillis();
        timestamps.add(now);

        // Remove timestamps outside the window
        timestamps.removeIf(ts -> now - ts > RAPID_WINDOW_MS);

        if (timestamps.size() >= RAPID_WITHDRAW_LIMIT) {
            System.out.println("\n[FRAUD ALERT] Rapid withdrawals detected!"
                    + "\n  → " + timestamps.size() + " withdrawals within "
                    + (RAPID_WINDOW_MS / 1000) + " seconds"
                    + "\n  → Latest: ₹" + t.getAmount()
                    + "\n  → ID:     " + t.getTransactionId() + "\n");
        }
    }
}