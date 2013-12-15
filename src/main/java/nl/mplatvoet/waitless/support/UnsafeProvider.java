package nl.mplatvoet.waitless.support;


import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

@SuppressWarnings("restriction")
public class UnsafeProvider {
    private UnsafeProvider() {
    }

    private static final Unsafe UNSAFE = extractInstance();

    @NotNull
    public static Unsafe instance() {
        return UNSAFE;
    }

    @NotNull
    private static Unsafe extractInstance() {
        try {

            Field declaredField = Unsafe.class.getDeclaredField("theUnsafe");
            declaredField.setAccessible(true);
            return (Unsafe) declaredField.get(null);

        } catch (Exception e) {
            throw new RuntimeException("Unable to obtain the sun.misc.Unsafe");
        }
    }
}
