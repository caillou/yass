package ch.softappeal.yass.core.remote.session.test;

import ch.softappeal.yass.core.remote.ContractId;
import ch.softappeal.yass.core.remote.MethodMapper;
import ch.softappeal.yass.core.remote.Server;
import ch.softappeal.yass.core.remote.TaggedMethodMapper;
import ch.softappeal.yass.core.remote.session.Connection;
import ch.softappeal.yass.core.remote.session.LocalConnection;
import ch.softappeal.yass.core.remote.session.Session;
import ch.softappeal.yass.core.remote.session.SessionFactory;
import ch.softappeal.yass.core.remote.session.SessionSetup;
import ch.softappeal.yass.core.test.InvokeTest;
import ch.softappeal.yass.util.NamedThreadFactory;
import ch.softappeal.yass.util.Nullable;
import ch.softappeal.yass.util.PerformanceTask;
import ch.softappeal.yass.util.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PerformanceTest extends InvokeTest {

  private static final int COUNTER = 100;

  public static final MethodMapper.Factory METHOD_MAPPER_FACTORY = TaggedMethodMapper.FACTORY;

  public static final ContractId<TestService> CONTRACT_ID = ContractId.create(TestService.class, 0);

  public static SessionSetup createSetup(final Executor requestExecutor, @Nullable final CountDownLatch latch, final int samples) {
    return new SessionSetup(
      new Server(METHOD_MAPPER_FACTORY, CONTRACT_ID.service(new TestServiceImpl())),
      requestExecutor,
      new SessionFactory() {
        @Override public Session create(final SessionSetup setup, final Connection connection) {
          return new Session(setup, connection) {
            @Override public void opened() throws Exception {
              if (latch == null) {
                return;
              }
              try (Session clientSession = this) {
                final TestService testService = CONTRACT_ID.invoker(clientSession).proxy();
                System.out.println("*** rpc");
                new PerformanceTask() {
                  @Override protected void run(final int count) throws DivisionByZeroException {
                    int counter = count;
                    while (counter-- > 0) {
                      Assert.assertTrue(testService.divide(12, 4) == 3);
                    }
                  }
                }.run(samples, TimeUnit.MICROSECONDS);
                System.out.println("*** oneway");
                new PerformanceTask() {
                  @Override protected void run(final int count) {
                    int counter = count;
                    while (counter-- > 0) {
                      testService.oneWay(-1);
                    }
                  }
                }.run(samples, TimeUnit.MICROSECONDS);

              }
              latch.countDown();
            }
            @Override public void closed(@Nullable final Throwable throwable) {
              // empty
            }
          };
        }
      }
    );
  }

  @Test public void test() throws InterruptedException {
    final ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory("executor", TestUtils.TERMINATE));
    try {
      final CountDownLatch latch = new CountDownLatch(1);
      LocalConnection.connect(createSetup(executor, latch, COUNTER), createSetup(executor, null, COUNTER));
      latch.await();
    } finally {
      executor.shutdownNow();
    }
  }

}
