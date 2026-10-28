package br.com.felipeblaft.reports.support;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prova do "na hora" (FR-047, SC-010): uma linha de execução repete uma requisição com a credencial
 * antiga enquanto a operação de revogação roda na linha principal. Toda requisição <strong>iniciada
 * depois</strong> da conclusão da operação ({@link #committed()}) que for aceita conta como
 * violação; as que estavam em curso podem terminar. A sonda só vale se houve requisições antes e
 * depois da conclusão ({@link #requestsBefore()} e {@link #requestsAfter()}).
 */
public final class ImmediacyProbe implements AutoCloseable {

    private final Callable<Boolean> acceptedRequest;
    private final AtomicBoolean committed = new AtomicBoolean();
    private final AtomicBoolean stop = new AtomicBoolean();
    private final AtomicInteger before = new AtomicInteger();
    private final AtomicInteger after = new AtomicInteger();
    private final AtomicInteger acceptedBefore = new AtomicInteger();
    private final AtomicInteger violations = new AtomicInteger();
    private volatile Throwable failure;
    private Thread thread;

    /**
     * @param acceptedRequest faz uma requisição e diz se a credencial antiga foi aceita
     */
    public ImmediacyProbe(Callable<Boolean> acceptedRequest) {
        this.acceptedRequest = acceptedRequest;
    }

    /** Inicia a repetição e espera a primeira rodada de requisições aceitas. */
    public ImmediacyProbe start() throws InterruptedException {
        thread = new Thread(this::loop, "immediacy-probe");
        thread.setDaemon(true);
        thread.start();
        await(() -> acceptedBefore.get() >= 3, "requisições aceitas antes da revogação");
        return this;
    }

    private void loop() {
        while (!stop.get()) {
            boolean startedAfterCommit = committed.get();
            try {
                boolean accepted = acceptedRequest.call();
                if (startedAfterCommit) {
                    after.incrementAndGet();
                    if (accepted) {
                        violations.incrementAndGet();
                    }
                } else {
                    before.incrementAndGet();
                    if (accepted) {
                        acceptedBefore.incrementAndGet();
                    }
                }
            } catch (Exception | AssertionError error) {
                failure = error;
                return;
            }
        }
    }

    /** A operação de revogação terminou: a partir daqui nada pode ser aceito. */
    public void committed() {
        committed.set(true);
    }

    /** Espera um número mínimo de requisições iniciadas depois da conclusão. */
    public void awaitRequestsAfter(int minimum) throws InterruptedException {
        await(() -> after.get() >= minimum, "requisições depois da revogação");
    }

    public int requestsBefore() {
        return before.get();
    }

    public int requestsAfter() {
        return after.get();
    }

    public int violations() {
        return violations.get();
    }

    private void await(java.util.function.BooleanSupplier condition, String what)
            throws InterruptedException {
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (failure != null) {
                throw new AssertionError("a sonda falhou: " + failure, failure);
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("tempo esgotado esperando " + what);
            }
            Thread.sleep(5);
        }
    }

    @Override
    public void close() throws InterruptedException {
        stop.set(true);
        if (thread != null) {
            thread.join(30_000);
        }
        if (failure != null) {
            throw new AssertionError("a sonda falhou: " + failure, failure);
        }
    }
}
