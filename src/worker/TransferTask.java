package worker;

import model.Transaction;
import service.TransactionService;

import java.util.concurrent.Callable;

public class TransferTask implements Callable<Transaction> {

    private final TransactionService service;
    private final String fromId;
    private final String toId;
    private final double amount;

    public TransferTask(TransactionService service, String fromId, String toId, double amount) {
        this.service = service;
        this.fromId  = fromId;
        this.toId    = toId;
        this.amount  = amount;
    }

    @Override
    public Transaction call() {
        System.out.println(Thread.currentThread().getName()
                + " | Transferring ₹" + amount 
                + " from " + fromId + " → " + toId
                + " | Lock order: "
                + (fromId.compareTo(toId) < 0
                    ? fromId + " → " + toId
                    : toId   + " → " + fromId)
                + " (deadlock-safe)");
        return service.transfer(fromId, toId, amount);
    }
}