package skipper_demo.workflows;

import io.github.rgamba.skipper.api.OperationError;
import io.github.rgamba.skipper.testUtils.WorkflowTest;
import lombok.val;
import org.junit.Before;
import org.junit.Test;
import skipper_demo.operations.Operations;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TransferWorkflowTest extends WorkflowTest {
  private TransferWorkflow transferWorkflow;
  private Operations mockOperations;

  @Before
  public void setUp() {
    super.setUp();
    mockOperations = mock(Operations.class);
    transferWorkflow = new TransferWorkflow();
    assertWorkflowIsValid(transferWorkflow);
    mockOperationField(transferWorkflow, "operations", mockOperations);
  }

  @Test
  public void testTransferHappyPath() throws Exception {
    // given
    when(mockOperations.withdraw(any(), anyInt(), any())).thenReturn("");
    when(mockOperations.deposit(any(), anyInt(), any())).thenReturn("");
    // when
    val result = transferWorkflow.transfer("a", "b", 10);
    // then
    assertEquals(1, 1);
    verify(mockOperations, times(1)).withdraw(eq("a"), eq(11), anyString());
    verify(mockOperations, times(2)).deposit(any(), anyInt(), anyString());
  }

  @Test
  public void testTransferWhenDepositFails() throws Exception {
    // given
    when(mockOperations.withdraw(any(), anyInt(), any())).thenReturn("123");
    when(mockOperations.deposit(any(), anyInt(), any()))
        .thenThrow(new OperationError(new RuntimeException("something went wrong")));
    // when
    val result = transferWorkflow.transfer("a", "b", 10);
    // then
    assertEquals(1, 1);
    verify(mockOperations, times(1)).rollbackWithdraw(eq("123"), anyString());
  }
}
