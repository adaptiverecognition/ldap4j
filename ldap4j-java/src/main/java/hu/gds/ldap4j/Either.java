package hu.gds.ldap4j;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public sealed interface Either<T, U> {
    final class Left<T, U> implements Either<T, U> {
        private final T value;

        public Left(T value) {
            this.value=value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this==obj) {
                return true;
            }
            if ((null==obj) || (!getClass().equals(obj.getClass()))) {
                return false;
            }
            Left<?, ?> left=(Left<?, ?>)obj;
            return Objects.equals(value, left.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public boolean isLeft() {
            return true;
        }

        @Override
        public T left() {
            return value;
        }

        @Override
        public String toString() {
            return "Left("+value+")";
        }
    }

    final class Right<T, U> implements Either<T, U> {
        private final U value;

        public Right(U value) {
            this.value=value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this==obj) {
                return true;
            }
            if ((null==obj) || (!getClass().equals(obj.getClass()))) {
                return false;
            }
            Right<?, ?> right=(Right<?, ?>)obj;
            return Objects.equals(value, right.value);
        }

        @Override
        public int hashCode() {
            return 13*Objects.hashCode(value);
        }

        @Override
        public boolean isRight() {
            return true;
        }

        @Override
        public U right() {
            return value;
        }

        @Override
        public String toString() {
            return "Right("+value+")";
        }
    }

    default boolean isLeft() {
        return false;
    }

    default boolean isRight() {
        return false;
    }

    default T left() {
        throw new ClassCastException("cannot cast %s to %s".formatted(this, Left.class));
    }

    static <T, U> @NotNull Left<T, U> left(T value) {
        return new Left<>(value);
    }

    default U right() {
        throw new ClassCastException("cannot cast %s to %s".formatted(this, Right.class));
    }

    static <T, U> @NotNull Right<T, U> right(U value) {
        return new Right<>(value);
    }
}
