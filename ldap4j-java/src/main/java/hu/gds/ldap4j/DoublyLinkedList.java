package hu.gds.ldap4j;

import java.util.NoSuchElementException;
import org.jetbrains.annotations.Nullable;

public class DoublyLinkedList<T> {
    private static class Node<T> {
        public @Nullable Node<T> next;
        public @Nullable Node<T> previous;
        public final T value;

        public Node(T value) {
            this.value=value;
        }
    }

    private @Nullable Node<T> first;
    private @Nullable Node<T> last;

    /**
     * @return removes element from the list
     */
    public Runnable addLast(T value) {
        Node<T> node=new Node<>(value);
        if (null==first) {
            if (null!=last) {
                throw new IllegalStateException();
            }
            first=node;
        }
        else if (null==last) {
            throw new IllegalStateException();
        }
        else {
            last.next=node;
            node.previous=last;
        }
        last=node;
        return remove(node);
    }

    public boolean isEmpty() {
        return null==first;
    }

    private Runnable remove(Node<T> node) {
        return ()->{
            Node<T> next=node.next;
            Node<T> previous=node.previous;
            node.next=null;
            node.previous=null;
            if ((null==next) && (null==previous)) {
                if (node==first) {
                    if (node!=last) {
                        throw new IllegalStateException();
                    }
                    first=null;
                    last=null;
                }
                return;
            }
            if ((null!=next) && (node!=next.previous)) {
                throw new IllegalStateException();
            }
            if ((null!=previous) && (node!=previous.next)) {
                throw new IllegalStateException();
            }
            if (null==next) {
                if (node!=last) {
                    throw new IllegalStateException();
                }
                last=previous;
                previous.next=null;
                return;
            }
            if (null==previous) {
                if (node!=first) {
                    throw new IllegalStateException();
                }
                first=next;
                next.previous=null;
                return;
            }
            next.previous=previous;
            previous.next=next;
        };
    }

    public T removeFirst() throws NoSuchElementException {
        if (null==first) {
            throw new NoSuchElementException();
        }
        if (null==last) {
            throw new IllegalStateException();
        }
        Node<T> node=first;
        if (first==last) {
            first=null;
            last=null;
        }
        else {
            first=node.next;
            node.next=null;
            if (null==first) {
                throw new IllegalStateException();
            }
            first.previous=null;
        }
        if (null!=node.next) {
            throw new IllegalStateException();
        }
        if (null!=node.previous) {
            throw new IllegalStateException();
        }
        return node.value;
    }
}
