package io.foldright.cffu;

import io.foldright.cffu.tuple.Tuple2;
import io.foldright.cffu.tuple.Tuple3;
import io.foldright.cffu.tuple.Tuple4;
import io.foldright.cffu.tuple.Tuple5;
import io.foldright.test_utils.TestThreadPoolManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.foldright.cffu.CompletableFutureUtils.*;
import static io.foldright.test_utils.TestUtils.*;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.junit.jupiter.api.Assertions.*;


class CompletableFutureUtilsTest {
    ////////////////////////////////////////////////////////////////////////////////
    //# allOf* methods
    ////////////////////////////////////////////////////////////////////////////////

    @Test
    void test_allOf__success__trivial_case() throws Exception {
        assertEquals(Arrays.asList(n, n + 1, n + 2), allResultsOf(
                completedFuture(n),
                completedFuture(n + 1),
                completedFuture(n + 2)
        ).get());

        assertEquals(Arrays.asList(n, n + 1), allResultsOf(completedFuture(n), completedFuture(n + 1)
        ).get());

        assertEquals(Collections.singletonList(n), allResultsOf(completedFuture(n)).get());

        assertEquals(Collections.emptyList(), allResultsOf().get());

        ////////////////////////////////////////////////////////////////////////////////

        assertEquals(Arrays.asList(n, n + 1, n + 2), allResultsOfFastFail(
                completedFuture(n),
                completedFuture(n + 1),
                completedFuture(n + 2)
        ).get());

        assertEquals(Arrays.asList(n, n + 1), allResultsOfFastFail(
                completedFuture(n),
                completedFuture(n + 1)
        ).get());

        assertEquals(Collections.singletonList(n), allResultsOfFastFail(completedFuture(n)).get());

        assertEquals(Collections.emptyList(), allResultsOfFastFail().get());

        ////////////////////////////////////////////////////////////////////////////////

        assertNull(allOfFastFail(completedFuture(n), completedFuture(n + 1), completedFuture(n + 2)).get());

        assertNull(allOfFastFail(completedFuture(n), completedFuture(n + 1)).get());

        assertNull(allOfFastFail(completedFuture(n)).get());

        assertNull(allOfFastFail().get());
    }

    @Test
    void test_allOf__exceptionally() throws Exception {
        // all failed
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            allResultsOf(
                    failedFuture(rte),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first given cf argument win
            //   ❗dependent on the implementation behavior of `CF.allOf`️
            assertSame(rte, expected.getCause());
        }

        // all failed - concurrent
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            allResultsOf(
                    CompletableFuture.supplyAsync(() -> {
                        sleep(100);
                        throw rte;
                    }),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first given cf argument win
            //   ❗dependent on the implementation behavior of `CF.allOf`️
            assertSame(rte, expected.getCause());
        }

        // success and failed
        try {
            allResultsOf(
                    completedFuture(n),
                    failedFuture(rte),
                    completedFuture(s),
                    failedFuture(anotherRte)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        // failed/incomplete/failed
        try {
            allResultsOf(
                    completedFuture(n),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(30, TimeUnit.MILLISECONDS);

            fail();
        } catch (TimeoutException expected) {
            // do nothing
        }

        // incomplete fail incomplete
        try {
            allResultsOf(
                    createIncompleteFuture(),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(100, TimeUnit.MILLISECONDS);

            fail();
        } catch (TimeoutException expected) {
            // do nothing
        }

        ////////////////////////////////////////////////////////////////////////////////

        // all failed
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            allResultsOfFastFail(
                    failedFuture(rte),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first complete(in time) cf argument win
            //   ❗dependent on the implementation behavior of `CF.anyOf`️
            assertSame(rte, expected.getCause());
        }

        // all failed - concurrent
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            allResultsOfFastFail(
                    CompletableFuture.supplyAsync(() -> {
                        sleep(100);
                        throw rte;
                    }),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first complete(in time) cf argument win
            //   ❗dependent on the implementation behavior of `CF.anyOf`️
            assertSame(anotherRte, expected.getCause());
        }

        // success and failed
        try {
            allResultsOfFastFail(
                    completedFuture(n),
                    failedFuture(rte),
                    completedFuture(s),
                    failedFuture(anotherRte)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        // failed/incomplete/failed
        try {
            allResultsOfFastFail(
                    completedFuture(n),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(30, TimeUnit.MILLISECONDS);

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        // incomplete fail incomplete
        try {
            allResultsOfFastFail(
                    createIncompleteFuture(),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(100, TimeUnit.MILLISECONDS);

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        ////////////////////////////////////////////////////////////////////////////////

        // all failed
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            allResultsOfFastFail(
                    failedFuture(rte),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first complete(in time) cf argument win
            //   ❗dependent on the implementation behavior of `CF.anyOf`️
            assertSame(rte, expected.getCause());
        }

        // all failed - concurrent
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            allOfFastFail(
                    CompletableFuture.supplyAsync(() -> {
                        sleep(100);
                        throw rte;
                    }),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first complete(in time) cf argument win
            //   ❗dependent on the implementation behavior of `CF.anyOf`️
            assertSame(anotherRte, expected.getCause());
        }

        // success and failed
        try {
            allOfFastFail(
                    completedFuture(n),
                    failedFuture(rte),
                    completedFuture(s),
                    failedFuture(anotherRte)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        // failed/incomplete/failed
        try {
            allOfFastFail(
                    completedFuture(n),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(30, TimeUnit.MILLISECONDS);

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        // incomplete fail incomplete
        try {
            allOfFastFail(
                    createIncompleteFuture(),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(100, TimeUnit.MILLISECONDS);

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    //# anyOf* methods
    ////////////////////////////////////////////////////////////////////////////////

    @Test
    void test_anyOf__success__trivial_case() throws Exception {
        assertEquals(n, anyOf(completedFuture(n), completedFuture(n + 1), completedFuture(n + 2)).get());
        assertEquals(n, anyOf(completedFuture(n), completedFuture(n + 1)).get());

        assertEquals(n, anyOf(completedFuture(n)).get());
        assertFalse(anyOf().isDone());

        // success with incomplete CF
        assertEquals(n, anyOf(createIncompleteFuture(), createIncompleteFuture(), completedFuture(n)).get());

        ////////////////////////////////////////

        assertEquals(n, anyOfSuccess(completedFuture(n), completedFuture(n + 1), completedFuture(n + 2)).get());
        assertEquals(n, anyOfSuccess(completedFuture(n), completedFuture(n + 1)).get());

        assertEquals(n, anyOfSuccess(completedFuture(n)).get());
        try {
            anyOfSuccess().get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(NoCfsProvidedException.class, expected.getCause().getClass());
        }

        // success with incomplete CF
        assertEquals(n, anyOfSuccess(createIncompleteFuture(), createIncompleteFuture(), completedFuture(n)).get());
    }

    @Test
    void test_anyOf__exceptionally() throws Exception {
        // all failed
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            anyOf(
                    CompletableFuture.supplyAsync(() -> {
                        sleep(100);
                        throw rte;
                    }),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first complete(in time) cf argument win
            //   ❗dependent on the implementation behavior of `CF.anyOf`️
            assertSame(anotherRte, expected.getCause());
        }
        // incomplete fail incomplete
        try {
            anyOf(createIncompleteFuture(), failedFuture(rte), createIncompleteFuture()).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        ////////////////////////////////////////

        // all failed
        try {
            RuntimeException ex1 = new RuntimeException("ex1");
            RuntimeException ex2 = new RuntimeException("ex2");
            anyOfSuccess(
                    CompletableFuture.supplyAsync(() -> {
                        sleep(100);
                        throw rte;
                    }),
                    failedFuture(anotherRte),
                    failedFuture(ex1),
                    failedFuture(ex2)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            // anyOfSuccess: the ex of first given cf argument win
            //   ❗dependent on the implementation behavior of `CF.allOf`️
            assertSame(rte, expected.getCause());
        }
        // incomplete fail incomplete
        try {
            anyOfSuccess(
                    createIncompleteFuture(),
                    failedFuture(rte),
                    createIncompleteFuture()
            ).get(30, TimeUnit.MILLISECONDS);

            fail();
        } catch (TimeoutException expected) {
            // do nothing
        }
    }

    @Test
    void test_anyOf__concurrent() throws Exception {
        // incomplete/wait-success then success
        assertEquals(n, anyOf(
                createIncompleteFuture(),
                createIncompleteFuture(),
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return anotherN;
                }),
                completedFuture(n)
        ).get());

        // wait/success then success
        assertEquals(n, anyOf(
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return anotherN;
                }),
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return anotherN;
                }),
                completedFuture(n)
        ).get());

        // success then failed
        assertEquals(n, anyOf(
                createIncompleteFuture(),
                createIncompleteFuture(),
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    throw rte;
                }),
                completedFuture(n)
        ).get());

        // failed then success
        try {
            anyOf(
                    CompletableFuture.supplyAsync(() -> {
                        sleep(100);
                        return n;
                    }),
                    failedFuture(rte),
                    failedFuture(rte)
            ).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        ////////////////////////////////////////

        // incomplete/wait-success then success
        assertEquals(n, anyOfSuccess(
                createIncompleteFuture(),
                createIncompleteFuture(),
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return anotherN;
                }),
                completedFuture(n)
        ).get());

        // wait/success then success
        assertEquals(n, anyOfSuccess(
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return anotherN;
                }),
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    return anotherN;
                }),
                completedFuture(n)
        ).get());

        // success then failed
        assertEquals(n, anyOfSuccess(
                createIncompleteFuture(),
                createIncompleteFuture(),
                CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    throw rte;
                }),
                completedFuture(n)
        ).get());

        // failed then success
        assertEquals(n, anyOfSuccess(
                CompletableFuture.supplyAsync(() -> {
                    sleep(100);
                    return n;
                }),
                failedFuture(rte),
                failedFuture(rte)
        ).get());
    }

    ////////////////////////////////////////////////////////////////////////////////
    //# both methods
    ////////////////////////////////////////////////////////////////////////////////

    @Test
    void test_both() throws Exception {
        final CompletableFuture<Integer> cf_n = completedFuture(n);
        final CompletableFuture<Integer> cf_nn = completedFuture(n + n);

        final Runnable runnable = () -> {
        };
        assertNull(runAfterBothFastFail(cf_n, cf_nn, runnable).get());
        assertNull(runAfterBothFastFailAsync(cf_n, cf_nn, runnable).get());
        assertNull(runAfterBothFastFailAsync(cf_n, cf_nn, runnable, executorService).get());

        BiConsumer<Integer, Integer> bc = (i1, i2) -> {
        };
        assertNull(thenAcceptBothFastFail(cf_n, cf_nn, bc).get());
        assertNull(thenAcceptBothFastFailAsync(cf_n, cf_nn, bc).get());
        assertNull(thenAcceptBothFastFailAsync(cf_n, cf_nn, bc, executorService).get());

        assertEquals(3 * n, thenCombineFastFail(cf_n, cf_nn, Integer::sum).get());
        assertEquals(3 * n, thenCombineFastFailAsync(cf_n, cf_nn, Integer::sum).get());
        assertEquals(3 * n, thenCombineFastFailAsync(cf_n, cf_nn, Integer::sum, executorService).get());
    }

    @Test
    void both_fastFail() throws Exception {
        CompletableFuture<Integer> cf_n = CompletableFuture.supplyAsync(() -> {
            sleep(2_000);
            return n;
        });
        final CompletableFuture<Integer> failed = failedFuture(rte);

        final Runnable runnable = () -> {
        };
        try {
            runAfterBothFastFail(cf_n, failed, runnable).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            runAfterBothFastFailAsync(cf_n, failed, runnable).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            runAfterBothFastFailAsync(cf_n, failed, runnable, executorService).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        BiConsumer<Integer, Integer> bc = (i1, i2) -> {
        };
        try {
            thenAcceptBothFastFail(cf_n, failed, bc).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            thenAcceptBothFastFailAsync(cf_n, failed, bc).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            thenAcceptBothFastFailAsync(cf_n, failed, bc, executorService).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        try {
            thenCombineFastFail(cf_n, failed, Integer::sum).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            thenCombineFastFailAsync(cf_n, failed, Integer::sum).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            thenCombineFastFailAsync(cf_n, failed, Integer::sum, executorService).get(1, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    //# combine methods
    ////////////////////////////////////////////////////////////////////////////////

    @Test
    void test_combine() throws Exception {
        final CompletableFuture<Integer> cf_n = completedFuture(n);
        final CompletableFuture<String> cf_s = completedFuture(s);
        final CompletableFuture<Double> cf_d = completedFuture(d);
        final CompletableFuture<Integer> cf_an = completedFuture(anotherN);
        final CompletableFuture<Integer> cf_nn = completedFuture(n + n);

        assertEquals(Tuple2.of(n, s), combine(cf_n, cf_s).get());
        assertEquals(Tuple2.of(n, s), combineFastFail(cf_n, cf_s).get());

        assertEquals(Tuple3.of(n, s, d), combine(cf_n, cf_s, cf_d).get());
        assertEquals(Tuple3.of(n, s, d), combineFastFail(cf_n, cf_s, cf_d).get());

        assertEquals(Tuple4.of(n, s, d, anotherN), combine(cf_n, cf_s, cf_d, cf_an).get());
        assertEquals(Tuple4.of(n, s, d, anotherN), combineFastFail(cf_n, cf_s, cf_d, cf_an).get());

        assertEquals(Tuple5.of(n, s, d, anotherN, n + n), combine(cf_n, cf_s, cf_d, cf_an, cf_nn).get());
        assertEquals(Tuple5.of(n, s, d, anotherN, n + n), combineFastFail(cf_n, cf_s, cf_d, cf_an, cf_nn).get());
    }

    @Test
    void test_combine_exceptionally() throws Exception {
        final CompletableFuture<Object> incomplete = new CompletableFuture<>();
        final CompletableFuture<Object> fail = failedFuture(rte);

        final CompletableFuture<Integer> cf_n = completedFuture(n);
        final CompletableFuture<String> cf_s = completedFuture(s);
        final CompletableFuture<Double> cf_d = completedFuture(d);
        final CompletableFuture<Integer> cf_an = completedFuture(anotherN);

        try {
            combine(cf_n, fail).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            combineFastFail(incomplete, fail).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        try {
            combine(cf_n, fail, cf_s).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            combineFastFail(incomplete, fail, cf_s).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        try {
            combine(cf_n, fail, cf_d, cf_s).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            combineFastFail(incomplete, fail, cf_d, cf_s).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        try {
            combine(cf_n, cf_d, fail, cf_s, cf_an).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            combineFastFail(incomplete, cf_d, fail, cf_s, cf_an).get();

            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }
    }

    @Test
    void test_combine_NotFastFail() throws Exception {
        final CompletableFuture<Object> incomplete = new CompletableFuture<>();
        final CompletableFuture<Object> fail = failedFuture(rte);

        final CompletableFuture<String> cf_s = completedFuture(s);
        final CompletableFuture<Double> cf_d = completedFuture(d);
        final CompletableFuture<Integer> cf_an = completedFuture(anotherN);

        try {
            combine(incomplete, fail).get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException expected) {
        }
        try {
            combine(incomplete, fail, cf_s).get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException expected) {
        }
        try {
            combine(incomplete, fail, cf_d, cf_s).get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException expected) {
        }
        try {
            combine(incomplete, cf_d, fail, cf_s, cf_an).get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException expected) {
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    //# either methods
    ////////////////////////////////////////////////////////////////////////////////

    @Test
    void test_either() throws Exception {
        final CompletableFuture<Integer> cf_n = completedFuture(n);
        CompletableFuture<Integer> incomplete = createIncompleteFuture();

        final Runnable runnable = () -> {
        };
        assertNull(runAfterEitherSuccess(cf_n, incomplete, runnable).get());
        assertNull(runAfterEitherSuccessAsync(cf_n, incomplete, runnable).get());
        assertNull(runAfterEitherSuccessAsync(cf_n, incomplete, runnable, executorService).get());

        Consumer<Integer> c = i -> {
        };
        assertNull(acceptEitherSuccess(cf_n, incomplete, c).get());
        assertNull(acceptEitherSuccessAsync(cf_n, incomplete, c).get());
        assertNull(acceptEitherSuccessAsync(cf_n, incomplete, c, executorService).get());

        assertEquals(n, applyToEitherSuccess(cf_n, incomplete, Function.identity()).get());
        assertEquals(n, applyToEitherSuccessAsync(cf_n, incomplete, Function.identity()).get());
        assertEquals(n, applyToEitherSuccessAsync(cf_n, incomplete, Function.identity(), executorService).get());
    }

    @Test
    void test_either_success() throws Exception {
        final CompletableFuture<Integer> failed = failedFuture(rte);
        CompletableFuture<Integer> cf = completedFuture(n);

        final Runnable runnable = () -> {
        };
        assertNull(runAfterEitherSuccess(failed, cf, runnable).get());
        assertNull(runAfterEitherSuccessAsync(failed, cf, runnable).get());
        assertNull(runAfterEitherSuccessAsync(failed, cf, runnable, executorService).get());

        Consumer<Integer> c = i -> {
        };
        assertNull(acceptEitherSuccess(failed, cf, c).get());
        assertNull(acceptEitherSuccessAsync(failed, cf, c).get());
        assertNull(acceptEitherSuccessAsync(failed, cf, c, executorService).get());

        assertEquals(n, applyToEitherSuccess(failed, cf, Function.identity()).get());
        assertEquals(n, applyToEitherSuccessAsync(failed, cf, Function.identity()).get());
        assertEquals(n, applyToEitherSuccessAsync(failed, cf, Function.identity(), executorService).get());
    }

    ////////////////////////////////////////////////////////////////////////////////
    //# Backport CF methods
    //  compatibility for low Java version
    ////////////////////////////////////////////////////////////////////////////////

    //# Factory methods

    @Test
    void test_failedFuture() throws Exception {
        assertTrue(failedFuture(rte).isDone());
        assertEquals(n, completedStage(n).toCompletableFuture().get());
        assertTrue(failedStage(rte).toCompletableFuture().isDone());
    }

    @Test
    void test_delayedExecutor() throws Exception {
        final AtomicReference<String> holder = new AtomicReference<>();

        Executor delayer = delayedExecutor(1, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> holder.set(testName), delayer).get();
        assertEquals(testName, holder.get());
    }

    @Test
    void test_exceptionallyAsync() throws Exception {
        CompletableFuture<Integer> cf = failedFuture(rte);
        assertEquals(n, exceptionallyAsync(cf, ex -> n).get());

        cf = completedFuture(n);
        assertEquals(n, exceptionallyAsync(cf, ex -> anotherN).get());

    }

    @Test
    void test_timeout() throws Exception {
        CompletableFuture<Integer> cf = createIncompleteFuture();
        try {
            orTimeout(cf, 1, TimeUnit.MILLISECONDS).get();
        } catch (ExecutionException expected) {
            assertEquals(TimeoutException.class, expected.getCause().getClass());
        }

        cf = createIncompleteFuture();
        assertEquals(n, completeOnTimeout(cf, n, 1, TimeUnit.MILLISECONDS).get());

        cf = completedFuture(n);
        assertEquals(n, completeOnTimeout(cf, anotherN, 1, TimeUnit.MILLISECONDS).get());

        cf = completedFuture(n);
        assertEquals(n, orTimeout(cf, 1, TimeUnit.MILLISECONDS).get());
    }

    @Test
    void test_exceptionallyCompose() throws Exception {
        CompletableFuture<Object> completed = completedFuture(n);
        CompletableFuture<Object> failed = failedFuture(rte);

        assertEquals(n, exceptionallyCompose(failed, ex -> completedFuture(n)).get());
        assertEquals(n, exceptionallyCompose(completed, ex -> completedFuture(anotherN)).get());

        assertEquals(n, exceptionallyComposeAsync(failed, ex -> completedFuture(n)).get());
        assertEquals(n, exceptionallyComposeAsync(completed, ex -> completedFuture(anotherN)).get());
    }

    @Test
    @SuppressWarnings({"ResultOfMethodCallIgnored", "ThrowableNotThrown"})
    void test_read() {
        final CompletableFuture<Integer> completed = completedFuture(n);

        assertEquals(n, join(completed, 1, TimeUnit.MILLISECONDS));
        assertEquals(n, resultNow(completed));
        try {
            exceptionNow(completed);
            fail();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() != null) assertEquals("Task completed with a result", expected.getMessage());
        }
        assertSame(CffuState.SUCCESS, state(completed));

        ////////////////////////////////////////

        final CompletableFuture<Object> failed = failedFuture(rte);

        try {
            join(failed, 1, TimeUnit.MILLISECONDS);
            fail();
        } catch (CompletionException expected) {
            assertSame(rte, expected.getCause());
        }
        try {
            resultNow(failed);
            fail();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() != null) assertEquals("Task completed with exception", expected.getMessage());
        }
        assertSame(rte, exceptionNow(failed));
        assertSame(CffuState.FAILED, state(failed));

        ////////////////////////////////////////

        CompletableFuture<Object> cancelled = createCancelledFuture();
        try {
            resultNow(cancelled);
            fail();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() != null) assertEquals("Task was cancelled", expected.getMessage());
        }
        try {
            exceptionNow(cancelled);
            fail();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() != null) assertEquals("Task was cancelled", expected.getMessage());
        }
        assertSame(CffuState.CANCELLED, state(cancelled));

        ////////////////////////////////////////

        final CompletableFuture<Object> incomplete = createIncompleteFuture();

        try {
            join(incomplete, 1, TimeUnit.MILLISECONDS);
            fail();
        } catch (CompletionException expected) {
            assertInstanceOf(TimeoutException.class, expected.getCause());
        }
        try {
            resultNow(incomplete);
            fail();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() != null) assertEquals("Task has not completed", expected.getMessage());
        }
        try {
            exceptionNow(incomplete);
            fail();
        } catch (IllegalStateException expected) {
            if (expected.getMessage() != null) assertEquals("Task has not completed", expected.getMessage());
        }
        assertSame(CffuState.RUNNING, state(incomplete));

        // Incomplete Future -> join before timeout
        CompletableFuture<Integer> later = createFutureCompleteLater(n);
        assertEquals(n, join(later, 3, TimeUnit.SECONDS));
    }

    @Test
    void test_write() throws Exception {
        assertEquals(n, completeAsync(createIncompleteFuture(), () -> n).get());
        assertEquals(n, completeAsync(createIncompleteFuture(), () -> n, commonPool()).get());
        try {
            completeAsync(createIncompleteFuture(), () -> {
                throw rte;
            }).get();
            fail();
        } catch (ExecutionException expected) {
            assertSame(rte, expected.getCause());
        }

        CompletableFuture<Integer> completed = completedFuture(n);
        assertEquals(n, completeAsync(completed, () -> anotherN).get());
    }

    @Test
    void test_re_config() throws Exception {
        CompletionStage<Integer> mf = minimalCompletionStage(completedFuture(n));
        assertEquals(n, mf.toCompletableFuture().get());

        CompletableFuture<Integer> cf = createIncompleteFuture();
        copy(cf).complete(n);
        assertFalse(cf.isDone());

        CompletableFuture<Object> incomplete = newIncompleteFuture(cf);
        assertFalse(incomplete.isDone());
        incomplete.complete(n);
        assertFalse(cf.isDone());
    }

    @Test
    void test_executor() {
        Executor executor = defaultExecutor();
        assertIsDefaultExecutor(executor);

        assertIsDefaultExecutor(screenExecutor(commonPool()));

        ExecutorService e = Executors.newCachedThreadPool();
        assertSame(e, screenExecutor(e));
    }

    ////////////////////////////////////////////////////////////////////////////////
    //# test helper fields
    ////////////////////////////////////////////////////////////////////////////////

    private static void assertIsDefaultExecutor(Executor executor) {
        final boolean USE_COMMON_POOL = ForkJoinPool.getCommonPoolParallelism() > 1;
        if (USE_COMMON_POOL) {
            assertSame(commonPool(), executor);
        } else {
            String executorClassName = executor.getClass().getName();
            assertTrue(executorClassName.endsWith("$ThreadPerTaskExecutor"));
        }
    }

    private static final String testName = "CompletableFutureUtilsTest";
    private static ExecutorService executorService;

    @BeforeAll
    static void beforeAll() {
        executorService = TestThreadPoolManager.createThreadPool("CffuTest");
    }

    @AfterAll
    static void afterAll() {
        TestThreadPoolManager.shutdownExecutorService(executorService);
    }
}
