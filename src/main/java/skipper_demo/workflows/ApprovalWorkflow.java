package skipper_demo.workflows;

import io.github.rgamba.skipper.OperationProxyFactory;
import io.github.rgamba.skipper.api.OperationConfig;
import io.github.rgamba.skipper.api.SkipperWorkflow;
import io.github.rgamba.skipper.api.WaitTimeout;
import io.github.rgamba.skipper.api.annotations.SignalConsumer;
import io.github.rgamba.skipper.api.annotations.StateField;
import io.github.rgamba.skipper.api.annotations.WorkflowMethod;
import io.github.rgamba.skipper.models.FixedRetryStrategy;
import lombok.NonNull;
import skipper_demo.operations.Operations;

import java.time.Duration;

public class ApprovalWorkflow implements SkipperWorkflow {
  // We declare the operations that are going to be used in the workflow.
  // Note that it is not valid to directly instantiate the operations within the workflow! We must follow the
  // pattern below so that skipper can properly instantiate the operations.
  private final Operations operations =
      OperationProxyFactory.create(
          Operations.class,
          OperationConfig.builder()
              .retryStrategy(
                  FixedRetryStrategy.builder()
                      .retryDelay(Duration.ofSeconds(2))
                      .maxRetries(3)
                      .build())
              .build());
  // This workflow will require manual intervention, which means that it is asynchronous.
  // Therefore, we need a way to keep track of the "state", which in this case is whether the approval response has been
  // received or not. This is where @StateField comes in handy.
  @StateField
  public Boolean isApproved = null;

  @WorkflowMethod
  public boolean getApproval(@NonNull String user, int amount) {
    // First we notify the approver that an approval is required! This of this as sending out an email.
    // Remember that all I/O or side effects must be performed through operations!
    // Also remember that the workflow code will wait until the below operation eventually completed before proceeding.
    operations.notifyApprovalRequest(user, amount);
    try {
      // The workflow will wait until the below condition is met. This condition can take an aribitrarily long time to
      // complete, which is perfectly fine and won't affect performance of the workflow. In this case, we are waiting for
      // the approval response to be received, which will happen as a result of a manual intervention. (The approver clicking
      // on an email link sent above or something similar).
      //
      // We also set a timeout for this wait. This is to prevent the workflow from waiting forever in case the approver
      // response is not received on a timely manner.
      waitUntil(() -> isApproved != null, Duration.ofMinutes(10));
      return isApproved; // This is only executed IF the approval was received!
    } catch (WaitTimeout t) {
      // In case the conditional wait times out, it will throw a WaitTimeout exception, so we must catch it and handle
      // it appropriately. In this case, we are assuming that no response is the same as a rejection.
      return false;
    }
  }

  // Signal consumers are a way for workflows to receive input from external sources AFTER the workflow creation time.
  // In this case, this becomes handy because we need to receive the approval response from the approver.
  // Think of the approval as the manual operation of the user clicking a link on the notification email, which sends
  // the user to a web page that eventually executed this method.
  @SignalConsumer
  public void approveTransfer(boolean approved) {
    // Here we set the approval response for the @StateField annotated field `isApproved`.
    // Now, remember that the workflow code above has a conditional wait on this field, which means that immediately after
    // we change the value of this field to non-null, the conditional wait will complete and the workflow will resume
    // execution.
    isApproved = approved;
  }
}
