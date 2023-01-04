package skipper_demo.resources;

import io.github.rgamba.skipper.client.SkipperClient;
import io.github.rgamba.skipper.models.WorkflowInstance;
import lombok.NonNull;
import lombok.val;
import skipper_demo.services.Ledger;
import skipper_demo.workflowHandlers.TransferCallbackHandler;
import skipper_demo.workflows.TransferWorkflow;
import skipper_demo.workflows.VendingMachine;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/vending")
@Produces(MediaType.APPLICATION_JSON)
public class VendingMachineResource {
    private final SkipperClient client;
    public VendingMachineResource(@NonNull SkipperClient engine) {
        this.client = engine;
    }

    @POST
    @Path("/")
    public WorkflowInstance startSession() {
        val response =
                client.createWorkflowInstance(
                        VendingMachine.class,
                        UUID.randomUUID().toString());
        return response.getWorkflowInstance();
    }

    @POST
    @Path("/{id}/add-product")
    public void addProduct(@PathParam("id") String id, @QueryParam("product") String product) {
        client.sendInputSignal(id, "addProduct", product);
    }

    @POST
    @Path("/{id}/add-coin")
    public void addProduct(@PathParam("id") String id, @QueryParam("amount") Integer coins) {
        client.sendInputSignal(id, "insertCoin", coins);
    }
}
