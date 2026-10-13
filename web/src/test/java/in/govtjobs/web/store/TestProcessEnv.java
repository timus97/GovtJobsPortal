package in.govtjobs.web.store;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Test-only. Points store code at a temp path without editing product classes. */
final class TestProcessEnv {

    private TestProcessEnv() {}

    static void set(String key, String value) {
        // Windows keeps the live map in theCaseInsensitiveEnvironment.
        // Linux keeps it in theEnvironment. Update every map that accepts a write.
        int updated = 0;
        RuntimeException rejected = null;
        for (String fieldName : new String[] {"theCaseInsensitiveEnvironment", "theEnvironment"}) {
            try {
                Map<String, String> env = envMap(fieldName);
                if (value == null) {
                    env.remove(key);
                } else {
                    env.put(key, value);
                }
                updated++;
            } catch (NoSuchFieldException ignored) {
                // This JDK uses the other field name.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not set " + key, e);
            } catch (UnsupportedOperationException e) {
                rejected = e;
            }
        }
        if (updated == 0) {
            throw new IllegalStateException("Could not set " + key, rejected);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> envMap(String fieldName) throws ReflectiveOperationException {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method staticFieldBase = unsafeType.getMethod("staticFieldBase", Field.class);
        Method staticFieldOffset = unsafeType.getMethod("staticFieldOffset", Field.class);
        Method getObject = unsafeType.getMethod("getObject", Object.class, long.class);
        Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
        Field field = processEnvironment.getDeclaredField(fieldName);
        Object base = staticFieldBase.invoke(unsafe, field);
        long offset = (long) staticFieldOffset.invoke(unsafe, field);
        return (Map<String, String>) getObject.invoke(unsafe, base, offset);
    }
}
