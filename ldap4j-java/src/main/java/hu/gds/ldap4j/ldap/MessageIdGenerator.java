package hu.gds.ldap4j.ldap;

public interface MessageIdGenerator {
    class Interval implements MessageIdGenerator {
        private final int max;
        private final int min;
        private int next;

        public Interval(int max, int min) {
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

    static MessageIdGenerator constant(int value) {
        return new Interval(value, value);
    }

    static MessageIdGenerator interval(int max, int min) {
        return new Interval(max, min);
    }

    int next();

    static MessageIdGenerator smallValues() {
        return interval(127, 1);
    }
}
