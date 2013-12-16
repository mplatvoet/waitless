package nl.mplatvoet.waitless;

import nl.mplatvoet.waitless.collections.ConcurrentCollection;
import nl.mplatvoet.waitless.misc.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static final int LENGTH = 100000;

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 3; ++i) {
            runTests();
        }
    }

    public static void runTests() throws InterruptedException {
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

//      Yes we know this is slow
//        log.info("run test with Collections.synchronizedList(LinkedList())");
//        ints = Collections.synchronizedList(new LinkedList<Integer>());
//        runTest(ints);
//        ints = Collections.synchronizedList(new LinkedList<Integer>());
//        runTest(ints);
//        ints = Collections.synchronizedList(new LinkedList<Integer>());
//        runTest(ints);

        log.info("run test with new ConcurrentLinkedQueue()");
        ints = new ConcurrentLinkedQueue<Integer>();
        runTest(ints);
        ints = new ConcurrentLinkedQueue<Integer>();
        runTest(ints);
        ints = new ConcurrentLinkedQueue<Integer>();
        runTest(ints);
    }

    private static void runTest(Collection<Integer> ints) throws InterruptedException {
        Stopwatch stopwatch = new Stopwatch(log);

        Thread t1 = new IntFiller(LENGTH, ints);
        Thread t2 = new IntFiller(LENGTH, ints);
        Thread t3 = new IntFiller(LENGTH, ints);
        Thread t4 = new IntFiller(LENGTH, ints);
        Thread c1 = new IntClearer(LENGTH, ints);
        Thread c2 = new IntClearer(LENGTH, ints);

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        c1.start();
        c2.start();
        t3.start();
        t4.start();

        c1.join();
        c2.join();
        t3.join();
        t4.join();

        stopwatch.elapsed("result #{}", ints.size());
    }


    public static class IntFiller extends Thread {

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

    public static class IntClearer extends Thread {

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
