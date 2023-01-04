package skipper_demo.operations;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.val;
import skipper_demo.services.Ledger;
import skipper_demo.services.LedgerError;

// In skipper, any class can be considered a workflow operation.
// The operation can expose one or more methods that can be called from a workflow.
@Singleton
public class Operations {

  private final Ledger ledger;

  // TODO: Default constructor needed for proxy creation, figure a way around this.
  public Operations() {
    ledger = null;
  }

  @Inject
  public Operations(@NonNull Ledger ledger) {
    this.ledger = ledger;
  }

  // Every public method in this class is considered a workflow operation.
  //
  // As such, it must behave idempotent! This is very important.
  // Note that this has a checked exception `LegerError`. Checked exceptions in workflow operations are used
  // to tell skipper that these type of errors should NOT be retried. Think of the case where we want to
  // withdraw money from a given account and there is not enough funds. In this case we don't want to retry
  // the error, so we signal that by checking the error types.
  //
  // Conversely, all other exceptions are considered transient and will be retried by skipper following the
  // retry policy in the workflow for the given operation.
  public String withdraw(String accountId, int amount, String idempotencyToken) throws LedgerError {
    return ledger.withdraw(accountId, amount, "transfer sent", idempotencyToken);
  }

  public boolean rollbackWithdraw(String creditId, String idempotencyKey) throws LedgerError {
    val transaction = ledger.getTransaction(creditId);
    ledger.deposit(
        transaction.getUserId(), transaction.getAmount(), "transfer send rollback", idempotencyKey);
    return true;
  }

  public String deposit(String account, int amount, String idempotencyKey) throws LedgerError {
    return ledger.deposit(account, amount, "transfer received", idempotencyKey);
  }

  public boolean rollbackDeposit(String debitId, String idempotencyKey) {
    val transaction = ledger.getTransaction(debitId);
    ledger.withdraw(
        transaction.getUserId(),
        transaction.getAmount(),
        "transfer receive rollback",
        idempotencyKey);
    return true;
  }

  public void notifyApprovalRequest(String account, Integer amount) {
    return;
  }
}
