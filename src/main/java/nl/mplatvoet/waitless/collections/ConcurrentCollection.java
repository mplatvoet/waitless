package nl.mplatvoet.waitless.collections;

import nl.mplatvoet.waitless.support.UnsafeProvider;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ConcurrentCollection<E> implements Collection<E> {
    private static final Unsafe UNSAFE = UnsafeProvider.instance();

    private volatile Node<E> head = new Node<E>(null);
    private volatile Node<E> tail = head;


    public boolean add(@NotNull E value) {
        Node<E> newTail = new Node<E>(value);
        newTail.state = Node.State.MUTATING;
        while (true) {
            Node<E> oldTail = tail;
            if (oldTail.casState(Node.State.AVAILABLE, Node.State.MUTATING)) {
                if (oldTail.casNext(null, newTail)) {
                    tail = newTail;
                    oldTail.state = Node.State.AVAILABLE;
                    newTail.state = Node.State.AVAILABLE;
                    return true;
                }
                oldTail.state = Node.State.AVAILABLE;
            }
        }
    }


    @Override
    public int size() {
        //maybe keep size in a separated AtomicInteger
        int size = 0;
        for (Node<?> node = head; node.next != null && size < Integer.MAX_VALUE; ++size) node = node.next;
        return size;
    }

    @Override
    public boolean isEmpty() {
        return head.next == null;
    }

    @Override
    public boolean contains(Object obj) {
        return obj != null && nodeForValue(obj) != null;
    }

    private Node<E> nodeForValue(Object o) {
        for (Node<E> node = head.next; node != null; node = node.next) {
            if (node.state != Node.State.ZOMBIE && node.value.equals(o)) return node;
        }
        return null;
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return new NodeIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        if (isEmpty()) {
            return new Object[0];
        }

        ArrayList<E> copy = new ArrayList<E>();
        for (Node<E> node = head.next; node != null; node = node.next) {
            copy.add(node.value);
        }
        return copy.toArray();
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(@NotNull T[] a) {
        Object[] objects = toArray();
        if (objects.length == 0) {
            return a;
        }
        if (objects.length <= a.length) {
            System.arraycopy(objects, 0, a, 0, objects.length);
            return a;
        }
        return (T[]) objects;

    }

    @Override
    public boolean remove(@NotNull Object obj) {
        for (Node<E> node = head.next, prev = head; node != null; prev = node, node = node.next) {
            if (node.state != Node.State.ZOMBIE && node.value.equals(obj)) {
                if (prev.casState(Node.State.AVAILABLE, Node.State.MUTATING)) {
                    if (node.casState(Node.State.AVAILABLE, Node.State.ZOMBIE)) {
                        prev.next = node.next;
                        prev.state = Node.State.AVAILABLE;
                        return true;
                    } else {
                        node = head.next;
                    }
                    prev.state = Node.State.AVAILABLE;
                } else {
                    // cas failed, others are mutating in this area,
                    //start from the beginning.
                    //TODO, this is wrong, might skip a node: could safely retry from prev node as starting point
                    node = head.next;

                }
            }
        }
        return false;

    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        if (c == this) return true;
        if (c.isEmpty()) return true;
        if (isEmpty()) return false;

        for (Object o : c) {
            if (!contains(o)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        if (c == this) {
            throw new IllegalArgumentException("you pervert");
        }
        if (c.isEmpty()) return false;

        for (E e : c) {
            add(e);
        }
        return true;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        if (c == this) {
            if (isEmpty()) return false;

            clear();
            return true; //this is an assumption, but nobody really cares.
        }
        boolean modified = false;
        for (Object o : c) {
            modified |= remove(o);
        }
        return modified;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        if (c.isEmpty()) {
            boolean empty = isEmpty();
            clear();
            return !empty;
        }

        boolean modified = false;
        for (Node<E> node = head.next; node != null; node = node.next) {
            if (node.state != Node.State.ZOMBIE && !c.contains(node.value)) {
                modified |= remove(node);
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        head = new Node<E>(null);
    }

    private final class NodeIterator implements Iterator<E> {
        private Node<E> next;


        private NodeIterator() {
            next = head.next;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public E next() {
            E value = next.value;
            next = next.next; //yes, I just did that!
            return value;
        }

        @Override
        public void remove() {
            //can be very costly since we don't want to remove a value but an exact node.
            //the previous node can be removed concurrently all the time. Which means we have to
            //search for the previous node of this node.
            //therefor preferring deleting values only.
            //should support this though
            throw new UnsupportedOperationException();
        }
    }

    private static final class Node<E> {
        enum State {AVAILABLE, MUTATING, ZOMBIE}

        volatile Node<E> next = null;
        volatile State state = State.AVAILABLE;
        volatile E value;

        Node(E value) {
            this.value = value;
        }

        boolean casState(State expected, State newState) {
            return UNSAFE.compareAndSwapObject(this, stateOffset, expected, newState);
        }

        boolean casNext(Node<E> expected, Node<E> newNode) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, expected, newNode);
        }

        static final long nextOffset;
        static final long stateOffset;

        static {
            try {
                nextOffset = UNSAFE.objectFieldOffset(Node.class.getDeclaredField("next"));
                stateOffset = UNSAFE.objectFieldOffset(Node.class.getDeclaredField("state"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }
}
