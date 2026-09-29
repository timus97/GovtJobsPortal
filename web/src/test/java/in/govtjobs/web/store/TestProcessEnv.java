package in.govtjobs.web.store;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Test-only. Points store code at a temp path without editing product classes. */
final class TestProcessEnv {

    private TestProcessEnv() {}

    static void set(String key, String value) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method staticFieldBase = unsafeType.getMethod("staticFieldBase", Field.class);
            Method staticFieldOffset = unsafeType.getMethod("staticFieldOffset", Field.class);
            Method getObject = unsafeType.getMethod("getObject", Object.class, long.class);
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            Field field = processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment");
            Object base = staticFieldBase.invoke(unsafe, field);
            long offset = (long) staticFieldOffset.invoke(unsafe, field);
            @SuppressWarnings("unchecked")
            Map<String, String> env = (Map<String, String>) getObject.invoke(unsafe, base, offset);
            if (value == null) {
                env.remove(key);
            } else {
                env.put(key, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set " + key, e);
        }
    }
}
