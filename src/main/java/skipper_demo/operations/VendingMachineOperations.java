package skipper_demo.operations;

import java.util.List;

public class VendingMachineOperations {
    public void dispense(List<String> cart) {
        System.out.printf("Dispensing: %s%n", cart);
    }

    public void returnChange(int amount) {
        System.out.printf("Returning change: %d%n", amount);
    }

    public void returnCoins(int balance) {
        System.out.printf("Returning coins: %d%n", balance);
    }
}
