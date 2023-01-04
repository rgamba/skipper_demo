package skipper_demo.workflows;

import io.github.rgamba.skipper.testUtils.WorkflowTest;
import org.junit.Before;
import org.junit.Test;
import skipper_demo.operations.VendingMachineOperations;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VendingMachineTest extends WorkflowTest {
    private VendingMachine vendingMachine;
    private VendingMachineOperations mockOperations;

    @Before
    public void setUp() {
        super.setUp();
        mockOperations = mock(VendingMachineOperations.class);
        vendingMachine = new VendingMachine();
        assertWorkflowIsValid(vendingMachine);
        mockOperationField(vendingMachine, "machineOperations", mockOperations);
    }

    @Test
    public void testHappyPath() {
        // the first time around, the workflow will wait for products to be added to the cart
        assertWorkflowIsInWaitingState(() -> vendingMachine.startSession());
        assertEquals(VendingMachine.VendingStage.WAITING_FOR_PRODUCTS, vendingMachine.stage);
        // Let's add a product to the cart
        vendingMachine.addProduct("coke");
        vendingMachine.addProduct("chips");
        assertEquals(2, vendingMachine.cart.size());
        // The workflow should still be waiting for more products
        assertWorkflowIsInWaitingState(() -> vendingMachine.startSession());
        assertEquals(VendingMachine.VendingStage.WAITING_FOR_PRODUCTS, vendingMachine.stage);
        // Now let's add a coin, this should move the workflow to the next stage, no more waiting for products
        vendingMachine.insertCoin(1);
        assertEquals(1, vendingMachine.balance);
        assertWorkflowIsInWaitingState(() -> vendingMachine.startSession());
        assertEquals(VendingMachine.VendingStage.WAITING_FOR_COINS, vendingMachine.stage);
        // Now let's add another coin (more than needed so we can test the change being returned)
        vendingMachine.insertCoin(8);
        vendingMachine.startSession(); // Workflow should've completed.
        // Verify that product was dispensed and that change was returned
        verify(mockOperations, times(1)).dispense(eq(vendingMachine.cart));
        verify(mockOperations, times(1)).returnChange(eq(1));
    }

    @Test
    public void testWhenProductSelectionTimesOut_WorkflowProceedsToNextStage() {
        // the first time around, the workflow will wait for products to be added to the cart
        assertWorkflowIsInWaitingState(() -> vendingMachine.startSession());
        assertEquals(VendingMachine.VendingStage.WAITING_FOR_PRODUCTS, vendingMachine.stage);
        // Let's add a product to the cart
        vendingMachine.addProduct("coke");
        // Now let the workflow wait for 30 seconds
        advanceCurrentTimeBy(Duration.ofSeconds(31));
        // Now the workflow should be in waiting state but in the WAIT_FOR_COINS stage
        expectAndRecordWaitTimeout(() -> vendingMachine.startSession());
        assertEquals(VendingMachine.VendingStage.WAITING_FOR_COINS, vendingMachine.stage);
    }

    @Test
    public void testAddingCoinWithoutProductsInCartShouldFail() {
        assertThrows(IllegalArgumentException.class, () -> vendingMachine.insertCoin(1));
    }

    @Test
    public void testAddingProductWhenSessionIsInCheckoutPhaseShouldFail() {
        vendingMachine.addProduct("coke");
        vendingMachine.insertCoin(1);
        assertWorkflowIsInWaitingState(() -> vendingMachine.startSession());
        assertThrows(IllegalArgumentException.class, () -> vendingMachine.addProduct("coke"));
    }

    @Test
    public void testWaitingForCoinWhenWaitTimesOutBalanceIsReturned() {
        vendingMachine.addProduct("coke");
        vendingMachine.insertCoin(1);
        assertWorkflowIsInWaitingState(() -> vendingMachine.startSession());
        advanceCurrentTimeBy(Duration.ofSeconds(31));
        expectAndRecordWaitTimeout(() -> vendingMachine.startSession());
        verify(mockOperations, times(1)).returnCoins(eq(1));
    }
}
