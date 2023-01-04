package skipper_demo.workflows;

import io.github.rgamba.skipper.OperationProxyFactory;
import io.github.rgamba.skipper.api.SkipperWorkflow;
import io.github.rgamba.skipper.api.WaitTimeout;
import io.github.rgamba.skipper.api.annotations.SignalConsumer;
import io.github.rgamba.skipper.api.annotations.StateField;
import io.github.rgamba.skipper.api.annotations.WorkflowMethod;
import lombok.NonNull;
import skipper_demo.operations.VendingMachineOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This workflow models a vending machine that can vend more than one item at a time.
 *
 * <p>The workflow is divided in 2 phases. The first phase where the user can select one or more items and
 * the second phase when the user introduces coins until the amount is enough to cover for the total price.
 */
public class VendingMachine implements SkipperWorkflow {
    private VendingMachineOperations machineOperations = OperationProxyFactory.create(VendingMachineOperations.class);
    private static final Map<String, Integer> products = new HashMap<String, Integer>(){{
        put("coke", 3);
        put("chips", 5);
    }};
    @StateField int balance = 0; // The amount of money inserted by the user so far
    @StateField List<String> cart = new ArrayList<>(); // The items selected by the user
    @StateField VendingStage stage = VendingStage.WAITING_FOR_PRODUCTS; // Marker for the current stage of the workflow

    /**
     * This workflow takes in no initial arguments. Think of the start of the user touching the screen of the vending
     * machine or pressing a button as the trigger for this workflow.
     *
     * <p>You'll also notice this workflow doesn't have a return value. All I/O, including dispensing the items, is
     * performed through operations.
     */
    @WorkflowMethod
    public void startSession() {
        // In the initial phase we'll wait for the user to select items. Given user selection is an async operation
        // that requires some input from the user, we'll use signals to allow the user to pass in the selections to this
        // workflow.
        //
        // We'll wait for 2 minutes between selections before we consider the user has finished selecting items before
        // proceeding to the next phase. We will also consider the user has finished selected items if they start inserting
        // coins to pay for the items.
        while (balance == 0) {
            int cartItems = cart.size();
            try {
                waitUntil(() -> cartItems != cart.size() || balance > 0, Duration.ofMinutes(2));
            } catch (WaitTimeout unused) {
                break;
            }
        }
        // Now that we have the cart with items full, we'll wait for the user to insert
        // coins until the cart balance is covered.
        stage = VendingStage.WAITING_FOR_COINS;
        try {
            waitUntil(() -> balance >= getCartTotal(), Duration.ofMinutes(2));
            machineOperations.dispense(cart);
            if (balance > getCartTotal()) {
                machineOperations.returnChange(balance - getCartTotal());
            }
        } catch (WaitTimeout unused) {
            // The user didn't insert enough coins. Return the balance and abort the transaction.
            machineOperations.returnCoins(balance);
        }
    }

    @SignalConsumer
    public void insertCoin(int amount) {
        if (cart.isEmpty()) {
            throw new IllegalArgumentException("Cannot insert coins without products in the cart");
        }
        balance += amount;
    }

    @SignalConsumer
    public void addProduct(@NonNull String product) {
        if (stage != VendingStage.WAITING_FOR_PRODUCTS) {
            throw new IllegalArgumentException("Cannot add products at this stage");
        }
        if (!products.containsKey(product)) {
            throw new IllegalArgumentException("invalid product code");
        }
        cart.add(product);
        System.out.println(cart);
    }

    int getCartTotal() {
        int total = 0;
        for (String product : cart) {
            total += products.get(product);
        }
        return total;
    }

    enum VendingStage {
        WAITING_FOR_PRODUCTS,
        WAITING_FOR_COINS
    }
}
