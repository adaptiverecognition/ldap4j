package hu.gds.ldap4j.ldap;

public interface MessageIdGenerator {
    abstract class Abstract implements MessageIdGenerator {
        private final boolean signKludge;

        public Abstract(boolean signKludge) {
            this.signKludge=signKludge;
        }

        @Override
        public boolean signKludge() {
            return signKludge;
        }
    }

    class Interval extends Abstract {
        private final int max;
        private final int min;
        private int next;

        public Interval(int max, int min, boolean signKludge) {
            super(signKludge);
            this.max=max;
            this.min=min;
            if (0>=min) {
                throw new IllegalArgumentException("0 >= min %,d".formatted(min));
            }
            if (max<min) {
                throw new IllegalArgumentException("max %,d < min %,d".formatted(max, min));
            }
            next=min;
        }

        @Override
        public int next() {
            int result=next;
            if (max==next) {
                next=min;
            }
            else {
                ++next;
            }
            return result;
        }
    }

    static MessageIdGenerator constant(boolean signKludge, int value) {
        return new Interval(value, value, signKludge);
    }

    static MessageIdGenerator interval(int max, int min, boolean signKludge) {
        return new Interval(max, min, signKludge);
    }

    int next();

    boolean signKludge();

    static MessageIdGenerator smallValues(boolean signKludge) {
        return interval(127, 1, signKludge);
    }
}
