# Skipper Demo

This is a very simple use-case for peer-to-peer transfers using Skipper. It is a simple web service that exposes
one endpoint for initiating a transfer, which in turn initiates a new workflow instance, and another endpoint to
"manually approve" a transfer that would require manual approval.

## Project structure

Take a look at the code, it is very simple. The important classes have been heavily documented to provide better context.

Skipper does not require you to follow any specific project structure. This is just a simple example.

* `operations` - The operations used by the workflow definitions live here. These typically contain business logic and are not coupled with skipper.
* `workflows` - The skipper workflow definitions live here. The code that lives here is the only one that will be modeled following skipper API.
* `workflowHandlers` - These are classes that are used to handle changes in the state of the workflows. Think of them as "callback/event handlers".
* `services` - These are all services/managers that are typically used by the operations. These are completely independent of skipper API, mostly business logic of your particular use-case.
* `resources` - These are the web resources used by dropwizard (REST endpoints, views, etc).

## How to run the demo

### Option 1: Using Docker

```bash
docker-compose -f docker-compose.yml up
```

### Option 2: Building locally

You'll need to make sure you have a MySQL instance running locally, and you'll need to update connection details on `DemoModule.java`.

```bash
mvn install
mvn exec:java -Dexec.mainClass="skipper_demo.DemoApp" -Dexec.args="server"
```

This will start a server on port 8080. You can then access the demo at http://localhost:8080.

## Creating a new workflow instance

```bash
curl --location --request POST 'localhost:8080/transfers?from=system&to=paola&amount=1'
```

Copy the `id` from the JSON response. Then navigate to http://localhost:8080/admin
In the top right corner, paste the workflow ID you copied earlier and click `Search`.

## Create a workflow instance that would require manual approval

If you look at `TransferWorkflow.java`, you'll see that all transfers > 1000 will trigger an `ApprovalWorkflow` which
in turn will require manual approval. Let's try that:

```bash
curl --location --request POST 'localhost:8080/transfers?from=system&to=paola&amount=1000'
```

Now copy the `id` from the response and navigate to the admin page for this workflow instance (same as prev step).

This time you'll notice down in the `OperationExecutionHistory` section, there is an entry for `skipper_demo.workflows.ApprovalWorkflow:getApproval` which
is in "pending" state. This is our `ApprovalWorkflow` sub-workflow! Click in the "View workflow" link next to it.

Now, copy the workflow ID from the `ApprovalWorkflow` (this is the sub-workflow workflow instance ID, not the parent one).

Finally, approve the transfer by running (replace with sub-workflow instance ID):

```bash
curl --location --request POST 'localhost:8080/transfers/<sub-workflow-instance-ID>/set-approval?isApproved=true'
```

## Modeling traditional finite state machines

If your use-case involves modeling a more traditional state machine that would typically be modeled as a DAG, take a look at
[VendingMachine.java](src/main/java/skipper_demo/workflows/VendingMachine.java) for an example.