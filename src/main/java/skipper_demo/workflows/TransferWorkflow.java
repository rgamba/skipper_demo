package skipper_demo.workflows;

import io.github.rgamba.skipper.OperationProxyFactory;
import io.github.rgamba.skipper.api.OperationConfig;
import io.github.rgamba.skipper.api.OperationError;
import io.github.rgamba.skipper.api.Saga;
import io.github.rgamba.skipper.api.SkipperWorkflow;
import io.github.rgamba.skipper.api.annotations.StateField;
import io.github.rgamba.skipper.api.annotations.WorkflowMethod;
import io.github.rgamba.skipper.models.FixedRetryStrategy;
import lombok.NonNull;
import lombok.Value;
import lombok.val;
import skipper_demo.operations.Operations;
import skipper_demo.services.LedgerError;

import java.time.Duration;

public class TransferWorkflow implements SkipperWorkflow {
  // These constants are part of the transfer business logic, unrelated to skipper.
  public static final String SYSTEM_ACCOUNT = "system";
  public static final Integer AMOUNT_APPROVAL_THRESHOLD = 100;
  // This is where we define the operations that are going to be used in our workflow.
  private final Operations operations =
      OperationProxyFactory.create(
          Operations.class,
          OperationConfig.builder()
              .retryStrategy(
                  // It is recommended to refine a retry strategy so that skipper knows how to react
                  // in case of unexpected errors.
                  FixedRetryStrategy.builder()
                      .retryDelay(Duration.ofSeconds(2))
                      .maxRetries(2)
                      .build())
              .build());
  // We are also going to use another workflow inside this workflow (sub-workflow). Similar to
  // how we declare the operations we are going to use, we need to declare the sub-workflows in a similar fashion.
  public final ApprovalWorkflow approvalWorkflow =
      OperationProxyFactory.create(
          ApprovalWorkflow.class, OperationConfig.builder().timeout(Duration.ofMinutes(1)).build());
  // Note how this field is annotated with @StateField. This is how we tell skipper that this field is going to be
  // used to keep track of the state of our workflow.
  // This is typically used when we need to make decisions based on the value of the state fields or simply in order to
  // persist data that can later on be used by the caller to introspect.
  //
  // For instance, here we are just going to mark whether the transfer required approval so that the workflow instance
  // creator can later know whether the transfer required an approval.
  @StateField
  Boolean approvalRequired = false;

  // This is our main workflow method! This is where all the stuff happens.
  //
  // Note how we must annotate this method
  // with @WorkflowMethod. This is how we tell skipper that this is the entry point of our workflow.
  //
  // It is important to remember that the code inside this method (and all methods called within this method)
  // MUST BE DETERMINISTIC. It must also avoid any side effects, and preferably it should perform any I/O through
  // operations rather than doing it directly.
  @WorkflowMethod
  public TransferResult transfer(@NonNull String from, @NonNull String to, int amount) {
    validateAmount(amount);
    int transferFee = transferFee(amount);
    // A saga is a convenient way to model a series of operations that and their compensating operations (rollbacks) in
    // case something goes wrong. Every operation in the saga might have a compensating operation that will only be
    // executed in case we need to roll back in case of a non-recoverable error.
    // We chose to use Saga here but there are other ways to model the same.
    Saga saga = new Saga();

    try {
      if (amount >= AMOUNT_APPROVAL_THRESHOLD) {
        approvalRequired = true;
        // We call our first operation here (well, in this case it is technically a sub-workflow, but it is pretty much
        // the same). It is important to remember that even though it seems that the code is executing synchronously,
        // in reality this code will "sleep" (not literally) until the operation is completed (or, in this case, the
        // sub-workflow completes).
        if (!approvalWorkflow.getApproval(from, amount)) {
          // Approval workflow was unable to get an approval, or it got a negative approval, so we will end the workflow
          // here and signal the caller that even though the workflow completed, it was not successful.
          return new TransferResult(false, "unable to get transfer approval");
        }
      }
      // We then initiate our transfer by executing a series of operations one after the other.
      val debitAuthCode = operations.withdraw(from, amount + transferFee, genIdempotencyToken());
      // Note that even though we create a compensation for every operation, the actual compensation WON'T BE EXECUTED here.
      // This is just a way to say "hey, in case you need to roll back the previous operation, this is how you do it".
      saga.addCompensation(operations::rollbackWithdraw, debitAuthCode, genIdempotencyToken());
      val creditAuthCode = operations.deposit(to, amount, genIdempotencyToken());
      saga.addCompensation(operations::rollbackDeposit, creditAuthCode, genIdempotencyToken());
      val systemCreditAuthCode =
          operations.deposit(SYSTEM_ACCOUNT, transferFee, genIdempotencyToken());
      saga.addCompensation(
          operations::rollbackDeposit, systemCreditAuthCode, genIdempotencyToken());
      // Yay! everything worked just fine so complete the workflow and signal a successful result to the caller.
      return new TransferResult(true, "transfer completed successfully");
    } catch (LedgerError | OperationError e) {
      // In case any of the previous operations failed to complete, either because of a non-recoverable error like
      // the sender not having enough funds, or because max number of retries was reached, we need to rollback or
      // execute compensating actions!
      //
      // This function is actually executing all compensating actions in parallel and waiting for all of them to
      // complete before proceeding.
      saga.compensate();
      // Even though compensation succeeded the workflow did not complete successfully, so we signal a failure to the
      // caller by setting a non-successful result.
      return new TransferResult(
          false,
          String.format("unexpected error when trying to complete transfer: %s", e.getMessage()));
    }
  }

  private void validateAmount(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
  }

  private int transferFee(int amount) {
    return (int) Math.round(amount * .1);
  }

  @Value
  public static class TransferResult {
    boolean isSuccess;
    String message;
  }
}
