package skipper_demo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.github.rgamba.skipper.OperationProxyFactory;
import io.github.rgamba.skipper.api.annotations.WorkflowOperation;
import io.github.rgamba.skipper.module.SkipperModule;
import skipper_demo.operations.Operations;
import skipper_demo.services.Ledger;

public class DemoModule extends AbstractModule {
  @Override
  protected void configure() {
    install(
        new SkipperModule(
            "jdbc:mysql://db:3306/skipper?serverTimezone=UTC", "skipper", "skipper"));
    bind(Ledger.class).toInstance(new Ledger());
  }

  @Provides
  @WorkflowOperation
  static skipper_demo.operations.Operations provideGreeterOperation() {
    return OperationProxyFactory.create(Operations.class);
  }
}