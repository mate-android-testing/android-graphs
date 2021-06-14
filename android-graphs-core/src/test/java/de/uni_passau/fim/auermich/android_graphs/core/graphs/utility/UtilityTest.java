package de.uni_passau.fim.auermich.android_graphs.core.graphs.utility;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import de.uni_passau.fim.auermich.android_graphs.core.utility.ClassUtils;
import de.uni_passau.fim.auermich.android_graphs.core.utility.ComponentUtils;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;
import org.antlr.runtime.RecognitionException;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.smali.SmaliTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class UtilityTest {

    private static final int OPCODE_API = 28;

    private ClassDef loadSmaliFile(InputStream inputStream, int apiLevel) {

        ByteSource byteSource = new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return inputStream;
            }
        };

        try {
            String smaliCode = byteSource.asCharSource(Charsets.UTF_8).read();
            return SmaliTestUtils.compileSmali(smaliCode, apiLevel);
        } catch (IOException | RecognitionException e) {
            throw new RuntimeException(e);
        }
    }

    @DisplayName("Testing primitive array typed class name.")
    @Test
    void testPrimitiveArrayTypeToJavaClassName() {
        String primitiveArrayType = "[I";
        String dottedClassName = ClassUtils.dottedClassName(primitiveArrayType);
        assertEquals(primitiveArrayType, dottedClassName);
    }

    @DisplayName("Testing complex array typed class name.")
    @Test
    void testComplexArrayTypeToJavaClassName() {
        String complexArrayType = "[Ljava/lang/Object;";
        String dottedClassName = ClassUtils.dottedClassName(complexArrayType);
        assertEquals("[java.lang.Object", dottedClassName);
    }

    @DisplayName("Testing primitive 2D array typed class name.")
    @Test
    void testPrimitive2DArrayTypeToJavaClassName() {
        String primitive2DArrayType = "[[I";
        String dottedClassName = ClassUtils.dottedClassName(primitive2DArrayType);
        assertEquals(primitive2DArrayType, dottedClassName);
    }

    @DisplayName("Testing complex 2D array typed class name.")
    @Test
    void testComplex2DArrayTypeToJavaClassName() {
        String complex2DArrayType = "[[Ljava/lang/Object;";
        String dottedClassName = ClassUtils.dottedClassName(complex2DArrayType);
        assertEquals("[[java.lang.Object", dottedClassName);
    }

    @DisplayName("Testing inner (nested) class name.")
    @Test
    void testInnerClassTypeToJavaClassName() {
        String innerClassType = "Lb/a/a/a/b$a;";
        String dottedClassName = ClassUtils.dottedClassName(innerClassType);
        assertEquals("b.a.a.a.b$a", dottedClassName);
    }

    @DisplayName("Testing inner (nested) array class name.")
    @Test
    void testInnerArrayClassTypeToJavaClassName() {
        String innerClassType = "[Lb/a/a/a/b$a;";
        String dottedClassName = ClassUtils.dottedClassName(innerClassType);
        assertEquals("[b.a.a.a.b$a", dottedClassName);
    }

    @DisplayName("Testing extraction of outer class name.")
    @Test
    void testOuterClassName() {
        String className = "Lb/a/a/a/b$a;";
        assertEquals("Lb/a/a/a/b;", ClassUtils.getOuterClass(className));
    }

    @DisplayName("Testing whether the given method is contained in the dex file!")
    @Test
    void testContainsMethod() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("BMIMain.smali");
        assertNotNull(inputStream, "Couldn't load resource file!");
        ClassDef classDef = loadSmaliFile(inputStream, OPCODE_API);
        DexFile dexFile = new ImmutableDexFile(Opcodes.forApi(OPCODE_API), Collections.singletonList(classDef));
        Optional<Method> method = MethodUtils.searchForTargetMethod(dexFile, "Lcom/zola/bmi/BMIMain;->calculateBMI(DD)D");
        assertTrue(method.isPresent(), "Target method not contained in dex file!");
        assertEquals(method.get().toString(), "Lcom/zola/bmi/BMIMain;->calculateBMI(DD)D");
    }

    @DisplayName("Testing whether the given class represents an activity!")
    @Test
    void testIsActivityClass() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("BMIMain.smali");
        assertNotNull(inputStream, "Couldn't load resource file!");
        ClassDef classDef = loadSmaliFile(inputStream, OPCODE_API);
        assertTrue(ComponentUtils.isActivity(Collections.singletonList(classDef), classDef));
    }
}

