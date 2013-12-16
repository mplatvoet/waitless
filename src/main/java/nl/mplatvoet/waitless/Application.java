package nl.mplatvoet.waitless;

import nl.mplatvoet.waitless.collections.ConcurrentCollection;
import nl.mplatvoet.waitless.misc.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.*;

public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static final ThreadPoolExecutor service = new ThreadPoolExecutor(4, 4, 1, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>(50));

    public static final int LENGTH = 1000000;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 3; ++i) {
            runTests();
        }

        service.shutdown();
        service.awaitTermination(1, TimeUnit.DAYS);
    }

    public static void runTests() throws Exception {
        log.info("run test with ConcurrentCollection");
        Collection<Integer> ints = new ConcurrentCollection<Integer>();
        runTest(ints);
        ints = new ConcurrentCollection<Integer>();
        runTest(ints);
        ints = new ConcurrentCollection<Integer>();
        runTest(ints);

        log.info("run test with ConcurrentLinkedDeque");
        ints = new ConcurrentLinkedDeque<Integer>();
        runTest(ints);
        ints = new ConcurrentLinkedDeque<Integer>();
        runTest(ints);
        ints = new ConcurrentLinkedDeque<Integer>();
        runTest(ints);

        log.info("run test with new ConcurrentLinkedQueue()");
        ints = new ConcurrentLinkedQueue<Integer>();
        runTest(ints);
        ints = new ConcurrentLinkedQueue<Integer>();
        runTest(ints);
        ints = new ConcurrentLinkedQueue<Integer>();
        runTest(ints);
    }

    private static void runTest(Collection<Integer> ints) throws Exception {

        Runnable filler = new IntFiller(LENGTH, ints);
        Runnable clearer = new IntClearer(LENGTH, ints);

        Stopwatch stopwatch = new Stopwatch(log);
        Future<?> f1 = service.submit(filler);
        Future<?> f2 = service.submit(filler);
        f1.get();
        f2.get();

        Future<?> f3 = service.submit(filler);
        Future<?> f4 = service.submit(filler);
        Future<?> c1 = service.submit(clearer);
        Future<?> c2 = service.submit(clearer);
        f3.get();
        f4.get();
        c1.get();
        c2.get();

        stopwatch.elapsed("result #{}", ints.size());
    }


    public static class IntFiller implements Runnable {

        private final int length;
        private final Collection<Integer> col;

        public IntFiller(int length, Collection<Integer> col) {
            this.length = length;
            this.col = col;
        }

        @Override
        public void run() {
            for (int i = 0; i < length; i++) {
                col.add(i);
            }
        }
    }

    public static class IntClearer implements Runnable {

        private final int length;
        private final Collection<Integer> col;

        public IntClearer(int length, Collection<Integer> col) {
            this.length = length;
            this.col = col;
        }

        @Override
        public void run() {
            for (int i = 0; i < length; i++) {
                col.remove(i);
            }
        }
    }

}
