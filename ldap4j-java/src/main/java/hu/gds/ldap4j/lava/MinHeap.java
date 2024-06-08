package hu.gds.ldap4j.lava;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.ToLongFunction;
import org.jetbrains.annotations.NotNull;

public class MinHeap<T> {
    private static class Node<T> {
        private Integer index;
        private T value;

        public Node(int index, T value) {
            this.index=index;
            this.value=value;
        }

        public void checkIndex(int index) {
            if ((null==this.index) || (index!=this.index)) {
                throw new IllegalStateException("this.index %s, index %s".formatted(this.index, index));
            }
        }

        public void index(int index) {
            if (null==this.index) {
                throw new IllegalStateException();
            }
            this.index=index;
        }

        public T peek() {
            if (null==index) {
                throw new IllegalStateException();
            }
            return value;
        }

        public T remove() {
            if (null==index) {
                throw new IllegalStateException();
            }
            T result=value;
            index=null;
            value=null;
            return result;
        }
    }

    private final List<Node<T>> heap;
    private final @NotNull ToLongFunction<T> nanosSelector;

    public MinHeap(int initialSize, @NotNull ToLongFunction<T> nanosSelector) {
        this.nanosSelector=Objects.requireNonNull(nanosSelector, "nanosSelector");
        heap=new ArrayList<>(initialSize);
    }

    public LongPredicate add(long nowNanos, T value) {
        Node<T> node=new Node<>(heap.size(), value);
        heap.add(node);
        fixUp(node.index, nowNanos);
        return (nowNanos2)->{
            if (null==node.index) {
                return false;
            }
            int index=node.index;
            if (heap.get(index)!=node) {
                throw new IllegalStateException();
            }
            Node<T> last=heap.remove(heap.size()-1);
            last.checkIndex(heap.size());
            if (index!=heap.size()) {
                last.index(index);
                heap.set(index, last);
                fixDown(index, nowNanos2);
            }
            node.remove();
            return true;
        };
    }

    private void fixDown(int index, long nowNanos) {
        while (heap.size()>index) {
            Node<T> parent=heap.get(index);
            parent.checkIndex(index);
            Node<T> left=null;
            int leftIndex=2*index+1;
            boolean leftMin=false;
            Node<T> min=parent;
            if (heap.size()>leftIndex) {
                left=heap.get(leftIndex);
                left.checkIndex(leftIndex);
                if (0<Clock.compareEndNanos(
                        nanosSelector.applyAsLong(min.peek()),
                        nanosSelector.applyAsLong(left.peek()),
                        nowNanos)) {
                    min=left;
                    leftMin=true;
                }
            }
            int rightIndex=leftIndex+1;
            Node<T> right=null;
            boolean rightMin=false;
            if (heap.size()>rightIndex) {
                right=heap.get(rightIndex);
                right.checkIndex(rightIndex);
                if (0<Clock.compareEndNanos(
                        nanosSelector.applyAsLong(min.peek()),
                        nanosSelector.applyAsLong(right.peek()),
                        nowNanos)) {
                    rightMin=true;
                }
            }
            if (rightMin) {
                right.index(index);
                parent.index(rightIndex);
                heap.set(index, right);
                heap.set(rightIndex, parent);
                index=rightIndex;
            }
            else if (leftMin) {
                left.index(index);
                parent.index(leftIndex);
                heap.set(index, left);
                heap.set(leftIndex, parent);
                index=leftIndex;
            }
            else {
                break;
            }
        }
    }

    private void fixUp(int index, long nowNanos) {
        while (0<index) {
            int parentIndex=(index-1)/2;
            Node<T> node=heap.get(index);
            node.checkIndex(index);
            Node<T> parent=heap.get(parentIndex);
            parent.checkIndex(parentIndex);
            if (0>=Clock.compareEndNanos(
                    nanosSelector.applyAsLong(parent.peek()),
                    nanosSelector.applyAsLong(node.peek()),
                    nowNanos)) {
                break;
            }
            node.index(parentIndex);
            parent.index(index);
            heap.set(index, parent);
            heap.set(parentIndex, node);
            index=parentIndex;
        }
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    public T peekMin() throws NoSuchElementException {
        return heap.get(0).peek();
    }

    public T removeMin(long nowNanos) throws NoSuchElementException {
        if (heap.isEmpty()) {
            throw new NoSuchElementException();
        }
        Node<T> last=heap.remove(heap.size()-1);
        Node<T> result;
        if (heap.isEmpty()) {
            result=last;
        }
        else {
            result=heap.set(0, last);
            last.index(0);
            fixDown(0, nowNanos);
        }
        result.checkIndex(0);
        return result.remove();
    }

    public int size() {
        return heap.size();
    }
}
