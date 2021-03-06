// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.psi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE

class AnnotatedTypeTest extends LightJavaCodeInsightFixtureTestCase {
  private PsiElementFactory factory
  private PsiElement context

  @Override
  void setUp() {
    super.setUp()

    factory = myFixture.javaFacade.elementFactory
    context = myFixture.addClass("""\
      package pkg;

      import java.lang.annotation.*;

      @interface A { }
      @Target(ElementType.TYPE_USE) @interface TA { int value() default 42; }

      class O { class I { } }

      @SuppressWarnings("ExceptionClassNameDoesntEndWithException") class E1 extends Exception { }
      @SuppressWarnings("ExceptionClassNameDoesntEndWithException") class E2 extends Exception { }""".stripIndent())
  }

  @Override
  void tearDown() {
    factory = null
    context = null
    super.tearDown()
  }

  void testPrimitiveArrayType() {
    doTest("@A @TA(1) int @TA(2) [] a", "@pkg.TA(1) int @pkg.TA(2) []", "int[]",
           "@TA int @TA []", "int[]")
  }

  void testEllipsisType() {
    doTest("@TA int @TA ... p", "@pkg.TA int @pkg.TA ...", "int...",
           "@TA int @TA ...", "int...")
  }

  void testClassReferenceType() {
    doTest("@A @TA(1) String s", "java.lang.@pkg.TA(1) String", "java.lang.String",
           "@TA String", "String")
  }

  void testQualifiedClassReferenceType() {
    doTest("@A java.lang.@TA(1) String s", "java.lang.@pkg.TA(1) String", "java.lang.String",
           "@TA String", "String")
  }

  void testQualifiedPackageClassReferenceType() {
    doTest("@A @TA java.lang.String s", "java.lang.String", "java.lang.String",   // packages cannot have type annotations
           "String", "String")
  }

  void testPartiallyQualifiedClassReferenceType() {
    doTest("@TA(1) O.@TA(2) I i", "pkg.@pkg.TA(1) O.@pkg.TA(2) I", "pkg.O.I",
           "@TA I", "I")
  }

  void testCStyleArrayType() {
    doTest("@A @TA(1) String @TA(2) [] f @TA(3) []", "java.lang.@pkg.TA(1) String @pkg.TA(2) [] @pkg.TA(3) []", "java.lang.String[][]",
           "@TA String @TA [] @TA []", "String[][]")
  }

  void testWildcardType() {
    doTest("Class<@TA(1) ?> c", "java.lang.Class<@pkg.TA(1) ?>", "java.lang.Class<?>",
           "Class<@TA ?>", "Class<?>")
  }

  void testWildcardBoundType() {
    doTest("Class<@TA(1) ? extends @TA(2) Object> c", "java.lang.Class<@pkg.TA(1) ? extends java.lang.@pkg.TA(2) Object>", 
           "java.lang.Class<? extends java.lang.Object>",
           "Class<@TA ? extends @TA Object>", "Class<? extends Object>")
  }

  void testDisjunctionType() {
    def psi = factory.createStatementFromText("try { } catch (@A @TA(1) E1 | @TA(2) E2 e) { }", context) as PsiTryStatement
    assertTypeText psi.catchBlockParameters[0].type, "pkg.@pkg.TA(1) E1 | pkg.@pkg.TA(2) E2", "pkg.E1 | pkg.E2",
          "@TA E1 | @TA E2", "E1 | E2"
  }

  void testDiamondType() {
    def psi = factory.createStatementFromText("Class<@TA String> cs = new Class<>()", context) as PsiDeclarationStatement
    def var = psi.declaredElements[0] as PsiVariable
    assertTypeText var.initializer.type, "java.lang.Class<java.lang.@pkg.TA String>", "java.lang.Class<java.lang.String>",
        "Class<@TA String>", "Class<String>"
  }

  void testImmediateClassType() {
    def aClass = myFixture.javaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT)
    def annotations = factory.createParameterFromText("@TA int x", context).modifierList.annotations
    def type = new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY, LanguageLevel.JDK_1_8, annotations)
    assertTypeText type, "java.lang.@pkg.TA Object", CommonClassNames.JAVA_LANG_OBJECT,
      "@TA Object", "Object"
  }

  void testFieldType() {
    def psi = factory.createFieldFromText("@A @TA(1) String f;", context)
    assertTypeText psi.type, "java.lang.@pkg.TA(1) String", "java.lang.String",
      "@TA String", "String"
    assertAnnotations psi.type, "@TA(1)"
  }

  void testMethodReturnType() {
    def psi = factory.createMethodFromText("@A @TA(1) <T> @TA(2) String m() { return null; }", context)
    assertTypeText psi.returnType, "java.lang.@pkg.TA(1) @pkg.TA(2) String", "java.lang.String",
      "@TA @TA String", "String"
    assertAnnotations psi.returnType, "@TA(1)", "@TA(2)"
  }

  void testIsAnnotated() {
    def unqualified = factory.createParameterFromText("@A @TA(1) String p", context)
    assert AnnotationUtil.isAnnotated(unqualified, "pkg.A", CHECK_TYPE)
    assert AnnotationUtil.isAnnotated(unqualified, "pkg.TA", CHECK_TYPE)

    def qualified = factory.createParameterFromText("@A java.lang.@TA(1) String p", context)
    assert AnnotationUtil.isAnnotated(qualified, "pkg.A", CHECK_TYPE)
    assert AnnotationUtil.isAnnotated(qualified, "pkg.TA", CHECK_TYPE)
  }


  private void doTest(String text, String annotated, String canonical, String presentableAnnotated, String presentable) {
    assertTypeText(factory.createParameterFromText(text, context).type, annotated, canonical, presentableAnnotated, presentable)
  }

  private static void assertTypeText(PsiType type, String annotated, String canonical, String presentableAnnotated, String presentable) {
    assert type.getCanonicalText(true) == annotated
    assert type.getCanonicalText(false) == canonical
    assert type.getPresentableText(true) == presentableAnnotated
    assert type.getPresentableText(false) == presentable
  }

  private static void assertAnnotations(PsiType type, String... annotations) {
    assert type.annotations.collect { it.text } == annotations.toList()
  }
}