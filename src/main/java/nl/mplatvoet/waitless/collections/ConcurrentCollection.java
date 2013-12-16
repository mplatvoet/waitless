package nl.mplatvoet.waitless.collections;

import nl.mplatvoet.waitless.support.UnsafeProvider;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ConcurrentCollection<E> implements Collection<E> {
    private static final Unsafe UNSAFE = UnsafeProvider.instance();
    private static final long headOffset = UnsafeProvider.objectFieldOffsetFor(ConcurrentCollection.class, "head");

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
            if (oldTail.state == Node.State.ZOMBIE && oldTail.casState(Node.State.ZOMBIE, Node.State.MUTATING)) {
                //revive to MUTATING, so this is the only thread operating on the tail
                Node<E> currentTail = head;
                while(currentTail.next != null) currentTail = currentTail.next;
                tail = currentTail; //might be a ZOMBIE too, but we'll find out later
                oldTail.state = Node.State.ZOMBIE;
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
        for (Node<E> prev = head, node = prev.next; node != null; ) {
            if (node.state != Node.State.ZOMBIE && node.value.equals(obj)) {
                if (prev.casState(Node.State.AVAILABLE, Node.State.MUTATING)) {
                    if (prev.next == node) {
                        if (node.casState(Node.State.AVAILABLE, Node.State.ZOMBIE)) {
                            prev.next = node.next;
                            prev.state = Node.State.AVAILABLE;
                            return true;
                        }
                    } else {
                        //link has been broken, reset and start from the beginning
                        prev = head;
                        node = prev.next;
                    }
                    prev.state = Node.State.AVAILABLE;
                } else if (prev.state == Node.State.ZOMBIE){
                    //link has been broken, start from the head
                    prev = head;
                    node = prev.next;
                }
            } else {
                prev = node;
                node = node.next;
            }
        }
        return false;

    }

    private void removeNode(@NotNull Node<E> node, @NotNull Node<E> prev) {
        //fast attempt, happy flow
        if (doRemoveNode(node, prev)) return;

        //let's make that bugger a Zombie node
        while (node.state != Node.State.ZOMBIE && !doRemoveNode(node, prev)) {
            if (prev.next != node) {
                for (prev = head; prev != null && prev.next != node; prev = prev.next) ;
                if (prev == null) return; //link has been broken, node is no longer in collection
            }
        }
    }

    private boolean doRemoveNode(@NotNull Node<E> node, @NotNull Node<E> prev) {
        if (prev.casState(Node.State.AVAILABLE, Node.State.MUTATING)) {
            if (prev.next == node && node.casState(Node.State.AVAILABLE, Node.State.ZOMBIE)) {
                prev.next = node.next;
                prev.state = Node.State.AVAILABLE;
                return true;
            }
            prev.state = Node.State.AVAILABLE;
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

        //don't assume by the previous call to empty that therefor this collection will be modified
        //if it's a concurrent collection too, it might be cleared in the mean time.
        boolean modified = false;
        for (E e : c) {
            modified |= add(e);
        }
        return modified;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        if (c == this) {
            return clearInternal();
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
            return clearInternal();
        }
        if (c == this) {
            return false;
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
        clearInternal();
    }

    private boolean clearInternal() {
        while (true) {
            Node<?> currentHead = head;
            //current head has no next pointer and is therefor empty.
            //no modifications by this call, therefor false.
            if (head.next == null) return false;

            //Only return true if cassing the new node was successful. Any modifications to the head means other
            //threads are clearing concurrently, just spin and see if the head is empty.
            Node<E> newHead = new Node<E>(null);
            if (UNSAFE.compareAndSwapObject(this, headOffset, currentHead, newHead)) {
                return true;
            }
        }
    }

    private final class NodeIterator implements Iterator<E> {
        private Node<E> prev;
        private Node<E> curr;
        private Node<E> next;


        private NodeIterator() {
            curr = head;
            next = curr.next;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public E next() {
            prev = curr;
            curr = next;
            next = next.next; //yes, I just did that!


            //I am ignoring zombie(deleted values), assuming iteration is normally rather fast
            //and therefor poses no problem since we just assume deletion came after the read.
            //but if time between hasNext() and next() is a considerable amount this might produce
            //wrong results.
            //considering a different approach in the future
            return curr.value;
        }

        @Override
        public void remove() {
            if (prev == null) {
                throw new IllegalStateException("Iterator has not advanced yet");
            }
            ConcurrentCollection.this.removeNode(curr, prev);
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

        static final long nextOffset = UnsafeProvider.objectFieldOffsetFor(Node.class, "next");
        static final long stateOffset = UnsafeProvider.objectFieldOffsetFor(Node.class, "state");
    }
}
