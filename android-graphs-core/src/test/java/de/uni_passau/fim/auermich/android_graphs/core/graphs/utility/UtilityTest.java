package de.uni_passau.fim.auermich.android_graphs.core.graphs.utility;

import de.uni_passau.fim.auermich.android_graphs.core.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilityTest {

    @DisplayName("Testing primitive array typed class name.")
    @Test
    void testPrimitiveArrayTypeToJavaClassName() {
        String primitiveArrayType = "[I";
        String dottedClassName = Utility.dottedClassName(primitiveArrayType);
        assertEquals(primitiveArrayType, dottedClassName);
    }

    @DisplayName("Testing complex array typed class name.")
    @Test
    void testComplexArrayTypeToJavaClassName() {
        String complexArrayType = "[Ljava/lang/Object;";
        String dottedClassName = Utility.dottedClassName(complexArrayType);
        assertEquals("[java.lang.Object", dottedClassName);
    }

    @DisplayName("Testing primitive 2D array typed class name.")
    @Test
    void testPrimitive2DArrayTypeToJavaClassName() {
        String primitive2DArrayType = "[[I";
        String dottedClassName = Utility.dottedClassName(primitive2DArrayType);
        assertEquals(primitive2DArrayType, dottedClassName);
    }

    @DisplayName("Testing complex 2D array typed class name.")
    @Test
    void testComplex2DArrayTypeToJavaClassName() {
        String complex2DArrayType = "[[Ljava/lang/Object;";
        String dottedClassName = Utility.dottedClassName(complex2DArrayType);
        assertEquals("[[java.lang.Object", dottedClassName);
    }

    @DisplayName("Testing inner (nested) class name.")
    @Test
    void testInnerClassTypeToJavaClassName() {
        String innerClassType = "Lb/a/a/a/b$a;";
        String dottedClassName = Utility.dottedClassName(innerClassType);
        assertEquals("b.a.a.a.b$a", dottedClassName);
    }

    @DisplayName("Testing inner (nested) array class name.")
    @Test
    void testInnerArrayClassTypeToJavaClassName() {
        String innerClassType = "[Lb/a/a/a/b$a;";
        String dottedClassName = Utility.dottedClassName(innerClassType);
        assertEquals("[b.a.a.a.b$a", dottedClassName);
    }

    @DisplayName("Testing extraction of outer class name.")
    @Test
    void testOuterClassName() {
        String className = "Lb/a/a/a/b$a;";
        assertEquals("Lb/a/a/a/b;", Utility.getOuterClass(className));
    }
}

