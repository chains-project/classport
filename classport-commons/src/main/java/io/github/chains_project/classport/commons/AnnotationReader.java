package io.github.chains_project.classport.commons;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.annotation.Annotation;

public class AnnotationReader {
    public static ClassportInfo getAnnotationValues(byte[] bytes) {
        ClassReader r;
        try {
            r = new ClassReader(bytes);
        } catch (IllegalArgumentException e) {
            System.err.println("Unable to parse Class file");
            return null;
        }

        AnnotationChecker an = new AnnotationChecker();
        r.accept(an, 0);
        HashMap<String, Object> classportValues = an.getAnnotationValues();

        // Does not contain an annotation
        if (classportValues.isEmpty())
            return null;

        try {
            return new ClassportInfo() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return ClassportInfo.class;
                }

                @Override
                public boolean isDirectDependency() {
                    // TODO Auto-generated method stub
                    return (boolean) classportValues.get("isDirectDependency");
                }

                @Override
                public String id() {
                    return classportValues.get("id").toString();
                }

                @Override
                public String artefact() {
                    return classportValues.get("artefact").toString();
                }

                @Override
                public String group() {
                    return classportValues.get("group").toString();
                }

                @Override
                public String version() {
                    return classportValues.get("version").toString();
                }

                @Override
                public String[] childIds() {
                    return (String[]) classportValues.get("childIds");
                }
            };
        } catch (NullPointerException e) {
            System.err.println(
                    "Missing annotation value. "
                            + "If Classport has been updated since this class was modified, "
                            + "you may need to run the embedding phase again.\n" + e.getMessage());
            return null;
        }
    }

    // The following classes are static as they don't require any field info from
    // their parent and are only ever used here.

    private static class AnnotationChecker extends ClassVisitor {
        private Class<?> annotationClass;
        private final HashMap<String, Object> annotationValues = new HashMap<>();

        AnnotationChecker() {
            super(Opcodes.ASM9);
            this.annotationClass = ClassportInfo.class;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean vis) {
            if (desc.equals(annotationClass.descriptorString())) {
                return new AnnotationParser(annotationValues);
            }
            return super.visitAnnotation(desc, vis);
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

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationVisitor(Opcodes.ASM9, av) {
                ArrayList<String> childIds = new ArrayList<>();

                @Override
                public void visit(String name, Object value) {
                    childIds.add(String.valueOf(value));
                }

                @Override
                public void visitEnd() {
                    annotationValues.put(name, childIds.toArray(new String[0]));
                }
            };
        }
    }
}
