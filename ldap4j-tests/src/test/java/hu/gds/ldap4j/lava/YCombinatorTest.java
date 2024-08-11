package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YCombinatorTest {
    private int factorial(@NotNull ContextHolder contextHolder, int value) throws Throwable {
        return contextHolder.getOrTimeoutDelayNanos(AbstractTest.TIMEOUT_NANOS, factorial(value));
    }

    private @NotNull Lava<@NotNull Integer> factorial(int value) {
        return Lava.supplier(()->Lava.<@NotNull Integer>yCombinator(
                        (recursion)->(value2)->(1>=value2)
                                ?Lava.complete(1)
                                :recursion.apply(value2-1)
                                .compose((result)->Lava.complete(result*value2)))
                .apply(value));
    }

    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    @ParameterizedTest
    public void test(@NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        try (ContextHolder contextHolder=contextHolderFactory.apply(Log.systemErr())) {
            contextHolder.start();
            assertEquals(1, factorial(contextHolder, 0));
            assertEquals(1, factorial(contextHolder, 1));
            assertEquals(2, factorial(contextHolder, 2));
            assertEquals(6, factorial(contextHolder, 3));
            assertEquals(24, factorial(contextHolder, 4));
            assertEquals(120, factorial(contextHolder, 5));
            assertEquals(720, factorial(contextHolder, 6));
        }
    }
}
