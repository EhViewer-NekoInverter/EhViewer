/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.yorozuya;

import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ObjectUtils {
    private ObjectUtils() {
    }

    /**
     * Returns true if two possibly-null objects are equal.
     */
    public static boolean equal(@Nullable Object a, @Nullable Object b) {
        return Objects.equals(a, b);
    }

    /**
     * Returns "null" for null or {@code o.toString()}.
     */
    public static String toString(Object o) {
        return (o == null) ? "null" : o.toString();
    }

    private static void dumpObject(Object o, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(o.getClass().getName());
        writer.write('\n');
    }

    private static void dumpBoolean(boolean z, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Boolean.toString(z));
        writer.write('\n');
    }

    private static void dumpByte(byte b, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Byte.toString(b));
        writer.write('\n');
    }

    private static void dumpChar(char c, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Character.toString(c));
        writer.write('\n');
    }

    private static void dumpShort(short s, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Short.toString(s));
        writer.write('\n');
    }

    private static void dumpInt(int i, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Integer.toString(i));
        writer.write('\n');
    }

    private static void dumpLong(long j, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Long.toString(j));
        writer.write('\n');
    }

    private static void dumpFloat(float f, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Float.toString(f));
        writer.write('\n');
    }

    private static void dumpDouble(double d, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(Double.toString(d));
        writer.write('\n');
    }

    private static void dumpArray(Object array, PrintWriter writer, String prefix, boolean skipFirstPrefix) {
        if (skipFirstPrefix) {
            dumpObject(array, writer, "");
        } else {
            dumpObject(array, writer, prefix);
        }

        String newPrefix = prefix + "    ";
        writer.write(newPrefix);
        writer.write('[');
        writer.write('\n');

        switch (array) {
            case Object[] a -> {
                for (Object o : a) {
                    dump(o, writer, newPrefix, false);
                }
            }
            case boolean[] a -> {
                for (boolean b : a) {
                    dumpBoolean(b, writer, newPrefix);
                }
            }
            case byte[] a -> {
                for (byte b : a) {
                    dumpByte(b, writer, newPrefix);
                }
            }
            case char[] a -> {
                for (char c : a) {
                    dumpChar(c, writer, newPrefix);
                }
            }
            case short[] a -> {
                for (short value : a) {
                    dumpShort(value, writer, newPrefix);
                }
            }
            case int[] a -> {
                for (int j : a) {
                    dumpInt(j, writer, newPrefix);
                }
            }
            case long[] a -> {
                for (long value : a) {
                    dumpLong(value, writer, newPrefix);
                }
            }
            case float[] a -> {
                for (float v : a) {
                    dumpFloat(v, writer, newPrefix);
                }
            }
            case double[] a -> {
                for (double v : a) {
                    dumpDouble(v, writer, newPrefix);
                }
            }
            case List<?> list -> {
                for (Object o : list) {
                    dump(o, writer, newPrefix);
                }
            }
            case Set<?> set -> {
                for (Object o : set) {
                    dump(o, writer, newPrefix);
                }
            }
            case Map<?, ?> map -> {
                for (Object key : map.keySet()) {
                    Object value = map.get(key);
                    writer.write(newPrefix);
                    writer.write(key.toString());
                    writer.write(": ");
                    dump(value, writer, newPrefix, true);
                }
            }
            default -> throw new IllegalStateException(array + " is not array");
        }

        writer.write(newPrefix);
        writer.write(']');
        writer.write('\n');
    }

    public static void dump(Object o, PrintWriter writer) {
        dump(o, writer, "", false);
    }

    public static void dump(Object o, PrintWriter writer, String prefix) {
        dump(o, writer, prefix, false);
    }

    public static void dump(Object o, PrintWriter writer, String prefix, boolean skipFirstPrefix) {
        if (o == null) {
            if (!skipFirstPrefix) {
                writer.write(prefix);
            }
            writer.write("null\n");
        } else if (o.getClass().isArray() || o instanceof List || o instanceof Set || o instanceof Map) {
            dumpArray(o, writer, prefix, skipFirstPrefix);
        } else if (o.getClass().isPrimitive() || o instanceof Boolean ||
                o instanceof Byte || o instanceof Character || o instanceof Short ||
                o instanceof Integer || o instanceof Long || o instanceof Float ||
                o instanceof Double || o instanceof String) {
            if (!skipFirstPrefix) {
                writer.write(prefix);
            }
            writer.write(o.toString());
            writer.write('\n');
        } else {
            if (skipFirstPrefix) {
                dumpObject(o, writer, "");
            } else {
                dumpObject(o, writer, prefix);
            }

            String newPrefix = prefix + "    ";
            for (Field field : o.getClass().getDeclaredFields()) {
                // Skip static filed
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                String name = field.getName();
                try {
                    Object value = field.get(o);
                    writer.write(newPrefix);
                    writer.write(name);
                    writer.write(": ");
                    dump(value, writer, newPrefix, true);
                } catch (IllegalAccessException e) {
                    // Ignore
                }
            }
        }
    }
}
