package nl.mplatvoet.waitless;

import nl.mplatvoet.waitless.collections.ConcurrentCollection;

import java.util.Collection;

public class Application {

    public static final int LENGTH = 10000000;

    public static void main(String[] args) throws InterruptedException {
        Collection<Integer> ints = new ConcurrentCollection<Integer>();
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

//        for (Integer anInt : ints) {
//            System.out.println("anInt = " + anInt);
//        }
        System.out.println("ints = " + ints.size());
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
