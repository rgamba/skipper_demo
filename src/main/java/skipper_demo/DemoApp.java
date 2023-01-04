package skipper_demo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import io.github.rgamba.skipper.DependencyRegistry;
import io.github.rgamba.skipper.SkipperEngine;
import io.github.rgamba.skipper.TimerProcessor;
import io.github.rgamba.skipper.admin.AdminResource;
import io.github.rgamba.skipper.client.SkipperClient;
import io.github.rgamba.skipper.module.SkipperEngineFactory;
import io.github.rgamba.skipper.module.TimerProcessorFactory;
import io.github.rgamba.skipper.store.mysql.MySqlMigrationsManager;
import lombok.val;
import skipper_demo.operations.Operations;
import skipper_demo.operations.VendingMachineOperations;
import skipper_demo.resources.TransfersResource;
import skipper_demo.resources.VendingMachineResource;
import skipper_demo.services.Ledger;
import skipper_demo.workflowHandlers.TransferCallbackHandler;
import skipper_demo.workflows.ApprovalWorkflow;
import skipper_demo.workflows.TransferWorkflow;
import skipper_demo.workflows.VendingMachine;

public class DemoApp extends Application<DemoAppConfiguration> {
    public static void main(String[] args) throws Exception {
        new DemoApp().run(args);
    }

    @Override
    public void run(DemoAppConfiguration appConfig, Environment environment) throws Exception {
        Injector injector = Guice.createInjector(new DemoModule());

        MySqlMigrationsManager migrationMgr = injector.getInstance(MySqlMigrationsManager.class);
        migrationMgr.migrate();

        val registry =
                DependencyRegistry.builder()
                        .addWorkflowFactory(() -> injector.getInstance(TransferWorkflow.class))
                        .addWorkflowFactory(() -> injector.getInstance(ApprovalWorkflow.class))
                        .addWorkflowFactory(() -> injector.getInstance(VendingMachine.class))
                        .addOperation(injector.getInstance(Operations.class))
                        .addOperation(injector.getInstance(VendingMachineOperations.class))
                        .addCallbackHandler(new TransferCallbackHandler())
                        .build();
        SkipperEngine engine = injector.getInstance(SkipperEngineFactory.class).create(registry);
        TimerProcessor processor = injector.getInstance(TimerProcessorFactory.class).create(engine);
        processor.start();

        val transfersResource = new TransfersResource(new SkipperClient(engine), injector.getInstance(Ledger.class));
        val vendingMachineResource = new VendingMachineResource(new SkipperClient(engine));
        environment.jersey().register(transfersResource);
        environment.jersey().register(vendingMachineResource);
        environment.jersey().register(new AdminResource(engine));
    }

    @Override
    public void initialize(Bootstrap<DemoAppConfiguration> bootstrap) {
        bootstrap.addBundle(new ViewBundle<DemoAppConfiguration>());
    }
}
