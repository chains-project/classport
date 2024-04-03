package tld.domain.me.classport.agent;

import org.objectweb.asm.*;
import java.util.HashMap;
import java.lang.annotation.Annotation;

public class AnnotationReader {
    // TODO: Return a ClassportInfo object here instead (we no likey using `get`
    // operations to extract things)
    public static HashMap<String, Object> getAnnotationValues(byte[] bytes, Class<? extends Annotation> cls) {
        ClassReader r = new ClassReader(bytes);
        AnnotationChecker an = new AnnotationChecker(cls);
        r.accept(an, 0);
        return an.getAnnotationValues();
    }

    // The following classes are static as they don't require any field info from
    // their parent and are only ever used here.

    private static class AnnotationChecker extends ClassVisitor {
        private Class<?> annotationClass;
        private final HashMap<String, Object> annotationValues = new HashMap<>();

        AnnotationChecker(Class<?> annotationClass) {
            super(Opcodes.ASM9);
            this.annotationClass = annotationClass;
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean vis) {
            if (desc.equals(annotationClass.descriptorString())) {
                return new AnnotationParser(annotationValues);
            }
            return cv.visitAnnotation(desc, vis);
        }

        public HashMap<String, Object> getAnnotationValues() {
            return this.annotationValues;
        }
    }

    private static class AnnotationParser extends AnnotationVisitor {
        private final HashMap<String, Object> annotationValues;

        protected AnnotationParser(HashMap<String, Object> annotationValues) {
            super(Opcodes.ASM9);
            this.annotationValues = annotationValues;
        }

        @Override
        public void visit(String name, Object value) {
            annotationValues.put(name, value);
        }
    }
}
