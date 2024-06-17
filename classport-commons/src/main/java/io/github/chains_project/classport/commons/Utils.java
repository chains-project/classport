package io.github.chains_project.classport.commons;

// Adapted from
// https://github.com/openjdk/jdk22/blob/fe9f05023e5a916b21e2db72fa5b1e8368a2c07d/src/java.base/share/classes/java/lang/Class.java#L4588
// so that we can get a descriptor string for classes even without running JDK >= 12.
// If Classport is ever Java >= 12 exclusive, this can all be removed.
public class Utils {
    public static String descriptorString(Class<?> c) {
        if (c.isPrimitive())
            return getPrimitiveDescriptor(c);

        if (c.isArray()) {
            return "[" + descriptorString(c.getComponentType());
            /*
             * Does not support hidden classes as this is a Java > 15 feature
             * } else if (c.isHidden()) {
             * String name = c.getName();
             * int index = name.indexOf('/');
             * return new StringBuilder(name.length() + 2)
             * .append('L')
             * .append(name.substring(0, index).replace('.', '/'))
             * .append('.')
             * .append(name, index + 1, name.length())
             * .append(';')
             * .toString();
             */
        } else {
            String name = c.getName().replace('.', '/');
            return new StringBuilder(name.length() + 2)
                    .append('L')
                    .append(name)
                    .append(';')
                    .toString();
        }
    }

    // See
    // https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3.2
    private static String getPrimitiveDescriptor(Class<?> c) {
        if (c.equals(byte.class))
            return "B";
        else if (c.equals(char.class))
            return "C";
        else if (c.equals(double.class))
            return "D";
        else if (c.equals(float.class))
            return "F";
        else if (c.equals(int.class))
            return "I";
        else if (c.equals(long.class))
            return "J";
        else if (c.equals(short.class))
            return "S";
        else if (c.equals(boolean.class))
            return "Z";
        else
            throw new IllegalArgumentException("Expected primitive type");
    }
}
