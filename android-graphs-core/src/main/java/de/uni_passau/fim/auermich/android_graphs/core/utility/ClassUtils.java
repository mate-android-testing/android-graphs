package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ClassUtils {

    private static final Logger LOGGER = LogManager.getLogger(ClassUtils.class);

    /**
     * It seems that certain resource classes are API dependent, e.g.
     * "R$interpolator" is only available in API 21.
     */
    private static final Set<String> RESOURCE_CLASSES = new HashSet<>() {{
        add("R$anim");
        add("R$attr");
        add("R$bool");
        add("R$color");
        add("R$dimen");
        add("R$drawable");
        add("R$id");
        add("R$integer");
        add("R$layout");
        add("R$mipmap");
        add("R$string");
        add("R$style");
        add("R$styleable");
        add("R$interpolator");
        add("R$menu");
        add("R$array");
        add("R$xml");
    }};

    private ClassUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Gets the super class of the given class.
     * NOTE: We assume that the super class is contained in the same dex file.
     *
     * @param dexFile The dex file potentially containing the super class.
     * @param clazz The class for which we look up its super class.
     * @return Returns the super class of the given class if present or {@code null} otherwise.
     */
    public static ClassDef getSuperClass(final DexFile dexFile, final ClassDef clazz) {

        if (clazz.getSuperclass() == null || clazz.getSuperclass().equals("Ljava/lang/Object;")) {
            return null;
        }

        for (ClassDef classDef : dexFile.getClasses()) {
            if (clazz.getSuperclass().equals(classDef.toString())) {
                return classDef;
            }
        }

        LOGGER.warn("Super class for class " + clazz + " not found in given dex file!");
        return null;
    }

    /**
     * Gets the interfaces of the given class.
     * NOTE: We assume that the interfaces are contained in the same dex file.
     *
     * @param dexFile The dex file potentially containing the interfaces.
     * @param clazz The class for which we look up its interfaces.
     * @return Returns the interfaces of the given class if present or an empty set otherwise.
     */
    public static Set<ClassDef> getInterfaces(final DexFile dexFile, final ClassDef clazz) {

        Set<ClassDef> interfaces = new HashSet<>();
        List<String> interfaceNames = clazz.getInterfaces();

        for (ClassDef classDef : dexFile.getClasses()) {
            if (interfaceNames.contains(classDef.toString())) {
                interfaces.add(classDef);
            }
        }

        return interfaces;
    }

    /**
     * Returns the method signature of the default constructor for a given class.
     *
     * @param clazz The name of the class.
     * @return Returns the default constructor signature.
     */
    public static String getDefaultConstructor(String clazz) {
        if (isInnerClass(clazz)) {
            String outerClass = getOuterClass(clazz);
            return clazz + "-><init>(" + outerClass + ")V";
        } else {
            return clazz + "-><init>()V";
        }
    }

    /**
     * Checks whether the given class represents an inner class.
     *
     * @param clazz The class to be checked against.
     * @return Returns {@code true} if the class is an inner class,
     * otherwise {@code false} is returned.
     */
    public static boolean isInnerClass(final String clazz) {
        return clazz.contains("$");
    }

    /**
     * Returns the outer class name from the given class.
     * Should be only called in case {@link #isInnerClass(String)} returns {@code true}.
     *
     * @param className The class name to be checked.
     * @return Returns the outer class name.
     */
    public static String getOuterClass(final String className) {
        assert isInnerClass(className);
        return className.split("\\$")[0] + ";";
    }

    /**
     * Transforms a class name containing '/' into a class name with '.'
     * instead, and removes the leading 'L' as well as the ';' at the end.
     *
     * @param className The class name which should be transformed.
     * @return The transformed class name.
     */
    public static String dottedClassName(String className) {

        if (className.startsWith("[")) {
            // array type
            int index = className.indexOf('L');
            if (index == -1) {
                // primitive array type, e.g [I or [[I
                return className;
            } else {
                // complex array type, e.g. [Ljava/lang/Integer;
                int beginClassName = className.indexOf('L');
                className = className.substring(0, beginClassName)
                        + className.substring(beginClassName + 1, className.indexOf(';'));
                className = className.replace('/', '.');
                return className;
            }
        } else if (!className.startsWith("L")) {
            // primitive type
            return className;
        } else {
            className = className.substring(className.indexOf('L') + 1, className.indexOf(';'));
            className = className.replace('/', '.');
            return className;
        }
    }

    /**
     * Checks whether the given class name represents an array type, e.g. [Ljava/lang/Integer;.
     * Handles both dalvik and java based class names.
     *
     * @param className The class name to be checked.
     * @return Returns {@code true} if the class name refers to an array type,
     * otherwise {@code false} is returned.
     */
    public static boolean isArrayType(final String className) {
        return className.startsWith("[");
    }

    /**
     * Checks whether the given class represents the dynamically generated BuildConfig class.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents the dynamically generated
     * BuildConfig class, otherwise {@code false} is returned.
     */
    public static boolean isBuildConfigClass(final ClassDef classDef) {
        String className = dottedClassName(classDef.toString());
        return className.endsWith("BuildConfig");
    }

    /**
     * Checks whether the given class represents the dynamically generated R class or any
     * inner class of it.
     *
     * @param classDef The class to be checked.
     * @return Returns {@code true} if the given class represents the R class or any
     * inner class of it, otherwise {@code false} is returned.
     */
    public static boolean isResourceClass(final ClassDef classDef) {

        String className = dottedClassName(classDef.toString());
        String[] tokens = className.split("\\.");

        // check whether it is the R class itself
        if (tokens[tokens.length - 1].equals("R")) {
            return true;
        }

        // check for inner R classes
        for (String resourceClass : RESOURCE_CLASSES) {
            if (className.contains(resourceClass)) {
                return true;
            }
        }

        // TODO: can be removed, just for illustration how to process annotations
        /*
        Set<? extends Annotation> annotations = classDef.getAnnotations();

        for (Annotation annotation : annotations) {

            // check if the enclosing class is the R class
            if (annotation.getType().equals("Ldalvik/annotation/EnclosingClass;")) {
                for (AnnotationElement annotationElement : annotation.getElements()) {
                    if (annotationElement.getValue() instanceof DexBackedTypeEncodedValue) {
                        DexBackedTypeEncodedValue value = (DexBackedTypeEncodedValue) annotationElement.getValue();
                        if (value.getValue().equals("Landroidx/appcompat/R;")) {
                            return true;
                        }
                    }
                }
            }
        }
         */
        return false;
    }

    /**
     * Returns the constructors of the given class.
     *
     * @param classDef The class for which the constructors should be retrieved.
     * @return Returns the constructors of the given class.
     */
    public static Set<String> getConstructors(ClassDef classDef) {
        return StreamSupport.stream(classDef.getDirectMethods().spliterator(), false)
                .filter(method -> method.getName().startsWith("<init>"))
                .map(Method::toString)
                .collect(Collectors.toSet());
    }
}
