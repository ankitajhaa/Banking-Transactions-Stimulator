import fraud.FraudDetector;
import model.Account;
import model.Transaction;
import service.TransactionService;
import worker.DepositTask;
import worker.TransferTask;
import worker.WithdrawTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        TransactionService service = new TransactionService();
        Account acc1 = new Account("ACC001", 5000);
        Account acc2 = new Account("ACC002", 3000);
        service.addAccount(acc1);
        service.addAccount(acc2);

        Thread fraudThread = new Thread(new FraudDetector(service.getTransactionLog()));
        fraudThread.setDaemon(true);
        fraudThread.setName("FraudDetector");
        fraudThread.start();

        ExecutorService executor = Executors.newFixedThreadPool(5);

        // ── Section 1: Basic async transactions ─────────────────
        System.out.println("═══ Submitting concurrent transactions ═══\n");

        Future<Transaction> deposit1  = executor.submit(new DepositTask(service,  "ACC001", 5000));
        Future<Transaction> withdraw1 = executor.submit(new WithdrawTask(service, "ACC001", 2000));
        Future<Transaction> withdraw2 = executor.submit(new WithdrawTask(service, "ACC001", 7000));
        Future<Transaction> transfer1 = executor.submit(new TransferTask(service, "ACC001", "ACC002", 3000));

        executor.submit((Runnable) () ->
            System.out.println(Thread.currentThread().getName()
                    + " | [Runnable] Balance check ACC001: ₹" + acc1.getBalance()));
        executor.submit((Runnable) () ->
            System.out.println(Thread.currentThread().getName()
                    + " | [Runnable] Balance check ACC002: ₹" + acc2.getBalance()));

        System.out.println("\n═══ Deadlock Prevention Test ═══");
        System.out.println("Submitting opposite-direction transfers simultaneously...\n");

        Future<Transaction> transfer2 = executor.submit(
                new TransferTask(service, "ACC001", "ACC002", 500)); // ACC001 → ACC002
        Future<Transaction> transfer3 = executor.submit(
                new TransferTask(service, "ACC002", "ACC001", 500)); // ACC002 → ACC001 (opposite!)

        System.out.println("\n═══ Fraud Detection Test ═══\n");

        // Trigger Rule 1 — large transaction
        Future<Transaction> largeTxn = executor.submit(
                new WithdrawTask(service, "ACC001", 15000));

        // Trigger Rule 2 — rapid withdrawals (5 quick withdrawals)
        List<Future<Transaction>> rapidWithdrawals = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rapidWithdrawals.add(executor.submit(
                    new WithdrawTask(service, "ACC001", 100)));
        }

        // Small pause so fraud detector scan picks up rapid withdrawals
        Thread.sleep(1000);

        // ── Section 4: invokeAll — batch async execution ────────
        System.out.println("\n═══ Stress test: 10 concurrent tasks ═══\n");
        List<Callable<Transaction>> stressTasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            stressTasks.add(new DepositTask(service,  "ACC001", 500));
            stressTasks.add(new WithdrawTask(service, "ACC001", 300));
        }
        // invokeAll blocks until ALL tasks complete (or timeout)
        List<Future<Transaction>> stressFutures =
                executor.invokeAll(stressTasks, 10, TimeUnit.SECONDS);

        // ── Section 5: Collect results with full exception handling
        System.out.println("\n═══ Results ═══\n");
        printResult("deposit1",  deposit1);
        printResult("withdraw1", withdraw1);
        printResult("withdraw2", withdraw2);
        printResult("transfer1", transfer1);

        System.out.println("\n── Deadlock prevention results ──");
        printResult("transfer2 (ACC001→ACC002)", transfer2);
        printResult("transfer3 (ACC002→ACC001)", transfer3);

        System.out.println("\n── Fraud test results ──");
        printResult("large txn ₹15000", largeTxn);
        for (Future<Transaction> f : rapidWithdrawals) {
            printResult("rapid withdrawal", f);
        }

        System.out.println("\n── Stress test results ──");
        for (Future<Transaction> f : stressFutures) {
            printResult("stress", f);
        }

        // ── Graceful shutdown ────────────────────────────────────
        System.out.println("\n═══ Initiating Graceful Shutdown ═══");
        executor.shutdown();

        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            System.out.println("[WARN] Executor did not finish in time — forcing shutdown");
            List<Runnable> pending = executor.shutdownNow();
            System.out.println("[WARN] " + pending.size() + " tasks were pending at shutdown");
        } else {
            System.out.println("✓ All tasks completed — executor shut down cleanly");
        }

        System.out.println("✓ FraudDetector daemon stopped (JVM exit)");

        System.out.println("\n═══ Final Balances ═══");
        System.out.println("ACC001: ₹" + acc1.getBalance());
        System.out.println("ACC002: ₹" + acc2.getBalance());
    }

    private static void printResult(String label, Future<Transaction> future) {
        try {
            Transaction t = future.get(5, TimeUnit.SECONDS);
            System.out.println("[" + label + "] " + t);
        } catch (TimeoutException e) {
            System.out.println("[" + label + "] FUTURE TIMEOUT — task took too long");
            future.cancel(true);
        } catch (CancellationException e) {
            System.out.println("[" + label + "] CANCELLED");
        } catch (ExecutionException e) {
            System.out.println("[" + label + "] EXECUTION ERROR: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[" + label + "] INTERRUPTED while waiting for result");
        }
    }
}